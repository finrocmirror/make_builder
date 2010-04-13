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
package makebuilder;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import makebuilder.handler.CppHandler;
import makebuilder.handler.MakeXMLLoader;
import makebuilder.libdb.LibDB;
import makebuilder.util.Util;

/**
 * @author max
 *
 * Main class for Building makefiles
 */
public class MakeFileBuilder implements FilenameFilter, Runnable {

	/** Input Character set */
	public static final Charset INPUT_CHARSET = Charset.forName("ISO-8859-1");

	/** Current working directory - should be $MCAHOME */
	public static final File HOME = new File(".").getAbsoluteFile().getParentFile();

	/** Shortcut to file separator */
	protected static final String FS = File.separator;

	/** Libraries and executables that need to be built */
	public final List<BuildEntity> buildEntities = new ArrayList<BuildEntity>();

	/** Options from command line */
	protected static Options opts;

	/** List of build file loaders used int this builder */
	private final List<BuildFileLoader> buildFileLoaders = new ArrayList<BuildFileLoader>();
	
	/** List of content handlers used in this builder - need to have correct order */
	private final List<SourceFileHandler> contentHandlers = new ArrayList<SourceFileHandler>();
	
	/** build path where compiled binaries are placed */
	public final SrcDir buildPath;

	/** persistent temporary build path where generated files can be stored */ 
	public final SrcDir tempBuildPath;
	
	/** temp-path - non-persistent temporary build artifacts belong here */
	public final SrcDir tempPath;
	
	/** Makefile */
	public final Makefile makefile;

//	/** Categorization of make targets (Target/Category => dependencies) */
//	private final SortedMap<String, List<String>> categories = new TreeMap<String, List<String>>();

	/** Cache for source files */
	protected final SourceScanner sources;

	/** Temporary directory for merged files */
	private final String TEMPDIR = "/tmp/mbuild_" + Util.whoami() + "_" + Math.abs(HOME.getAbsolutePath().hashCode());

	/** Error message for console - are collected and presented at the end */
	private final List<String> errorMessages = new ArrayList<String>();
	
	public static void main(String[] args) {

		// Parse command line options
		opts = new Options(args);

		try {
			opts.mainClass.newInstance().run();
		} catch (Exception e) {
			e.printStackTrace();
			printErrorAdvice();
		}
	}

	/**
	 * @return Parsed options from command line
	 */
	public static Options getOptions() {
		return opts;
	}
	
	public MakeFileBuilder() throws Exception {
		this("dist", "build");
	}
	
	/**
	 * Performs diverse initializations
	 */
	public MakeFileBuilder(String relBuildDir, String relTempBuildDir) {
		
		// init source scanner and paths
		sources = new SourceScanner(HOME, this);
		buildPath = sources.findDir(relBuildDir, true);
		tempBuildPath = sources.findDir(relTempBuildDir, true);
		tempPath = sources.findDir(TEMPDIR, true);
		
		// create makefile object
		makefile = new Makefile("$(TARGET_DIR)", "$(TEMP_BUILD_DIR)", "$(TEMP_DIR)");
//		makefile.addInitCommand("mkdir -p " + "$(TARGET_DIR)");
//		makefile.addInitCommand("mkdir -p " + "$(TEMP_BUILD_DIR)");
//		makefile.addInitCommand("mkdir -p " + "$(TEMP_DIR)");
		
		// add variables
		makefile.addVariable("TARGET_DIR=" + buildPath.relative);
		makefile.addVariable("TEMP_BUILD_DIR=" + tempBuildPath.relative);
		makefile.addVariable("TEMP_DIR=" + tempPath.relative);
	}
	
