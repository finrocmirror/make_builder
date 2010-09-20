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
import makebuilder.util.CCOptions;
import makebuilder.util.ToStringComparator;

/**
 * @author max
 *
 * Responsible for building executables and libraries from C/C++ source files
 */
public class CppHandler implements SourceFileHandler {

    /** Standard compile and linke options (included in every compile/link) */
    private final String compileOptions, linkOptions;

    /** Do compiling and linking separately (or rather in one gcc call)? */
    private final boolean separateCompileAndLink;

    /** Dependency buffer */
    private final TreeSet<SrcFile> dependencyBuffer = new TreeSet<SrcFile>(ToStringComparator.instance);

    /**
     * @param compileOptions Standard compile options (included in every compile)
     * @param linkOptions Standard linker options (in every link)
     * @param separateCompileAndLink Do compiling and linking separately (or rather in one gcc call)?
     */
    public CppHandler(String compileOptions, String linkOptions, boolean separateCompileAndLink) {
        this.compileOptions = compileOptions;
        this.linkOptions = linkOptions;
        this.separateCompileAndLink = separateCompileAndLink;
    }

    @Override
    public void init(Makefile makefile) {
        // add variables to makefile
        makefile.addVariable("CFLAGS=-g2");
        makefile.addVariable("GCC_VERSION=");
        makefile.addVariable("CC=gcc$(GCC_VERSION)");
        makefile.addVariable("CCFLAGS=$(CFLAGS)");
        makefile.addVariable("CC_OPTS=$(CCFLAGS) " + compileOptions);
        makefile.addVariable("CXX=g++$(GCC_VERSION)");
        makefile.addVariable("CXXFLAGS=$(CFLAGS)");
        makefile.addVariable("CXX_OPTS=$(CXXFLAGS) " + compileOptions);
        makefile.addVariable("LINK_OPTS=$(LDFLAGS) " + linkOptions);
    }

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner sources, MakeFileBuilder builder) {
        if (file.hasExtension("c", "cpp", "h", "hpp")) {
            if (!file.isInfoUpToDate()) {
                processIncludes(file, sources);
            }
            file.resolveDependencies(false);
        }
    }

    /**
     * Find #include statements in C++ files and add dependencies to source files
     *
     * @param file File to scan
     * @param sources SourceScanner instance that contains possible includes
     */
    public static void processIncludes(SrcFile file, SourceScanner sources) {
        for (String line : file.getCppLines()) {
            String orgLine = line;
            if (line.trim().startsWith("#")) {
                line = line.substring(1).trim();
                try {
                    if (line.startsWith("include")) {
                        line = line.substring(7).trim();
                        if (line.startsWith("\"")) {
                            line = line.substring(1, line.lastIndexOf("\""));
                            file.rawDependencies.add(line);
                        } else if (line.startsWith("<")) {
                            //line = line.substring(1, line.indexOf(">"));
                        } else {
                            throw new RuntimeException("Error getting include string");
                        }

                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error while parsing include file. Line was: " + orgLine);
                }
            }
        }
    }

    @Override
    public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) {

        // build it?
        if (!(be.getFinalHandler().equals(CppHandler.class))) {
            return;
        }

        // create compiler options
        CCOptions options = new CCOptions();
        options.merge(be.opts, true);
        options.linkOptions.add("$(LINK_OPTS)");
        options.cCompileOptions.add("$(CC_OPTS)");
        options.cxxCompileOptions.add("$(CXX_OPTS)");

        // find/prepare include paths
        for (SrcDir path : be.getRootDir().defaultIncludePaths) {
            options.includePaths.add(path.relative);
        }
        for (SrcFile sf : be.sources) { // add directories of all source files - not especially nice - but required for some MCA2 libraries
            if (sf.hasExtension("c", "cpp") && (!sf.dir.isTempDir())) {
                options.includePaths.add(sf.dir.relative);
            }
        }

        if (separateCompileAndLink) {
            ArrayList<SrcFile> copy = new ArrayList<SrcFile>(be.sources);
            boolean atLeastOneCxx = false;

            // compile...
            for (SrcFile sf : copy) {
                if (sf.hasExtension("c", "cpp")) {
                    SrcFile ofile = builder.getTempBuildArtifact(sf, "o");
                    Makefile.Target target = makefile.addTarget(ofile.relative, true, be.getRootDir());
                    be.sources.remove(sf);
                    be.sources.add(ofile);
                    dependencyBuffer.clear();
                    target.addDependencies(sf.getAllDependencies(dependencyBuffer));
                    boolean cxx = sf.hasExtension("cpp");
                    atLeastOneCxx |= cxx;
                    target.addCommand(options.createCompileCommand(sf.relative, ofile.relative, cxx), true);
                }
            }

            // ... and link
            copy.clear();
            copy.addAll(be.sources);
            String sources = "";
            for (SrcFile sf : copy) {
                if (sf.hasExtension("o", "os")) {
                    be.sources.remove(sf);
                    be.target.addDependency(sf);
                    sources += " " + sf.relative;
                }
            }
            be.target.addCommand(options.createLinkCommand(sources, be.getTarget(), atLeastOneCxx), true);

        } else { // compiling and linking in one step
            ArrayList<SrcFile> copy = new ArrayList<SrcFile>(be.sources);
            dependencyBuffer.clear();

            String sources = "";
            String cxxSources = "";
            SrcFile cfile = null;
            for (SrcFile sf : copy) {
                if (sf.hasExtension("c", "cpp", "o", "os")) {
                    be.sources.remove(sf);
                    be.target.addDependency(sf);
                    if (sf.hasExtension("c")) {
                        sources += " " + sf.relative;
                        cfile = sf;
                    } else {
                        cxxSources += " " + sf.relative;
                    }
                    sf.getAllDependencies(dependencyBuffer);
                }
            }

            if (sources.length() == 0 || cxxSources.length() == 0) {
                boolean cxx = cxxSources.length() > 0;
                be.target.addCommand(options.createCompileAndLinkCommand(cxx ? cxxSources : sources, be.getTarget(), cxx), true);
            } else {
                // we need to compile c files and then link them into compiled c++ files

                // Create .o for c file
                SrcFile ofile = builder.getTempBuildArtifact(cfile, "o");
                Makefile.Target target = makefile.addTarget(ofile.relative, true, be.getRootDir());
                target.addDependencies(dependencyBuffer);
                target.addCommand(options.createCompileCommand(sources, ofile.relative, false), true);

                // Compile and link c++ files
                be.target.addDependency(ofile.relative);
                be.target.addCommand(options.createCompileAndLinkCommand(cxxSources + " " + ofile.relative, be.getTarget(), true), true);
            }
            be.target.addDependencies(dependencyBuffer);
        }
    }
}
