package net.minecraftforge.gradle.user.patch;

public class UserPatchConstants
{
   public static final String SRC_DIR            = "{USER_DEV}/src/main/java";
   public static final String RES_DIR            = "{USER_DEV}/src/main/resources";
   public static final String FML_AT             = RES_DIR + "/fml_at.cfg";
   public static final String FORGE_AT           = RES_DIR + "/forge_at.cfg";

   public static final String BINPATCHES         = "{USER_DEV}/devbinpatches.pack.lzma";
   public static final String BINARIES_JAR       = "{USER_DEV}/binaries.jar";
   public static final String JAVADOC_JAR        = "{USER_DEV}/javadoc.jar";

   public static final String JSON               = "{USER_DEV}/dev.json";
   public static final String ECLIPSE_LOCATION   = "eclipse/.metadata/.plugins/org.eclipse.core.resources/.projects";

   public static final String JAR_BINPATCHED     = "{API_CACHE_DIR}/{API_NAME}-binpatched-{API_VERSION}.jar";

   public static final String CLASSIFIER_PATCHED = "patched";

   public static final String FML_PATCHES_ZIP    = "{USER_DEV}/fmlpatches.zip";
   public static final String FORGE_PATCHES_ZIP  = "{USER_DEV}/forgepatches.zip";

   public static final String START_DIR          = "{API_CACHE_DIR}/start";
}
