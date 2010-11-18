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

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcDir;
import makebuilder.SrcFile;
import makebuilder.Makefile.Target;
import makebuilder.ext.CakeHandler;
import makebuilder.ext.mca.DependencyHandler;
import makebuilder.ext.mca.DescriptionBuilderHandler;
import makebuilder.ext.mca.EtcDirCopier;
import makebuilder.ext.mca.HFileCopier;
import makebuilder.ext.mca.LibInfoGenerator;
import makebuilder.ext.mca.MCALibrary;
import makebuilder.ext.mca.MCAPlugin;
import makebuilder.ext.mca.MCAProgram;
import makebuilder.ext.mca.MCASystemLibLoader;
import makebuilder.ext.mca.SConscriptParser;
import makebuilder.handler.CppHandler;
import makebuilder.handler.CppMerger;
import makebuilder.handler.JavaHandler;
import makebuilder.handler.MakeXMLLoader;
import makebuilder.handler.NvccHandler;
import makebuilder.handler.Qt4Handler;
import makebuilder.handler.ScriptHandler;
import makebuilder.util.CodeBlock;
import makebuilder.util.Util;
import makebuilder.util.Util.Color;

/**
 * @author max
 *
 * MakeFileBuilder customization for Finroc
 */
public class FinrocBuilder extends MakeFileBuilder {

    /** Are we building finroc? */
    public final static boolean BUILDING_FINROC = System.getenv("FINROC_TARGET") != null;

    /** Environment variable containing target */
    private final static String TARGET_ENV_VAR = BUILDING_FINROC ? "FINROC_TARGET" : "MCATARGET";

    /** Target to build */
    private final static String TARGET = System.getenv(TARGET_ENV_VAR);

    /** Source directories to use */
    private final static String[] SOURCE_PATHS = BUILDING_FINROC ?
            new String[] {"sources"} :
            new String[] {"libraries", "projects", "tools", "rrlib"};

    /** Include paths to use */
    private final static String[] INCLUDE_PATHS = BUILDING_FINROC ? new String[] {"sources/cpp"} : new String[] {"libraries", "projects", "tools", "."};

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

    public FinrocBuilder() {
        super("export/$(TARGET)", "build/$(TARGET)");
        //super("export" + FS + opts.getProperty("build"), "build" + FS + opts.getProperty("build"));

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
        //makefile.addVariable("TARGET_PLUGIN=$(TARGET_DIR)/plugin");

        // init global defines
        globalDefine.add("#define _MCA_VERSION_ \"2.4.1\"");
        //globalDefine.add("#define _MCA_DEBUG_");
        //globalDefine.add("#define _MCA_PROFILING_");
        globalDefine.add("#define _MCA_LINUX_");


        // init handlers
        addLoader(new SConscriptParser());
        addLoader(new MakeXMLLoader(MCALibrary.class, MCAPlugin.class, MCAProgram.class, FinrocLibrary.class, FinrocPlugin.class, TestProgram.class, JavaTestProgram.class,
                                    RRLib.class, FinrocProgram.class, RRJavaLib.class, FinrocJavaProgram.class, FinrocJavaLibrary.class, FinrocJavaPlugin.class));
        addHandler(new Qt4Handler());
        addHandler(new NvccHandler(""/*"-include libinfo.h"*/));
        addHandler(new DescriptionBuilderHandler());
        if (!(new File(DescriptionBuilderHandler.DESCRIPTION_BUILDER_BIN.trim()).exists())) {
            DescriptionBuilderHandler.DESCRIPTION_BUILDER_BIN = "sources/cpp/mca2-legacy/" + DescriptionBuilderHandler.DESCRIPTION_BUILDER_BIN; // not nice... but ok for now
        }
        if (getOptions().combineCppFiles) {
            addHandler(new CppMerger("#undef LOCAL_DEBUG", "#undef MODULE_DEBUG"));
            makefile.changeVariable(Makefile.DONE_MSG_VAR + "=" + QUICK_BUILD_DONE_MSG);
        }
        String sysLinkPath = "";
        String sysLinkPath2 = "";
        if (getOptions().containsKey("usesysteminstall")) {
            systemInstall = new MCASystemLibLoader();
            if (systemInstall.MCA_SYSTEM_LIB != null) {
                sysLinkPath = systemInstall.MCA_SYSTEM_LIB.getAbsolutePath();
                sysLinkPath2 = ",-rpath," + sysLinkPath;
                sysLinkPath = " -L" + sysLinkPath;
            }
        }
        addHandler(new CppHandler("-Wall -Wwrite-strings -Wno-unknown-pragmas -include libinfo.h",
                                  "-lm -L" + targetLib.relative + sysLinkPath + " -Wl,-rpath," + targetLib.relative + sysLinkPath2,
                                  !opts.combineCppFiles));
        addHandler(new JavaHandler());
        addHandler(new CakeHandler());
        addHandler(new ScriptHandler("$(TARGET_BIN)", "$$FINROC_HOME"));
        //addHandler(new LdPreloadScriptHandler());

        // is MCA installed system-wide?
        if (systemInstall != null) {
            addHandler(systemInstall);
        }

        // generate library info files?
        if (getOptions().containsKey("systeminstall")) {
            makefile.addVariable("TARGET_INFO:=$(TARGET_DIR)/info");
            makefile.addVariable("TARGET_INCLUDE:=$(TARGET_DIR)/include");
            makefile.addVariable("TARGET_ETC:=$(TARGET_DIR)/etc");
            addHandler(new LibInfoGenerator("$(TARGET_INFO)"));
            addHandler(new HFileCopier("$(TARGET_INCLUDE)"));
            addHandler(new EtcDirCopier("$(TARGET_ETC)"));
            Target t = makefile.addPhonyTarget("sysinstall", "libs", "tools", "test");
            t.addCommand("echo success > $(TARGET_DIR)/success", true);
        }

        // Calculate dependencies option?
        if (getOptions().calculateDependencies) {
            addHandler((dependencyHandler = new DependencyHandler()));
        }
        
        // Add "tools" target - if it does not exist yet
        if (makefile.getPhonyTarget("tools") == null) {
            makefile.addPhonyTarget("tools");
        }
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
            //File targetFile = new File(System.getenv("FINROC_HOME") + "/etc/targets/" + target);
            String home = BUILDING_FINROC ? System.getenv("FINROC_HOME") : System.getenv("MCAHOME");
            File targetFile = new File(home + "/etc/targets/" + TARGET);
            //File targetFile = Util.getFileInEtcDir("../targets/" + target);
            if (targetFile.exists()) {
                System.out.println(Util.color("Using custom options from target config file: " + targetFile.getCanonicalPath(), Color.GREEN, true));
                makefile.applyVariablesFromFile(targetFile);
            } else {
                System.out.println(Util.color("No configuration file for current target found (" + targetFile.getAbsolutePath() + ")", Color.Y, true));
            }
        }

