package tools.turbobuilder;

import java.io.File;

/**
 * @author max
 *
 * Builder Options
 */
public class Options {
	boolean compileTestParts = true;  // compile test parts in libraries?
	boolean combineCppFiles = false;  // combine cpp files in one large file? - option not supported yet
	File kernel;           			  // kernel directory - use alternative kernel?
	String build = "experimental";
	String project = null;
	boolean compilePartsToSO = false; // compile parts to shared objects?
	int gccThreads = 0;				  // number of separate gcc threads
	boolean compileOnlyDeps = false;  // only compile specified project/library and its dependencies
	File libPath;		  	  		  // path of library to compile
	boolean makeFile = false;   	  // create make file instead of compiling
	
	// Debugging options
	boolean DEBUG_SCONSCRIPT_PARSING = false;
	
	public Options(String[] args) {
		// parse command line parameters
		for (String s : args) {
			if (s.startsWith("--notests")) {
				compileTestParts = false;
			}
			if (s.startsWith("--combine") || s.startsWith("--hugecpps")) {
				combineCppFiles = true;
			}
			if (s.startsWith("--kernel=")) {
				kernel = new File(s.substring(9));
			}
			if (s.startsWith("--build=")) {
				build = s.substring(8);
			}
			if (!s.startsWith("-")) {
				project = s; 
			}
			if (s.startsWith("--jobs=")) {
				gccThreads = Integer.parseInt(s.substring(7));
			}
			if (s.startsWith("--so")) {
				compilePartsToSO = true;
			}
			if (s.startsWith("--make")) {
				makeFile = true;
			}
		}
	}
}
