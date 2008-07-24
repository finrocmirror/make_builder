package tools.turbobuilder;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.turbobuilder.LibDB.ExtLib;

public class SourceCache implements Serializable {

	/** UID */
	private static final long serialVersionUID = -893549510692442452L;

	/** Constants */
	static final String FS = File.separator;
	static final String MCAROOT = new File(".").getAbsoluteFile().getParent();
	static final String GCCROOT = "/usr/include/c++/4.2"; //getGCCRoot();	
	static final List<String> includeDirs = Arrays.asList(new String[]{MCAROOT + FS + "libraries", MCAROOT, MCAROOT + FS + "tools", MCAROOT + FS + "projects"});
	static final List<String> systemIncludeDirs = GCC.getSystemIncludeDirs();
	
	/** Virtual file system */
	transient List<String> tempDirList;  // not thread-safe
	transient FileSystem fileSystem;
	class FileSystem {
		Map<File, SourceFile> fileSystem = new HashMap<File, SourceFile>();

		public SourceFile get(File include) {
			return fileSystem.get(include);
		}

		public void put(File include, SourceFile result) {
			if (fileSystem.containsKey(include)) {
				System.out.println("warning: overwriting " + include.toString());
			}
			fileSystem.put(include, result);
		}

		public SourceFile findInclude(String include, List<String> inDirs, List<ExtLib> extlibs, boolean systemOnly) throws Exception {
			for (String dir : inDirs) {
				File f = new File(dir + FS + include);
				SourceFile sf = fileSystem.get(f);
				if (sf != null) {
					return sf;
				} else if (f.exists() && !f.isDirectory()) {
					return createSourceFileEntry(f, extlibs, systemOnly);
				}
			}
			return null;
		}
	}
	
	/** Cached information about system include files */
	Map<File, SourceFile.System> systemInclude = new HashMap<File, SourceFile.System>();

	/** Define Manager instance */
	DefineManager defineManager = new DefineManager();

	private void checkFileSystem() {
		if (fileSystem == null) {
			fileSystem = new FileSystem();
			fileSystem.fileSystem.putAll(systemInclude);
			tempDirList = new ArrayList<String>(20);
		}
	}

	public SourceFile getSourceFile(File include, List<ExtLib> extlibs) throws Exception {
		return getSourceFile(include, extlibs, false);
	}
	
	public SourceFile getSourceFile(File include, List<ExtLib> extlibs, boolean systemOnly) throws Exception {
		checkFileSystem();
		SourceFile result = fileSystem.get(include);
		return result != null ? result : createSourceFileEntry(include, extlibs, systemOnly); 
	}
	
	private SourceFile createSourceFileEntry(File include, List<ExtLib> extlibs, boolean systemOnly) throws Exception {	
		if ((include.getAbsolutePath().startsWith(MCAROOT) || include.equals(TurboBuilder.CONFIGH_FILE))) {
			SourceFile.Local result = new SourceFile.Local(include);
			fileSystem.put(include, result);
			return result;
		}
		SourceFile.System results = new SourceFile.System(include, this, extlibs);
		// fileSystem.put(include, results); // done by constructor above
		systemInclude.put(include, results);
		return results;
	}
	
	public SourceFile getSourceFile(String include, String curDir, List<ExtLib> extlibs, BuildEntity be) throws Exception {
		checkFileSystem();
		include = normalizeFile(include);
		
		// directories to look in
		if (include.startsWith("/")) {
			tempDirList.add("");
		} else {
			tempDirList.clear();
			tempDirList.add(curDir);
			tempDirList.addAll(includeDirs);
			tempDirList.addAll(be.addIncludes);
			while(curDir.startsWith(MCAROOT)) {
				curDir = curDir.substring(0, curDir.lastIndexOf("/"));
				tempDirList.add(curDir);
			}
		}
		SourceFile sf = fileSystem.findInclude(include, tempDirList, extlibs, false);
		if (sf != null) {
			return sf;
		}
		
		// system include file?
		sf = getSystemInclude(include, curDir, extlibs);
		if (sf == null) {
			if (include.startsWith("descr_h_") || include.contains("/descr_h_")) { // included automatically
				return null;
			}
			
			throw new Exception("Could not find " + include);
		}
		return sf;
	}
		
	private String normalizeFile(String include) {
		while (include.startsWith(".")) {
			include = include.substring(include.indexOf("/") + 1);
		}
		return include;
	}

	public SourceFile getSystemInclude(String include, String curDir, List<LibDB.ExtLib> extLibs) throws Exception {
		
		if (include.startsWith("libraries")) {
			throw new Exception("Holla");
		}
		
		checkFileSystem();
		include = normalizeFile(include);
		
		// directories to look in
		tempDirList.clear();
		tempDirList.add("/usr/include");
		for (LibDB.ExtLib s : extLibs) {
			String opts = s.options;
			while(opts.contains("-I")) {
				opts = opts.substring(opts.indexOf("-I") + 2);
				if (opts.contains(" ")) {
					tempDirList.add(opts.substring(0, opts.indexOf(" ")));
				} else {
					tempDirList.add(opts);
				}
			}
		}
		boolean parent = include.contains("/");
		for (String s : systemIncludeDirs) {
			if (parent) {
				s = s.substring(0, s.lastIndexOf("/"));
			}
			tempDirList.add(s);
		}
		
		return fileSystem.findInclude(include, tempDirList, extLibs, true);
		//TurboBuilder.printerrln("Could not find " + include + " (curDir: " + curDir + ")");
	}

	public SourceFile.System getSystemInclude(File include, List<ExtLib> extlibs) throws Exception {
		return (SourceFile.System)getSourceFile(include, extlibs, true);
	}

	public SourceFile.Local createVirtualInclude(File virtualInclude, CodeBlock cb) {
		checkFileSystem();
		SourceFile.Local result = new SourceFile.Local(virtualInclude, cb);
		fileSystem.put(virtualInclude, result);
		return result;
	}
}
