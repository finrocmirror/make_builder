/**
 * 
 */
package makebuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import makebuilder.libdb.LibDB;
import makebuilder.util.CCOptions;

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

	/** name of entity to be built */
	public String name;
	
	/** Build file */
	public SrcFile buildFile;
	
	/** Involved source files */
	public final List<SrcFile> sources = new ArrayList<SrcFile>(); 
	
	/** Additional Compiler options to use for compiling & linking */
	public CCOptions opts = new CCOptions();
	
	/** Involved libraries */
	public final List<String> libs = new ArrayList<String>(); // libs/dependencies (as specified in SConscrip)t
	public final List<String> optionalLibs = new ArrayList<String>(); // optional libs/dependencies (as specified in SConscript)
	public final List<LibDB.ExtLib> extlibs = new ArrayList<LibDB.ExtLib>(); // resolved external libraries (from libs and available ones in optionalLibs)
	public final List<BuildEntity> dependencies = new ArrayList<BuildEntity>(); // resolved local (mca2) dependencies (from libs)
	public final List<BuildEntity> optionalDependencies = new ArrayList<BuildEntity>(); // resolved optional local (mca2) dependencies (from optionalLibs)

	/** are any dependencies missing? */
	public boolean missingDep;

	/** Final target in makefile for this build entity - will create library or executable */
	public Makefile.Target target;
	
	/**
	 * @param tb Reference to main builder instance
	 */
	public BuildEntity() {}

	/**
	 * @return Target file as relative path (usually executable or .so)
	 */
	public abstract String getTarget();
	
	public String toString() {
		return name;
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
				mfb.printErrorLine("Not building " + name + " due to dependency " + be.name + " which cannot be built");
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
		List<LibDB.ExtLib> extLibs2 = new ArrayList<LibDB.ExtLib>();
		mergeExtLibs(extLibs2);
		extlibs.clear();
		extlibs.addAll(extLibs2);
	}
	
	/**
	 * Compute the set of options to build this build entity with a C/C++ compiler
	 */
	public void computeOptions() {
		for (LibDB.ExtLib el : extlibs) {
			opts.merge(el.ccOptions);
		}
		for (BuildEntity be : dependencies) {
			String s = be.getTarget();
			target.addDependency(s);
			s = s.substring(s.lastIndexOf("/lib") + 4, s.lastIndexOf(".so"));
			opts.libs.add(s);
		}
	}

	/**
	 * Recursice helper function for above
	 * 
	 * @param extLibs2 List of external libraries
	 */
	private void mergeExtLibs(List<LibDB.ExtLib> extLibs2) {
		for (LibDB.ExtLib s : extlibs) {
			if (!extLibs2.contains(s)) {
				extLibs2.add(s);
			}
		}
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
			if (!be.missingDep) {
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
		for (int i = 0; i < libs.size(); i++) {
			resolveDependency(false, buildEntities, libs.get(i), builder);
		}
		for (String dep : optionalLibs) {
			resolveDependency(true, buildEntities, dep, builder);
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
			extlibs.add(xl);
			dependencies.addAll(xl.dependencies);
			return;
		}
		for (BuildEntity be : buildEntities) { // local dependency?
			String dep2 = "/lib" + dep + ".so";
			if (be.getTarget().endsWith(dep2)) {
				if (optional) {
					optionalDependencies.add(be);
				} else {
					dependencies.add(be);
				}
				return;
			}
		}
		// not found...
		if (!optional) {
			missingDep = true;
			builder.printErrorLine("Not building " + name + " due to missing dependency " + dep);
		}
	}

	/**
	 * Initialize final target for makefile
	 * 
	 * @param makefile Makefile
	 */
	public void initTarget(Makefile makefile) {
		target = makefile.addTarget(getTarget(), false);
		//target.addDependency("init");
		target.addDependency(buildFile.relative); // to ensure (e.g. after makeMakefile) that changes to build structure will be considered 
	}
}