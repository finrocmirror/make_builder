package makebuilder.libdb;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.util.CCOptions;
import makebuilder.util.Files;
import makebuilder.util.Util;

/**
 * @author max
 *
 * Contains specific information about external libraries
 * 
 * Handles/manages entries in libdb.txt
 */
public class LibDB {

	/** Mapping: Library name => library */
	private static Map<String, ExtLib> libs = new HashMap<String, ExtLib>();

	/** Load libdb.txt at the beginning */
	static {
		reinit();
	}

	/**
	 * reads and processes libdb.txt file
	 * may be called again, if file changes 
	 */
	public static void reinit() {
		libs.clear();
		try {
			File f = Util.getFileInEtcDir("libdb.txt");
			List<String> lines = Files.readLines(f);
			for (String s : lines) {
				if (s.trim().length() <= 1) {
					continue;
				}
				s = s.replace("$MCAHOME$", MakeFileBuilder.HOME.getAbsolutePath());
				int split = s.indexOf(":");
				String name =  s.substring(0, split).trim();
				String flags = s.substring(split + 1).trim();
				libs.put(name, new ExtLib(name, flags));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Add all available libraries to specified lines.
	 * Entries of the form '_LIB_???_PRESENT_' are added to the list.
	 * 
	 * @param defines List with defines
	 */
	public static void addDefines(List<String> defines) {
		for (Map.Entry<String, ExtLib> e : libs.entrySet()) {
			String opts = e.getValue().options;
			if (!opts.contains("N/A")) {
				// _LIB_OPENCV_PRESENT_
				defines.add("_LIB_" + e.getKey().toUpperCase() + "_PRESENT_");
			}
		}
	}
	
	/**
	 * @param lib Library name
	 * @return Library with this name
	 * @throws Exception Thrown when not found
	 */
	public static ExtLib getLib(String lib) throws Exception {
		ExtLib el = libs.get(lib);
		if (el != null) {
			return libs.get(lib);
		}
		throw new Exception("cannot find entry for external library " + lib);
	}

	/**
	 * @param lib Library name
	 * @return Is library with this name available? 
	 */
	public static boolean available(String lib) {
		ExtLib el = libs.get(lib);
		if (el != null) {
			return el.available();
		}
		return false;
	}
	
	/**
	 * Find local dependencies in external libraries 
	 * having this is ugly... but sometimes occurs
	 * 
	 * @param bes All build entities
	 */
	public static void findLocalDependencies(Collection<BuildEntity> bes) {
		for (ExtLib el : libs.values()) {
			el.findLocalDependencies(bes);
		}
	}
	
	/**
	 * @author max
	 *
	 * Represents one external library 
	 */
	public static class ExtLib {
		
		/** library name */
		public final String name;
		
		/** compiler options to set for this library */
		public final String options;
		
		/** wrapped/more sophisticated version of above */
		public CCOptions ccOptions;
		
		/** Dependencies to other mca2 libraries (Strings: "mca2_*") */
		public final List<BuildEntity> dependencies = new ArrayList<BuildEntity>();
		
		/**
		 * @param name Library name
		 * @param options libdb.txt line
		 */
		public ExtLib(String name, String options) {
			this.name = name;
			this.options = options;
			ccOptions = new CCOptions(options);
		}
		
		/**
		 * @return Is library available on current system?
		 */
		public boolean available() {
			return !options.contains("N/A");
		}
		
		public String toString() {
			return name;
		}
		
		/**
		 * Find local dependencies in external libraries 
		 * having this is ugly... but sometimes occurs
		 * 
		 * @param bes All build entities
		 */
		public void findLocalDependencies(Collection<BuildEntity> bes) {
			ArrayList<String> libCopy = new ArrayList<String>(ccOptions.libs);
			for (String lib : libCopy) {
				for (BuildEntity be : bes) {
					if (be.getTarget().endsWith("/lib" + lib + ".so")) {
						ccOptions.libs.remove(lib);
						dependencies.add(be);
						break;
					}
				}
			}
		}
	}
}
