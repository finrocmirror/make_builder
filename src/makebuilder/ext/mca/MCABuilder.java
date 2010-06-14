/**
 * You received this file as part of an experimental
 * build tool ('makebuilder') - originally developed for MCA2.
 *
 * Copyright (C) 2008-2009 Max Reichardt,
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
package makebuilder.ext.mca;

import java.io.File;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcDir;
import makebuilder.SrcFile;
import makebuilder.Makefile.Target;
import makebuilder.ext.CakeHandler;
import makebuilder.handler.CppHandler;
import makebuilder.handler.CppMerger;
import makebuilder.handler.MakeXMLLoader;
import makebuilder.handler.NvccHandler;
import makebuilder.handler.Qt4Handler;
import makebuilder.util.CodeBlock;
import makebuilder.util.Util;

/**
 * @author max
 *
 * MakeFileBuilder customization for MCA
 */
public class MCABuilder extends MakeFileBuilder {

    /** Global definitions - e.g. */
    private final CodeBlock globalDefine = new CodeBlock();

    /** Target directory for libraries, Target directory for binaries */
    public final SrcDir targetLib, targetBin;

    /** Standard compiler options for MCA */
    public static final String MCAOPTS = "-include Makefile.h -Ilibraries -Iprojects -Itools -I. ";

    /** System library installation handler */
    public final MCASystemLibLoader systemInstall;

    /** Done message with warning for quick builds */
    public static final String QUICK_BUILD_DONE_MSG = "done \\(reminder: This is a \\\"quick \\& dirty\\\" build. Please check with makeSafe*** whether your code is actually correct C/C++, before committing to the svn repositories.\\)";

    /** Optional dependency handler */
    private DependencyHandler dependencyHandler;

