package makebuilder;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.SortedSet;

import makebuilder.util.Files;
import makebuilder.util.Util;

/** Relevant info on single source file */
public class SrcFile implements Serializable {

	/** UID */
	private static final long serialVersionUID = 13835934634512L;

	/** Absolute file name */
	public final transient File absolute;
	
	/** relative file name */
	public final String relative;

	/** Is this file created during the build process? (=> not-yet-existent) */
	public final boolean buildProduct;
	
	/** Reference to SrcDir containing file */
	public final transient SrcDir dir;
	
	/** Contains any data that ContentHandlers wish to store about this file */
	public final Properties properties = new Properties();
	
	/** Date of last change to source file */
	public final long lastChange;
	
	/** Size of source file */
	public final long size;
	
	/** True, if properties/dependencies were cached and loaded from last run and file has not changed in the mean-time */ 
	private transient boolean infoCachedAndUpToDate = false;

	/** Other source files that this file directly depends on - as parsed from file */
	public final List<String> rawDependencies = new ArrayList<String>();
	
	/** Other source files that this file directly depends on - resolved */
	public transient final List<SrcFile> dependencies = new ArrayList<SrcFile>();
	
	/** Raw source lines - in case file needs to be analyzed (temporary) */
	public transient List<String> srcLines;
	
	/** Source code lines without c++/java comments and strings */
	public transient List<String> cppLines;
	
	/** Build entity that this source file belongs to - may be null if not clear */
	private transient BuildEntity owner;
	
	/**
	 * @param dir Directory that file is in
	 * @param file File
	 * @param buildProduct Is this file created during the build process? (=> not-yet-existent)
	 */
	public SrcFile(SrcDir dir, File file, boolean buildProduct) {
		absolute = file.getAbsoluteFile();
		relative = dir.relative + File.separator + file.getName();
		this.buildProduct = buildProduct;
		this.dir = dir;
		lastChange = file.lastModified();
		size = file.length();
	}
	
	/**
	 * Applies information from cached source file
	 * 
	 * @param cachedInfo Cached file information
	 */
	public void applyCachedInfo(SrcFile cachedInfo) {
		if ((cachedInfo != null) && (cachedInfo.size == size) && (cachedInfo.lastChange == lastChange)) {
			infoCachedAndUpToDate = true;
			properties.putAll(cachedInfo.properties);
			rawDependencies.addAll(cachedInfo.rawDependencies);
		}
	}
	
	/**
	 * @return Returns whether properties/dependencies were cached and loaded from last run and file has not changed in the mean-time
	 */
	public boolean isInfoUpToDate() {
		return infoCachedAndUpToDate;
	}
	
	/**
	 * Find include file
	 * 
	 * @param include Include file string as found in .cpp/.h file
	 */
	public SrcFile findInclude(String include) {
		for (SrcDir sd : dir.defaultIncludePaths) {
			SrcFile result = dir.sources.find(sd + File.separator + include);
			if (result != null) {
				return result;
			}
		}
		return null;
	}
	
	public String toString() {
		return relative;
	}

	/**
	 * @return File name (without path, but with extension)
	 */
	public String getName() {
		return absolute.getName();
	}

	/**
	 * @return File name without extension
	 */
	public String getRawName() {
		String n = absolute.getName();
		return n.contains(".") ? n.substring(0, n.indexOf(".")) : n;
	}
	
	/**
	 * @return File extension
	 */
	public String getExtension() {
		String n = absolute.getName();
		return n.contains(".") ? n.substring(n.indexOf(".") + 1) : n;
	}
	
