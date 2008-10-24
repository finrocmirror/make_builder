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

	Map<String, Element> list = new HashMap<String, Element>();

	private static MakeFileBlacklist instance;
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
	
	public Element get(String beName) {
		return list.get(beName);
	}
	
	public class Element {
		String name;
		List<String> compileSeparately = new ArrayList<String>();
		boolean compileAllSeparately = false;
		boolean importMode = false;
		boolean linkLibs = false;
		
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

		public boolean contains(String c) {
			return compileAllSeparately || compileSeparately.contains(c);
		}
	}
}
