package net.minecraftforge.gradle.tasks

import net.minecraftforge.gradle.delayed.DelayedFile
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask.Cached
import org.gradle.api.tasks.TaskAction

import org.mcdh.jda.JavaDecompileProxy

import java.nio.file.Files

class JdaDecompileTask extends CachedTask {
 @Cached
 private DelayedFile inputDir

 @Cached
 private DelayedFile outputDir

 //TODO
 @Cached
 private Map<String, String> options = new LinkedHashMap<>()

 @TaskAction
 def doTask() {
  final def decompiler = new JavaDecompileProxy(options)
  final File input = inputDir.call(), output = outputDir.call()
  project
   .fileTree(input)
   .asList()
//   .parallelStream()
   .stream()
   .forEach { f ->
    final def absInputPath = sanitize(f.getAbsolutePath())
    final def relInputPath = absInputPath.replace(input.getAbsolutePath(), '')
    final def outputPath = "${sanitize(output.getAbsolutePath())}/${relInputPath}".replace('.class', '.java')
    logger.debug("Decompiling: ${relInputPath}")
    final File out = project.file(outputPath)
    if (!out.parentFile.exists()) {
     out.parentFile.mkdirs()
    }
    if (out.exists()) {
     out.delete()
    }
    out.createNewFile()
    Files.write(out.toPath(), decompiler.decompile(absInputPath).getBytes())
   }
 }

 def setInputDir(final def inputDir) {
  this.inputDir = inputDir
 }

 def setOutputDir(final def outputDir) {
  this.outputDir = outputDir
 }

 def addOptions(final Map<String, String> options) {
  this.options.putAll(options)
 }

 def getInputDir() {
  return inputDir
 }

 def getOutputDir() {
  return outputDir
 }

 def getOptions() {
  return this.options.clone()
 }

 static String sanitize(final String path) {
  return path.replace('\\', '/')
 }
}
