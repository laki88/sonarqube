/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.computation.task.projectanalysis.issue;

import static org.sonar.server.computation.task.projectanalysis.component.ComponentVisitor.Order.POST_ORDER;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.tracking.Tracking;
import org.sonar.db.component.BranchType;
import org.sonar.server.computation.task.projectanalysis.analysis.AnalysisMetadataHolder;
import org.sonar.server.computation.task.projectanalysis.analysis.Branch;
import org.sonar.server.computation.task.projectanalysis.component.Component;
import org.sonar.server.computation.task.projectanalysis.component.Component.Status;
import org.sonar.server.computation.task.projectanalysis.component.CrawlerDepthLimit;
import org.sonar.server.computation.task.projectanalysis.component.TypeAwareVisitorAdapter;
import org.sonar.server.computation.task.projectanalysis.filemove.MovedFilesRepository;
import org.sonar.server.util.cache.DiskCache;

import com.google.common.base.Optional;

public class IntegrateIssuesVisitor extends TypeAwareVisitorAdapter {

  private final TrackerExecution tracker;
  private final IssueCache issueCache;
  private final IssueLifecycle issueLifecycle;
  private final IssueVisitors issueVisitors;
  private final ComponentsWithUnprocessedIssues componentsWithUnprocessedIssues;
  private final MovedFilesRepository movedFilesRepository;
  private final ComponentIssuesLoader issuesLoader;
  private final AnalysisMetadataHolder analysisMetadataHolder;
  private final ShortBranchTrackerExecution shortBranchTrackerExecution;

  public IntegrateIssuesVisitor(TrackerExecution tracker, IssueCache issueCache, IssueLifecycle issueLifecycle, IssueVisitors issueVisitors,
    ComponentsWithUnprocessedIssues componentsWithUnprocessedIssues, MovedFilesRepository movedFilesRepository, ComponentIssuesLoader issuesLoader,
    AnalysisMetadataHolder analysisMetadataHolder, ShortBranchTrackerExecution shortBranchTrackerExecution) {
    super(CrawlerDepthLimit.FILE, POST_ORDER);
    this.tracker = tracker;
    this.issueCache = issueCache;
    this.issueLifecycle = issueLifecycle;
    this.issueVisitors = issueVisitors;
    this.componentsWithUnprocessedIssues = componentsWithUnprocessedIssues;
    this.movedFilesRepository = movedFilesRepository;
    this.issuesLoader = issuesLoader;
    this.analysisMetadataHolder = analysisMetadataHolder;
    this.shortBranchTrackerExecution = shortBranchTrackerExecution;
  }

  @Override
  public void visitAny(Component component) {
    processIssues(component);

    componentsWithUnprocessedIssues.remove(component.getUuid());
    Optional<MovedFilesRepository.OriginalFile> originalFile = movedFilesRepository.getOriginalFile(component);
    if (originalFile.isPresent()) {
      componentsWithUnprocessedIssues.remove(originalFile.get().getUuid());
    }
  }

  private void processIssues(Component component) {
    try (DiskCache<DefaultIssue>.DiskAppender cacheAppender = issueCache.newAppender()) {
      issueVisitors.beforeComponent(component);

      if (isShortLivingBranch()) {
        ShortBranchTracking shortBranchTracking = shortBranchTrackerExecution.track(component);
        fillNewOpenIssues(component, shortBranchTracking.getUnmatchedRaws(), cacheAppender);
        fillExistingOpenIssues(component, shortBranchTracking.getMatchedWithBase(), cacheAppender);
        fillExistingLongBranchOpenIssues(component, shortBranchTracking.getMatchedWithMergeBranch(), cacheAppender);
        closeUnmatchedBaseIssues(component, shortBranchTracking.getUnmatchedBases(), cacheAppender);
      } else if (isIncremental(component)) {
        List<DefaultIssue> issues = issuesLoader.loadForComponentUuid(component.getUuid());
        fillIncrementalOpenIssues(component, issues, cacheAppender);
      } else {
        Tracking<DefaultIssue, DefaultIssue> tracking = tracker.track(component);
        fillNewOpenIssues(component, tracking.getUnmatchedRaws(), cacheAppender);
        fillExistingOpenIssues(component, tracking.getMatchedRaws(), cacheAppender);
        closeUnmatchedBaseIssues(component, tracking.getUnmatchedBases(), cacheAppender);
      }
      issueVisitors.afterComponent(component);
    } catch (Exception e) {
      throw new IllegalStateException(String.format("Fail to process issues of component '%s'", component.getKey()), e);
    }
  }

  private boolean isShortLivingBranch() {
    java.util.Optional<Branch> branch = analysisMetadataHolder.getBranch();
    return branch.isPresent() && branch.get().getType() == BranchType.SHORT;
  }

  private boolean isIncremental(Component component) {
    return analysisMetadataHolder.isIncrementalAnalysis() && component.getStatus() == Status.SAME;
  }

  private void fillNewOpenIssues(Component component, Iterable<DefaultIssue> issues, DiskCache<DefaultIssue>.DiskAppender cacheAppender) {
    for (DefaultIssue issue : issues) {
      issueLifecycle.initNewOpenIssue(issue);
      process(component, issue, cacheAppender);
    }
  }

  private void fillExistingLongBranchOpenIssues(Component component, Map<DefaultIssue, DefaultIssue> issues, DiskCache<DefaultIssue>.DiskAppender cacheAppender) {
    for (Map.Entry<DefaultIssue, DefaultIssue> entry : issues.entrySet()) {
      DefaultIssue raw = entry.getKey();
      DefaultIssue base = entry.getValue();
      issueLifecycle.copyExistingOpenIssue(raw, base);
      process(component, raw, cacheAppender);
    }
  }

  private void fillIncrementalOpenIssues(Component component, Collection<DefaultIssue> issues, DiskCache<DefaultIssue>.DiskAppender cacheAppender) {
    for (DefaultIssue issue : issues) {
      process(component, issue, cacheAppender);
    }
  }

  private void fillExistingOpenIssues(Component component, Map<DefaultIssue, DefaultIssue> matched, DiskCache<DefaultIssue>.DiskAppender cacheAppender) {
    for (Map.Entry<DefaultIssue, DefaultIssue> entry : matched.entrySet()) {
      DefaultIssue raw = entry.getKey();
      DefaultIssue base = entry.getValue();
      issueLifecycle.mergeExistingOpenIssue(raw, base);
      process(component, raw, cacheAppender);
    }
  }

  private void closeUnmatchedBaseIssues(Component component, Iterable<DefaultIssue> issues, DiskCache<DefaultIssue>.DiskAppender cacheAppender) {
    for (DefaultIssue issue : issues) {
      // TODO should replace flag "beingClosed" by express call to transition "automaticClose"
      issue.setBeingClosed(true);
      // TODO manual issues -> was updater.setResolution(newIssue, Issue.RESOLUTION_REMOVED, changeContext);. Is it a problem ?
      process(component, issue, cacheAppender);
    }
  }

  private void process(Component component, DefaultIssue issue, DiskCache<DefaultIssue>.DiskAppender cacheAppender) {
    issueLifecycle.doAutomaticTransition(issue);
    issueVisitors.onIssue(component, issue);
    cacheAppender.append(issue);
  }

}
