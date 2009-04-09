package makebuilder.handler;

import java.util.ArrayList;
import java.util.TreeSet;

import makebuilder.BuildEntity;
import makebuilder.SourceFileHandler;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcDir;
import makebuilder.SrcFile;
import makebuilder.util.CCOptions;
import makebuilder.util.ToStringComparator;

/**
 * @author max
 *
 * Responsible for building executables and libraries from C/C++ source files
 */
public class CppHandler implements SourceFileHandler {

	/** Standard compile and linke options (included in every compile/link) */
	private final String compileOptions, linkOptions;
	
	/** Do compiling and linking separately (or rather in one gcc call)? */
	private final boolean separateCompileAndLink;
	
	/** Dependency buffer */
	private final TreeSet<SrcFile> dependencyBuffer = new TreeSet<SrcFile>(ToStringComparator.instance);
	
	/**
	 * @param compileOptions Standard compile options (included in every compile)
	 * @param linkOptions Standard linker options (in every link)
	 * @param separateCompileAndLink Do compiling and linking separately (or rather in one gcc call)?
	 */
	public CppHandler(String compileOptions, String linkOptions, boolean separateCompileAndLink) {
		this.compileOptions = compileOptions;
		this.linkOptions = linkOptions;
		this.separateCompileAndLink = separateCompileAndLink;
	}

	@Override
	public void init(Makefile makefile) {
		// add variables to makefile
		makefile.addVariable("CFLAGS=-g2");
		makefile.addVariable("CC=gcc");
		makefile.addVariable("CXX_OPTS=$(CFLAGS) " + compileOptions);
		makefile.addVariable("LINK_OPTS=" + linkOptions);
	}
	
	@Override
	public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner sources, MakeFileBuilder builder) {
		if (file.hasExtension("c", "cpp", "h", "hpp")) {
			if (!file.isInfoUpToDate()) {
				processIncludes(file, sources);
			}
			file.resolveDependencies(true);
		}
	}

	/**
	 * Find #include statements in C++ files and add dependencies to source files
	 * 
	 * @param file File to scan
	 * @param sources SourceScanner instance that contains possible includes
	 */
	public static void processIncludes(SrcFile file, SourceScanner sources) {
		for (String line : file.getCppLines()) {
			if (line.trim().startsWith("#")) {
				line = line.substring(1).trim();
				if (line.startsWith("include")) {
					line = line.substring(7).trim();
					if (line.startsWith("\"")) {
						line = line.substring(1, line.lastIndexOf("\""));
					} else if (line.startsWith("<")) {
						line = line.substring(1, line.indexOf(">"));
					} else {
						throw new RuntimeException("Error getting include string");
					}
					file.rawDependencies.add(line);
				}
			}
		}	
	}

	@Override
	public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) {

		// create C++ compiler options
		CCOptions options = new CCOptions();
		options.merge(be.opts);
		options.compileOptions.add("$(CXX_OPTS)");
		options.linkOptions.add("$(LINK_OPTS)");
		
		// find/prepare include paths
		for (SrcDir path : be.getRootDir().defaultIncludePaths) {
			options.includePaths.add(path.relative);
		}
		for (SrcFile sf : be.sources) { // add directories of all source files - not especially nice - but required for some MCA2 libraries
			if (sf.hasExtension("c", "cpp") && (!sf.dir.isTempDir())) {
				options.includePaths.add(sf.dir.relative);
			}
		}
		
		if (separateCompileAndLink) {
			ArrayList<SrcFile> copy = new ArrayList<SrcFile>(be.sources);
			
			// compile...
			for (SrcFile sf : copy) {
				if (sf.hasExtension("c", "cpp")) {
					SrcFile ofile = builder.getTempBuildArtifact(sf, "o");
					Makefile.Target target = makefile.addTarget(ofile.relative, true);
					be.sources.remove(sf);
					be.sources.add(ofile);
					dependencyBuffer.clear();
					target.addDependencies(sf.getAllDependencies(dependencyBuffer));
					target.addCommand(options.createCompileCommand(sf.relative, ofile.relative), true);
				}
			}
			
			// ... and link
			copy.clear();
			copy.addAll(be.sources);
			String sources = "";
			for (SrcFile sf : copy) {
				if (sf.hasExtension("o", "os")) {
					be.sources.remove(sf);
					be.target.addDependency(sf);
					sources += " " + sf.relative; 
				}
			}
			be.target.addCommand(options.createLinkCommand(sources, be.getTarget()), true);
			
		} else { // compiling and linking in one step
			ArrayList<SrcFile> copy = new ArrayList<SrcFile>(be.sources);
			dependencyBuffer.clear();
			
			String sources = "";
			for (SrcFile sf : copy) {
				if (sf.hasExtension("c", "cpp", "o", "os")) {
					be.sources.remove(sf);
					be.target.addDependency(sf);
					sources += " " + sf.relative;
					sf.getAllDependencies(dependencyBuffer);
				}
			}
			be.target.addCommand(options.createCompileAndLinkCommand(sources, be.getTarget()), true);
			be.target.addDependencies(dependencyBuffer);
		}
	}
}