        // include target file
        makefile.addVariable("-include etc/targets/$(TARGET)");

        if (getOptions().containsKey("usesysteminstall")) {
            new FinrocRepositoryTargetCreator().postprocess(makefile);
        } else {
            new FinrocRepositoryTargetCreator().postprocess(makefile, "tools");
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
            if (!be.missingDep) {
                if (be instanceof RRLib) {
                    // _RRLIB_COMPUTER_VISION_BASE_PRESENT_
                    globalDefine.add("#define _LIB_RRLIB_" + be.name.toUpperCase() + "_PRESENT_");
                } else if (be instanceof FinrocLibrary) {
                    // _LIB_FINROC_COMPUTER_VISION_BASE_PRESENT_
                    globalDefine.add("#define _LIB_FINROC_" + be.name.toUpperCase() + "_PRESENT_");
                } else if (be instanceof FinrocPlugin) {
                    // _FINROC_PLUGIN_COMPUTER_VISION_BASE_PRESENT_
                    globalDefine.add("#define _LIB_FINROC_PLUGIN_" + be.name.toUpperCase() + "_PRESENT_");
                } else if (be instanceof MCALibrary) {
                    // _LIB_MCA2_COMPUTER_VISION_BASE_PRESENT_
                    globalDefine.add("#define _LIB_MCA2_" + be.name.toUpperCase() + "_PRESENT_");
                } else if ((be instanceof MCASystemLibLoader.SystemLibrary) && be.getTarget().endsWith(".so")) {
                    String def = be.getTargetFilename().toUpperCase();
                    def = def.substring(0, def.length() - 3); // cut off ".SO"
                    if (def.startsWith("LIB")) {
                        globalDefine.add("#define _LIB_" + def.substring("lib".length()) + "_PRESENT_");
                    }
                }
            }
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
        return relative;
    }

    @Override
    public SrcFile getTempBuildArtifact(BuildEntity source, String targetExtension, String suggestedPrefix) {
        if (targetExtension.equals("mf")) {
            return sources.registerBuildProduct(tempBuildPath.getParent().getSubDir("java") + FS + source.getTargetFilename().replaceAll("[.]jar$", "") + ".mf");
        }
        return sources.registerBuildProduct(tempPath + FS + source.getTargetFilename().replace('.', '_') + "_" + suggestedPrefix + "." + targetExtension);
    }

    @Override
    public String getTempBuildDir(BuildEntity source) {
        if (source.getFinalHandler() == JavaHandler.class) {
            return tempBuildPath.getParent().getSubDir("java") + FS + source.getTargetFilename().replaceAll("[.]jar$", "");
        }
        String srcDir = source.getRootDir().relativeTo(source.getRootDir().getSrcRoot());
        return tempBuildPath.relative + FS + trimDir(srcDir);
    }

}