	/** Create makefile */
	public void run() {
		try {
			build();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}		
		
	public void build() throws Exception {
		
		// init content handlers
		if (buildFileLoaders.isEmpty()) {
			buildFileLoaders.add(new MakeXMLLoader());
		}
		if (contentHandlers.isEmpty()) { // add default content handlers
			contentHandlers.add(new CppHandler("", "", !opts.combineCppFiles));
		}
		for (SourceFileHandler ch : contentHandlers) {
			ch.init(makefile);
		}
		
		// read/process/cache source files
		System.out.println("Caching and processing local source files...");
		sources.scan(makefile, buildFileLoaders, contentHandlers, true, getSourceDirs());
		
		// find local dependencies in "external libraries"
		LibDB.findLocalDependencies(buildEntities);
		
		// process dependencies
		System.out.println("Processing dependencies...");
		for (BuildEntity be : buildEntities) {
			be.resolveDependencies(buildEntities, this);
		}

		// check whether all dependencies are met
		for (BuildEntity be : buildEntities) {
			be.checkDependencies(this);
		}
		
		// add available optional libs
		for (BuildEntity be : buildEntities) {
			be.addOptionalLibs();
		}

		// collect external libraries needed for building
		for (BuildEntity be : buildEntities) {
			be.mergeExtLibs();
		}
		
		// add build commands for entity to makefile
		for (BuildEntity be : buildEntities) {
			if (be.missingDep) {
				continue;
			}
			build(be);
		}
		
		// Write makefile
		writeMakefile();

		// print error messages at the end... so nobody will miss them
		//Collections.sort(errorMessages);
		for (String err : errorMessages) {
			System.err.println(err);
		}
		
		// completed
		System.out.println("Creating Makefile successful.");
	}
	
	/** 
	 * Writes makefile to disk (may be overridden for custom adjustments)
	 */
	protected void writeMakefile() throws Exception {

		makefile.writeTo(new File("Makefile"));
	}
	
	/**
	 * @param handler Source file handler to add to this build entity
	 */
	protected void addHandler(SourceFileHandler handler) {
		contentHandlers.add(handler);
	}
	
	/**
	 * @param loader Build file loader to add to this build entity
	 */
	protected void addLoader(BuildFileLoader loader) {
		buildFileLoaders.add(loader);
	}
	
	/**
	 * @return Get source directories (relative) - should be overidden
	 */
	public String[] getSourceDirs() {
		return new String[]{"src"};
	}

	/**
	 * Build single build entity
	 * 
	 * @param be Build entity to build
	 */
	private void build(final BuildEntity be) throws Exception {
		System.out.println("Processing " + be.name);
		be.initTarget(makefile);
		be.computeOptions();
		
		for (SourceFileHandler ch : contentHandlers) {
			ch.build(be, makefile, this);
		}
	}
	
	/**
	 * Create and name intermediate temporary build artifact for a single source file
	 * (can be overridden to perform custom naming)
	 * (creates files in persistent temporary building directory)
	 * 
	 * @param source Source file 
	 * @param targetExtension Suggested extension for target 
	 * @return
	 */
	public SrcFile getTempBuildArtifact(SrcFile source, String targetExtension) {
		String srcDir = source.dir.relativeTo(source.dir.getSrcRoot());
		return sources.registerBuildProduct(tempBuildPath.relative + FS + srcDir + FS + source.getRawName() + "." + targetExtension);
	}
	
	/**
	 * Create and name intermediate temporary build artifact for multiple source files
	 * (can be overridden to perform custom naming)
	 * (creates files in persistent temporary building directory)
	 * 
	 * @param source Source file 
	 * @param targetExtension Suggested extension for target 
	 * @return
	 */
	public SrcFile getTempBuildArtifact(BuildEntity source, String targetExtension, String suggestedPrefix) {
		String srcDir = source.getRootDir().relativeTo(source.getRootDir().getSrcRoot());
		return sources.registerBuildProduct(tempBuildPath.relative + FS + srcDir + FS +	source.name + "_" + suggestedPrefix + "." + targetExtension);
	}
	
	/** 
	 * Get directory (normally in persistent temporary building directory) for build files
	 * (can be overridden to perform custom naming)
	 * 
	 * @param be Build Entity
	 * @return Directory
	 */
	public String getTempBuildDir(BuildEntity source) {
		String srcDir = source.getRootDir().relativeTo(source.getRootDir().getSrcRoot());
		return tempBuildPath.relative + FS + srcDir;
	}
	
	/**
	 * Print error line deferred (when tool exits)
	 *
	 * @param s line to print
	 */
	public void printErrorLine(String s) {
		errorMessages.add(s);
	}

	/** Print advice if an error occured */
	public static void printErrorAdvice() {
		System.out.println("An error was encountered during the build process.");
		System.out.println("Make sure you have the current version of this tool, called ant, and");
		System.out.println("the libdb.txt file is up to date (call updatelibdb),");
	}

	public boolean accept(File dir, String name) {
		File f = new File(dir.getAbsolutePath() + File.separator + name);
		return (f.isDirectory() || name.equals("SConscript"));
	}

	/**
	 * Set default include paths for directory (used for finding/resolving .h dependencies)
	 * (may/should be overridden)
	 * 
	 * @param dir SrcDir instance of which to set default include paths
	 */
	public void setDefaultIncludePaths(SrcDir dir, SourceScanner sources) {
		dir.defaultIncludePaths.add(dir);
	}
	
	/**
	 * @return Cache for source files 
	 */
	public SourceScanner getSources() {
		return sources;
	}
}
