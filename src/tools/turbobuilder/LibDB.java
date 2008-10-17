package tools.turbobuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author max
 *
 * Contains specific information about libraries
 */
public class LibDB {

	private static Map<String, ExtLib> libs = new HashMap<String, ExtLib>();
	static List<String> critical = new ArrayList<String>();
	
	static {
		reinit();
	}

	public static void reinit() {
		libs.clear();
		try {
			File f = Util.getFileInEtcDir("libdb.txt");
			List<String> lines = Files.readLines(f);
			for (String s : lines) {
				if (s.trim().length() <= 1) {
					continue;
				}
				s = s.replace("$MCAHOME$", TurboBuilder.HOME.getAbsolutePath());
				int split = s.indexOf(":");
				String name =  s.substring(0, split).trim();
				String flags = s.substring(split + 1).trim();
				libs.put(name, new ExtLib(name, flags));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			File f = Util.getFileInEtcDir("critical.txt");
			List<String> lines = Files.readLines(f);
			for (String s : lines) {
				if (s.trim().length() <= 1) {
					continue;
				}
				critical.add(s);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void addDefines(List<String> defines) {
		for (Map.Entry<String, ExtLib> e : libs.entrySet()) {
			String opts = e.getValue().options;
			if (!opts.contains("N/A")) {
				// _LIB_OPENCV_PRESENT_
				defines.add("_LIB_" + e.getKey().toUpperCase() + "_PRESENT_");
			}
		}
	}
	
	static class ExtLib {
		final String name;
		final String options;
		final List<String> libDefines = new ArrayList<String>();
		final List<String> dependencies = new ArrayList<String>();
		
		public ExtLib(String name, String options) {
			this.name = name;
			String opts = options;
			while(opts.contains("-D ")) {
				opts = opts.substring(opts.indexOf("-D ") + 3);
				if (opts.indexOf(" ") > 0) {
					libDefines.add(opts.substring(0, opts.indexOf(" ")));
				}
			}

			while (options.contains("-lmca2_")) {
				String dep = options.substring(options.indexOf("-lmca2_") + 2);
				options = options.substring(0, options.indexOf("-lmca2_"));
				if (dep.contains(" ")) {
					dep = dep.substring(0, dep.indexOf(" "));
					options += dep.substring(dep.indexOf(" "));
				}	
				dependencies.add(dep);
			}
			
			this.options = options;
		}
		
		public boolean available() {
			return !options.contains("N/A");
		}
	}

	public static ExtLib getLib(String lib) throws Exception {
		ExtLib el = libs.get(lib);
		if (el != null) {
			return libs.get(lib);
		}
		throw new Exception("cannot find entry for external library " + lib);
	}

	public static boolean available(String lib) {
		ExtLib el = libs.get(lib);
		if (el != null) {
			return el.available();
		}
		return false;
	}

	public static List<List<String>> partition(List<String> cpps) {
		List<List<String>> result = new ArrayList<List<String>>();
		List<String> cur = new ArrayList<String>();
		result.add(cur);
		for (String cpp : cpps) {
			if (critical.contains(cpp)) {
				cur = new ArrayList<String>();
				result.add(cur);
			}
			cur.add(cpp);
		}
		return result;
	}
}
