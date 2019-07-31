package net.minecraftforge.gradle.tasks.abstractutil;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;
import org.apache.commons.io.FileUtils;
import org.apache.shiro.util.AntPathMatcher;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class CopyFilesTask extends CachedTask {
 private final AntPathMatcher pathMatcher = new AntPathMatcher();
 @Cached
 public DelayedFile input;
 public DelayedFile output;
 public DelayedString includePaths, excludePaths;

// @Cached
// public DelayedFile cacheSwitch;

// public boolean recurse = false;

 @TaskAction
 public void doTask() {
  final String[]
   includes = includePaths == null ? new String[0] : includePaths.resolveDelayed().split(" "),
   excludes = excludePaths == null ? new String[0] : excludePaths.resolveDelayed().split(" ");
  final File
   input = this.input.call(),
   output = this.output.call();
  final String
   inputPath = input.getAbsolutePath(),
   outputPath = output.getAbsolutePath();
  if (input.exists() && output.exists()) {
   try (Stream<Path> paths = Files.walk(input.toPath())) {
    paths
     .map(p -> Paths.get(inputPath).relativize(p))
     .filter(rp -> {
      final String relative = rp.toString();
      for (final String exclude : excludes) {
       if (pathMatcher.matches(exclude, relative)) {
        return false;
       }
      }
      for (final String include : includes) {
       if (pathMatcher.matches(include, relative)) {
        return true;
       }
      }
      return includes.length == 0;
     })
     .forEach(rp -> {
      final Path
       inPath = Paths.get(inputPath, rp.toString()),
       outPath = Paths.get(outputPath, rp.toString());
      final File
       in = inPath.toFile(),
       out = outPath.toFile();
      if (out.exists()) {
       out.delete();
      }
//      if (in.isDirectory()) {
//       out.mkdirs();
//      } else {
      if (!in.isDirectory()) {
       final File parentDir = out.getParentFile();
       if (!parentDir.exists()) {
        parentDir.mkdirs();
       }
       try {
        Files.copy(inPath, outPath);
       } catch(Throwable t) {
        throw new RuntimeException("Error copying files!", t);
       }
      }
     });
//    FileUtils.copyDirectory(input, output);
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

 public void setIncludes(final DelayedString paths) {
  this.includePaths = paths;
 }

 public void setExcludes(final DelayedString paths) {
  this.excludePaths = paths;
 }
}
