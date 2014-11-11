/**
 * You received this file as part of an experimental
 * build tool ('makebuilder') - originally developed for MCA2.
 *
 * Copyright (C) 2011 Max Reichardt,
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
package makebuilder.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import makebuilder.BuildEntity;
import makebuilder.SourceFileHandler;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcDir;
import makebuilder.SrcFile;
import makebuilder.libdb.LibDB;
import makebuilder.util.CCOptions;
import makebuilder.util.Files;
import makebuilder.util.ToStringComparator;

/**
 * @author Max Reichardt
 *
 * Creates strings for all enum constants
 */
public class EnumStringsBuilderHandler extends SourceFileHandler.Impl {

    /** Single target for .cpp descr files */
    class CppDescrTarget {

        /** Makefile target for descr file */
        final Makefile.Target target;

        /** List of .h files that descr file is generated from */
        final ArrayList<SrcFile> originalSourceFiles = new ArrayList<SrcFile>();

        /** Descr file */
        final SrcFile descrFile;

        public CppDescrTarget(Makefile.Target target, SrcFile descrFile) {
            this.target = target;
            this.descrFile = descrFile;
        }
    }

    /** enum strings builder script */
    public static final String DESCRIPTION_BUILDER_BIN = "make_builder/scripts/enum_strings_builder";

    /** enum strings builder llvm plugin */
    public final String LLVM_CLANG_PLUGIN;
    public static final String LLVM_CLANG_PLUGIN_SOURCE = "make_builder/enum_strings_builder/clang-plugin-enum_strings.cpp";

    /** Use (experimental) llvm-clang plugin for building enum strings (instead of script utilizing doxygen) */
    public static final boolean USE_LLVM_PLUGIN;

    /** Extra flags for compiling with clang */
    public static final String EXTRA_CLANG_FLAGS;

    /** Contains a makefile target for each build entity with files to call strings builder upon */
    private Map<BuildEntity, CppDescrTarget> descrTargets = new HashMap<BuildEntity, CppDescrTarget>();

    /** Dependency buffer */
    private final TreeSet<SrcFile> dependencyBuffer = new TreeSet<SrcFile>(ToStringComparator.instance);

    /** Build directory - set if lib_enum_strings.so needs to be built */
    private final String buildDir;

    /** Target for enum string library */
    private Makefile.Target enumStringsLib;

    /** Compiler flags for clang (used instead of ($CXX_OPTS)) */
    private final String clangFlags;

    // Detect which method to use for building enum strings
    static {
        boolean suitableDoxygenVersion = false;
        boolean suitableLlvmVersion = false;

        // get doxygen version
        try {
            String[] versionString = Files.readLines(Runtime.getRuntime().exec("doxygen --version").getInputStream()).get(0).split("[.]");
            suitableDoxygenVersion = versionString[0].equals("1") && Integer.parseInt(versionString[1]) <= 7;
            if (suitableDoxygenVersion) {
                System.out.println("Suitable doxygen version found");
            }
        } catch (Exception e) {
        }
        // get llvm version
        try {
            String[] output = Files.readLines(Runtime.getRuntime().exec("clang++ --version").getInputStream()).get(0).split("[ ]");
            String[] versionString = null;
            for (String o : output) {
                if (o.contains(".")) {
                    versionString = o.split("[.-]");
                    break;
                }
            }
            suitableLlvmVersion = Integer.parseInt(versionString[0]) > 3 || (Integer.parseInt(versionString[0]) == 3 && Integer.parseInt(versionString[1]) >= 3);
            if (suitableLlvmVersion) {
                System.out.println("Suitable llvm clang++ version found");
            }
        } catch (Exception e) {
        }

        if (!suitableDoxygenVersion && (!suitableLlvmVersion)) {
            System.err.println("ERROR: Either doxygen version <= 1.7.6.1 or llvm clang++ >= 3.3 required for compiling");
            System.exit(-1);
        }
        USE_LLVM_PLUGIN = suitableLlvmVersion;

        // Workaround for bug https://bugs.launchpad.net/ubuntu/+source/llvm-toolchain-snapshot/+bug/1215572
        EXTRA_CLANG_FLAGS = System.getProperty("os.arch").equals("i386") ? " -I/usr/include/i386-linux-gnu/c++/4.8" : "";
    }

