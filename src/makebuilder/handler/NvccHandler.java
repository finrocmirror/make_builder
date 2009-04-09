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
public class NvccHandler extends SourceFileHandler.Impl {

	/** Options for compiling */
	public final String compileOptions;
	
	/** Dependency buffer */
	private final TreeSet<SrcFile> dependencyBuffer = new TreeSet<SrcFile>(ToStringComparator.instance);
	
	/**
	 * @param compileOptions Standard compile options (included in every compile)
	 */
	public NvccHandler(String compileOptions) {
		this.compileOptions = compileOptions;
	}

	@Override
	public void init(Makefile makefile) {
		makefile.addVariable("NVCC_OPTS=" + compileOptions);
	}
	
	@Override
	public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
		if (file.hasExtension("cu")) {
			if (!file.isInfoUpToDate()) {
				CppHandler.processIncludes(file, scanner);
			}
			file.resolveDependencies(true);
		}
	}
	
	@Override
	public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) {
		
		// create nvcc compiler options
		CCOptions options = new CCOptions();
		options.merge(be.opts);
		options.compileOptions.add("$(NVCC_OPTS)");
		for (SrcDir path : be.getRootDir().defaultIncludePaths) {
			options.includePaths.add(path.relative);
		}
		
		// compile...
		ArrayList<SrcFile> copy = new ArrayList<SrcFile>(be.sources);
		for (SrcFile sf : copy) {
			if (sf.hasExtension("cu")) {
				SrcFile ofile = builder.getTempBuildArtifact(sf, "o");
				Makefile.Target target = makefile.addTarget(ofile.relative, false);
				be.sources.remove(sf);
				be.sources.add(ofile);
				dependencyBuffer.clear();
				target.addDependencies(sf.getAllDependencies(dependencyBuffer));
				target.addCommand("nvcc -c -o " + ofile.relative + " " + sf.relative + " " + options.createCudaString(), true);
			}
		}
	}
}
