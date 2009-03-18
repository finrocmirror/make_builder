package tools.turbobuilder;

import java.io.File;

/**
 * @author max
 *
 * Builder Options
 * Call is used for parsing and managing them.
 */
public class Options {
	
	boolean compileTestParts = true;  // compile test parts in libraries?
	boolean combineCppFiles = false;  // combine cpp files in one large file? - option not supported yet
	String build = "experimental";
	boolean compilePartsToSO = false; // compile parts to shared objects?
	boolean compileOnlyDeps = false;  // only compile specified project/library and its dependencies
	File libPath;		  	  		  // path of library to compile
	
	// Debugging options
	boolean DEBUG_SCONSCRIPT_PARSING = false;
	
	/**
	 * @param args Command line arguments
	 */
	public Options(String[] args) {
		// parse command line parameters
		for (String s : args) {
			if (s.startsWith("--notests")) {
				compileTestParts = false;
			}
			if (s.startsWith("--combine") || s.startsWith("--hugecpps")) {
				combineCppFiles = true;
			}
			if (s.startsWith("--build=")) {
				build = s.substring(8);
			}
			if (s.startsWith("--so")) {
				compilePartsToSO = true;
			}
		}
	}
}
