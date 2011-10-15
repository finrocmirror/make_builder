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
package makebuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

import makebuilder.handler.CppHandler;
import makebuilder.libdb.LibDB;
import makebuilder.util.AddOrderSet;
import makebuilder.util.CCOptions;
import makebuilder.util.Util;
import makebuilder.util.Util.Color;

/**
 * @author max
 *
 * "Build Entity".
 * This is typically a set of source files that will be compiled to a library or executable.
 * There may be dependencies among build entities
 */
public abstract class BuildEntity {

    /** File separator short cut */
    protected static final String FS = File.separator;
    
    /** Are we linking with --as-needed flag? */
    protected static final boolean LINKING_AS_NEEDED = true;

    /** name of entity to be built */
    public String name;

    /** Build file */
    public SrcFile buildFile;

    /** Involved source files */
    public final List<SrcFile> sources = new ArrayList<SrcFile>();

    /** Additional Compiler options to use for compiling & linking */
    public CCOptions opts = new CCOptions();

    /** Involved libraries */
    public final List<String> libs = new ArrayList<String>(); // libs/dependencies (as specified in SConscript/make.xml)
    public final List<String> optionalLibs = new ArrayList<String>(); // optional libs/dependencies (as specified in SConscript/make.xml)
    public final AddOrderSet<LibDB.ExtLib> extlibs = new AddOrderSet<LibDB.ExtLib>(); // resolved external libraries (from libs and available ones in optionalLibs; from build entity itself and its dependencies)
    public final AddOrderSet<LibDB.ExtLib> directExtlibs = new AddOrderSet<LibDB.ExtLib>(); // resolved external libraries (from libs and available ones in optionalLibs; only from build entity itself)
    public final List<BuildEntity> dependencies = new ArrayList<BuildEntity>(); // resolved local (mca2) dependencies (from libs)
    public final List<BuildEntity> optionalDependencies = new ArrayList<BuildEntity>(); // resolved optional local (mca2) dependencies (from optionalLibs)

    /** are any dependencies missing? */
    public boolean missingDep;

    /** Final target in makefile for this build entity - will create library or executable */
    public Makefile.Target target;

    /** All attributes/parameters/tags that were set in make.xml for this target */
    public SortedMap<String, String> params;

    /** Start scripts for this build entity */
    public List<StartScript> startScripts = new ArrayList<StartScript>();

    /** "Stack" for cycle check */
    private static ArrayList<BuildEntity> cycleCheckStack = new ArrayList<BuildEntity>(100);

    /** Search for dependencies automatically */
    public boolean autoDependencies;

    /** Error message id */
    public int errorMessageId = -1;

    /**
     * @param tb Reference to main builder instance
     */
    public BuildEntity() {}

    /**
     * @return Target file as relative path (usually executable or .so)
     */
    public abstract String getTarget();

    /**
     * @return Target file name (without path)
     */
    public String getTargetFilename() {
        String s = getTarget();
        return s.substring(s.lastIndexOf("/") + 1);
    }

    /**
     * @return Target path
     */
    public String getTargetPath() {
        String s = getTarget();
        return s.substring(0, s.lastIndexOf("/"));
    }

    public String toString() {
        return name;
    }

    public boolean isLibrary() {
        return getTarget().endsWith(".so") || getTarget().endsWith(".jar");
    }

    /**
     * Check dependency tree for cycles
     */
    public void checkForCycles() {
        if (cycleCheckStack.contains(this)) {
            System.out.println("Detected cyclic dependency: ");
            for (BuildEntity be : cycleCheckStack) {
                System.out.println("-> " + be.toString() +  " [" + be.getRootDir().toString() + "]");
            }
            System.out.println("-> " + toString() +  " [" + getRootDir().toString() + "]");
            System.exit(-1);
        }

        cycleCheckStack.add(this);
        for (BuildEntity be : dependencies) {
            be.checkForCycles();
        }
        cycleCheckStack.remove(this);
    }

    /**
     * Determine, whether build entity can be built.
     * missingDep is set accordingly
     */
    public void checkDependencies(MakeFileBuilder mfb) {
        if (missingDep) {
            return;
        }
        for (BuildEntity be : dependencies) {
            be.checkDependencies(mfb);
            if (be.missingDep) {
                missingDep = true;
                mfb.printCannotBuildError(this, Util.color(" due to dependency " + be.name + " (" + be.errorMessageId + ")", Util.Color.X, false) + " (" + be.buildFile.relative + ") " + Util.color("which cannot be built", Util.Color.X, false), Util.Color.X);
                return;
            }
        }
    }