    /**
     * @param buildDir Build directory - set if lib_enum_strings.so needs to be built
     * @param clangFlags Compiler flags for clang (used instead of ($CXX_OPTS))
     * @param nativeArchitectureString Identification of compiling platform's architecture (e.g. "i686"). Is appended to clang-Plugin name.
     */
    public EnumStringsBuilderHandler(String buildDir, String clangFlags, String nativeArchitectureString) {
        this.buildDir = buildDir;
        this.clangFlags = clangFlags;
        LLVM_CLANG_PLUGIN = "make_builder/dist/clang-plugin-enum_strings-" + nativeArchitectureString + ".so";
    }

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
        if (file.hasExtension("h")) {

            // find enum keyword
            if (!file.isInfoUpToDate()) {
                List<String> lines = file.getCppLines();
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.startsWith("enum") && (line.length() <= 4 || (Character.isWhitespace(line.charAt(4))))) {

                        // Okay, we have an enum. Check whether it's an anonymous one.
                        line = line.substring(4).trim();
                        while (!line.contains("{")) {
                            i++;
                            line += " " + lines.get(i).trim();
                        }
                        String[] words = line.substring(0, line.indexOf('{')).split("\\s");
                        if (words.length == 0 || words[0].trim().length() == 0) {
                            continue;
                        }

                        file.mark("enum");
                        break;
                    }
                }
            }

            BuildEntity be = file.getOwner();
            if (be == null || (!file.hasMark("enum"))) { // no enum or we don't know where generated code belongs
                //System.out.println("warning: found DESCR macros in " + file.relative + " but don't know which build entity it belongs to => won't process it");
                return;
            }

            // get or create target
            CppDescrTarget target = descrTargets.get(be);
            if (target == null) {
                SrcFile sft = builder.getTempBuildArtifact(be, "cpp", "enum_strings");
                target = new CppDescrTarget(makefile.addTarget(sft.relative, true, file.dir), sft);
                target.target.addDependency(be.buildFile);
                target.target.addMessage("Creating " + sft.relative);
                target.target.addDependency(USE_LLVM_PLUGIN ? LLVM_CLANG_PLUGIN : DESCRIPTION_BUILDER_BIN);
                //target.target.addCommand("echo \\/\\/ generated > " + target.target.getName(), false);
                be.sources.add(0, sft);
                be.opts.libs.add("enum_strings");
                descrTargets.put(be, target);
            }
            target.target.addDependency(file);
            target.originalSourceFiles.add(file);
            //target.target.addCommand(DESCRIPTION_BUILDER_BIN + file.relative + " >> " + target.target.getName(), false);

        }
    }

    /**
     * @return Enum string lib target
     */
    private Makefile.Target getEnumStringsLib(Makefile makefile, MakeFileBuilder builder) {
        if (enumStringsLib == null) {
            enumStringsLib = makefile.addTarget(buildDir + "/libenum_strings.$(LIB_EXTENSION)", false, null);
            if (builder.isStaticLinkingEnabled()) {
                String ofile = buildDir + "/libenum_strings.o";
                enumStringsLib.addCommand("$(CXX) $(CXX_OPTIONS_LIB) -c -o " + ofile + " -include make_builder/enum_strings_builder/enum_strings.h make_builder/enum_strings_builder/enum_strings.cpp", true);
                enumStringsLib.addCommand("ar rs " + enumStringsLib.getName() + " " + ofile, true);
                enumStringsLib.addCommand("rm " + ofile, false);
            } else {
                enumStringsLib.addCommand("$(CXX) $(CXX_OPTIONS_LIB) -shared -o " + buildDir + "/libenum_strings.so -include make_builder/enum_strings_builder/enum_strings.h make_builder/enum_strings_builder/enum_strings.cpp", true);
            }
            enumStringsLib.addDependency("make_builder/enum_strings_builder/enum_strings.h");
            enumStringsLib.addDependency("make_builder/enum_strings_builder/enum_strings.cpp");

            if (USE_LLVM_PLUGIN) {
                if (!LibDB.getInstance("native").available("clang")) {
                    System.err.print("LLVM clang headers not available. Please install and run 'updatelibdb'.");
                    System.exit(-1);
                }
                Makefile.Target target = makefile.addTarget(LLVM_CLANG_PLUGIN, false, null);
                try {
                    target.addCommand("c++ -std=c++11 " + LibDB.getInstance("native").getLib("clang").options + " -shared -fPIC -o " + LLVM_CLANG_PLUGIN + " " + LLVM_CLANG_PLUGIN_SOURCE, true);
                    target.addDependency(LLVM_CLANG_PLUGIN_SOURCE);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return enumStringsLib;
    }

    @Override
    public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {
        CppDescrTarget target = descrTargets.get(be);
        getEnumStringsLib(makefile, builder); // make sure we always build enum string lib - also when using system installation
        if (target != null) {

            // Add dependency to libenum_strings.so
            if (buildDir != null) {
                be.target.addDependency(getEnumStringsLib(makefile, builder).getName());
            }

            // Add all dependencies of original files to generated .descr cpp file
            dependencyBuffer.clear();
            for (SrcFile sf : target.originalSourceFiles) {
                sf.getAllDependencies(dependencyBuffer);
            }
            target.descrFile.dependencies.addAll(dependencyBuffer);

            // Input files string
            String inputFiles = "";
            for (SrcFile sf : target.originalSourceFiles) {
                inputFiles += sf.relative + " ";
            }
            inputFiles = '"' + inputFiles.trim() + '"';

            // Create commands
            if (!USE_LLVM_PLUGIN) {
                String outputDir = builder.getTempBuildDir(be) + "/" + be.getTargetFilename() + "_enum_strings";
                target.target.addCommand("mkdir -p " + outputDir, false);
                target.target.addCommand("INPUT_FILES=" + inputFiles + " OUTPUT_DIR=" + outputDir + " doxygen make_builder/enum_strings_builder/doxygen.conf", false);
                target.target.addCommand("perl -I" + outputDir + "/perlmod " + DESCRIPTION_BUILDER_BIN + " " + target.target.getName(), false);
            } else {

                // create compiler options
                CCOptions options = new CCOptions();
                options.merge(be.opts, true);

                // find/prepare include paths
                for (SrcDir path : be.getRootDir().defaultIncludePaths) {
                    options.includePaths.add(path.relative);
                }

                // create input files string for c compiler (all header files except the last are added via '-include')
                String clangInputFiles = "";
                String includeGuards = "";
                String includeDir = "";
                for (SrcFile sf : target.originalSourceFiles) {
                    clangInputFiles += "-include " + sf.relative + " ";
                    Object includeGuard = sf.properties.get(CppHandler.CPP_INCLUDE_GUARD_KEY);
                    if (includeGuard != null) {
                        includeGuards += " " + includeGuard.toString();
                    }
                    if (includeDir.length() == 0) {
                        for (SrcDir path : be.getRootDir().defaultIncludePaths) {
                            if (sf.relative.startsWith(path.relative + '/')) {
                                includeDir = path.relative;
                                break;
                            }
                        }
                    }
                }
                clangInputFiles = clangInputFiles.trim();
                if (includeGuards.length() > 0) {
                    includeGuards = " -D" + includeGuards;
                }

                // create clang++ command that will create generated file
                target.target.addCommand("clang++ -c " + options.createOptionString(true, false, true) + " " + clangFlags + EXTRA_CLANG_FLAGS + includeGuards +
                                         " -Xclang -load -Xclang " + LLVM_CLANG_PLUGIN + " -Xclang -plugin -Xclang enum-strings " +
                                         " -Xclang -plugin-arg-enum-strings -Xclang --output=" + target.target.getName() +
                                         " -Xclang -plugin-arg-enum-strings -Xclang --inputs=" + inputFiles + " " +
                                         (includeDir.length() > 0 ? ("-Xclang -plugin-arg-enum-strings -Xclang --include_dir=" + includeDir + " ") : "") +
                                         clangInputFiles + " make_builder/enum_strings_builder/empty.cpp", true);
            }
        }
    }
}
