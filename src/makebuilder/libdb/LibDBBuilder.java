package makebuilder.libdb;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

import makebuilder.MakeFileBuilder;
import makebuilder.util.Files;
import makebuilder.util.GCC;
import makebuilder.util.Util;

/**
 * @author max
 *
 * Builds lib-database for specific system.
 * (Reads libdb.raw and creates libdb.txt)
 */
public class LibDBBuilder implements FilenameFilter, Runnable {

	/** File separator shortcut */
	static final String FS = File.separator;

	/** GCC version */
	static final String GCC_VERSION = GCC.getGCCVersion();

	/** Current working directory - should be $MCAHOME */
	static final File HOME = new File(".").getAbsoluteFile().getParentFile();

	/** List with preferred paths for libraries */
	static final List<String> preferredPaths = new ArrayList<String>();
	
	public void run() {
		try {
			buildDB();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Reads libdb.raw and creates libdb.txt
	 */
	private void buildDB() throws Exception {

        System.out.println("Found GCC version: " + GCC_VERSION);

        // parse preferred paths
        String[] preferred = MakeFileBuilder.getOptions().getProperty("prefer", "").split("\\s");
        for (String pref : preferred) {
        	pref = pref.trim();
        	if (pref.length() > 0) {
        		preferredPaths.add(pref);
        	}
        }
        
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
		List<String> furtherLibDirs = new ArrayList<String>();
		for (String line : rawLibDB) {
			furtherLibDirs.clear();
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
					
					// look in directories specified by any preceding -L argument
					boolean found = false;
					for (String dir : furtherLibDirs) {
						String base = dir + File.separator + "lib" + reqLib;
						if (new File(base + ".so").exists() || new File(base + ".a").exists()) {
							found = true;
							result += arg + " ";
							break;
						}
					}
					
					if (!found) { // usual case
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
					}

				} else if (arg.startsWith("-L")) {
					furtherLibDirs.add(arg.substring(2));
					result += arg + " ";
					
				} else {
					result += arg + " ";
				}
			}

			System.out.println(missing == null ? "yes" : "no (missing " + missing + ")");
			newLibDB.add(name + ": " + (missing == null ? result : "N/A"));
		}

		// find correct moc and uic commands
		File mocqt4 = null;
		for (File moc : mocs) {
			String result = callMoc(moc);
			if (result.contains(" 4.")) {
				mocqt4 = moc;
			}
		}
		newLibDB.add("moc-qt4: " + (mocqt4 != null ? mocqt4.getAbsolutePath() : "N/A"));
		newLibDB.add("uic-qt4: " + (mocqt4 != null ? mocqt4.getParentFile().getAbsolutePath() + FS + "uic" : "N/A"));

		Files.writeLines(Util.getFileInEtcDir("libdb.txt"), newLibDB);
	}

	/**
	 * Run (qt) moc command
	 *
	 * @param moc Moc executable
	 * @return String that command returns with no parameter
	 */
	private String callMoc(File moc) throws Exception {
		Process p = Runtime.getRuntime().exec(moc.getAbsolutePath() + " -v");
		p.waitFor();
		for (String s : Files.readLines(p.getErrorStream())) {
			return s;
		}
		return null;
	}

	private static boolean isGCCDir(File dir) {
		return dir.getAbsolutePath().contains("/gcc/");
	}
	
	/**
	 * When library is found in multiple directories -
	 * returns the most likely one (currently simple heuristics).
	 *
	 * @param candidateDirs The directories
	 * @return The most likely one
	 */
	private String mostLikelyLibDir(List<File> candidateDirs) throws Exception {
		return mostLikelyDir(candidateDirs, "/usr/lib", "/usr/local/lib");
	}

	/**
	 * When headers are found in multiple directories -
	 * returns the most likely one (currently simple heuristics).
	 *
	 * @param candidateDirs The directories
	 * @return The most likely one
	 */
	private String mostLikelyIncludeDir(List<File> candidateDirs) throws Exception {
		return mostLikelyDir(candidateDirs, "/usr/include", "/usr/local/include");
	}
	
	/** Helper for above two functions */
	private String mostLikelyDir(List<File> candidateDirs, String defaultDir1, String defaultDir2) throws Exception {
		String best = candidateDirs.get(0).getAbsolutePath();
		boolean allInGCCDir = true;
		boolean someInGCCDir = true;
		for (File dir : candidateDirs) {
			for (String pref : preferredPaths) {
				if (dir.getAbsolutePath().startsWith(pref)) {
					return pref;
				}
			}
			
			if (dir.getAbsolutePath().equals(defaultDir1) || dir.getAbsolutePath().equals(defaultDir2)) {
				return null;
			}
			// heuristic: shorter paths are better
			if (dir.getAbsolutePath().length() < best.length()) {
				best = dir.getAbsolutePath();
			}
			allInGCCDir &= isGCCDir(dir);
			someInGCCDir |= isGCCDir(dir);
		}

		if (someInGCCDir) {
			for (File dir : candidateDirs) {
				if (dir.getAbsolutePath().contains("/" + GCC_VERSION + "/") || dir.getAbsolutePath().endsWith("/" + GCC_VERSION)) {
					return null;
				}
			}
		}
		if (isGCCDir(new File(best))) {
			throw new RuntimeException("No matching GCC version");
		}
		return best;
	}

	/**
	 * Remove directories from candidates that do not contain specfied header
	 *
	 * @param reqHeader Header
	 * @param candidateDirs List of directories (candidates)
	 */
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

	/**
	 * Get all directories that contain specified file
	 *
	 * @param reqFile the file
	 * @param files List of all relevant files (cached during search)
	 * @return List with directories
	 */
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

	/**
	 * for caching files...
	 * look for .h/.hpp and lib*.so files in directory and all subdirectories
	 *
	 * @param path Directory to search in
	 * @param header List with header files
	 * @param libs List with library files
	 * @param mocs List with qt-moc executables
	 * @param level Recursion depth
	 * @param excludes List of (sub)directories to exclude
	 */
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
