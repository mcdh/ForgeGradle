package net.minecraftforge.gradle.dev;

import net.minecraftforge.gradle.delayed.DelayedFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;

public class DelayedDeleteTask extends DefaultTask {
 private final Project project = getProject();
 private final LinkedList<DelayedFile> toDelete = new LinkedList<>();

 @TaskAction
 public void doTask() {
  toDelete
   .parallelStream()
   .forEach(d -> {
    final File toDelete = d.call();
    project.delete(toDelete);
    toDelete.mkdirs();
   });
 }

 public void delete(final DelayedFile... files) {
  toDelete.addAll(Arrays.asList(files));
 }
}
