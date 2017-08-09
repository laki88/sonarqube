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

import java.util.Map;

import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.tracking.Tracking;

public class ShortBranchTracking {
  private Iterable<DefaultIssue> unmatchedBases;
  private Iterable<DefaultIssue> unmatchedRaws;
  private Map<DefaultIssue, DefaultIssue> matchedWithBase;
  private Map<DefaultIssue, DefaultIssue> matchedWithMergeBranch;

  public ShortBranchTracking(Tracking<DefaultIssue, DefaultIssue> baseTracking, Tracking<DefaultIssue, DefaultIssue> mergeBranchTracking) {
    this.unmatchedRaws = mergeBranchTracking.getUnmatchedRaws();
    this.unmatchedBases = baseTracking.getUnmatchedBases();
    this.matchedWithBase = baseTracking.getMatchedRaws();
    this.matchedWithMergeBranch = mergeBranchTracking.getMatchedRaws();
  }

  /**
   * Raw issues that weren't matched with either the base or the merge branch
   */
  public Iterable<DefaultIssue> getUnmatchedRaws() {
    return unmatchedRaws;
  }

  public Map<DefaultIssue, DefaultIssue> getMatchedWithBase() {
    return matchedWithBase;
  }

  /**
   * Issues that weren't matched with issues in the base but were matched with issues in the merge branch.
   * @return map raw to merge branch issues
   */
  public Map<DefaultIssue, DefaultIssue> getMatchedWithMergeBranch() {
    return matchedWithMergeBranch;
  }

  public Iterable<DefaultIssue> getUnmatchedBases() {
    return unmatchedBases;
  }
}
