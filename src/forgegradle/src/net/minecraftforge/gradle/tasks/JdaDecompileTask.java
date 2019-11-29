package net.minecraftforge.gradle.tasks;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;
import net.minecraftforge.gradle.util.ZipUtils;
import org.apache.shiro.util.AntPathMatcher;
import org.cliffc.high_scale_lib.NonBlockingHashSet;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.mcdh.jda.JavaDecompileProxy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class JdaDecompileTask extends CachedTask {
 private static final String mismatchedFileType = "Only zip files and directories may be passed to the decompiler!";
 private final AntPathMatcher antPathMatcher = new AntPathMatcher();

 @Input
 private DelayedFile input;
 @Cached
 private DelayedFile output;
 private DelayedString targets;
 private final Set<Path> processedTargets = new NonBlockingHashSet<>();
 private final Set<Path> matchedTargets = new NonBlockingHashSet<>();
 private final Map<String, String> options = new LinkedHashMap<>();
 private final ArrayList<DelayedFile> classpath = new ArrayList<>();
// private ClassLoader ff_cl;

 public static String cropFileName(final String name) {
  final int lio = name.lastIndexOf(".");
  if (lio != -1) {
   return name.substring(lio);
  }
  return name;
 }

 public static String cropFileExtension(final String name) {
  final int lio = name.lastIndexOf(".");
  if (lio != -1) {
   return name.substring(0, lio);
  }
  return name;
 }

 private Path copyToTmpDir(final Path tmpDir, final ZipEntry ze, final ZipInputStream zis) throws IOException {
  final Path output = tmpDir.resolve(ze.getName());
  final File parent = output.toFile().getParentFile();
  if (!parent.exists()) {
   parent.mkdirs();
  }
  ZipUtils.copyEntry(zis, ze, output);
  return output;
 }

 private Path copyToTmpDir(final Path tmpDir, final Path root, final Path toCopy) throws IOException {
  final Path output = tmpDir.resolve(root.relativize(toCopy));
  final File parent = output.toFile().getParentFile();
  if (!parent.exists()) {
   parent.mkdirs();
  }
  Files.copy(output, toCopy);
  return output;
 }

 @TaskAction
 public void doTask() {
  final String[] targets = this.targets.resolveDelayed().split(" ");
  final File
   input = this.input.call(),
   output = this.output.call(),
   tmpDir = getTemporaryDir();
  final Path
   inputPath = input.toPath().toAbsolutePath(),
   outputPath = output.toPath().toAbsolutePath(),
   tmpDirPath = tmpDir.toPath().toAbsolutePath(),
   tmpExistingPath = tmpDirPath.resolve("existing/"),
   tmpCompiledPath = tmpDirPath.resolve("compiled/");

  if (!input.exists()) {
   throw new RuntimeException("Non-existent input provided to decompile task!");
  }
  if (!tmpDir.mkdirs()) {
   tmpDir.delete();
   tmpDir.mkdirs();
  }
  //Find previously decompiled sources
  final boolean outputExists = output.exists();
  final Set<Path> existingSources = new NonBlockingHashSet<>();
  if (outputExists) {
   if (output.isDirectory()) {
    try (final Stream<Path> stream = Files.walk(outputPath)) {
     stream
      .parallel()
      .map(outputPath::relativize)
      .filter(p -> p.toString().endsWith(".java"))
      .peek(existingSources::add);
    } catch (final Throwable t) {
     throw new RuntimeException(t);
    }
   } else {
    final String outputFileExtension = cropFileName(output.getName()).toLowerCase();
    if (outputFileExtension.equals(".zip") || outputFileExtension.equals(".jar")) {
     try (final ZipInputStream zis = new ZipInputStream(Files.newInputStream(outputPath))) {
      ZipEntry ze;
      while ((ze = zis.getNextEntry()) != null) {
       if (cropFileName(ze.getName()).toLowerCase().equals(".java")) {
        existingSources.add(tmpExistingPath.relativize(copyToTmpDir(tmpExistingPath, ze, zis)));
       }
      }
     } catch (final Throwable t) {
      throw new RuntimeException(t);
     }
    } else {
     throw new RuntimeException(mismatchedFileType);
    }
   }
  }
  //Filter lambda for matching decompile targets
  //Accepts relative paths only
  final Predicate<Path> filter = path -> {
   String
    sPath = cropFileExtension(path.toString()),
    extension = cropFileName(path.toString());
   int subclassIndex = sPath.indexOf("$");
   if (subclassIndex != -1) {
    sPath = sPath.substring(0, subclassIndex);
   }
//   for (final Path previous : existingSources) {
//    if (sPath.equals(previous.toString().replace(".class", ""))) {
//     return false;
//    }
//   }
   for (String target : targets) {
    target = target.replace(".java", "");
    if (antPathMatcher.matches(target, sPath) && extension.equals(".class")) {
     return true;
    }
   }
   return false;
  };
  //Collect inputs (filters out non-targets and previously decompiled sources)
  if (input.isDirectory()) {
   try (final Stream<Path> inputs = Files.walk(inputPath)) {
    inputs
     .parallel()
     .map(inputPath::relativize)
     .filter(filter)
     .peek(p -> {
      try {
       matchedTargets.add(copyToTmpDir(tmpCompiledPath, inputPath, p));
      } catch (final Throwable t) {
       throw new RuntimeException(t);
      }
     });
   } catch (final Throwable t) {
    throw new RuntimeException(t);
   }
  } else {
   final String inputFileExtension = cropFileName(input.getName()).toLowerCase();
   if (inputFileExtension.equals(".zip") || inputFileExtension.equals(".jar")) {
    try (final ZipInputStream zis = new ZipInputStream(Files.newInputStream(inputPath))) {
     ZipEntry ze;
     while ((ze = zis.getNextEntry()) != null) {
      if (filter.test(Paths.get(ze.getName()))) {
       matchedTargets.add(tmpCompiledPath.relativize(copyToTmpDir(tmpCompiledPath, ze, zis)));
      }
     }
    } catch (final Throwable t) {
     throw new RuntimeException(t);
    }
   } else {
    throw new RuntimeException(mismatchedFileType);
   }
  }
  //Perform decompilation and write outputs
  //Filter out nested from the processedTargets list
  processedTargets.addAll(
   matchedTargets
    .parallelStream()
    .filter(toProcess -> {
     if (toProcess.toString().contains("$")) {
      return false;
     }
     for (final Path existing : existingSources) {
      if (cropFileExtension(toProcess.toString()).equals(cropFileExtension(existing.toString()))) {
       return false;
      }
     }
     return true;
    })
    .collect(Collectors.toList())
  );
  if (processedTargets.size() > 0) {
   final JavaDecompileProxy decompiler = new JavaDecompileProxy(options);
   try (final Stream<Path> stream = processedTargets.stream()) {
    if (output.isDirectory()) {
     stream
      .parallel()
      .peek(classFilePath -> {
       final Path classInputPath = tmpCompiledPath.resolve(classFilePath).toAbsolutePath();
       final String sourceOutputName = classFilePath.toString().replace(".class", ".java");
       final Path sourceOutputPath = outputPath.resolve(sourceOutputName);
       sourceOutputPath.toFile().getParentFile().mkdirs();
       try {
        Files.write(sourceOutputPath, decompiler.decompile(classInputPath.toString()).getBytes());
       } catch (final Throwable t) {
        throw new RuntimeException(t);
       }
      });
    } else {
     if (!outputExists) {
      try {
       output.createNewFile();
      } catch (final Throwable t) {
       throw new RuntimeException(t);
      }
     }
     //TODO parallel decompile, serial zip
     try (final ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputPath, StandardOpenOption.WRITE))) {
      for (final Path classFilePath : processedTargets) {
       final Path classInputPath = tmpCompiledPath.resolve(classFilePath).toAbsolutePath();
       final String sourceOutputName = classFilePath.toString().replace(".class", ".java");
       try {
        zos.putNextEntry(new ZipEntry(sourceOutputName));
        zos.write(decompiler.decompile(classInputPath.toString()).getBytes());
       } catch (final Throwable t) {
        throw new RuntimeException(t);
       }
      }
     } catch (final Throwable t) {
      output.delete();
      throw new RuntimeException(t);
     }
    }
   }
  }
 }

 public void setInput(final DelayedFile input) {
  this.input = input;
 }

 public void setOutput(final DelayedFile output) {
  this.output = output;
 }

 public void setTargets(final DelayedString targets) {
  this.targets = targets;
 }

 public void addOption(final String option, final String value) {
  options.put(option, value);
 }

 public List<Path> getProcessedTargets() {
  return new ArrayList<>(processedTargets);
 }

 public List<Path> getMatchedTargets() {
  return new ArrayList<>(matchedTargets);
 }
}
