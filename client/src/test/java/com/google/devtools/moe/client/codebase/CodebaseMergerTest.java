/*
 * Copyright (c) 2011 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.moe.client.codebase;

import static com.google.common.truth.Truth.assertThat;
import static org.easymock.EasyMock.expect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.moe.client.CommandRunner;
import com.google.devtools.moe.client.FileSystem;
import com.google.devtools.moe.client.Injector;
import com.google.devtools.moe.client.Ui;
import com.google.devtools.moe.client.tools.FileDifference.ConcreteFileDiffer;
import com.google.devtools.moe.client.tools.FileDifference.FileDiffer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;

/**
 * Unit tests for the CodebaseMerger class.
 *
 * <p>Here is a diagram illustrating a merge situation. The test cases below will refer to this
 * diagram when explaining what type of case they are testing.
 * <pre>
 *                                                   _____
 *                                                  |     |
 *                                                  |  7  | (mod)
 *                                                  |_____|
 *                                                     |
 *                                                     |
 *                                                     |
 *                                                     |
 *                        ____                       __|__
 *                       |    |                     |     |
 *                (dest) |1006|=====================|  6  | (orig)
 *                       |____|                     |_____|
 *
 *                    internalrepo                 publicrepo
 * </pre>
 */
public class CodebaseMergerTest extends TestCase {
  private final IMocksControl control = EasyMock.createControl();
  private final FileSystem fileSystem = control.createMock(FileSystem.class);
  private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
  private final Ui ui = new Ui(stream, /* fileSystem */ null);
  private final CommandRunner cmd = control.createMock(CommandRunner.class);
  private final FileDiffer fileDiffer = new ConcreteFileDiffer(cmd, fileSystem);

  private final Codebase orig = control.createMock(Codebase.class);
  private final Codebase dest = control.createMock(Codebase.class);
  private final Codebase mod = control.createMock(Codebase.class);

  /**
   * Test generateMergedFile(...) in the case where the file exists in orig and mod but not dest.
   * In this case, the file is unchanged in orig and mod, so the file is not placed in the
   * merged codebase.
   */
  public void testGenerateMergedFileDestDelete() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(true).anyTimes();

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(false);
    destFile = new File("/dev/null");

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(true).anyTimes();

    expect(fileSystem.isExecutable(origFile)).andReturn(false);
    expect(fileSystem.isExecutable(modFile)).andReturn(false);

    expect(cmd.runCommand(
            "diff",
            ImmutableList.of("-N", "-u", origFile.getAbsolutePath(), modFile.getAbsolutePath()),
            ""))
        .andReturn(null);

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(ui, fileSystem, cmd, fileDiffer, orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();
  }