    /**
     * Collect external libraries.
     * All required external libraries from this entity as well as all dependencies are stored in extlibs.
     * extlibs contains every external library at most once.
     *
     * Their options are added to opts
     */
    public void mergeExtLibs() {
        mergeExtLibs(extlibs);
    }

    /**
     * Compute the set of options to build this build entity with a C/C++ compiler
     */
    public void computeOptions() {
        if (getFinalHandler() != CppHandler.class) {
            return;
        }
        for (LibDB.ExtLib el : directExtlibs) {
            opts.merge(el.ccOptions, true);
        }
        for (LibDB.ExtLib el : extlibs) {
            opts.merge(el.ccOptions, LINKING_AS_NEEDED);
        }
        for (BuildEntity be : dependencies) {
            if (!be.isLibrary()) {
                throw new RuntimeException(toString() + " depends on non-library " + be.toString());
            }
            String s = be.getTarget();
            target.addDependency(s);
            s = s.substring(s.lastIndexOf("/lib") + 4, s.lastIndexOf(".so"));
            opts.libs.add(s);
            
            if (LINKING_AS_NEEDED) {
                addIndirectDependencyLibs(be);
            }
        }
    }
    
    public void addIndirectDependencyLibs(BuildEntity be) {
        for (BuildEntity be2 : be.dependencies) {
            String s = be.getTarget();
            s = s.substring(s.lastIndexOf("/lib") + 4, s.lastIndexOf(".so"));
            opts.libs.add(s);
            addIndirectDependencyLibs(be2);
        }
    }

    /**
     * Recursice helper function for above
     *
     * @param extLibs2 List of external libraries
     */
    private void mergeExtLibs(Set<LibDB.ExtLib> extLibs2) {
        extLibs2.addAll(directExtlibs);
        for (BuildEntity be : dependencies) {
            be.mergeExtLibs(extLibs2);
        }
    }

    /**
     * @return Name of build entity, if it were compiled to a library
     */
    protected String getLibName() {
        return toString() + ".so";
    }

    /**
     * after all primary dependencies have been checked:
     * Check which mca2 dependencies are available
     */
    public void addOptionalLibs() {
        for (BuildEntity be : optionalDependencies) {
            if (!be.missingDep && (!dependencies.contains(be))) {
                dependencies.add(be);
            }
        }
    }

    /**
     * @return Directory in which build file that specifies this build entity is located
     */
    public SrcDir getRootDir() {
        return buildFile.dir;
    }

    /**
     * Resolve dependencies.
     * (Find appropriate build entity object or external library object for every String in libs and optionalLibs)
     *
     * @param buildEntities All existing build entities
     * @param builder MakeFileBuilder instance
     */
    public void resolveDependencies(List<BuildEntity> buildEntities, MakeFileBuilder builder) throws Exception {
        if (autoDependencies) {
            for (SrcFile sf : sources) {
                checkForDependencies(sf, builder, false);
                if (missingDep) {
                    return;
                }
            }
        }
        for (int i = 0; i < libs.size(); i++) {
            resolveDependency(false, buildEntities, libs.get(i), builder);
        }
        for (String dep : optionalLibs) {
            resolveDependency(true, buildEntities, dep, builder);
        }
    }

    /**
     * sf is one of our (possibly indirect) source files.
     *
     * Check for dependencies to other build entities.
     *
     * @param sf Source file
     * @param builder Makefile builder instance
     * @param optional Optional dependencies?
     */
    public void checkForDependencies(SrcFile sf, MakeFileBuilder builder, boolean optional) {
        if (sf.processing) {
            return;
        }
        if (sf.getMissingDependency() != null) {
            if (optional) {
                return;
            }
            missingDep = true;
            String miss = sf.getMissingDependency();
            String msg = miss + " in " + sf.relative;
            String hint = getHintForMissingDependency(sf);
            if (hint != null) {
                msg += " (" + hint + ")";
            }
            builder.printCannotBuildError(this, " due to missing dependency " + msg, Color.RED);
            return;
        }

        sf.processing = true;
        checkForDependencies2(sf, builder, optional, sf.dependencies);
        if (missingDep) {
            sf.processing = false;
            return;
        }
        checkForDependencies2(sf, builder, true, sf.optionalDependencies);
        sf.processing = false;
    }

