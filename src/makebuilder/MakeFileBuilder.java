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

	/** Cache for h files */
	protected final SourceScanner sources;

	/** Temporary directory for merged files */
	private final String TEMPDIR = "/tmp/mbuild_" + Util.whoami();

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
		makefile.writeTo(new File("Makefile"));

		// print error messages at the end... so nobody will miss them
		for (String err : errorMessages) {
			System.err.println(err);
		}
		
		// completed
		System.out.println("Creating Makefile successful.");
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
	
	// legacy code...
//	private void temp() {
//		
//		// init paths
//		String rootDir = relativeLoc(be.rootDir);
//		String tempBuildPath = tempBuildPathBase + FS + rootDir;
//		String target = relativeLoc(be.getTarget());
//
//		// init targets
//		Makefile.Target finalTargetSafe = makeSafe.addTarget(target);
//		Makefile.Target finalTargetFast = makeFast.addTarget(target);
//		finalTargetSafe.addDependency("init");
//		finalTargetFast.addDependency("init");
//		
//		// add to categories
//		boolean tool = be.categories.contains("tools");
//		for (String cat : be.categories) {
//			finalTargetSafe.addToPhony(cat, tool ? new String[]{} : new String[]{"tools"});
//			finalTargetFast.addToPhony(cat, tool ? new String[]{} : new String[]{"tools"});
//		}
//
//		// collection of relevant header files regarding uic, description_buidler etc.
//		List<SrcFile> hs = new ArrayList<SrcFile>();
//		
//		// process/collect seperately specified headers
//		for (String h : be.hs) {
//			if (!h.toLowerCase().contains(".h")) { // only .h files are relevant
//				continue;
//			}
//			File hfile = new File(rootDir + FS + h);
//			String hdir = hfile.getParent();
//			SrcFile hf = hCache.find(h, hdir);
//			hs.add(hf);
//			if (hf == null) {
//				throw new RuntimeException("Could not find " + hfile);
//			}
//		}
//
//		// in safe-mode: all uic/descriptionbuilder/... output is placed in these
//		String tempFile = tempBuildPath + FS + be.name + "temp.o";
//		String tempFileCpp = tempBuildPath + FS + be.name + "temp.cpp";
//		
//		// safe-mode: String with files to link
//		String linkFiles = "";
//		
//		// generate compiler options
//		String gccopts = " $(CFLAGS) $(STD_OPTS) -I$(BUILD_DIR)/" + rootDir + " " + be.opts + " ";
//		String nvccopts = " $(NVCC_OPTS) -I$(BUILD_DIR)/" + rootDir + " " + be.opts + " ";
//		String addOpts = "";
//
//		// add all parent directories to include path
//		String rootDirTmp = rootDir;
//		while(rootDirTmp.contains(FS)) {
//			addOpts += "-I" + rootDirTmp + " ";
//			rootDirTmp = rootDirTmp.substring(0, rootDirTmp.lastIndexOf(FS));
//		}
//		
//		// extra flags for .so files
//		if (!be.compileToExecutable()) {
//			gccopts += "-shared -fPIC ";
//		}
//
//		// add/process MCA2 dependencies
//		for (BuildEntity s : be.dependencies) {
//			finalTargetSafe.addDependency(relativeLoc(s.getTarget()));
//			finalTargetFast.addDependency(relativeLoc(s.getTarget()));
//			addOpts += "-l" + s.toString() + " ";
//		}
//		
//		// process external dependencies
//		for (LibDB.ExtLib s : be.extlibs) {
//			addOpts += s.options + " ";
//		}
//
//		// create different variants of compiler options
//		String addOptsCompileOnly = removeOptions(removeOptions(addOpts, "-l"), "-L");
//		String gccoptsLight = gccopts + addOptsCompileOnly;
//		nvccopts += addOptsCompileOnly;
//		gccopts += addOpts;
//        nvccopts = nvccopts.replace(" -fopenmp", "");
//
//		// for fast building
//		MakeFileBlacklist.Element blacklist = MakeFileBlacklist.getInstance().get(be.name); // blacklist entry
//		finalTargetFast.addDependency(be.sconscript);
//		String turboCompiles = ""; // files to compile in fast mode
//		String turboC = TEMPDIR + FS + be.name + ".c"; // name of merged .c file
//		String turboCpp = TEMPDIR + FS + be.name + ".cpp"; // name of merged .cpp file
//		
//		// do we need temporary c file?
//		boolean tmpC = be.cs.size() > 1; // 
//		if (tmpC) {
//			finalTargetFast.addCommand("echo \\/\\/ generated > " + turboC);
//			turboCompiles += turboC + " ";
//		}
//
//		// do we
//		boolean tmpCpp = false; // do we need temporary cpp file?
//		for ()
//		
//		
//		
//		// commands - in case we have more than a single file
//		List<String> mergeCommands = new ArrayList<String>();
//		
//		// command addition if blacklist says we want to replace #include with #import
//		String importString = (blacklist != null && blacklist.importMode) ? " | sed -e 's/#include \"/#import \"/'" : "";
//
//		/*mergeCommands.add("\techo \\/\\/ generated > " + turboCpp);
//		turboCompiles += turboCpp + " ";
//		String turboCompile = "";*/
//		
//		// compile c files
//		for (String c : be.cs) {
//			SourceCache.SrcFile cInfo = hCache.find(cAbs);
//			String raw = c.substring(0, c.lastIndexOf(".")); // raw filename
//			String o = tempBuildPath + FS + raw + ".o"; // compiled filename
//			String cAbs = rootDir + FS + c; // source filename
//			
//			// add stuff to appropriate places in Makefiles
//			Makefile.Target ct = makeSafe.addTarget(o); 
//			addDependencies(cInfo, ct);
//			finalTargetSafe.addDependency(o);
//			linkFiles += o + " ";
//			ct.addCommand("mkdir -p " + new File(cAbs).getParent());
//			ct.addCommand("$(CC) -c " + cAbs + " -o " + o + " " + gccoptsLight);
//			
//			// complete dependencies
//			getAllDeps(cInfo, hs);
//
//			// fast makefile
//			if (blacklist == null || !blacklist.contains(c)) { // typical case
//				if (!tmpC) {
//					mergeCommands.add("echo \\/\\/ generated > " + turboC);
//					tmpC = true;
//					turboCompiles += turboC + " ";
//				}
//				mergeCommands.add("@echo \\#line 1 \\\"" + cAbs + "\\\" >> " + turboC);
//				mergeCommands.add("cat " + cAbs + importString + " >> " + turboC);
//				mergeCommands.add("@echo \\#undef LOCAL_DEBUG >> " + turboC);
//				mergeCommands.add("@echo \\#undef MODULE_DEBUG >> " + turboC);
//			} else {
//				turboCompiles += cAbs + " "; // compile separately
//			}
//			turboDeps += cAbs + " ";
//			turboCompile = cAbs;
//
//			if (c.contains(FS)) {
//				String cPath = "-I" + cAbs.substring(0, cAbs.lastIndexOf(FS)) + " ";
//				if (!gccopts.contains(cPath)) {
//					gccopts += cPath;
//					gccoptsLight += cPath;
//				}
//			}
//		}
//
//		// compile cpp files
//		for (String c : be.cpps) {
//			String raw = c.substring(0, c.lastIndexOf("."));
//			String o = tempBuildPath + FS + raw + ".o";
//			String cAbs = rootDir + FS + c;
//			String cFirst = o + " : " + cAbs;
//			firstLine += o + " ";
//			secondLine += o + " ";
//			cFirst += getHDeps(hs, c, be);
//			cb.add(cFirst);
//			mkdir(cb, o);
//			cb.add("\t$(CC) -c " + cAbs + " -o " + o + " " + gccoptsLight);
//
//			// turbo
//			if (blacklist == null || !blacklist.contains(c)) {
//				turboCb.add("\t@echo \\#line 1 \\\"" + cAbs + "\\\" >> " + turboCpp);
//				turboCb.add("\tcat " + cAbs + importString + " >> " + turboCpp);
//				//turboCb.add("\techo \\#undef LOCAL_DEBUG\\\\n\\#undef MODULE_DEBUG >> " + turboCpp);
//				turboCb.add("\t@echo \\#undef LOCAL_DEBUG >> " + turboCpp);
//				turboCb.add("\t@echo \\#undef MODULE_DEBUG >> " + turboCpp);
//			} else {
//				turboCompiles += cAbs + " ";
//			}
//			turboDeps += cAbs + " ";
//			turboCompile = cAbs;
//
//			if (c.contains(FS)) {
//				String cPath = "-I" + cAbs.substring(0, cAbs.lastIndexOf(FS)) + " ";
//				if (!gccopts.contains(cPath)) {
//					gccopts += cPath;
//					gccoptsLight += cPath;
//				}
//			}
//		}
//
//		// include precompiled object files
//		for (String o : be.os) {
//			String o2 = rootDir + FS + o + " ";
//			firstLine += o2;
//			secondLine += o2;
//			turboCompiles += o2;
//			turboDeps += o2;
//			turboCompile = o2;
//		}
//
//		// compile CUDA files
//		for (String c : be.cudas) {
//			String raw = c.substring(0, c.lastIndexOf("."));
//			String o = tempBuildPath + FS + raw + ".o";
//			String cAbs = rootDir + FS + c;
//			String cFirst = o + " : " + cAbs;
//			firstLine += o + " ";
//			secondLine += o + " ";
//			cFirst += getHDeps(hs, c, be);
//			cb.add(cFirst);
//			mkdir(cb, o);
//			//cb.add("\t/usr/local/cuda/bin/nvcc -c -D_DEBUG " + cAbs + " -o " + o);
//			//cb.add("\tnvcc -c -D_DEBUG " + cAbs + " -o " + o + " " + nvccopts);
//            cb.add("\tnvcc -c " + cAbs + " -o " + o + " " + nvccopts);
//
//			// turbo
//			makefileT.add(cFirst);
//			mkdir(makefileT, o);
//			//makefileT.add("\t/usr/local/cuda/bin/nvcc -c -D_DEBUG " + cAbs + " -o " + o);
//			//makefileT.add("\tnvcc -c -D_DEBUG " + cAbs + " -o " + o + " " + nvccopts);
//            makefileT.add("\tnvcc -c " + cAbs + " -o " + o + " " + nvccopts);
//			turboDeps += o + " ";
//			turboCompiles += o + " ";
//		}
//
//
//		// create temp file containing generated code
//		CodeBlock hdr2 = new CodeBlock();
//		hdr2.add("\tmkdir -p " + tempBuildPath);
//		hdr2.add("\techo \\/\\* \\*\\/ > " + tempFileCpp);
//
//		// process libs
//		if (blacklist != null && blacklist.linkLibs) {
//			CodeBlock link = new CodeBlock();
//			link.add("\tln -s -f " + be.rootDir.getAbsolutePath() + FS + "*.so " + targetLib);
//			hdr2.add(link);
//			turboCb.add(link);
//		}
//
//		CodeBlock hdr = new CodeBlock();
//		hdr.add(firstLine);
//		hdr.add("\tmkdir -p " + target.substring(0, target.lastIndexOf("/")));
//		boolean generatedPrinted = false;
//		for (SrcFile.HFile inc : hs) {
//			// perform necessary operations on h files
//			if (includeGeneratedCode(inc, be.cpps, rootDir, be)) {
//				if (!generatedPrinted) {
//					turboCb.add("\techo \\#line `cat " + turboCpp + " | wc -l` \\\"" + turboCpp + "\\\" >> " + turboCpp);
//					generatedPrinted = true;
//				}
//				if (!inc.template && inc.descr) {
//					hdr2.add("\t" + descrBuilderBin + inc.relFile + " >> " + tempFileCpp);
//					turboCb.add("\t" + descrBuilderBin + inc.relFile + importString + " >> " + turboCpp);
//				}
//				if (inc.moc) {
//					String qtCall = LibDB.getLib("moc-qt4").options.trim();
//					String call = qtCall + " " + inc.relFile;
//					hdr2.add("\t" + call + " >> " + tempFileCpp);
//					turboCb.add("\t" + call + importString + " >> " + turboCpp);
//					//hdr2.add("\t" + descrBuilderBinExt + inc.relFile + " >> " + tempFileCpp);
//				}
//			}
//
//			turboDeps += inc.relFile + " ";
//		}
//		hdr2.add("\t$(CC) -c -I. " + tempFileCpp + " -o " + tempFile + " " + gccoptsLight);
//		if (hdr2.size() > 3) {
//			hdr.add(hdr2);
//		}
//		hdr.add("\t$(CC) -o " + target + (hdr2.size() > 3 ? " " + tempFile : "") + " " + secondLine + " " + gccopts + " -lc -lm -lz -lcrypt -lpthread -lstdc++ -Wl,-rpath," + targetLib + be.getLinkerOpts() + " ");
//		cb.add(0,hdr);
//		cb.add("");
//		makefile.add(cb);
//
//		// turbo
//		if (countCat(turboCb) > 1 || turboCompiles.trim().split(" ").length > 1) {
//			turboCb.add(0, turboDeps);
//			mkdir(turboCb, target);
//			turboCb.add("\t$(CC) -o " + target + " " + turboCompiles + " " + gccopts + " -lc -lm -lz -lcrypt -lpthread -lstdc++ -Wl,-rpath," + targetLib + be.getLinkerOpts() + " ");
//		} else {
//			turboCb.clear();
//			turboCb.add(turboDeps);
//			mkdir(turboCb, target);
//			turboCb.add("\t$(CC) -o " + target + " " + turboCompile + " " + gccopts + " -lc -lm -lz -lcrypt -lpthread -lstdc++ -Wl,-rpath," + targetLib + be.getLinkerOpts() + " ");
//		}
//	}

