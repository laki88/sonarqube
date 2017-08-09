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
package com.sonarsource.branch;

import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.sonar.api.CoreProperties;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.MessageException;
import org.sonar.core.config.ScannerProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class BranchConfigurationImplTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private Configuration settings;

  @Before
  public void setUp() {
    settings = Mockito.mock(Configuration.class);
    when(settings.get(Matchers.anyString())).thenReturn(Optional.empty());
  }

  @Test
  public void should_be_longLived_and_target_null_when_branchName_missing() {
    BranchConfigurationImpl branchConfig = BranchConfigurationImpl.create(settings, Collections.emptyList());
    assertThat(branchConfig.branchType()).isEqualTo(BranchConfigurationImpl.BranchType.LONG);
    assertThat(branchConfig.branchTarget()).isNull();
  }

  @Test
  public void should_be_longLived_and_target_null_when_branchName_new_and_matches_pattern() {
    String branchName = "long";
    when(settings.get(Matchers.eq(ScannerProperties.BRANCH_NAME))).thenReturn(Optional.of(branchName));
    when(settings.get(Matchers.eq(CoreProperties.LONG_LIVED_BRANCHES_REGEX))).thenReturn(Optional.of(branchName));
    BranchConfigurationImpl branchConfig = BranchConfigurationImpl.create(settings, Collections.emptyList());
    assertThat(branchConfig.branchType()).isEqualTo(BranchConfigurationImpl.BranchType.LONG);
    assertThat(branchConfig.branchTarget()).isNull();
  }

  @Test
  public void should_be_shortLived_and_target_null_when_branchName_new_and_does_not_match_pattern() {
    String branchName = "long";
    when(settings.get(Matchers.eq(ScannerProperties.BRANCH_NAME))).thenReturn(Optional.of(branchName));
    when(settings.get(Matchers.eq(CoreProperties.LONG_LIVED_BRANCHES_REGEX))).thenReturn(Optional.of(branchName + "x"));
    BranchConfigurationImpl branchConfig = BranchConfigurationImpl.create(settings, Collections.emptyList());
    assertThat(branchConfig.branchType()).isEqualTo(BranchConfigurationImpl.BranchType.SHORT);
    assertThat(branchConfig.branchTarget()).isNull();
  }

  @Test
  public void should_be_shortLived_when_branchName_exists_regardless_of_pattern() {
    String branchName = "long";
    when(settings.get(Matchers.eq(ScannerProperties.BRANCH_NAME))).thenReturn(Optional.of(branchName));
    when(settings.get(Matchers.eq(CoreProperties.LONG_LIVED_BRANCHES_REGEX))).thenReturn(Optional.of(branchName));
    assertThat(BranchConfigurationImpl.create(settings, Collections.singletonList(
      new BranchConfigurationImpl.BranchInfo(branchName, false)
    )).branchType()).isEqualTo(BranchConfigurationImpl.BranchType.SHORT);
  }

  @Test
  public void should_fail_when_longLived_regex_property_missing() {
    thrown.expect(MessageException.class);
    thrown.expectMessage("Property must exist");

    String branchName = "long";
    when(settings.get(Matchers.eq(ScannerProperties.BRANCH_NAME))).thenReturn(Optional.of(branchName));
    BranchConfigurationImpl.create(settings, Collections.emptyList());
  }

  @Test
  public void should_fail_when_branchTarget_nonexistent() {
    thrown.expect(MessageException.class);
    thrown.expectMessage("Target branch does not exist");

    String branchName = "long";
    when(settings.get(Matchers.eq(ScannerProperties.BRANCH_NAME))).thenReturn(Optional.of(branchName));
    when(settings.get(Matchers.eq(ScannerProperties.BRANCH_TARGET))).thenReturn(Optional.of("nonexistent"));
    when(settings.get(Matchers.eq(CoreProperties.LONG_LIVED_BRANCHES_REGEX))).thenReturn(Optional.of("dummy"));
    BranchConfigurationImpl.create(settings, Collections.emptyList());
  }

  @Test
  public void should_use_specified_branchTarget() {
    String branchName = "dummy";
    String branchTarget = "some-long";
    when(settings.get(Matchers.eq(ScannerProperties.BRANCH_NAME))).thenReturn(Optional.of(branchName));
    when(settings.get(Matchers.eq(ScannerProperties.BRANCH_TARGET))).thenReturn(Optional.of(branchTarget));
    when(settings.get(Matchers.eq(CoreProperties.LONG_LIVED_BRANCHES_REGEX))).thenReturn(Optional.of("foo"));
    assertThat(BranchConfigurationImpl.create(settings, Collections.singletonList(
      new BranchConfigurationImpl.BranchInfo(branchTarget, true)
    )).branchTarget()).isEqualTo(branchTarget);
  }

  @Test
  public void should_fail_when_target_branch_is_not_long() {
    thrown.expect(MessageException.class);
    thrown.expectMessage("Target branch is not long-lived");

    String branchName = "dummy";
    String branchTarget = "some-long";
    when(settings.get(Matchers.eq(ScannerProperties.BRANCH_NAME))).thenReturn(Optional.of(branchName));
    when(settings.get(Matchers.eq(ScannerProperties.BRANCH_TARGET))).thenReturn(Optional.of(branchTarget));
    when(settings.get(Matchers.eq(CoreProperties.LONG_LIVED_BRANCHES_REGEX))).thenReturn(Optional.of("foo"));
    BranchConfigurationImpl.create(settings, Collections.singletonList(
      new BranchConfigurationImpl.BranchInfo(branchTarget, false)
    ));
  }
}
