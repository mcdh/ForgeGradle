package net.minecraftforge.gradle.dev;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.patching.ContextualPatch;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;
import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.gradle.api.Project;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;

//TODO add DSL configuration
public class PatchSourcesTask extends CachedTask implements ContextualPatch.IContextProvider {
 private final class PatchedFile {
  public final File fileToPatch;
  public final ContextualPatch patch;

  public PatchedFile(final File file) {
   try {
    this.fileToPatch = file;
    this.patch = ContextualPatch
     .create(Files.toString(file, Charset.defaultCharset()), PatchSourcesTask.this)
     .setAccessC14N(false)
     .setMaxFuzz(PatchSourcesTask.this.maxFuzz);
   } catch (final Throwable t) {
    throw new TaskExecutionException(PatchSourcesTask.this, t);
   }
  }

  public File makeRejectFile() {
   return new File(fileToPatch.getParentFile(), fileToPatch.getName() + ".rej");
  }
 }

 @Cached
 public DelayedFile target;

 @Cached
 public DelayedFile patchDir;

 private int
  strip = 3,
  maxFuzz = 0;

 private final Project project = getProject();
 private final Logger logger = getLogger();
 private final Map<String, String> sourceMap = new NonBlockingHashMap<>();
 private Throwable failure = null;

 @Override
 public List<String> getData(String target) {
  String value = sourceMap.get(target);
  if (value == null) {
   final File patchTarget = this.target
    .call()
    .toPath()
    .resolve(target)
    .toFile();

   if (patchTarget.exists() && patchTarget.isFile()) {
    try {
     value = Files
      .toString(patchTarget, Charset.defaultCharset())
      .replace("\r\n", "\n");
    } catch (final Throwable t) {
     throw new RuntimeException(t);
    }
   }
  }
  if (value != null) {
   final ArrayList<String> data = new ArrayList<>();
   Collections.addAll(data, value.split("\n", -1));
   return data;
  }
  return null;
 }

 @Override
 public void setData(String target, List<String> data) {
  sourceMap.put(target, Joiner.on(Constants.NEWLINE).join(data));
 }

 @TaskAction
 public void doTask() {
  final File
   target = this.target.call(),
   patchDir = this.patchDir.call();

  if (!target.exists() || !patchDir.exists()) {
   throw new RuntimeException("The patch and target files must exist!");
  }

  if (patchDir.listFiles().length != 0) {
   try {
    if (!target.exists()) {
     final String name = target.getName();
     if (name.endsWith(".jar") || name.endsWith(".zip")) {
      target.createNewFile();
     } else {
      target.mkdirs();
     }
    }

    //Process file output file or directory
    if (target.isDirectory()) {
     patchDirectory(patchDir);
     sourceMap
      .entrySet()
      .parallelStream()
      .forEach(e -> {
       final File f = project.file(target.getAbsolutePath() + "/" + e.getKey());
       if (f.exists()) {
        project.delete(f);
       }
       try {
        f.createNewFile();
        Files.write(e.getValue().getBytes(), f);
       } catch (final Throwable t) {
        throw new TaskExecutionException(PatchSourcesTask.this, t);
       }
      });
    } else {
     //TODO if '.jar' or '.zip' then compress result
     throw new RuntimeException("This task does not support patching archives yet.");
    }
   } catch (final Throwable t) {
    failure = t;
   }
  }
 }

 //TODO use separate PrintStreams for each thread to properly serialize stdout writes
 public void patchDirectory(final File patchDir) {
  final boolean[] fuzzed = { false };
  final boolean[] patchesFailed = { false };
  project
   .fileTree(patchDir)
   .getFiles()
//   .parallelStream()
   .stream()
   .filter(f -> !f.isDirectory() && f.getPath().endsWith(".patch"))
   .peek(f -> logger.debug("Found patch: {}", f.getAbsolutePath()))
   .map(PatchedFile::new)
   .forEach(pf -> {
    try {
     for (final ContextualPatch.PatchReport report : pf.patch.patch(false)) {
      final ContextualPatch.PatchStatus status = report.getStatus();
      final String patchedFilePath = PatchSourcesTask.this.strip(report.getTarget());
      final Throwable failure = report.getFailure();
      final List<ContextualPatch.HunkReport> hunkReports = report.getHunks();
      //Catch failed patch directory
      patchesFailed[0] |= !status.isSuccess() || status == ContextualPatch.PatchStatus.Fuzzed;
      if (!status.isSuccess()) {
       final File reject = pf.makeRejectFile();
       if (reject.exists()) {
        reject.delete();
       }
       logger.log(LogLevel.ERROR, "Patching failed: {} {}", patchedFilePath, failure.getMessage());
       //Split hunks
       int failed = 0;
       for (final ContextualPatch.HunkReport hunk : hunkReports) {
        if (!hunk.getStatus().isSuccess()) {
         failed += 1;
         logger.error(
          "  " +
          hunk.getHunkID() +
          ": " +
          (hunk.getFailure() == null ? "" : hunk.getFailure().getMessage()) +
          " @ " +
          hunk.getIndex()
         );
         Files.append(String.format("++++ REJECTED PATCH %d\n", hunk.getHunkID()), reject, Charsets.UTF_8);
         Files.append(Joiner.on('\n').join(hunk.hunk.lines), reject, Charsets.UTF_8);
         Files.append(String.format("\n++++ END PATCH\n"), reject, Charsets.UTF_8);
        } else if (hunk.getStatus() == ContextualPatch.PatchStatus.Fuzzed) {
         logger.info("  " + hunk.getHunkID() + " fuzzed " + hunk.getFuzz() + "!");
        }
       }
       logger.log(LogLevel.ERROR, "  {}/{} failed", failed, report.getHunks().size());
       logger.log(LogLevel.ERROR, "  Rejects written to {}", reject.getAbsolutePath());
       //Catch fuzzed patch directory
      } else if (status == ContextualPatch.PatchStatus.Fuzzed) {
       logger.info("Patching fuzzed: {}", patchedFilePath);
       fuzzed[0] = true;
       //Spit the hunks
       for (final ContextualPatch.HunkReport hunk : hunkReports) {
        //Catch failed hunks
        if (hunk.getStatus() == ContextualPatch.PatchStatus.Fuzzed) {
         logger.info("  {} fuzzed {}!", hunk.getHunkID(), hunk.getFuzz());
        }
       }
       //Successful patch directory
      } else {
       logger.info("Patch succeeded: {}", patchedFilePath);
      }
     }
     if (fuzzed[0]) {
      logger.lifecycle("Patches Fuzzed!");
     }
    } catch(final Throwable t) {
     throw new TaskExecutionException(PatchSourcesTask.this, t);
    }
   });
  if (patchesFailed[0]) {
   throw new TaskExecutionException(this, new Throwable("Patching Failed!"));
  }
 }

 private String strip(String target) {
  target = target.replace('\\', '/');
  int index = 0;
  for (int x = 0; x < strip; x++) {
   index = target.indexOf('/', index) + 1;
  }
  return target.substring(index);
 }

 public void setTarget(final DelayedFile target) {
  this.target = target;
 }

 public void setPatchDir(final DelayedFile patchDir) {
  this.patchDir = patchDir;
 }

 public Throwable hasPatchingFailed() {
  return failure;
 }
}