//	/**
//	 * Remove options from options string
//	 *
//	 * @param original Original string
//	 * @param option Options with this prefix are removed (e.g. -L)
//	 * @return New String
//	 */
//	private String removeOptions(String original, String option) {
//		String opt = " " + option;
//		while (original.contains(opt)) {
//			String temp1 = original.substring(0, original.indexOf(opt));
//			String temp2 = original.substring(original.indexOf(opt) + 3);
//			if (temp2.contains(" ")) {
//				original = temp1 + temp2.substring(temp2.indexOf(" "));
//			} else {
//				original = temp1;
//			}
//		}
//		return original;
//	}
//
//	private int countCat(CodeBlock turboCb) {
//		int result = 0;
//		for (Object s : turboCb) {
//			if (s.toString().startsWith("\techo") || s.toString().startsWith("\t@echo") || s.toString().startsWith("\tmkdir")) {
//				continue;
//			}
//			result++;
//		}
//		return result;
//	}
//
//	public static String mkdir(String h) {
//		return "mkdir -p " + h.substring(0, h.lastIndexOf(FS));
//	}
//
//	boolean includeGeneratedCode(SrcFile inc, List<String> sources, String rootDir, BuildEntity be) {
//		if (!(inc.moc || inc.descr)) {
//			return false;
//		}
//		if (inc.template) {
//			return true;
//		}
//		if (inc.relFile.endsWith("MCACommunication.h")) {
//			return be.name.equals("kernel");
//		}
//		if (inc.relFile.startsWith(rootDir)) {
//			String s = inc.relFile.substring(rootDir.length() + 1);
//			String cpps = s.substring(0, s.lastIndexOf(".")) + ".cpp";
//			File cpp = new File(be.rootDir + FS + cpps);
//			if (!cpp.exists()) {
//				return true;
//			}
//			return sources.contains(cpps);
//		}
//		if (inc.relFile.startsWith(tempBuildPathBase + FS + rootDir)) {
//			return true;
//		}
//		return false;
//	}

