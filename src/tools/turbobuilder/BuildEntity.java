/**
 * 
 */
package tools.turbobuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 *
 * "Build Entity".
 * 
 * This can be an MCA program or an MCA library.
 * Objects of this class are the result of parsing MCA2 SConscripts.
 */
public abstract class BuildEntity {

	/** File sepearator short cut */
	static final String FS = File.separator;

	/** name of entity to be built */
	String name;
	
	/** variable name of build entity in SConscript */
	String sconsID; 
	
	/** root directory of SConscript */
	File rootDir;
	
	/** SConscript file relative to root directory */
	String sconscript;
	
	/** targets/groups in makefiles this build entity belongs to */
	List<String> categories = new ArrayList<String>();
	
	/** Involved source files */
	List<String> cpps = new ArrayList<String>(); // cpp files
	List<String> cs = new ArrayList<String>(); // c files
	List<String> hs = new ArrayList<String>(); // h files
	List<String> os = new ArrayList<String>(); // o files
	List<String> cudas = new ArrayList<String>(); // cuda files
	List<String> addIncludes = new ArrayList<String>(); // additional h files - not sure about those
	List<String> uics = new ArrayList<String>(); // qt uic files
	
	/** Compiler options to use for building */
	String opts = "";
	
	/** Involved libraries */
	List<String> libs = new ArrayList<String>(); // libs as specified in SConscript
	List<String> optionalLibs = new ArrayList<String>(); // optional libs as specified in SConscript
	List<LibDB.ExtLib> extlibs = new ArrayList<LibDB.ExtLib>(); // resolved external libraries in libs (and available ones in optionalLibs)
	List<BuildEntity> dependencies = new ArrayList<BuildEntity>(); // resolved mca2 dependencies (from libs)
	List<BuildEntity> optionalDependencies = new ArrayList<BuildEntity>(); // resolved optional mca2 dependencies (from optionalLibs)

	/** Reference to main builder instance */
	MakeFileBuilder tb;
	
	/** has entity been processed in this run? - or does this still need to be done */
	//boolean built = false;
	
	/** target file. Final result of compiling and linking */
	private File target;
	
	/** are any dependencies missing? */
	public boolean missingDep;
	
	/**
	 * @param tb Reference to main builder instance
	 */
	public BuildEntity(MakeFileBuilder tb) {
		this.tb = tb;
	}

	/**
	 * @return target file. Final result of compiling and linking
	 */
	public File getTarget() {
		if (target != null) {
			return target;
		}
		String target = tb.buildPath.getAbsolutePath() + FS;
		if (!compileToExecutable()) {
			target += "lib" + FS + getLibName();
		} else {
			target += "bin" + FS + toString();
		}
		return new File(target);
	}
	
	/**
	 * @return Compile build entity to executable? (or rather library (.so))
	 */
	public boolean compileToExecutable() {
		return (this instanceof MCAProgram) && (!tb.opts.compilePartsToSO);
	}

	public String toString() {
		return name;
	}

	/**  
	 * Determine, whether build entity can be built.
	 * missingDep is set accordingly 
	 */
	public void checkDepencies() {
		if (missingDep) {
			return;
		}
		for (BuildEntity be : dependencies) {
			be.checkDepencies();
			if (be.missingDep) {
				missingDep = true;
				tb.printErrorLine("Not building " + name + " due to dependency " + be.name + " which cannot be built");
				return;
			}
		}
	}
	
	/**
	 * Collect external libraries.
	 * All required external libraries from this entity as well as all dependencies are stored in extlibs.
	 * extlibs contains every external library at most once.
	 */
	public void mergeExtLibs() {
		List<LibDB.ExtLib> extLibs2 = new ArrayList<LibDB.ExtLib>();
		mergeExtLibs(extLibs2);
		extlibs = extLibs2;
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
	 * @return Is this a test program? (Heuristic: yes, if there's test somewhere in the name)
	 */
	public boolean isTestPart() {
		return (this instanceof MCAProgram) && (name.contains("_test") || name.contains("test_"));
	}
	
	/**
	 * @return Name of build entity, if it were compiled to a library
	 */
	protected String getLibName() {
		return toString() + ".so";
	}

	/**
	 * @return Additional Linker options possibly required because of external library dependencies
	 */
	public String getLinkerOpts() {
		for (LibDB.ExtLib el : extlibs) {
			if (el.name.equals("ltdl")) {
				return ",--export-dynamic";
			}
		}
		return "";
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
}