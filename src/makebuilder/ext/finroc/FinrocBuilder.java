/**
 * You received this file as part of an experimental
 * build tool ('makebuilder') - originally developed for MCA2.
 *
 * Copyright (C) 2008-2010 Max Reichardt,
 *   Robotics Research Lab, University of Kaiserslautern
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package makebuilder.ext.finroc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcDir;
import makebuilder.SrcFile;
import makebuilder.ext.mca.DependencyHandler;
import makebuilder.ext.mca.DescriptionBuilderHandler;
import makebuilder.ext.mca.MCALibrary;
import makebuilder.ext.mca.MCAPlugin;
import makebuilder.ext.mca.MCAProgram;
import makebuilder.ext.mca.MCASystemLibLoader;
import makebuilder.ext.mca.SConscriptParser;
import makebuilder.handler.CppHandler;
import makebuilder.handler.CppMerger;
import makebuilder.handler.EnumStringsBuilderHandler;
import makebuilder.handler.JavaHandler;
import makebuilder.handler.MakeXMLLoader;
import makebuilder.handler.NvccHandler;
import makebuilder.handler.PkgConfigFileHandler;
import makebuilder.handler.Qt4Handler;
import makebuilder.handler.ScriptHandler;
import makebuilder.libdb.LibDB;
import makebuilder.util.CodeBlock;
import makebuilder.util.Util;
import makebuilder.util.Util.Color;

/**
 * @author Max Reichardt
 *
 * MakeFileBuilder customization for Finroc
 */
public class FinrocBuilder extends MakeFileBuilder implements JavaHandler.ImportDependencyResolver {

    /** Are we building finroc? */
    public final static boolean BUILDING_FINROC = System.getenv("FINROC_TARGET") != null;

    /** Environment variable containing target */
    private final static String TARGET_ENV_VAR = BUILDING_FINROC ? "FINROC_TARGET" : "MCATARGET";

    /** Target to build */
    private final static String TARGET = System.getenv(TARGET_ENV_VAR);

    /** File containing target configuration */
    private final static File TARGET_FILE = new File((BUILDING_FINROC ? System.getenv("FINROC_HOME") : System.getenv("MCAHOME")) + "/etc/targets/" + TARGET);

    /** Source directories to use */
    private final static String[] SOURCE_PATHS = BUILDING_FINROC ?
            new String[] {"sources"} :
            new String[] {"libraries", "projects", "tools", "rrlib"};

    /** Include paths to use */
    private final static String[] INCLUDE_PATHS = BUILDING_FINROC ?
            new String[] {"sources/cpp"} :
            new String[] {"libraries", "projects", "tools", "."};

    /** Include paths for mca2-legacy targets */
    private final String[] LEGACY_INCLUDE_PATHS = new String[] {"sources/cpp", "sources/cpp/mca2-legacy/libraries", "sources/cpp/mca2-legacy/projects", "sources/cpp/mca2-legacy/tools"};

    /** Global definitions - e.g. */
    private final CodeBlock globalDefine = new CodeBlock();

    /** Target directory for libraries, Target directory for binaries */
    public final SrcDir targetLib, targetBin;

    /** System library installation handler */
    public MCASystemLibLoader systemInstall;

    /** Done message with warning for quick builds */
    public static final String QUICK_BUILD_DONE_MSG = "done \\(reminder: This is a \\\"quick \\& dirty\\\" build. Please check with makeSafe*** whether your code is actually correct C/C++, before committing to the svn repositories.\\)";

    /** Optional dependency handler */
    private DependencyHandler dependencyHandler;

    /** Is make_builder used for cross-compiling? */
    private Boolean crossCompiling;

    /** Is static linking enabled? */
    private boolean staticLinking;

    /** libdb to use for actual compiling */
    private LibDB targetLibDB;

    /** Finroc C++ handler - in case BUILDING_FINROC is true - otherwise null */
    private FinrocCppHandler finrocCppHandler;

