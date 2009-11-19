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
package makebuilder.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeSet;

/**
 * @author max
 * 
 * This is a helper class to deal with calls to the C Compiler.
 * It wraps a set of GCC command line options.
 * 
 * (implements String comparator for better path sorting: local paths should be output first)
 */
public class CCOptions implements Comparator<String> {

	/** C++ Options that are only relevant for linking */
	public final static String[] LINK_ONLY_OPTIONS = new String[]{"-shared"};

	/** C++ Options that are only relevant for compiling */
	public final static String[] COMPILE_ONLY_OPTIONS = new String[]{"-fpermissive"};

	/** Libraries for linking */
	public final ArrayList<String> libs = new ArrayList<String>();

	/** Library paths */
	public final TreeSet<String> libPaths = new TreeSet<String>(this);

	/** Include paths */
	public final TreeSet<String> includePaths = new TreeSet<String>(this);

	/** Common options for compiling and linking */
	public final TreeSet<String> options = new TreeSet<String>();

	/** Options only for linking */
	public final TreeSet<String> linkOptions = new TreeSet<String>();

	/** Options only for compiling */
	public final TreeSet<String> compileOptions = new TreeSet<String>();
	
	public CCOptions() {}
	
	/** Parse C compiler options from string */
	public CCOptions(String options) {
		addOptions(options);
	}

	/**
	 * Add a set of options
	 * 
	 * @param options Options as string (will be parsed)
	 */
	public void addOptions(String options) {
		if (options != null) {
			String[] opts = options.split("\\s-");
			for (String opt : opts) {
				opt = opt.trim();
				addOption(opt.startsWith("-") ? opt : "-" + opt);
			}
		}
	}
	
	/**
	 * Add a single option 
	 * 	
	 * @param opt Options a string (will be parsed)
	 */
	public void addOption(String opt) {
		opt = opt.trim();
		if (opt.startsWith("-l")) {
			addLib(opt.substring(2));
		} else if (opt.startsWith("-L")) {
			libPaths.add(opt.substring(2));
		} else if (opt.startsWith("-I")) {
			includePaths.add(opt.substring(2));
		} else if (opt.startsWith("-Wl")) {
			linkOptions.add(opt);
		} else if (opt.startsWith("-D")) {
			compileOptions.add(opt);
		} else {
			// some other option
			for (String s : LINK_ONLY_OPTIONS) {
				if (s.equals(opt)) {
					linkOptions.add(opt);
					return;
				}
			}
			for (String s : COMPILE_ONLY_OPTIONS) {
				if (s.equals(opt)) {
					compileOptions.add(opt);
					return;
				}
			}
			options.add(opt);
		}
	}
	
	/**
	 * Add library to "Libraries for linking" (if it doesn't contain this yet)
	 * 
	 * @param lib
	 */
	private void addLib(String lib) {
		if (!libs.contains(lib)) {
			libs.add(lib);
		}
	}

	/** Merge options with other options */
	public void merge(CCOptions other) {
		compileOptions.addAll(other.compileOptions);
		includePaths.addAll(other.includePaths);
		libPaths.addAll(other.libPaths);
		for (String lib : other.libs) {
			addLib(lib);
		}
		linkOptions.addAll(other.linkOptions);
		options.addAll(other.options);
	}
	
	/**
	 * Create string with options
	 * 
	 * @param compile Is this a compiling operation?
	 * @param link Is this a linking operation?
	 * @return String with options
	 */
	public String createOptionString(boolean compile, boolean link) {
		String result = "";
		for (String s : options) {
			result += " " + s;
		}
		if (compile) {
			for (String s : compileOptions) {
				result += " " + s;
			}
		}
		if (link) {
			for (String s : linkOptions) {
				result += " " + s;
			}
		}
		if (compile) {
			for (String s : includePaths) {
				result += " -I" + s;
			}
		}
		if (link) {
			for (String s : libPaths) {
				result += " -L" + s;
			}
			for (String s : libs) {
				result += " -l" + s;
			}
		}
		return result.trim();
	}
	
	/**
	 * @return String with options required by nvcc compiler
	 */
	public String createCudaString() {
		String result = "";
		for (String s : compileOptions) {
			if (s.startsWith("-D") || s.startsWith("$")) {
				result += " " + s;
			}
		}
		for (String s : includePaths) {
			result += " -I" + s;
		}
		return result;
	}
	
	/**
	 * Create Cpp compiler call for linking only
	 * 
	 * @param inputs Input files (divided by whitespace)
	 * @param output Output file
	 * @param cxx Use C++ compiler? (instead of c)
	 * @return GCC Compilter call for makefile
	 */
	public String createLinkCommand(String inputs, String output, boolean cxx) {
		return cleanCommand((cxx ? "$(CXX)" : "$(CC)") + " -o " + output + " " + inputs + " " + createOptionString(false, true));
	}
	
	/**
	 * Create Cpp compiler call for compiling only
	 * 
	 * @param inputs Input files (divided by whitespace)
	 * @param output Output file
	 * @param cxx Use C++ compiler? (instead of c)
	 * @return GCC Compilter call for makefile
	 */
	public String createCompileCommand(String inputs, String output, boolean cxx) {
		return cleanCommand((cxx ? "$(CXX)" : "$(CC)") + " -c -o " + output + " " + inputs + " " + createOptionString(true, false));
	}
	
	/**
	 * Create Cpp compiler call for compiling and linking
	 * 
	 * @param inputs Input files (divided by whitespace)
	 * @param output Output file
	 * @param cxx Use C++ compiler? (instead of c)
	 * @return GCC Compilter call for makefile
	 */
	public String createCompileAndLinkCommand(String inputs, String output, boolean cxx) {
		return cleanCommand((cxx ? "$(CXX)" : "$(CC)") + " -o " + output + " " + inputs + " " + createOptionString(true, true));
	}
	
	/**
	 * Remove double spaces etc. from call
	 * 
	 * @param s call
	 * @return "Cleaner" call command
	 */
	public static String cleanCommand(String s) {
		s = s.replace("  ", " ");
		return s;
	}

	@Override
	public int compare(String s1, String s2) {
		if (s1.startsWith("/") && (!s2.startsWith("/"))) {
			return 1;
		} else if (!s1.startsWith("/") && s2.startsWith("/")) {
			return -1;
		}
		return s1.compareTo(s2);
	}
}

