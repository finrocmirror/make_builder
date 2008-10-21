package tools.turbobuilder;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 *
 * Builds lib-database for specific system
 */
public class LibDBBuilder implements FilenameFilter {

	static final String FS = File.separator; 
	
	static final String GCC_VERSION = GCC.getGCCVersion();
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		try {
			new LibDBBuilder().buildDB();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void buildDB() throws Exception {
		
		// read raw db
		List<String> rawLibDB = Files.readLines(Util.getFileInEtcDir("libdb.raw"));
		String searchDirString = rawLibDB.remove(0);
		searchDirString = searchDirString.substring(searchDirString.indexOf(":") + 1);
		String[] searchDirsTmp = searchDirString.split(" ");
		List<String> searchDirs = new ArrayList<String>();
		List<String> excludes = new ArrayList<String>();
		for (String s : searchDirsTmp) {
			if (s.startsWith("!")) {
				excludes.add(s.substring(1));
			} else {
				searchDirs.add(s);
			}
		}
		
		// cache all of the system's .h/.hpp and lib*.so files
		System.out.println("Collecting .h, .hpp, lib*.so and lib*.a files from...");
		List<File> libs = new ArrayList<File>();
		List<File> header = new ArrayList<File>();
		List<File> mocs = new ArrayList<File>();
		for (String searchDir : searchDirs) {
			searchDir = searchDir.trim();
			if (searchDir.length() > 0) {
				System.out.print(" " + searchDir + "...");
				cacheFiles(new File(searchDir), header, libs, mocs, 0, excludes);
				System.out.println(" done");
			}
		}
			
		// create new db
		List<String> newLibDB = new ArrayList<String>();
		
		// make replacements in raw db
		for (String line : rawLibDB) {
			if (line.trim().length() == 0) {
				continue;
			}
			line = line.replace('\t', ' ');
			String name = line.substring(0, line.indexOf(":")).trim();
			System.out.print("Checking " + name + "... ");
			line = line.substring(line.indexOf(":") + 1).trim();
			String[] args = line.split("[ ]");
			String missing = null;
			String result = "";
			for (String arg : args) {
				arg = arg.trim();
				
				// process required headers
				if (arg.startsWith("-I<")) {
					String temp = arg.substring(3, arg.length() - 1);
					String[] reqHeaders = temp.split(",");
					List<File> candidateDirs = null;
					for (String reqHeader : reqHeaders) {
						reqHeader = reqHeader.replace("'", "");
						if (candidateDirs == null) {
							// first header
							candidateDirs = getCandidateDirs(reqHeader, header);
						} else {
							reduceCandidateDirs(reqHeader, candidateDirs);
						}
					}
					try {
						if (candidateDirs.size() > 0) {
							String dir = mostLikelyIncludeDir(candidateDirs);
							if (dir != null) {
								result += "-I" + dir + " ";
							}
						} else {
							missing = temp; 
							break;
						}
					} catch (Exception e) {
						missing = temp; 
						break;
					}
					
				// process required libraries
				} else if (arg.startsWith("-l") && (!arg.startsWith("-lmca2"))) {
					String reqLib = arg.substring(2, arg.length());
					List<File> candidateDirs = getCandidateDirs("lib" + reqLib + ".so", libs);
					if (candidateDirs.size() == 0) {
						candidateDirs = getCandidateDirs("lib" + reqLib + ".a", libs);
					}
					try {
						if (candidateDirs.size() > 0) {
							String dir = mostLikelyLibDir(candidateDirs);
							if (dir != null) {
								result += "-L" + dir + " ";
							} 
							result += arg + " ";
						} else {
							missing = reqLib;
							break;
						}
					} catch (Exception e) {
						missing = reqLib;
						break;
					}

				} else {
					result += arg + " ";
				}
			}
			
			System.out.println(missing == null ? "yes" : "no (missing " + missing + ")");
			newLibDB.add(name + ": " + (missing == null ? result : "N/A"));
		}
		
		// find correct moc and uic commands
		File mocqt3 = null;
		File mocqt4 = null;
		for (File moc : mocs) {
			String result = callMoc(moc);
			if (result.contains(" 4.")) {
				mocqt4 = moc;
			} else if (result.contains(" 3.")) {
				mocqt3 = moc;
			}
		}
		newLibDB.add("moc-qt3: " + (mocqt3 != null ? mocqt3.getAbsolutePath() : "N/A"));
		newLibDB.add("moc-qt4: " + (mocqt4 != null ? mocqt4.getAbsolutePath() : "N/A"));
		newLibDB.add("uic-qt3: " + (mocqt3 != null ? mocqt3.getParentFile().getAbsolutePath() + FS + "uic" : "N/A"));
		newLibDB.add("uic-qt4: " + (mocqt4 != null ? mocqt4.getParentFile().getAbsolutePath() + FS + "uic" : "N/A"));
		
		Files.writeLines(Util.getFileInEtcDir("libdb.txt"), newLibDB);
	}
	
	/*private List<File> getLibs() throws Exception {
		List<File> result = new ArrayList<File>();
		Process p = Runtime.getRuntime().exec("/sbin/ldconfig -p");
		for (String s : Files.readLines(p.getErrorStream())) {
			if (s.contains("=>")) {
				result.add(new File(s.substring(s.indexOf("=>") + 2).trim()));
			}
		}
		return result;
	}*/

	private String callMoc(File moc) throws Exception {
		Process p = Runtime.getRuntime().exec(moc.getAbsolutePath() + " -v");
		p.waitFor();
		for (String s : Files.readLines(p.getErrorStream())) {
			return s;
		}
		return null;
	}

	private String mostLikelyLibDir(List<File> candidateDirs) throws Exception {
		String best = candidateDirs.get(0).getAbsolutePath();
		boolean allInGCCDir = true;
		for (File dir : candidateDirs) {
			if (dir.getAbsolutePath().equals("/usr/lib")) {
				return null;
			}
			// heuristic: shorter paths are better
			if (dir.getAbsolutePath().length() < best.length()) {
				best = dir.getAbsolutePath();
			}
			allInGCCDir &= dir.getAbsolutePath().contains("/gcc/");
		}

		if (!allInGCCDir) {
			return best;
		} else {
			for (File dir : candidateDirs) {
				if (dir.getAbsolutePath().contains("/" + GCC_VERSION + "/") || dir.getAbsolutePath().endsWith("/" + GCC_VERSION)) {
					return dir.getAbsolutePath();
				}
			}
			throw new Exception("No matching GCC version");
		}
	}

	private String mostLikelyIncludeDir(List<File> candidateDirs) throws Exception  {
		String best = candidateDirs.get(0).getAbsolutePath();
		boolean allInGCCDir = true;
		for (File dir : candidateDirs) {
			if (dir.getAbsolutePath().equals("/usr/include")) {
				return null;
			}
			// heuristic: shorter paths are better
			if (dir.getAbsolutePath().length() < best.length()) {
				best = dir.getAbsolutePath();
			}
			allInGCCDir &= dir.getAbsolutePath().contains("/gcc/");
		}
		
		if (!allInGCCDir) {
			return best;
		} else {
			for (File dir : candidateDirs) {
				if (dir.getAbsolutePath().contains("/" + GCC_VERSION + "/") || dir.getAbsolutePath().endsWith("/" + GCC_VERSION)) {
					return dir.getAbsolutePath();
				}
			}
			throw new Exception("No matching GCC version");
		}
	}

	private void reduceCandidateDirs(String reqHeader, List<File> candidateDirs) {
		for (int i = 0; i < candidateDirs.size(); i++) {
			File cdir = candidateDirs.get(i);
			File check = new File(cdir.getAbsoluteFile() + FS + reqHeader);
			if (!check.exists()) {
				candidateDirs.remove(i);
				i--;
			}
		}
	}

	private List<File> getCandidateDirs(String reqFile, List<File> files) {
		List<File> result = new ArrayList<File>();
		reqFile = "/" + reqFile;
		for (File f : files) {
			String fs = f.getAbsolutePath();
			if (fs.endsWith(reqFile)) {
				result.add(new File(fs.substring(0, fs.length() - reqFile.length())));
			}
		}
		return result;
	}

	// for caching files...
	private void cacheFiles(File path, List<File> header, List<File> libs, List<File> mocs, int level, List<String> excludes) throws Exception {
		if (!path.isDirectory()) {
			return;
		}
		if (level >= 20) {
			throw new Exception("level too deep: " + path.getAbsolutePath());
		}
		File[] fs = path.listFiles(this);
		if (fs == null) {
			System.out.println("cannot read " + path.toString());
			return;
		}
		for (File f : fs) {
			String pathC = path.getCanonicalPath();
			String fileC = f.getCanonicalPath();
			if (f.isDirectory() && (!pathC.startsWith(fileC)) && fileC.startsWith(pathC)) {
				if (!excludes.contains(f.getAbsolutePath())) {
					cacheFiles(f, header, libs, mocs, level + 1, excludes);
				} else {
					System.out.print(" Skipping " + f.getAbsolutePath() + " "); 
				}
			} else if (f.getName().endsWith(".h") || f.getName().endsWith(".hpp")) {
				header.add(f);
			} else if (f.getName().endsWith(".so") || f.getName().endsWith(".a")) {
				libs.add(f);
			} else if (f.getName().equals("moc")) {
				mocs.add(f);
			}
		}
	}

	public boolean accept(File dir, String name) {
		File f = new File(dir.getAbsolutePath() + FS + name);
		if (f.isDirectory()) {
			return true;
		}
		if (name.equals("moc") || name.endsWith(".h") || name.endsWith(".hpp") || (name.startsWith("lib") && (name.endsWith(".so") || name.endsWith(".a")))) {
			return true;
		}
		return false;
	}

}