    public FinrocBuilder() {
        super("export/$(TARGET)", "build/$(TARGET)");

        // Process target file
        if (!TARGET_FILE.exists()) {
            System.out.println(Util.color("No configuration file for current target found (expected " + TARGET_FILE.getPath() + ")!", Color.RED, true));
            System.out.println(Util.color("Maybe you need to source scripts/setenv again to update your environment.", Color.RED, true));
            System.exit(-1);
        }
        try {
            for (String line : Util.readLinesWithoutComments(TARGET_FILE, false)) {
                if (line.trim().equals("STATIC_LINKING=true")) {
                    staticLinking = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // init libdb
        targetLibDB = LibDB.getInstance("native").reinit(null);
        if (isCrossCompiling()) {
            targetLibDB = LibDB.getInstance(System.getenv("FINROC_ARCHITECTURE")).reinit(new File("make_builder/etc/libdb." + System.getenv("FINROC_ARCHITECTURE")));
        }

        // init target paths
        targetBin = buildPath.getSubDir("bin");
        targetLib = buildPath.getSubDir("lib");
        makefile.addVariable(TARGET_ENV_VAR + "?=" + TARGET);
        makefile.addVariable("TARGET:=$(" + TARGET_ENV_VAR + ")");
        makefile.addVariable("TARGET_BIN:=$(TARGET_DIR)/bin");
        makefile.addVariable("TARGET_LIB:=$(TARGET_DIR)/lib");
        makefile.addVariable("TARGET_JAVA:=export/java");
        makefile.addBuildDir("build/java");
        makefile.addBuildDir("export/java");
        makefile.addBuildDir("make_builder/dist");
        //makefile.addVariable("TARGET_PLUGIN=$(TARGET_DIR)/plugin");

        // init global defines
        globalDefine.add("#define _MCA_VERSION_ \"2.4.1\"");
        //globalDefine.add("#define _MCA_DEBUG_");
        //globalDefine.add("#define _MCA_PROFILING_");
        globalDefine.add("#define _MCA_LINUX_");


        // init handlers
        addLoader(new SConscriptParser());
        if (BUILDING_FINROC) {
            addLoader(new MakeXMLLoader(Library.class, Program.class, MCALibrary.class, MCAProgram.class, FinrocLibrary.class, FinrocPlugin.class, UnitTest.class, TestProgram.class, RRLib.class, FinrocProgram.class));
        } else {
            addLoader(new MakeXMLLoader(Library.class, Program.class, MCALibrary.class, MCAPlugin.class, MCAProgram.class, FinrocLibrary.class, FinrocPlugin.class, UnitTest.class, TestProgram.class, RRLib.class, FinrocProgram.class));
        }
        addHandler(new Qt4Handler());
        addHandler(new NvccHandler("-Xcompiler -fPIC"/*"-include libinfo.h"*/));
        addHandler(new DescriptionBuilderHandler());
        String cflags = "-Wall -Wwrite-strings -Wno-unknown-pragmas -include libinfo.h";
        String cxxflags = cflags + " -include make_builder/enum_strings_builder/enum_strings.h";
        String clangCodeGenerationFlags = "-std=c++11 -include libinfo.h -include make_builder/enum_strings_builder/enum_strings.h";
        globalDefine.add("#define _LIB_ENUM_STRINGS_PRESENT_");
        addHandler(new EnumStringsBuilderHandler("export/$(TARGET)/lib", clangCodeGenerationFlags, "$(FINROC_ARCHITECTURE_NATIVE)"));

        if (BUILDING_FINROC) {
            addHandler(new PortDescriptionBuilderHandler(clangCodeGenerationFlags, "$(FINROC_ARCHITECTURE_NATIVE)"));
        }
        if (getOptions().combineCppFiles) {
            addHandler(new CppMerger("#undef LOCAL_DEBUG", "#undef MODULE_DEBUG"));
            makefile.changeVariable(Makefile.DONE_MSG_VAR + "=" + QUICK_BUILD_DONE_MSG);
        }

        // generate pkg-config files
        makefile.addVariable("TARGET_PKGINFO:=export/pkgconfig");
        addHandler(new PkgConfigFileHandler("$(TARGET_PKGINFO)", "/usr"));

        // look for any system-installed libraries
        if (BUILDING_FINROC && (!getOptions().containsKey("local-libs-only"))) {
            addHandler(new FinrocSystemLibLoader());
        }

        if (BUILDING_FINROC) {
            finrocCppHandler = new FinrocCppHandler(cflags, cxxflags, "$(if $(STATIC_LINKING),,-fPIC)", "", "-lm -L" + targetLib.relative + " -Wl,--no-as-needed,-rpath," + targetLib.relative, "", "", !opts.combineCppFiles);
            addHandler(finrocCppHandler);
        } else {
            addHandler(new CppHandler(cflags, cxxflags, "$(if $(STATIC_LINKING),,-fPIC)", "", "-lm -L" + targetLib.relative + " -Wl,--no-as-needed,-rpath," + targetLib.relative, "", "", !opts.combineCppFiles));
        }
        addHandler(new JavaHandler(this));
        addHandler(new ScriptHandler("$(TARGET_BIN)", "$$FINROC_HOME"));
        //addHandler(new LdPreloadScriptHandler());

        // Calculate dependencies option?
        if (getOptions().calculateDependencies) {
            addHandler((dependencyHandler = new DependencyHandler()));
        }

        // Add "tools" target - if it does not exist yet
        if (makefile.getPhonyTarget("tools") == null) {
            makefile.addPhonyTarget("tools");
        }

        // generate $(TARGET_DIR)/share/java symbolic link
        Makefile.Target shareJava = makefile.addTarget("$(TARGET_DIR)/share/java.created", false, null, false);
        shareJava.addCommand("ln -f -s ../../../$(TARGET_JAVA) $(TARGET_DIR)/share/java", true);
        shareJava.addCommand("echo done > $(TARGET_DIR)/share/java.created", true);
    }

    @Override
    protected void writeMakefile() throws Exception {

        if (getOptions().calculateDependencies) {
            Object print = getOptions().get("print");
            dependencyHandler.writeFiles(print == null ? null : print.toString());
            return;
        }

        // apply options for specific target?
        if (TARGET != null) {
            // gcc should be default compiler if no other compiler was specified
            makefile.changeVariable("CC=gcc$(GCC_VERSION)");
            makefile.changeVariable("CXX=g++$(GCC_VERSION)");
            makefile.addVariable("STATIC_LINKING_CHECK=" + (staticLinking ? "true" : ""));

            makefile.addVariable("include etc/targets/$(TARGET)");

            makefile.addVariable("ifneq ($(STATIC_LINKING_CHECK), $(STATIC_LINKING))");
            makefile.addVariable("$(error Setting on static linking has changed. Please recreate Makefile.)");
            makefile.addVariable("endif");
        }

        if (getOptions().containsKey("usesysteminstall")) {
            new FinrocRepositoryTargetCreator().postprocess(makefile);
        } else {
            new FinrocRepositoryTargetCreator().postprocess(makefile, "tools");
        }

        // udpate 'presence' files
        if (finrocCppHandler != null) {
            finrocCppHandler.updatePresenceFiles();
        }

        super.writeMakefile();
    }

    @Override
    public void setDefaultIncludePaths(SrcDir dir, SourceScanner sources) {

        String[] includePaths = INCLUDE_PATHS;
        boolean legacyTarget = dir.relative.contains("mca2-legacy");
        if (BUILDING_FINROC && legacyTarget) {
            includePaths = LEGACY_INCLUDE_PATHS;
        }

        if (!dir.relative.startsWith("/")) {
            for (String s : includePaths) {
                dir.defaultIncludePaths.add(sources.findDir(s, true));
            }
        }

        // add system include paths - in case MCA is installed system-wide
        if (systemInstall != null && systemInstall.systemInstallExists) {
            String p = systemInstall.MCA_SYSTEM_INCLUDE.getAbsolutePath();
            for (String s : includePaths) {
                if (BUILDING_FINROC && s.startsWith("sources/cpp")) {
                    s = s.substring("sources/cpp".length());
                }  else {
                    s = "/" + s;
                }
                dir.defaultIncludePaths.add(sources.findDir(p + s, true));
            }
        }
        dir.defaultIncludePaths.add(sources.findDir("/usr/include", true));
        if (new File("/usr/include/finroc").exists()) {
            dir.defaultIncludePaths.add(sources.findDir("/usr/include/finroc", true));
        }

        if (dir.relative.startsWith(tempBuildPath.relative) && dir.relative.startsWith("/")) {
            return;
        }
        if (BUILDING_FINROC) {
            if (legacyTarget) {
                dir.defaultIncludePaths.add(sources.findDir(tempBuildPath.relative + FS + "mca2-legacy/libraries", true));
                dir.defaultIncludePaths.add(sources.findDir(tempBuildPath.relative + FS + "mca2-legacy/projects", true));
            }
        } else {
            dir.defaultIncludePaths.add(sources.findDir(tempBuildPath.relative + FS + "projects", true));
            dir.defaultIncludePaths.add(sources.findDir(tempBuildPath.relative + FS + "libraries", true));
            dir.defaultIncludePaths.add(sources.findDir(tempBuildPath.relative, true));
        }
    }

    @Override
    public String[] getSourceDirs() {
        if (systemInstall != null && systemInstall.systemInstallExists) {
            String[] tmp = new String[SOURCE_PATHS.length + 1];
            System.arraycopy(SOURCE_PATHS, 0, tmp, 0, SOURCE_PATHS.length);
            tmp[SOURCE_PATHS.length] = systemInstall.MCA_SYSTEM_INCLUDE.getAbsolutePath();
            return tmp;
        }
        return SOURCE_PATHS;
    }

    public void run() {
        super.run();

        // create additional defines
        for (BuildEntity be : buildEntities) {
            if (!be.missingDep && be.isLibrary()) {
                if (be instanceof RRLib || be instanceof FinrocLibrary || be instanceof FinrocPlugin || be instanceof Library) {
                    // _RRLIB_COMPUTER_VISION_BASE_PRESENT_
                    FinrocBuildEntity finrocBE = (FinrocBuildEntity)be;
                    globalDefine.add("#define _LIB_" + (finrocBE.createTargetPrefix() + finrocBE.createNameString()).toUpperCase() + finrocBE.createTargetSuffix().toUpperCase().replace('-', '_') + "_PRESENT_");
                    //globalDefine.add("#define _LIB_RRLIB_" + be.name.toUpperCase() + "_PRESENT_");
                } else if (be instanceof MCALibrary) {
                    // _LIB_MCA2_COMPUTER_VISION_BASE_PRESENT_
                    globalDefine.add("#define _LIB_MCA2_" + be.name.toUpperCase() + "_PRESENT_");
                } else if (((be instanceof MCASystemLibLoader.SystemLibrary) || (be instanceof FinrocSystemLibLoader.SystemLibrary)) && be.getTarget().endsWith(".so")) {
                    String def = be.getTargetFilename().toUpperCase();
                    def = def.substring(0, def.length() - 3); // cut off ".SO"
                    if (def.startsWith("LIB")) {
                        globalDefine.add("#define _LIB_" + def.substring("lib".length()) + "_PRESENT_");
                    }
                }
            }
        }

        if (FinrocSystemLibLoader.areSystemLibsLoaded() || (systemInstall != null && systemInstall.systemInstallExists)) {
            globalDefine.add("#define _FINROC_SYSTEM_INSTALLATION_PRESENT_");
        }

        // write defines to libinfo.h
        globalDefine.add("");
        try {
            globalDefine.writeTo(new File("libinfo.h"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public SrcFile getTempBuildArtifact(SrcFile source, String targetExtension) {
        SrcDir targetDir = tempBuildPath.getSubDir(trimDir(source.dir.relative));
        if (source.getExtension().equals("ui")) {
            return sources.registerBuildProduct(targetDir.relative + FS + "ui_" + source.getRawName() + ".h");
        } else if (targetExtension.equals("hpp")) { // description builder template
            return sources.registerBuildProduct(targetDir.relative + FS + "descr_h_" + source.getRawName() + ".hpp");
        }
        return sources.registerBuildProduct(targetDir.relative + FS + source.getRawName() + "." + targetExtension);
    }

    /**
     * @param relative Relative path
     * @return Relative path without source/cpp in front
     */
    private String trimDir(String relative) {
        if (relative.startsWith("sources/cpp/")) {
            return relative.substring("sources/cpp/".length());
        }
        if (relative.startsWith("build/$(TARGET)")) {
            return relative.substring("build/$(TARGET)".length());
        }
        return relative;
    }

    @Override
    public SrcFile getTempBuildArtifact(BuildEntity source, String targetExtension, String suggestedPrefix) {
        if (targetExtension.equals("mf")) {
            return sources.registerBuildProduct(tempBuildPath.getParent().getSubDir("java") + FS + source.getTargetFilename().replaceAll("[.]jar$", "") + ".mf");
        }
        SrcDir targetDir = tempBuildPath.getSubDir(trimDir(source.getRootDir().relative));
        String filename = source.getTargetFilename();
        if (filename.contains(".")) {
            filename = filename.substring(0, filename.lastIndexOf("."));
        }
        return sources.registerBuildProduct(targetDir.relative + FS + filename + "_" + suggestedPrefix + "." + targetExtension);
    }

    @Override
    public String getTempBuildDir(BuildEntity source) {
        if (source.getFinalHandler() == JavaHandler.class) {
            return tempBuildPath.getParent().getSubDir("java") + FS + source.getTargetFilename().replaceAll("[.]jar$", "");
        }
        String srcDir = source.getRootDir().relativeTo(source.getRootDir().getSrcRoot());
        if (source.getRootDir().relative.startsWith("sources/cpp/")) {
            srcDir = source.getRootDir().relative.substring("sources/cpp/".length());
        }
        return tempBuildPath.relative + FS + trimDir(srcDir);
    }

    @Override
    public short getVersion() {
        return 1;
    }

    @Override
    public ArrayList<String> getDependencies(SrcFile file, List<String> imports) {
        ArrayList<String> result = new ArrayList<String>();
        for (String imp : imports) {
            if (imp.contains("*")) {
                System.err.println("warning: ignoring import statement with '*' for automatic dependency resolving (" + file.relative + ")");
                continue;
            }
            String dependency = null;
            if (imp.startsWith("org.rrlib.") || imp.startsWith("org.finroc.")) {
                String[] parts = imp.split("[.]");
                if (parts[1].equals("rrlib")) {
                    dependency = "rrlib_" + parts[2] + ".jar";
                } else {
                    if (parts[2].equals("core")) {
                        dependency = "finroc_core.jar";
                    } else if (parts[2].equals("tools")) {
                        if (parts[3].equals("gui") && parts[4].equals("plugins")) {
                            dependency = "finroc_tools_gui_plugins_" + parts[5] + ".jar";
                        } else if (parts[3].equals("gui")) {
                            dependency = "fingui.jar";
                        } else {
                            dependency = parts[3] + ".jar";
                        }
                    } else {
                        dependency = "finroc_" + parts[2] + "_" + parts[3] + ".jar";
                    }
                }
            }
            if (dependency != null && (!result.contains(dependency)) &&
                    (file.getOwner() == null || (!file.getOwner().getTargetFilename().equals(dependency)))) {
                result.add(dependency);
            }
        }
        return result;
    }

    @Override
    public boolean isCrossCompiling() {
        if (crossCompiling == null) {
            crossCompiling = BUILDING_FINROC && (!System.getenv("FINROC_ARCHITECTURE").equals(System.getenv("FINROC_ARCHITECTURE_NATIVE")));
        }
        return crossCompiling;
    }

    @Override
    public boolean isStaticLinkingEnabled() {
        return staticLinking;
    }

    @Override
    public LibDB getTargetLibDB() {
        return targetLibDB;
    }
}
