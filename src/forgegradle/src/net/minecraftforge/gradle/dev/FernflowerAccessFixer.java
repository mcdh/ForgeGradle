package net.minecraftforge.gradle.dev;

import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FernflowerAccessFixer extends ClassLoader {
 private static final Method mdefineClass0;
// private static final Field fparent;

 static {
  try {
   mdefineClass0 = ClassLoader.class.getDeclaredMethod("defineClass0", String.class, byte[].class, int.class, int.class, ProtectionDomain.class);
   mdefineClass0.setAccessible(true);
//   fparent = ClassLoader.class.getDeclaredField("parent");
//   fparent.setAccessible(true);
  } catch (Throwable t) {
   throw new RuntimeException(t);
  }
 }

 private final Set<String> libraryCandidates;

 public FernflowerAccessFixer(final ClassLoader parent, final List<File> libraryCandidates) {
  super(parent);
  try {
//   fparent.set(this, parent);
   Thread.currentThread().setContextClassLoader(this);
  } catch (Throwable t) {
   throw new RuntimeException(t);
  }
  this.libraryCandidates = new HashSet<>();
  libraryCandidates
   .parallelStream()
   .filter(l -> {
    final String name = l.getName();
    if (!(name.endsWith(".class") || name.endsWith(".jar"))) {
     System.err.println("Library candidate: '" + l.getAbsolutePath() + "' is not valid and will be omitted!");
     return false;
    }
    return true;
   })
   .forEach(l -> FernflowerAccessFixer.this.libraryCandidates.add(l.getAbsolutePath()));
 }

// public void reInject() {
//  Thread.currentThread().setContextClassLoader(this);
// }

 public Class<?> loadClass(String clazz) throws ClassNotFoundException {
//  if (clazz.contains("org.jetbrains")) {
  final String
   clazz_std = clazz.toLowerCase(),
   clazzPath = clazz.replace(".", "/") + ".class";
  if (clazz_std.contains("fernflower")
   || clazz_std.contains("org.jetbrains.java.decompiler")
   || clazz_std.contains("org.mcdh")
  ) {
//   System.out.println("Class Requested: " + clazz);
   try {
    for (final String candidate : libraryCandidates) {
     byte[] data = null;
     final File f = new File(candidate);
     if (candidate.endsWith(".class")) {
      //Load class if it matches the request
      final String binaryName = candidate.replace("/", ".");
      if (binaryName.contains(clazz)) {
       data = Files.readAllBytes(f.toPath());
      }
     } else if (candidate.endsWith(".jar")) {
      //Search the jar for the given class and load it if found
      final ZipInputStream zis = new ZipInputStream(new FileInputStream(f));
      ZipEntry ze;
      while ((ze = zis.getNextEntry()) != null) {
       if (ze.getName().equals(clazzPath)) {
        break;
       }
      }
      if (ze == null) {
       continue;
//       throw new IOException("No entry for '" + clazz + "' in JDA library '" + "LIBRARY_HERE" + "'!");
      }
      final int entrySize = (int)ze.getSize();
      data = new byte[entrySize];
      int
       read,
       totalRead = 0;
      while((read = zis.read(data, totalRead, entrySize - totalRead)) > 0) {
       totalRead += read;
      }
      if (totalRead != entrySize) {
       throw new IOException("Read entry did not match reported entry size!");
      }
      zis.closeEntry();
      zis.close();
     }
//     final InputStream is;
//     if ((is = super.getResourceAsStream(clazzPath)) == null) {
//      throw new RuntimeException("Super ClassLoader could not find class: " + clazz);
//     }
//     System.out.println("Bytecode Matches: " + Arrays.equals(data, ByteStreams.toByteArray(is)));
     return (Class<?>)mdefineClass0.invoke(this, clazz, data, 0, data.length, null);
    }
   } catch (Throwable t) {
    throw (ClassNotFoundException)new ClassNotFoundException().initCause(t);
   }
  }
  return super.loadClass(clazz);
 }
}
