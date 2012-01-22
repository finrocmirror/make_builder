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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
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
    private final String cCompileOptions, cxxCompileOptions, linkOptions;

    /** Do compiling and linking separately (or rather in one gcc call)? */
    private final boolean separateCompileAndLink;

    /** Key for code model in source file properties */
    public static final String CPP_MODEL_KEY = "cppModel";

    /** Dependency buffer */
    private final TreeSet<SrcFile> dependencyBuffer = new TreeSet<SrcFile>(ToStringComparator.instance);

    /** Debug cpp handler? */
    private final boolean debug = MakeFileBuilder.getOptions().containsKey("debug_cpp_handler");

    /**
     * @param compileOptions Standard compile options (included in every compile; C and C++)
     * @param linkOptions Standard linker options (in every link)
     * @param separateCompileAndLink Do compiling and linking separately (or rather in one gcc call)?
     */
    public CppHandler(String compileOptions, String linkOptions, boolean separateCompileAndLink) {
        this.cCompileOptions = compileOptions;
        this.cxxCompileOptions = compileOptions;
        this.linkOptions = linkOptions;
        this.separateCompileAndLink = separateCompileAndLink;
    }

    /**
     * @param cCompileOptions Standard compile options (included in every compile of C file)
     * @param cxxCompileOptions Standard compile options (included in every compile of C++ file)
     * @param linkOptions Standard linker options (in every link)
     * @param separateCompileAndLink Do compiling and linking separately (or rather in one gcc call)?
     */
    public CppHandler(String cCompileOptions, String cxxCompileOptions, String linkOptions, boolean separateCompileAndLink) {
        this.cCompileOptions = cCompileOptions;
        this.cxxCompileOptions = cxxCompileOptions;
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
        makefile.addVariable("CC_OPTS=$(CCFLAGS) " + cCompileOptions);
        makefile.addVariable("CXX=g++$(GCC_VERSION)");
        makefile.addVariable("CXXFLAGS=$(CFLAGS)");
        makefile.addVariable("CXX_OPTS=$(CXXFLAGS) " + cxxCompileOptions);
        makefile.addVariable("LINK_OPTS=$(LDFLAGS) " + linkOptions);
    }

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner sources, MakeFileBuilder builder) {
        if (file.hasExtension("c", "cpp", "h", "hpp")) {
            if (!file.isInfoUpToDate()) {
                processIncludes(file, sources);
            }

            if (debug) {
                System.out.println("\nParsing of " + file.relative + ":");
                ((CodeTreeNode)file.properties.get(CPP_MODEL_KEY)).dumpTree("");
            }
            resolveDependencies(file, (CodeTreeNode)file.properties.get(CPP_MODEL_KEY), true, false, false);
            if (debug) {
                System.out.println("\nResolved dependencies:");
                for (SrcFile sf : file.dependencies) {
                    System.out.println(" " + sf.relative);
                }
                System.out.println("\nResolved optional dependencies:");
                for (SrcFile sf : file.dependencies) {
                    System.out.println(" " + sf.relative);
                }
            }
        }
    }

    /**
     * Find #include statements in C++ files and add dependencies to source files
     *
     * @param file File to scan
     * @param sources SourceScanner instance that contains possible includes
     */
    public static void processIncludes(SrcFile file, SourceScanner sources) {

        CodeTreeNode root = new CodeTreeNode(null, null, false);
        CodeTreeNode curNode = root;

        // parse code an build code tree model
        for (String line : file.getCppLines()) {
            String orgLine = line;
            if (line.trim().startsWith("#")) {
                line = line.trim().substring(1).trim();
                try {
                    if (line.startsWith("include")) {
                        line = line.substring(7).trim();
                        if (line.startsWith("\"")) {
                            line = line.substring(1, line.lastIndexOf("\""));
                            if (curNode.elseBranch) {
                                curNode.altIncludes.add(line);
                            } else {
                                curNode.includes.add(line);
                            }
                            //file.rawDependencies.add(line);
                        } else if (line.startsWith("<")) {
                            //line = line.substring(1, line.indexOf(">"));
                        } else {
                            throw new RuntimeException("Error getting include string");
                        }
                    } else if (line.startsWith("if")) {
                        if (line.startsWith("ifdef")) {
                            curNode = new CodeTreeNode(line.substring(5).trim(), curNode, curNode.elseBranch);
                        } else {
                            curNode = new CodeTreeNode(null, curNode, curNode.elseBranch);
                        }
                    } else if (line.startsWith("el")) {
                        curNode.elseBranch = true;
                    } else if (line.startsWith("endif")) {
                        if (curNode.parent == null) {
                            System.err.println("Warning parsing " + file.relative + ": There seem to be more #endif than #if");
                        } else {
                            curNode = curNode.parent;
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error while parsing C++ file (" + file.relative + "). Line was: " + orgLine);
                }
            }
        }

        // optimize tree (delete empty leaves and branches)
        root.optimize();

        // set source file's tree model
        file.properties.put(CPP_MODEL_KEY, root);
    }

    /**
     * (Recursive helper method)
     * Process code model node and resolve dependencies
     *
     * @param file Source file
     * @param node node to process
     * @param mandatory Is this node's includes mandatory?
     * @param ignoreMissing Ignore missing includes?
     * @param elseBranch Check out else branch of node?
     */
    public static void resolveDependencies(SrcFile file, CodeTreeNode node, boolean mandatory, boolean ignoreMissing, boolean elseBranch) {
        assert(node != null);
        SrcDir dir = file.dir;
        List<SrcFile> result = new ArrayList<SrcFile>();
        for (String raw : (elseBranch ? node.altIncludes : node.includes)) {
            boolean found = ignoreMissing;
            for (SrcDir sd : dir.defaultIncludePaths) {
                SrcFile sf = dir.sources.find(sd, raw);
                if (sf != null) {
                    result.add(sf);
                    found = true;
                    break;
                }
            }
            if (!found) {
                if (mandatory) {
                    file.missingDependency = raw;
                } else if ((!elseBranch) && (!node.altIncludes.isEmpty())) {
                    resolveDependencies(file, node, true, ignoreMissing, true);
                }
                return;
                //throw new RuntimeException("Dependency " + raw + " not found");
            }
        }
        (mandatory ? file.dependencies : file.optionalDependencies).addAll(result);

        for (CodeTreeNode child : (elseBranch ? node.altChildren : node.children)) {
            resolveDependencies(file, child, mandatory && (child.makroName == null), ignoreMissing, false);
            if (file.missingDependency != null) {
                break;
            }
        }
    }


    @Override
    public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) {

        // build it?
        if (!(be.getFinalHandler() == CppHandler.class)) {
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
        /*for (SrcFile sf : be.sources) { // add directories of all source files - not especially nice - but required for some MCA2 libraries
            if (sf.hasExtension("c", "cpp") && (!sf.dir.isTempDir())) {
                options.includePaths.add(sf.dir.relative);
            }
        }*/

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
        if (be.isUnitTest()) {
            be.target.addCommand("@echo ; echo \"===== Running unit test " + be.getTargetFilename() + " =====\"" , true);
            be.target.addCommand("@" + be.getTarget(), true);
            be.target.addCommand("@echo \"=====\" ; echo", true);
        }
    }

    /**
     * Node of a preprocessor model/tree of a source file
     *
     * Every #if-nesting creates a new node
     */
    public static class CodeTreeNode implements Serializable {

        /** UID */
        private static final long serialVersionUID = -1187066325757049661L;

        /** Name of makro - if block starts with #ifdef - otherwise null */
        private String makroName;

        /** Parent node */
        private CodeTreeNode parent;

        /** Child nodes - in standard case */
        private ArrayList<CodeTreeNode> children = new ArrayList<CodeTreeNode>();

        /** Child nodes - in "else* case */
        private ArrayList<CodeTreeNode> altChildren = new ArrayList<CodeTreeNode>();

        /** Include files - in standard case */
        private ArrayList<String> includes = new ArrayList<String>();

        /** Include files - in "else" case */
        private ArrayList<String> altIncludes = new ArrayList<String>();

        /** temporary variable - are we in "else" branch now? */
        private transient boolean elseBranch;

        private CodeTreeNode(String makroName, CodeTreeNode parent, boolean inElseBranch) {
            this.makroName = makroName;
            this.parent = parent;
            if (parent != null) {
                if (inElseBranch) {
                    parent.altChildren.add(this);
                } else {
                    parent.children.add(this);
                }
            }
        }

        /**
         * remove empty leaves and branches
         *
         * @return Is this node empty now?
         */
        public boolean optimize() {
            boolean allEmpty = true;
            for (CodeTreeNode child : new ArrayList<CodeTreeNode>(children)) {
                allEmpty &= child.optimize();
            }
            for (CodeTreeNode child : new ArrayList<CodeTreeNode>(altChildren)) {
                allEmpty &= child.optimize();
            }
            allEmpty &= (includes.isEmpty() & altIncludes.isEmpty());
            if (allEmpty && parent != null) {
                parent.children.remove(this);
            }
            return allEmpty;
        }

        /**
         * Debug method: dump tree
         *
         * @param indent String containing spaces
         */
        public void dumpTree(String indent) {
            String indent2 = indent + " ";
            if (parent != null) {
                System.out.println(indent + "#if" + (makroName == null ? " <something>" : ("def " + makroName)));
            }
            for (String include : includes) {
                System.out.println(indent2 + "#include \"" + include + "\"");
            }
            for (CodeTreeNode child : children) {
                child.dumpTree(indent2);
            }
            if ((!altIncludes.isEmpty()) || (!altChildren.isEmpty())) {
                System.out.println(indent + "#else");
            }
            for (String include : altIncludes) {
                System.out.println(indent2 + "#include \"" + include + "\"");
            }
            for (CodeTreeNode child : altChildren) {
                child.dumpTree(indent2);
            }
            if (parent != null) {
                System.out.println(indent + "#endif");
            }
        }
    }
}