    /**
     * If dependency is missing, provide hint for user
     *
     * @param sf Missing dependency
     * @return Hint (as string - will be put in brackets) - null if there's no hint
     */
    protected String getHintForMissingDependency(SrcFile sf) {
        return null;
    }

    /**
     * Helper for above method
     *
     * @param sf Source file
     * @param builder Makefile builder instance
     * @param optional Optional dependencies?
     * @param sfDependencies Dependency list of file to process
     */
    private void checkForDependencies2(SrcFile sf, MakeFileBuilder builder, boolean optional, List<SrcFile> sfDependencies) {
        List<BuildEntity> resultList = optional ? optionalDependencies : dependencies;
        for (SrcFile sfDep : sfDependencies) {
            BuildEntity owner = sfDep.getOwner();
            if (owner != null && owner != this && (!dependencies.contains(owner)) && (!resultList.contains(owner))) {
                //System.out.println("Adding " + owner.toString() + " to " + toString() + " because of " + sfDep.toString());
                resultList.add(owner);
            } else if (owner == null || owner == this) {
                checkForDependencies(sfDep, builder, optional);
            }
            if (missingDep) {
                return;
            }
        }
    }

    /**
     * Resolve depency
     *
     * @param optional Optional dependency?
     * @param buildEntities All existing build entities
     * @param dep Dependency to find
     * @param builder MakeFileBuilder instance
     */
    private void resolveDependency(boolean optional, List<BuildEntity> buildEntities, String dep, MakeFileBuilder builder) throws Exception {
        if (LibDB.available(dep)) { // External library dependency?
            LibDB.ExtLib xl = LibDB.getLib(dep);
            directExtlibs.add(xl);
            for (BuildEntity be : xl.dependencies) {
                if (!dependencies.contains(be)) {
                    dependencies.add(be);
                }
            }
            return;
        }
        for (BuildEntity be : buildEntities) { // local dependency?
            if (be.getReferenceName().equals(dep)) {
                if (optional) {
                    optionalDependencies.add(be);
                } else if (!dependencies.contains(be)) {
                    dependencies.add(be);
                }
                return;
            }
        }

        // not found...
        if (!optional) {
            missingDep = true;
            builder.printCannotBuildError(this, " due to missing dependency " + dep, Color.Y);
        }
    }

    /**
     * Initialize final target for makefile
     *
     * @param makefile Makefile
     */
    public void initTarget(Makefile makefile) {
        target = makefile.addTarget(getTarget(), false, getRootDir());
        for (StartScript scr : startScripts) {
            scr.initTarget(makefile, getTargetPrefix(), getRootDir());
            scr.getTarget().addDependency(buildFile.relative);
            scr.getTarget().addDependency(target);
        }
        //target.addDependency("init");
        target.addDependency(buildFile.relative); // to ensure (e.g. after makeMakefile) that changes to build structure will be considered
    }

    /**
     * @return name that entity can be referenced with (e.g. in make.xml dependencies)
     */
    public String getReferenceName() {
        String s = getTargetFilename(); // file name
        if (s.startsWith("lib") && s.endsWith(".so")) {
            return s.substring(3, s.length() - 3);
        }
        return s;
    }

    /**
     * Get custom attribute/parameter/tag that was set in make.xml for this target
     *
     * @param key Key
     * @return Value (null if it wasn't set)
     */
    public String getParameter(String key) {
        return params == null ? null : params.get(key);
    }

    /**
     * @return Handler that will finally create this target
     */
    public abstract Class <? extends SourceFileHandler > getFinalHandler();

    /**
     * @return Prefix for target filename and scripts ("" if none)
     */
    public String getTargetPrefix() {
        return "";
    }

    /**
     * @return Is this a test program?
     */
    public boolean isTestProgram() {
        return false;
    }

    /**
     * @return Is this a unit test (program) that should be executed at compile time?
     */
    public boolean isUnitTest() {
        return false;
    }
}
