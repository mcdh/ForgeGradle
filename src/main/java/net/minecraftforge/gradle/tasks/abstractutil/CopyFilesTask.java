package net.minecraftforge.gradle.tasks.abstractutil;

import net.minecraftforge.gradle.delayed.DelayedFile;
import org.apache.commons.io.FileUtils;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;

import java.io.File;

public class CopyFilesTask extends CachedTask {
 @Cached
 public DelayedFile input;
 public DelayedFile output;

// @Cached
// public DelayedFile cacheSwitch;

// public boolean recurse = false;

 @TaskAction
 public void doTask() {
  final File
   input = this.input.call(),
   output = this.output.call();
  if (input.exists() && output.exists()) {
   try {
    FileUtils.copyDirectory(input, output);
//   if (recurse) {
//    FileUtils.copyDirectory(target, output);
//   } else {
//    Files.copy(target, output);
//   }
   } catch (Throwable t) {
    throw new TaskExecutionException(this, t);
   }
  }
 }

 public void setInput(final DelayedFile input) {
  this.input = input;
 }

 public void setOutput(final DelayedFile output) {
  this.output = output;
 }

// public void setRecurse(final boolean recurse) {
//  this.recurse = recurse;
// }
}
