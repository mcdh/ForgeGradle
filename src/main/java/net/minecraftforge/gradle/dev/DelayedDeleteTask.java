package net.minecraftforge.gradle.dev;

import net.minecraftforge.gradle.delayed.DelayedFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import java.util.Arrays;
import java.util.LinkedList;

public class DelayedDeleteTask extends DefaultTask {
 private final Project project = getProject();
 private final LinkedList<DelayedFile> toDelete = new LinkedList<>();

 @TaskAction
 public void doTask() {
  toDelete
   .parallelStream()
   .forEach(d -> project.delete(d.call()));
 }

 public void delete(final DelayedFile... files) {
  toDelete.addAll(Arrays.asList(files));
 }
}
