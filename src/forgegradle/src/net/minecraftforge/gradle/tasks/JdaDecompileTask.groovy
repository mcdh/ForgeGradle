package net.minecraftforge.gradle.tasks

import net.minecraftforge.gradle.delayed.DelayedFile
import net.minecraftforge.gradle.delayed.DelayedString
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask.Cached
import org.apache.shiro.util.AntPathMatcher
import org.cliffc.high_scale_lib.NonBlockingHashSet
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskExecutionException
import org.mcdh.jda.JavaDecompileProxy

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class JdaDecompileTask extends CachedTask {
//class JdaDecompileTask extends DefaultTask {
 @Input
 private DelayedFile input

 @Cached
 private DelayedFile output

 private DelayedString targets

 //Any targets that were decompiled during the task's execution cycle
 private List<Path> _processedTargets = new ArrayList<>()
 //Any targets that matched the Ant path matching (not necessarily processed)
 private Set<Path> _matchedTargets = new NonBlockingHashSet<>()

 private Map<String, String> options = new LinkedHashMap<>()
 private ArrayList<DelayedFile> classpath = new ArrayList<>()

 private ClassLoader ff_cl

// private void bruh() {
//  final def files = new ArrayList<File>()
//  project
//   .getConfigurations()
//   .parallelStream()
////   .filter { c -> c.allDependencies.stream().anyMatch { d -> d.name.toLowerCase().contains("fernflower") } }
//   .map { c -> c.files }
//   .forEach { fl ->
//    fl
//     .stream()
//     .filter { f -> f.name.toLowerCase().contains("fernflower") }
//     .forEach { f -> files.add(f) }
//   }
//  files.forEach { f -> println f }
//  println "TASK EXECUTION THREAD NAME: ${Thread.currentThread().getName()}"
//  println "THREAD CONTEXT CLASSLOADER: ${Thread.getClassLoader()}"
////  System.exit(-1)
// }

 @TaskAction
 def doTask() {
//  bruh()
  final AntPathMatcher pathMatcher = new AntPathMatcher()
  final String[] targets = this.targets.resolveDelayed().split(" ")
  final File input = this.input.call(), output = this.output.call()
  final File inputCache = getInputCache(), outputCache = getOutputCache()
  final Path inputCachePath = inputCache.toPath(), outputCachePath = outputCache.toPath()
  //Copy inputs into local build cache, if not already present
  if (!inputCache.exists()) {
   project.copy {
    from input.isDirectory() ? input : project.zipTree(input)
    into inputCache
   }
  }
  if (!outputCache.exists()) {
   //Copy all non-sources to output directory
   project.copy {
    from inputCache
    into outputCache
    exclude '*.class'
    exclude '**/*.class'
   }
  }
  //Create decompiler input list
  try {
   //Collect any any previously decompiled classes
   final Stream<Path> sPreviousClasses = Files.walk(outputCachePath)
   final List<Path> existing = sPreviousClasses
    .parallel()
    .map { p -> outputCachePath.relativize(p) }
    .filter { p -> p.toString().endsWith(".java") }
    .collect(Collectors.toList())
   sPreviousClasses.close()
   //Collect new classes to be decompiled
   final Stream<Path> sNewClasses = Files.walk(inputCachePath)
   //thanks groovy, very cool
   final def refMatchedTargets = _matchedTargets
   _processedTargets = sNewClasses
    .parallel()
    .map { p -> inputCachePath.relativize(p) }
    //filter out non-classes / sources and perform Ant path matching
    .filter { p ->
     final String rPath = p.toString(), name = p.getFileName().toString()
     if (name.endsWith(".class") && !name.contains('$') && !name.contains("package-info")) {
      for (target in targets) {
       if (target.endsWith(".class") || target.endsWith(".java")) {
        if (cropFileExtension(rPath) == cropFileExtension(target)) {
         return true
        }
       }
       if (pathMatcher.match(target, rPath)) {
        return true
       }
      }
     }
     return false
    }
    //Capture all matched targets
    .map { p ->
     refMatchedTargets.add(p)
     return p
    }
    //filter out already existing decompiled sources
    .filter { p ->
     final def toFilter = p.toString()
     for (path in existing) {
      final def checkAgainst = path.toString()
      if (cropFileExtension(checkAgainst) == cropFileExtension(toFilter)) {
       return false
      }
     }
     return true
    }
    .collect(Collectors.toList())
   sNewClasses.close()
   //Decompile any new targets
   if (processedTargets.size() > 0) {
//   final def decompiler = new JavaDecompileProxy(options, classpath)
    final def cJavaDecompileProxy = ff_cl.loadClass("org.mcdh.jda.JavaDecompileProxy")
//   final def decompiler = new JavaDecompileProxy(options)
    final def decompiler = cJavaDecompileProxy.<JavaDecompileProxy>newInstance(options)
    //Decompile classes
    _processedTargets.forEach { p ->
     final String path = p.toString()
     final String relativeOutput = path.replace(".class", ".java")
     final String decompilerInput = Paths
      .get(inputCachePath.toAbsolutePath().toString(), path)
      .toAbsolutePath()
      .toString()
     Files.write(
      Paths.get(outputCachePath.toAbsolutePath().toString(), relativeOutput),
      decompiler.decompile(decompilerInput).getBytes()
     )
    }
   }
  } catch (Throwable t) {
   throw new TaskExecutionException(this, t)
  }
  //Copy decompiled output from local build cache to desired output directory / file
  if (output.isDirectory()) {
   if (output.exists()) {
    //Copy new processed files to the output directory
    _processedTargets.forEach { p ->
     final String relativeInput = p.toAbsolutePath().toString().replace(".class", ".java")
     final Path decompilerOutput = Paths.get(outputCachePath.toAbsolutePath().toString(), relativeInput)
     final Path destination = Paths.get(output.toPath().toAbsolutePath().toString(), relativeInput)
     Files.copy(decompilerOutput, destination)
    }
   } else {
    //Copy everything to the output directory
    output.mkdirs()
    project.copy {
     from outputCache
     into output
    }
   }
  } else {
   //Only repackage the output file if new targets were processed
   if (_processedTargets.size() > 0) {
    final Path outputFilePath = output.toPath(), localOutputPath = outputCache.toPath()
    final ZipOutputStream zos = new ZipOutputStream(outputFilePath.newDataOutputStream())
    final Stream<Path> paths = Files.walk(localOutputPath)
    paths
     .filter { p -> !p.toFile().isDirectory() }
     .forEach { p ->
      zos.putNextEntry(new ZipEntry(localOutputPath.relativize(p).toString()))
      zos.write(Files.readAllBytes(p))
      zos.closeEntry()
     }
    paths.close()
    zos.finish()
    zos.close()
   }
  }
 }

 def setInput(final def inputDir) {
  this.input = inputDir
 }

 def setOutput(final def outputDir) {
  this.output = outputDir
 }

 def setTargets(final def targets) {
  this.targets = targets
 }

 def addOptions(final Map<String, String> options) {
  this.options.putAll(options)
 }

 def addOption(final String flag, final String value) {
  this.options.put(flag, value)
 }

 def addClasspath(final DelayedFile file) {
  classpath.add(file)
 }

 def getInput() {
  return input
 }

 def getOutput() {
  return output
 }

 def getTargets() {
  return targets
 }

 File getInputCache() {
  return project.file("${project.getBuildDir()}/tmp/jdaExtracted/compiled/")
 }

 File getOutputCache() {
  return project.file("${project.getBuildDir()}/tmp/jdaExtracted/decompiled/")
 }

 List<Path> getProcessedTargets() {
  return new ArrayList<>(_processedTargets)
 }

 List<Path> getMatchedTargets() {
  return new ArrayList<>(_matchedTargets)
 }

 def getOptions() {
  return this.options.clone()
 }

 def setClassLoader(ClassLoader cl) {
  this.ff_cl = cl
 }

 static String sanitize(final String path) {
  return path.replace('\\', '/')
 }

 static String cropFileExtension(final String path) {
  final int lastIndexOf = path.lastIndexOf(".")
  if (lastIndexOf != -1) {
   return path.substring(0, lastIndexOf)
  }
  return path
 }
}
