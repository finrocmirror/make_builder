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
package makebuilder.handler;

import java.util.ArrayList;
import java.util.TreeSet;

import makebuilder.BuildEntity;
import makebuilder.SourceFileHandler;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcDir;
import makebuilder.SrcFile;
import makebuilder.handler.CppHandler.CodeTreeNode;
import makebuilder.util.CCOptions;
import makebuilder.util.ToStringComparator;

/**
 * @author max
 *
 * Responsible for building executables and libraries from C/C++ source files
 */
public class NvccHandler extends SourceFileHandler.Impl {

    /** Options for compiling */
    public final String compileOptions;

    /** Dependency buffer */
    private final TreeSet<SrcFile> dependencyBuffer = new TreeSet<SrcFile>(ToStringComparator.instance);

    /**
     * @param compileOptions Standard compile options (included in every compile)
     */
    public NvccHandler(String compileOptions) {
        this.compileOptions = compileOptions;
    }

    @Override
    public void init(Makefile makefile) {
        makefile.addVariable("NVCC_FLAGS=");
        makefile.addVariable("NVCC_OPTIONS=$(NVCC_FLAGS) " + compileOptions);
        makefile.addVariable("NVCC=nvcc");
    }

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
        if (file.hasExtension("cu")) {
            if (!file.isInfoUpToDate()) {
                CppHandler.processIncludes(file, scanner);
            }

            CppHandler.resolveDependencies(file, (CodeTreeNode)file.properties.get(CppHandler.CPP_MODEL_KEY), true, false, false);
        }
    }

    @Override
    public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) {

        // create nvcc compiler options
        CCOptions options = new CCOptions();
        options.merge(be.opts, true);
        options.cxxCompileOptions.add("$(NVCC_OPTIONS)");
        options.cCompileOptions.add("$(NVCC_OPTIONS)");
        for (SrcDir path : be.getRootDir().defaultIncludePaths) {
            options.includePaths.add(path.relative);
        }

        // compile...
        ArrayList<SrcFile> copy = new ArrayList<SrcFile>(be.sources);
        for (SrcFile sf : copy) {
            if (sf.hasExtension("cu")) {
                SrcFile ofile = builder.getTempBuildArtifact(sf, "o");
                Makefile.Target target = makefile.addTarget(ofile.relative, false, be.getRootDir());
                be.sources.remove(sf);
                be.sources.add(ofile);
                dependencyBuffer.clear();
                target.addDependencies(sf.getAllDependencies(dependencyBuffer));
                target.addCommand("$(NVCC) -c -o " + ofile.relative + " " + sf.relative + " " + options.createCudaString(), true);
            }
        }
    }
}
