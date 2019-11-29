package net.minecraftforge.gradle.tasks.abstractutil;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.util.ZipUtils;
import org.apache.commons.io.FileUtils;
import org.apache.shiro.util.AntPathMatcher;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CopyFilesTask extends DefaultTask {
 private final AntPathMatcher pathMatcher = new AntPathMatcher();
 public DelayedFile input;
 public DelayedFile output;
 public DelayedString includePaths, excludePaths;

 @TaskAction
 public void doTask() {
  //Filter out empty strings
  String[]
   includes = Arrays
    .stream(includePaths == null ? new String[0] : includePaths.resolveDelayed().split(" "))
    .filter(s -> !s.isEmpty())
    .collect(Collectors.toList())
    .toArray(new String[0]),
   excludes = Arrays
    .stream(excludePaths == null ? new String[0] : excludePaths.resolveDelayed().split(" "))
    .filter(s -> !s.isEmpty())
    .collect(Collectors.toList())
    .toArray(new String[0]);
  final File
   input = this.input.call(),
   output = this.output.call();
  final String
   inputPath = input.getAbsolutePath(),
   outputPath = output.getAbsolutePath();
  if (!output.exists()) {
   output.mkdirs();
  }
  if (input.isDirectory()) {
   try (Stream<Path> paths = Files.walk(input.toPath())) {
    paths
     .map(p -> Paths.get(inputPath).relativize(p))
     .filter(rp -> {
      //TODO change the filtering to match the one below
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
   } catch (Throwable t) {
    throw new TaskExecutionException(this, t);
   }
  } else {
   try (final ZipInputStream zis = new ZipInputStream(Files.newInputStream(input.toPath()))) {
    ZipEntry entry;
    while ((entry = zis.getNextEntry()) != null) {
     final String name = entry.getName();
     boolean shouldInclude = true;
     for (final String exclude : excludes) {
      if (pathMatcher.matches(exclude, name)) {
       shouldInclude = false;
       break;
      }
     }
     //Only search through includes if the excludes clause has not flagged the entry
     if (shouldInclude) {
      if (includes.length > 0) {
       for (final String include : includes) {
        if (pathMatcher.matches(include, name)) {
         ZipUtils.copyEntry(
          zis,
          entry,
          Paths.get(outputPath, name)
         );
         break;
        }
       }
      } else {
       ZipUtils.copyEntry(
        zis,
        entry,
        Paths.get(outputPath, name)
       );
      }
     }
    }
   } catch (final Throwable t) {
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
