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
package makebuilder.ext.finroc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import makebuilder.BuildEntity;
import makebuilder.SourceFileHandler;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcDir;
import makebuilder.SrcFile;
import makebuilder.handler.EnumStringsBuilderHandler;
import makebuilder.libdb.LibDB;
import makebuilder.util.CCOptions;
import makebuilder.util.ToStringComparator;

/**
 * @author max
 *
 * Responsible for calling finroc_port_description_builder on relevant files
 */
public class PortDescriptionBuilderHandler extends SourceFileHandler.Impl {

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

    /** Description builder script */
    public static final String DESCRIPTION_BUILDER_BIN = "scripts/tools/port_name_builder";

    /** enum strings builder llvm plugin */
    public final String LLVM_CLANG_PLUGIN;
    public static final String LLVM_CLANG_PLUGIN_SOURCE = "make_builder/src/makebuilder/ext/finroc/clang-plugin-port_names.cpp";

    /** Use (experimental) llvm-clang plugin for building port names (instead of script utilizing doxygen) */
    public static final boolean USE_LLVM_PLUGIN = makebuilder.handler.EnumStringsBuilderHandler.USE_LLVM_PLUGIN;

    /** Contains a makefile target for each build entity with files to call description build upon */
    private Map<BuildEntity, CppDescrTarget> descrTargets = new HashMap<BuildEntity, CppDescrTarget>();

    /** Dependency buffer */
    private final TreeSet<SrcFile> dependencyBuffer = new TreeSet<SrcFile>(ToStringComparator.instance);

    /** Target for clang plugin */
    private Makefile.Target clangPlugin;

    /** Compiler flags for clang (used instead of ($CXX_OPTS)) */
    private final String clangFlags;

    /**
     * @param clangFlags Compiler flags for clang (used instead of ($CXX_OPTS))
     * @param nativeArchitectureString Identification of compiling platform's architecture (e.g. "i686"). Is appended to clang-Plugin name.
     */
    public PortDescriptionBuilderHandler(String clangFlags, String nativeArchitectureString) {
        this.clangFlags = clangFlags;
        LLVM_CLANG_PLUGIN = "make_builder/dist/clang-plugin-port_names-" + nativeArchitectureString + ".so";
    }

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
        if (file.hasExtension("h") && (file.getName().startsWith("m") || file.getName().startsWith("g")) && (!file.getName().toLowerCase().equals(file.getName()))) { // at least one upper case character

            BuildEntity be = file.getOwner();
            if (be == null || be.buildFile.relative.startsWith("sources/cpp/rrlib/") || be.buildFile.relative.startsWith("sources/cpp/mca2-legacy/")) { // we don't know where generated code belongs
                return;
            }

            // get or create target
            CppDescrTarget target = descrTargets.get(be);
            if (target == null) {
                SrcFile sft = builder.getTempBuildArtifact(be, "cpp", "descriptions"); // sft = "source file target"
                target = new CppDescrTarget(makefile.addTarget(sft.relative, true, file.dir), sft);
                target.target.addDependency(be.buildFile);
                target.target.addMessage("Creating " + sft.relative);
                target.target.addDependency(USE_LLVM_PLUGIN ? getClangPlugin(makefile).getName() : DESCRIPTION_BUILDER_BIN);
                //target.target.addCommand("echo \\/\\/ generated > " + target.target.getName(), false);
                be.sources.add(sft);
                descrTargets.put(be, target);
            }
            target.target.addDependency(file);
            target.originalSourceFiles.add(file);
            //target.target.addCommand(DESCRIPTION_BUILDER_BIN + file.relative + " >> " + target.target.getName(), false);

        }
    }

    /**
     * @return clang plugin target
     */
    private Makefile.Target getClangPlugin(Makefile makefile) {
        if (clangPlugin == null) {
            if (!LibDB.getInstance("native").available("clang")) {
                System.err.print("LLVM clang headers not available. Please install and run 'updatelibdb'.");
                System.exit(-1);
            }
            clangPlugin = makefile.addTarget(LLVM_CLANG_PLUGIN, false, null);
            try {
                clangPlugin.addCommand("c++ " + LibDB.getInstance("native").getLib("clang").options + " -shared -fPIC -o " + LLVM_CLANG_PLUGIN + " " + LLVM_CLANG_PLUGIN_SOURCE, true);
                clangPlugin.addDependency(LLVM_CLANG_PLUGIN_SOURCE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return clangPlugin;
    }

    @Override
    public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {
        CppDescrTarget target = descrTargets.get(be);
        if (target != null) {

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
                String outputDir = builder.getTempBuildDir(be) + "/" + be.getTargetFilename() + "_descr";
                target.target.addCommand("mkdir -p " + outputDir, false);
                target.target.addCommand("INPUT_FILES=" + inputFiles + " OUTPUT_DIR=" + outputDir + " doxygen etc/port_descriptions_doxygen.conf", false);
                target.target.addCommand("perl -I" + outputDir + "/perlmod " + DESCRIPTION_BUILDER_BIN + " > " + target.target.getName(), false);
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
                for (SrcFile sf : target.originalSourceFiles) {
                    clangInputFiles += "-include " + sf.relative + " ";
                }
                clangInputFiles = clangInputFiles.trim();

                // create clang++ command that will create generated file
                target.target.addCommand("clang++ -c " + options.createOptionString(true, false, true) + " " + clangFlags + EnumStringsBuilderHandler.EXTRA_CLANG_FLAGS +
                                         " -Xclang -load -Xclang " + LLVM_CLANG_PLUGIN + " -Xclang -plugin -Xclang finroc_port_names " +
                                         " -Xclang -plugin-arg-finroc_port_names -Xclang --output=" + target.target.getName() +
                                         " -Xclang -plugin-arg-finroc_port_names -Xclang --inputs=" + inputFiles + " " +
                                         clangInputFiles + " make_builder/enum_strings_builder/empty.cpp", true);
            }
        }
    }
}