    public MCABuilder() {
        super("export" + FS + opts.getProperty("build"), "build" + FS + opts.getProperty("build"));

        // init target paths
        targetBin = buildPath.getSubDir("bin");
        targetLib = buildPath.getSubDir("lib");
        makefile.addVariable("TARGET_BIN=$(TARGET_DIR)/bin");
        makefile.addVariable("TARGET_LIB=$(TARGET_DIR)/lib");

        // init global defines
        globalDefine.add("#define _MCA_VERSION_ \"2.4.1\"");
        //globalDefine.add("#define _MCA_DEBUG_");
        //globalDefine.add("#define _MCA_PROFILING_");
        globalDefine.add("#define _MCA_LINUX_");

        // init handlers
        addLoader(new SConscriptParser());
        addLoader(new MakeXMLLoader(MCALibrary.class, MCAPlugin.class, MCAProgram.class));
        addHandler(new Qt4Handler());
        addHandler(new NvccHandler(""/*"-include Makefile.h"*/));
        addHandler(new DescriptionBuilderHandler());
        if (getOptions().combineCppFiles) {
            addHandler(new CppMerger("#undef LOCAL_DEBUG", "#undef MODULE_DEBUG"));
            makefile.changeVariable(Makefile.DONE_MSG_VAR + "=" + QUICK_BUILD_DONE_MSG);
        }
        addHandler(new CppHandler("-Wall -Wwrite-strings -Wno-unknown-pragmas -include Makefile.h",
                                  "-lm -L" + targetLib.relative + " -Wl,-rpath," + targetLib.relative,
                                  !opts.combineCppFiles));
        addHandler(new CakeHandler());

        // is MCA installed system-wide?
        if (getOptions().containsKey("usesysteminstall")) {
            systemInstall = new MCASystemLibLoader();
            addHandler(systemInstall);
        } else {
            systemInstall = null;
        }

        // generate library info files?
        if (getOptions().containsKey("systeminstall")) {
            makefile.addVariable("TARGET_INFO=$(TARGET_DIR)/info");
            makefile.addVariable("TARGET_INCLUDE=$(TARGET_DIR)/include");
            makefile.addVariable("TARGET_ETC=$(TARGET_DIR)/etc");
            addHandler(new LibInfoGenerator("$(TARGET_INFO)"));
            addHandler(new HFileCopier("$(TARGET_INCLUDE)"));
            addHandler(new EtcDirCopier("$(TARGET_ETC)"));
            Target t = makefile.addPhonyTarget("sysinstall", "libs", "tools");
            t.addCommand("echo success > $(TARGET_DIR)/success", true);
        }

        // Calculate dependencies option?
        if (getOptions().calculateDependencies) {
            addHandler((dependencyHandler = new DependencyHandler()));
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
        String target = System.getenv("MCATARGET");
        if (target != null) {
            File targetFile = Util.getFileInEtcDir("../targets/" + target);
            if (targetFile.exists()) {
                System.out.println("Using custom options from target config file: " + targetFile.getCanonicalPath());
                makefile.applyVariablesFromFile(targetFile);
            }
        }

        if (getOptions().containsKey("usesysteminstall")) {
            new MCA2RepositoryTargetCreator().postprocess(makefile);
        } else {
            new MCA2RepositoryTargetCreator().postprocess(makefile, "tools");
        }

        super.writeMakefile();
    }

    @Override
    public void setDefaultIncludePaths(SrcDir dir, SourceScanner sources) {
        dir.defaultIncludePaths.add(sources.findDir(".", true));
        dir.defaultIncludePaths.add(sources.findDir("projects", true));
        dir.defaultIncludePaths.add(sources.findDir("libraries", true));
        dir.defaultIncludePaths.add(sources.findDir("tools", true));

        // add system include paths - in case MCA is installed system-wide
        if (systemInstall != null && systemInstall.systemInstallExists) {
            String p = systemInstall.MCA_SYSTEM_INCLUDE.getAbsolutePath();
            dir.defaultIncludePaths.add(sources.findDir(p, true));
            dir.defaultIncludePaths.add(sources.findDir(p + "/projects", true));
            dir.defaultIncludePaths.add(sources.findDir(p + "/libraries", true));
            dir.defaultIncludePaths.add(sources.findDir(p + "/tools", true));
        }

        if (dir.relative.startsWith(tempBuildPath.relative)) {
            return;
        }
        dir.defaultIncludePaths.add(sources.findDir(tempBuildPath.relative + FS + "projects", true));
        dir.defaultIncludePaths.add(sources.findDir(tempBuildPath.relative + FS + "libraries", true));

        // add all parent directories (in source and build paths)... not nice but somehow required :-/
        SrcDir parent = dir;
        SrcDir parent2 = sources.findDir(tempBuildPath.relative + FS + dir.relative, true);
        while (parent.relative.contains(FS) && (parent.relative.charAt(0) != '/')) {
            dir.defaultIncludePaths.add(parent);
            dir.defaultIncludePaths.add(parent2);
            parent = parent.getParent();
            parent2 = parent2.getParent();
        }
    }

    @Override
    public String[] getSourceDirs() {
        return new String[] {"libraries", "projects", "tools"};
    }

    public void run() {
        super.run();

        // create additional defines
        for (BuildEntity be : buildEntities) {
            if (!be.missingDep && be instanceof MCALibrary) {
                // _LIB_MCA2_COMPUTER_VISION_BASE_PRESENT_
                globalDefine.add("#define _LIB_MCA2_" + be.name.toUpperCase() + "_PRESENT_");
            }
        }

        // write defines to makefile.h
        globalDefine.add("");
        try {
            globalDefine.writeTo(new File("Makefile.h"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public SrcFile getTempBuildArtifact(SrcFile source, String targetExtension) {
        SrcDir targetDir = tempBuildPath.getSubDir(source.dir.relative);
        if (source.getExtension().equals("ui")) {
            return sources.registerBuildProduct(targetDir.relative + FS + "ui_" + source.getRawName() + ".h");
        } else if (targetExtension.equals("hpp")) { // description builder template
            return sources.registerBuildProduct(targetDir.relative + FS + "descr_h_" + source.getRawName() + ".hpp");
        }
        return sources.registerBuildProduct(targetDir.relative + FS + source.getRawName() + "." + targetExtension);
    }

    public SrcFile getTempBuildArtifact(BuildEntity source, String targetExtension, String suggestedPrefix) {
        return sources.registerBuildProduct(tempPath + FS + source.name + "_" + suggestedPrefix + "." + targetExtension);
    }

}
