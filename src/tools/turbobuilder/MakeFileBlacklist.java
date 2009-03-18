package tools.turbobuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author max
 *
 * Manages list of sources that cannot be combined in one great file without problems
 */
public class MakeFileBlacklist {

	/** List of sources (Scons name => Special settings) */
	Map<String, Element> list = new HashMap<String, Element>();

	/** Singleton instance */
	private static MakeFileBlacklist instance;
	
	/** @return Singleton instance */
	public static MakeFileBlacklist getInstance() {
		if (instance == null) {
			instance = new MakeFileBlacklist();
			try {
				File f = Util.getFileInEtcDir("blacklist.txt");
				List<String> lines = Files.readLines(f);
				for (String s : lines) {
					if (s.trim().length() <= 1) {
						continue;
					}
					Element el = instance.new Element(s);
					instance.list.put(el.name, el);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return instance;
	}
	
	/** 
	 * @param beName Scons entity (name)
	 * @return Special settings for this Scons entity (name)
	 */
	public Element get(String beName) {
		return list.get(beName);
	}
	
	/**
	 * Special make settings for Scons entity
	 */
	public class Element {
		
		/** name of Scons entity */
		String name;
		
		/** List of files to compile separately */
		List<String> compileSeparately = new ArrayList<String>();
		
		/** Compile all files separately? */
		boolean compileAllSeparately = false;
		
		/** Replace all #includes with #imports - necessary when .h files are not guarded */
		boolean importMode = false;
		
		/** unused - create symbolic links in build dir to binary libraries (.so) in sources */
		boolean linkLibs = false;
		
		/**
		 * @param s blacklist.txt line
		 */
		public Element(String s) {
			name = s.substring(0, s.indexOf(":"));
			String rest = s.substring(s.indexOf(":") + 1).trim();
			if (rest.contains("#")) {
				rest = rest.substring(0, rest.indexOf("#")).trim();
			}
			for (String s2 : rest.split("\\s")) {
				String s3 = s2.trim();
				if (s3.startsWith("-import")) {
					importMode = true;
				} else if (s3.startsWith("-linklibs")) {
					linkLibs = true;
				} else if (s3.startsWith("-safe")) {
					compileAllSeparately = true;
				} else if (s3.length() > 0) {
					compileSeparately.add(s3);
				}
			}
		}

		/**
		 * Compile this file seperately?
		 * 
		 * @param c filename
		 * @return answer
		 */
		public boolean contains(String c) {
			return compileAllSeparately || compileSeparately.contains(c);
		}
	}
}