//	private String getHDeps(List<SourceCache.HFile> includes, String source, BuildEntity be) throws Exception {
//		String result = " ";
//		String source2 = be.rootDir + FS + source;
//		String curDir = source2.substring(rootlen);
//		curDir = curDir.substring(0, curDir.lastIndexOf(FS));
//		includes2.clear();
//
//		List<String> x = Util.readLinesWithoutComments(new File(source2), false);
//		for (String s : x) {
//			if (s == null) {
//				break;
//			}
//			SourceCache.HFile include = hCache.getInclude(s, curDir);
//			if (include != null) {
//				result += includeAll(include, includes) + " ";
//			}
//		}
//
//		return result;
//	}
//
//	/** Temporary list for function below */
//	private final List<SrcFile> tempList = new ArrayList<SrcFile>();
//	
//	/**
//	 * Add dependencies of single source file (including this file) to Makefile target
//	 * 
//	 * @param sourceFile Source file
//	 * @param target Makefile target
//	 */
//	private void addDependencies(SrcFile sourceFile, Makefile.Target target) {
//		tempList.clear();
//		getAllDeps(sourceFile, tempList);
//		for (SrcFile file : tempList) {
//			target.addDependency(file.relFile);
//		}
//	}
//	
//	/**
//	 * Get all includes for a source file - but at most once
//	 * (recursive function)
//	 * 
//	 * @param sourceFile Source file
//	 * @param deps List with dependencies (may already be partly full) 
//	 */
//	private void getAllDeps(SrcFile sourceFile, List<SrcFile> deps) {
//		if (!deps.contains(sourceFile)) {
//			deps.add(sourceFile);
//
//			for (SrcFile hfile : sourceFile.includes) {
//				getAllDeps(hfile, deps);
//			}
//		}
//	}

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
}
