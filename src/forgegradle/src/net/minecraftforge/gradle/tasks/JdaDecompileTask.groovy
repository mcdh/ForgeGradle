package net.minecraftforge.gradle.tasks

import net.minecraftforge.gradle.delayed.DelayedFile
import net.minecraftforge.gradle.delayed.DelayedString
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask.Cached
import org.apache.shiro.util.AntPathMatcher
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.mcdh.jda.JavaDecompileProxy

import java.nio.file.Files
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class JdaDecompileTask extends CachedTask {
//class JdaDecompileTask extends DefaultTask {
 private final AntPathMatcher pathMatcher = new AntPathMatcher()

 @Input
 private DelayedFile input

 @Cached
 private DelayedFile output

 private DelayedString targets

 private ArrayList<File> processedTargets

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
  final String[] targets = this.targets.resolveDelayed().split(" ")
  final File inputF = input.call(), outputF = output.call()
  final def localInput = project.file("${project.getBuildDir()}/tmp/jdaExtracted/compiled/")
  final def localOutput = project.file("${project.getBuildDir()}/tmp/jdaExtracted/decompiled/")
  //Copy inputs into local build cache, if not already present
  if (!localInput.exists()) {
   project.copy {
    from inputF.isDirectory() ? inputF : project.zipTree(inputF)
    into localInput
   }
  }
  if (!localOutput.exists()) {
   //Copy all non-sources to output directory
   project.copy {
    from localInput
    into localOutput
    exclude '*.class'
    exclude '**/*.class'
   }
   //Create decompiler input list
   def inputs = new ArrayList<File>()
   inputs.addAll(localInput.listFiles())
   boolean directoryAdded = true
   while (directoryAdded) {
    directoryAdded = false
    final def toAdd = new ArrayList<File>(), toRemove = new ArrayList<File>()
    inputs.forEach {
     if (it.isDirectory()) {
      directoryAdded = true
      toAdd.addAll(it.listFiles())
      toRemove.add(it)
     }
    }
    inputs.removeAll(toRemove)
    inputs.addAll(toAdd)
   }
//   inputs = inputs
   processedTargets = inputs
    .parallelStream()
    .filter {
      if (it.name.endsWith(".class") && !it.name.contains('$') && !name.contains("package-info")) {
       for (target in targets) {
        if (pathMatcher.match(target, it.absolutePath)) {
         return true
        }
       }
      }
      return false
//     it.name.endsWith(".class") && !it.name.contains('$') && it.name.contains("BasicMachineRecipe")
    }
    .collect(Collectors.toList())
//   final def decompiler = new JavaDecompileProxy(options, classpath)
   final def cJavaDecompileProxy = ff_cl.loadClass("org.mcdh.jda.JavaDecompileProxy")
//   final def decompiler = new JavaDecompileProxy(options)
   final def decompiler = cJavaDecompileProxy.<JavaDecompileProxy>newInstance(options)
   //Decompile classes
//   inputs.forEach {
   processedTargets.forEach {
    final def relativePath = it
     .absolutePath
     .replace(localInput.absolutePath, "")
     .replace(".class", ".java")
    Files.write(
     new File("${localOutput.absolutePath}$relativePath").toPath(),
     decompiler.decompile(it.absolutePath).getBytes()
    )
   }
  }
  //Copy decompiled output from local build cache to desired output directory / file
  if (!outputF.exists()) {
   if (outputF.isDirectory()) {
    project.copy {
     from localOutput
     into outputF
    }
   } else {
    final String zipOutput = "${outputF.getParentFile().absolutePath}/${outputF.getName()}"
    final ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(zipOutput)))
    final List<File> files = new ArrayList<>()
    files.addAll(localOutput.listFiles())
    boolean additionMade = true
    while (additionMade) {
     additionMade = false
     final List<File> toRemove = new ArrayList<>(), toAdd = new ArrayList<>()
     files.forEach {
      if (it.isDirectory()) {
       additionMade = true
       toAdd.addAll(it.listFiles())
       toRemove.add(it)
      }
     }
     files.removeAll(toRemove)
     files.addAll(toAdd)
    }
//    println("Files to zip: ${files}")
    files.forEach {
     final String entryName = it.absolutePath.replace("${localOutput.absolutePath}/", "")
//     println "Adding entry: $entryName"
     zos.putNextEntry(new ZipEntry(entryName))
     zos.write(Files.readAllBytes(it.toPath()))
     zos.closeEntry()
    }
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

 def getProcessedTargets() {
  return new ArrayList<>(processedTargets)
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
}
