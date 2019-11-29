package net.minecraftforge.gradle.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipUtils {
 public static byte[] readEntry(final ZipEntry ze, final ZipInputStream zis) throws IOException {
  final int entrySize = (int)ze.getSize();
  if (entrySize == 0 || ze.isDirectory()) {
   return new byte[0];
  }
  final byte[] data = new byte[entrySize];
  int
   read,
   totalRead = 0;
  while ((read = zis.read(data, totalRead, entrySize - totalRead)) > 0) {
   totalRead += read;
  }
  if (totalRead != entrySize) {
   throw new IOException("Read entry did not match reported entry size!");
  }
  zis.closeEntry();
  return data;
 }

 public static void copyEntry(final ZipInputStream zis, final ZipEntry entry, final Path target) throws IOException {
  final File file = target.toFile();
  if (file.exists()) {
   file.delete();
  }
  if (entry.isDirectory()) {
   file.mkdirs();
  } else {
   final File parent = file.getParentFile();
   if (!parent.exists()) {
    parent.mkdirs();
   }
   file.createNewFile();
   byte[] data = new byte[512];
   int read;
   while ((read = zis.read(data, 0, 512)) > 0) {
    byte[] toWrite = data;
    if (read != 512) {
     final byte[] dataRead = new byte[read];
     System.arraycopy(data, 0, dataRead, 0, read);
     toWrite = dataRead;
    }
    Files.write(target, toWrite, StandardOpenOption.APPEND);
   }
  }
  zis.closeEntry();
 }
}