	/**
	 * @param extensions Extensions (uppercase/lowercase is not relevant)
	 * @return Has file one of these extensions?
	 */
	public boolean hasExtension(String... extensions) {
		String ex = getExtension();
		for (String e : extensions) {
			if (e.equalsIgnoreCase(ex)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Called after file has been completely processed
	 * (read lines may be released)
	 */
	void scanCompleted() {
		srcLines = null; // free memory
		cppLines = null;
	}

	/**
	 * @param owner Build entity that this source file belongs to
	 */
	void setOwner(BuildEntity owner) {
		this.owner = owner;
	}
	
	/**
	 * @return Build entity that this source file belongs to - may be null if not clear
	 */
	public BuildEntity getOwner() {
		return owner;
	}
	
	/**
	 * @return A file's source code lines
	 */
	public List<String> getLines() {
		if (srcLines == null) {
			try {
				srcLines = Files.readLines(absolute);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return srcLines;
	}

	/**
	 * @return A file's source code lines (without c++/java comments and strings)
	 */
	public List<String> getCppLines() {
		if (cppLines == null) {
			try {
				cppLines = Util.readLinesWithoutComments(absolute, false);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return cppLines;
	}

	/**
	 * Collect ALL (direct and indirect) dependencies of source files
	 * (includes this file as well)
	 * (recursive function - obviously)
	 * 
	 * @param Set that will contain results - may contain entries already - these won't be deleted
	 * @return Returns the parameter - for convenience
	 */
	public SortedSet<SrcFile> getAllDependencies(SortedSet<SrcFile> result) {
		result.add(this);
		for (SrcFile dep : dependencies) {
			if (!result.contains(dep)) {
				dep.getAllDependencies(result);
			}
		}
		return result;
	}

	/**
	 * Mark file
	 * 
	 * @param mark Name of mark
	 */
	public void mark(String mark) {
		properties.put(mark, "");
	}
	
	/**
	 * @return Does file have mark with specified name?
	 */
	public boolean hasMark(String mark) {
		return properties.containsKey(mark);
	}

	/**
	 * Resolve dependencies (rawDependencies => dependencies)
	 * (looks for "raw dependencies" in standard include paths)
	 * 
	 * @param ignoreMissing Ignore dependencies that cannot be found (instead of throwing an exception) ?
	 */
	public void resolveDependencies(boolean ignoreMissing) {
		for (String raw : rawDependencies) {
			boolean found = ignoreMissing;
			for (SrcDir sd : dir.defaultIncludePaths) {
				SrcFile sf = dir.sources.find(sd, raw);
				if (sf != null) {
					dependencies.add(sf);
					found = true;
					break;
				}
			}
			if (!found) {
				throw new RuntimeException("Dependency " + raw + " not found");
			}
		}
	}
	
//	/** File object to Source file */
//	private File file;
//
//	/** Dependencies */ 
//	final List<SrcFile> includes = new ArrayList<SrcFile>();
//
//	/** Dependencies as strings - temporary */ 
//	private final List<String> includesTemp = new ArrayList<String>();
//
//	/** DESCR macro found in file? => execute description_builder */
//	private boolean descr = false;
//
//	/** Moc file? */
//	boolean moc = false;
//
//	/** Template found in file */
//	private boolean template = false;
//
//	/** Possibly name of description_builder .hpp target file */
//	private String templateGenHName;

	//		/** Is file guarded with #ifdefs? */
	//		private boolean guarded = false;
	//		
	//		/** Is source file empty? */
	//		private boolean empty = true;

//	/**
//	 * Create h file
//	 * 
//	 * @param f File to parse
//	 * @param home $MCAHOME
//	 */
//	public SrcFile(File f, String home) {
//		this(f.getAbsolutePath().substring(home.length() + 1));
//		file = f;
//	}
//
//	/** Add virtual HFile (those that will be generated by description_builder etc.) */
//	public SrcFile(String virtualFileName) {
//		relFile = virtualFileName;
//		relFileReverse = new StringBuilder(relFile).reverse().toString();
//		for (int i = 0; i < relFile.length(); i++) {
//			if (relFile.charAt(i) == SourceScanner.FS) {
//				fsCount++;
//			}
//		}
//	}
//
//	/** Resolve dependencies */
//	public void resolveDeps() {
//		String curDir = relFile.substring(0, relFile.lastIndexOf(SourceScanner.FS));
//		for (String inc : includesTemp) {
//			SrcFile inch = sourceCache.find(inc, curDir);
//			if (inch != null) {
//				includes.add(inch);
//			}
//		}
//	}
//
//	public String toString() {
//		return relFileReverse;
//	}
//
//	/**
//	 * Parse file to find relevant information
//	 *  
//	 * @param hfiles2 List with .h files
//	 * @param makefile Makefile to add targets to
//	 * @param buildBase Base directory for building ($MCAHOME/build)
//	 */
//	public void parse(List<SrcFile> hfiles2, Makefile makefile, String buildBase) throws Exception {
//		if (file == null) { // skip virtual includes
//			return;
//		}
//		boolean classPassed = false;
//
//		for (String s : Util.readLinesWithoutComments(file, false)) {
//			if (s == null) {
//				break;
//			}
//			s = s.trim();
//
//			// find dependencies
//			String include = SourceScanner.getIncludeString(s);
//			if (include != null) {
//				includesTemp.add(include);
//				continue;
//			}
//
//			if (!descr && s.startsWith("_DESCR_")) {
//				//result.add("\t" + "MCAHOME=" + HOME.getAbsolutePath() + " " + targetBin + FS + "descriptionbuilder " + inc + " >> " + tempFileCpp);
//				descr = true;
//			}
//
//			//				if (!guarded && (s.startsWith("#if") || s.startsWith("# if") || s.startsWith("#pragma once"))) {
//			//					guarded = true;
//			//				}
//
//			if (!moc && (s.contains("Q_OBJECT") || s.contains("Q_PROPERTY") || s.contains("Q_CLASSINFO"))) {
//				moc = true;
//			}
//
//			if (!classPassed && s.startsWith("template") && s.substring(8).trim().startsWith("<")) {
//				template = true;
//			}
//
//			classPassed |= s.startsWith("class");
//
//			//				empty &= s.length() <= 1;
//		}
//
//		if (template && descr) {
//			String tmp = buildBase + SourceScanner.FS + relFile.substring(0, relFile.lastIndexOf(".")) + ".hpp";
//			String templateGenHName = tmp.substring(0, tmp.lastIndexOf(SourceScanner.FS) + 1) + "descr_h_" + tmp.substring(tmp.lastIndexOf(SourceScanner.FS) + 1);
//			hfiles2.add(new SrcFile(templateGenHName));
//			Makefile.Target t = makefile.addTarget(templateGenHName);
//			t.addDependency(relFile);
//			t.addDependency(MakeFileBuilder.DESCRIPTION_BUILDER_BIN);
//			t.addCommand("mkdir -p " + templateGenHName.substring(0, templateGenHName.lastIndexOf(SourceScanner.FS)));
//			t.addCommand(MakeFileBuilder.DESCRIPTION_BUILDER_BIN + " " + relFile + " > " + templateGenHName);
//		}
//
//		//			if (!empty && !guarded && !relFile.endsWith(".hpp")) {
//		//				//System.out.println("warning: " + relFile + " not guarded");
//		//				//System.out.println(relFile);
//		//			}
//	}
//
//	@Override
//	public int hashCode() {
//		return relFile.hashCode();
//	}
}