  /**
   * Test generateMergedFile(...) in the case where the file exists in orig and dest but not mod.
   * The orig version and the dest version of the file differ. This should be treated as a conflict
   * for the user to resolve.
   */
  public void testGenerateMergedFileModDeleteConflict() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(true);

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true);

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(false);

    // Expect no copy operations or anything. Deletion of the original file (i.e. non-existence of
    // modFile) should result in not copying destFile to the merged codebase, i.e. deleting it.

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(ui, fileSystem, cmd, null, orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();

    assertThat(merger.getFailedToMergeFiles()).isEmpty();
    assertThat(merger.getMergedFiles()).isEmpty();
  }

  /**
   * Test generateMergedFile(...) in the case when the file exists only in mod.
   * In this case, the file should simply be copied to the merged codebase.
   */
  public void testGenerateMergedFileAddFile() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(false);

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(false);

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(true);

    File mergedFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(mergedFile);
    fileSystem.copyFile(modFile, mergedFile);

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(ui, fileSystem, cmd, null, orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();

    assertThat(merger.getMergedFiles()).isEmpty();
    assertThat(merger.getFailedToMergeFiles()).isEmpty();
  }

  /**
   * Test generateMergedFile(...) in the most ideal case where the file exists in all three
   * codebases and there is no conflict.
   */
  public void testGenerateMergedFileClean() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(true);

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true);

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(true);

    File mergedFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(mergedFile);
    fileSystem.copyFile(destFile, mergedFile);

    List<String> mergeArgs =
        ImmutableList.of(
            mergedFile.getAbsolutePath(), origFile.getAbsolutePath(), modFile.getAbsolutePath());

    expect(cmd.runCommand("merge", mergeArgs, mergedCodebaseLocation.getAbsolutePath()))
        .andReturn("");

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(ui, fileSystem, cmd, null, orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();

    assertThat(merger.getFailedToMergeFiles()).isEmpty();
    assertThat(merger.getMergedFiles()).contains(mergedFile.getAbsolutePath());
  }

  /**
   * Test generateMergedFile(...) in the case where the file exists in all three codebases but
   * there is a conflict when merging.
   */
  public void testGenerateMergedFileConflict() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(true);

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true);

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(true);

    File mergedFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(mergedFile);
    fileSystem.copyFile(destFile, mergedFile);

    List<String> mergeArgs =
        ImmutableList.of(
            mergedFile.getAbsolutePath(), origFile.getAbsolutePath(), modFile.getAbsolutePath());

    expect(cmd.runCommand("merge", mergeArgs, mergedCodebaseLocation.getAbsolutePath()))
        .andThrow(new CommandRunner.CommandException("merge", mergeArgs, "", "", 1));

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(ui, fileSystem, cmd, null, orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();

    assertThat(merger.getMergedFiles()).isEmpty();
    assertThat(merger.getFailedToMergeFiles()).contains(mergedFile.getAbsolutePath());
  }

  /**
   * Test generateMergedFile(...) in the case where the file only exists in dest. The file should
   * appear in the merged codebase unchanged.
   */
  public void testGenerateMergedFileDestOnly() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(false);

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true);

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(false);

    File mergedFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(mergedFile);
    fileSystem.copyFile(destFile, mergedFile);

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(ui, fileSystem, cmd, null, orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();

    assertThat(merger.getFailedToMergeFiles()).isEmpty();
    assertThat(merger.getMergedFiles()).isEmpty();
  }

  /**
   * Test generateMergedFile(...) in the case where the file exists in mod and dest but not orig.
   * In this case, the mod version and dest version are the same so there should be no conflict.
   * The file should remain unchanged in the merged codebase.
   */
  public void testGenerateMergedFileNoOrig() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(false);
    origFile = new File("/dev/null");

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true);

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(true);

    File mergedFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(mergedFile);
    fileSystem.copyFile(destFile, mergedFile);

    List<String> mergeArgs =
        ImmutableList.of(
            mergedFile.getAbsolutePath(), origFile.getAbsolutePath(), modFile.getAbsolutePath());

    expect(cmd.runCommand("merge", mergeArgs, mergedCodebaseLocation.getAbsolutePath()))
        .andReturn("");

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(ui, fileSystem, cmd, null, orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();

    assertThat(merger.getFailedToMergeFiles()).isEmpty();
    assertTrue(merger.getMergedFiles().contains(mergedFile.getAbsolutePath()));
  }

  /**
   * Test generateMergedFile(...) in the case where the file exists in mod and dest but not orig.
   * In this case, the mod version and dest version are different so a conflict should occur when
   * merging so that the user can resolved the discrepancy.
   */
  public void testGenerateMergedFileNoOrigConflict() throws Exception {
    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(false);
    origFile = new File("/dev/null");

    File destFile = new File("dest/foo");
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true);

    File modFile = new File("mod/foo");
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(true);

    File mergedFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(mergedFile);
    fileSystem.copyFile(destFile, mergedFile);

    List<String> mergeArgs =
        ImmutableList.of(
            mergedFile.getAbsolutePath(), origFile.getAbsolutePath(), modFile.getAbsolutePath());

    expect(cmd.runCommand("merge", mergeArgs, mergedCodebaseLocation.getAbsolutePath()))
        .andThrow(new CommandRunner.CommandException("merge", mergeArgs, "", "", 1));

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(ui, fileSystem, cmd, null, orig, mod, dest);
    merger.generateMergedFile("foo");

    control.verify();

    assertEquals(0, merger.getMergedFiles().size());
    assertTrue(merger.getFailedToMergeFiles().contains(mergedFile.getAbsolutePath()));
  }

  /**
   * Test for merge()
   */
  public void testMerge() throws Exception {
    Ui ui = control.createMock(Ui.class);
    Injector.INSTANCE = new Injector(fileSystem, cmd, ui);

    File mergedCodebaseLocation = new File("merged_codebase_7");
    expect(fileSystem.getTemporaryDirectory("merged_codebase_")).andReturn(mergedCodebaseLocation);

    File origRoot = new File("orig");
    expect(orig.path()).andReturn(origRoot).anyTimes();
    File destRoot = new File("dest");
    expect(dest.path()).andReturn(destRoot).anyTimes();
    File modRoot = new File("mod");
    expect(mod.path()).andReturn(modRoot).anyTimes();


    // generateMergedFile(...) on foo
    File origFile = new File("orig/foo");
    expect(orig.getFile("foo")).andReturn(origFile);
    expect(fileSystem.exists(origFile)).andReturn(true);

    File destFile = new File("dest/foo");
    expect(fileSystem.findFiles(destRoot)).andReturn(ImmutableSet.of(destFile));
    expect(dest.getFile("foo")).andReturn(destFile);
    expect(fileSystem.exists(destFile)).andReturn(true);

    File modFile = new File("mod/foo");
    expect(fileSystem.findFiles(modRoot)).andReturn(ImmutableSet.of(modFile));
    expect(mod.getFile("foo")).andReturn(modFile);
    expect(fileSystem.exists(modFile)).andReturn(true);

    File mergedFile = new File("merged_codebase_7/foo");
    fileSystem.makeDirsForFile(mergedFile);
    fileSystem.copyFile(destFile, mergedFile);

    List<String> mergeArgs =
        ImmutableList.of(
            mergedFile.getAbsolutePath(), origFile.getAbsolutePath(), modFile.getAbsolutePath());

    expect(cmd.runCommand("merge", mergeArgs, mergedCodebaseLocation.getAbsolutePath()))
        .andReturn("");

    // No merging of bar, just follow deletion of origFile by not copying destFile to merged
    // codebase.

    // Expect in call to report()
    ui.message("Merged codebase generated at: %s", mergedCodebaseLocation.getAbsolutePath());
    ui.message("%d files merged successfully. No merge conflicts.", 1);

    control.replay();

    CodebaseMerger merger = new CodebaseMerger(ui, fileSystem, cmd, null, orig, mod, dest);
    merger.merge();

    control.verify();

    assertThat(merger.getMergedFiles()).contains(mergedFile.getAbsolutePath());
    assertThat(merger.getFailedToMergeFiles()).isEmpty();
  }
}
