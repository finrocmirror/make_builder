package makebuilder.ext.mca;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SrcFile;
import makebuilder.libdb.LibDB;
import makebuilder.util.ToStringComparator;

/**
 * @author max
 *
 * Generates information file for every library (.so)
 * This information currently includes the required closures for compiling this library 
 * (and using its headers!).
 * Output is one file per .so: <target-name> + ".info"
 * 
 * (MCA-specific; only needed for system-installs) 
 */
public class LibInfoGenerator extends SourceFileHandler.Impl {

	/** Directory that will contain library information files */
	private final String outputDir;
	
	/** File extension for information files */
	public final static String EXT = ".info";
	
	/**
	 * @param outputDir Directory that will contain library information files
	 */
	public LibInfoGenerator(String outputDir) {
		this.outputDir = outputDir;
	}
	
	@Override
	public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {
		if (!be.isLibrary()) {
			return;
		}
		
		String beTarget = be.getTarget();
		final String infoFile = outputDir + File.separator + beTarget.substring(beTarget.lastIndexOf(File.separator) + 1) + EXT;
		Makefile.Target t = makefile.addTarget(infoFile, false);
		be.target.addOrderOnlyDependency(infoFile);
		
		// add all build files as dependendencies
		Set<SrcFile> buildFiles = new TreeSet<SrcFile>(ToStringComparator.instance);
		collectBuildFiles(be, buildFiles);
		for (SrcFile sf : buildFiles) {
			t.addDependency(sf.relative);
		}
		
		// string with all closure names
		String extlibs = ""; 
		for (LibDB.ExtLib el : be.extlibs) {
			extlibs += " " + el.name;
		}
		t.addCommand("echo '" + extlibs.trim() + "' > " + infoFile, false);
	}

	/**
	 * Collect build files of this build entity and all of its dependencies
	 * (recursive function)
	 * 
	 * @param be Current build entity
	 * @param buildFiles Set with results
	 */
	private void collectBuildFiles(BuildEntity be, Set<SrcFile> buildFiles) {
		buildFiles.add(be.buildFile);
		for (BuildEntity be2 : be.dependencies) {
			collectBuildFiles(be2, buildFiles);
		}
	}
}
