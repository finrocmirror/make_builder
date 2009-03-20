package tools.turbobuilder;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import tools.turbobuilder.MakeFileHCache.HFile;

/**
 * @author max
 *
 * Main class for Building makefiles
 */
public class MakeFileBuilder implements FilenameFilter {

	/** Input Character set */
	static final Charset INPUT_CHARSET = Charset.forName("ISO-8859-1");

	/** Current working directory - should be $MCAHOME */
	static final File HOME = new File(".").getAbsoluteFile().getParentFile();

	/** Shortcut to file separator */
	private static final String FS = File.separator;

	/** Libraries and executables that need to be built */
	private final List<BuildEntity> buildEntities = new ArrayList<BuildEntity>();

	/** Options from command line */
	Options opts;

	/** build path where compiled binaries are placed */
	File buildPath;

	/** Whole makefile */
	private CodeBlock makefile = new CodeBlock();

	/** Start of makefile */
	private CodeBlock firstBlock = new CodeBlock();

	/** Global definitions */
	private CodeBlock globalDefine = new CodeBlock();

	/** "Turbo" version of makefile */
	private CodeBlock makefileT = new CodeBlock();

	/** temporary build path where .o files are placed */
	private String tempBuildPathBase;

	/** Length of $MCAHOME */
	private int rootlen;

	/** Description builder script */
	private final String descrBuilderBin = "script/description_builder.pl ";

	/** Target base directory, Target directory for libraries, Target directory for binaries */
	String targetBase, targetLib, targetBin;

	/** Categorization of make targets (Target/Category => dependencies) */
	private SortedMap<String, List<String>> categories = new TreeMap<String, List<String>>();

	/** Cache for h files */
	private MakeFileHCache hCache;

	/** Build "turbo" makefile (where sources are merged in one file before compiling) */
	private final boolean turbo;

	/** Temporary directory for merged files */
	private final String TEMPDIR = "/tmp/mbuild_" + Util.whoami();

	/** Standard compiler options for MCA */
	public static final String MCAOPTS = "-include Makefile.h -Ilibraries -Iprojects -Itools -I. ";

	/** Error message for console - are collected and presented at the end */
	private final List<String> errorMessages = new ArrayList<String>();

	public static void main(String[] args) {

		// Call updateLibDb (?)
		if (args.length > 0 && args[0].equals("--updatelibs")) {
			LibDBBuilder.main(args);
			System.exit(0);
		}

		// Parse command line options
		Options options = new Options(args);

		try {
			new MakeFileBuilder(options).build();
		} catch (Exception e) {
			e.printStackTrace();
			printErrorAdvice();
		}
	}

	/**
	 * @param opts Command line options
	 */
	public MakeFileBuilder(Options opts) {
		this.opts = opts;

		makefile.add(firstBlock);
		tempBuildPathBase = "build" + FS + opts.build;
		rootlen = HOME.getAbsolutePath().length() + 1;

		globalDefine.add("#define _MCA_VERSION_ \"2.4.1\"");
		globalDefine.add("#define _MCA_DEBUG_");
		globalDefine.add("#define _MCA_PROFILING_");
		globalDefine.add("#define _MCA_LINUX_");

		turbo = opts.combineCppFiles;

		buildPath = new File(HOME.getAbsolutePath() + FS + "export" + FS + opts.build);
	}

	/** Create makefile */
	private void build() throws Exception {

		// Parse Scons scripts
		System.out.println("Parsing SCons scripts...");
		//String projectsRoot = HOME.getAbsolutePath() + FS + "projects";
		for (File sconscript : Files.getAllFiles(HOME, this, false, false)) {
			if (sconscript.getParentFile().getName().equals("kernel.original")) {
				continue;
			}
			try {
				// only parse SConsripts in directories tools, projects and libraries
				String relative = sconscript.getAbsolutePath().substring(HOME.getAbsolutePath().length() + 1);
				if ((!relative.startsWith("libraries"))  && (!relative.startsWith("projects")) && (!relative.startsWith("tools"))) {
					continue;
				}

				Collection<BuildEntity> temp = SConscript.parse(sconscript, this);
				buildEntities.addAll(temp);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// process dependencies
		System.out.println("Processing dependencies...");
		for (BuildEntity be : buildEntities) {
			for (int i = 0; i < be.libs.size(); i++) {
				String lib = be.libs.get(i);
				try {
					processDependency(be, lib, false);
				} catch (Exception e) {
					printErrorLine(e.getMessage());
					be.missingDep = true;
				}
			}
			for (int i = 0; i < be.optionalLibs.size(); i++) {
				String lib = be.optionalLibs.get(i);
				processDependency(be, lib, true);
			}
		}

		for (BuildEntity be : buildEntities) {
			// check whether all dependencies are met
			be.checkDepencies();
		}

		// add available optional libs
		for (BuildEntity be : buildEntities) {
			be.addOptionalLibs();
		}

		// add available optional libs
		for (BuildEntity be : buildEntities) {
			// collect external libraries needed for building
			be.mergeExtLibs();
		}

		// create additional defines
		for (BuildEntity be : buildEntities) {
			if (!be.missingDep && be instanceof MCALibrary) {
				// _LIB_MCA2_COMPUTER_VISION_BASE_PRESENT_
				addDefine("_LIB_MCA2_" + be.name.toUpperCase() + "_PRESENT_");
			}
		}

		// build entities
		for (BuildEntity be : buildEntities) {
			if (be.missingDep || ((!opts.compileTestParts) && be.isTestPart())) {
				continue;
			}
			build(be, false);
		}

		// Write makefile
		storeMakefile();

		// completed
		System.out.println("Creating Makefile successful.");
	}

	/**
	 * Process single dependency of build entity
	 *
	 * @param be Build entity
	 * @param lib Library name of dependency
	 * @param optional Optional dependency
	 */
	private void processDependency(BuildEntity be, String lib, boolean optional) throws Exception {
		if (!lib.startsWith("mca2_")) {
			if (!LibDB.available(lib)) {
				if (optional) {
					return;
				}
				throw new Exception("Not building " + be.name + " due to missing library " + lib);
			}
			LibDB.ExtLib x = LibDB.getLib(lib);
			be.extlibs.add(x);
			be.libs.addAll(x.dependencies);
			//be.qt3 |= lib.equals("qt");
			//be.qt4 |= lib.startsWith("qt4");
			return;
		}
		boolean found = false;
		for (BuildEntity be2 : buildEntities) {
			if ((be2 instanceof MCALibrary) && be2.toString().equals(lib)) {
				if (!optional) {
					be.dependencies.add(be2);
				} else {
					be.optionalDependencies.add(be2);
				}
				found = true;
				break;
			}
		}
		if (!found && (!optional)) {
			throw new Exception("Not building " + be.name + " due to missing MCA library " + lib);
		}
	}

	/**
	 * Create and save Makefile
	 */
	private void storeMakefile() throws Exception {

		// write makefile
		String phony = ".PHONY: clean";
		for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
			phony += " " + entry.getKey();
			String deps = "";
			for (String dep : entry.getValue()) {
				deps += " " + dep;
			}
			if (entry.getKey().equals("all")) {
				firstBlock.add(0, "");
				firstBlock.add(1, "all :" + deps);
				firstBlock.add(2, "\t@echo done");
			} else if (entry.getKey().equals("tools") || entry.getKey().equals("mcabrowser") || entry.getKey().equals("mcagui")) {
				firstBlock.add("");
				firstBlock.add(entry.getKey() + " :" + deps);
				firstBlock.add("\t@echo done");
			} else {
				firstBlock.add("");
				firstBlock.add(entry.getKey() + " : tools" + deps);
				firstBlock.add("\t@echo done");
			}
		}
		firstBlock.add(0, phony);
		firstBlock.add("");
		firstBlock.add("clean:");
        firstBlock.add("\trm -R -f " + tempBuildPathBase);
        firstBlock.add("\trm -R -f " + targetBase);
        CodeBlock preFirstBlock = new CodeBlock();
        firstBlock.add(0, preFirstBlock);
		preFirstBlock.add("CFLAGS=-g2\n");
		preFirstBlock.add("CC=gcc\n");
		preFirstBlock.add("BUILD_DIR=" + tempBuildPathBase + "\n");
		preFirstBlock.add("NVCC_OPTS=-L" + targetLib + " " + MCAOPTS + " " + "-I$(BUILD_DIR)/libraries -I$(BUILD_DIR)/projects\n");
		preFirstBlock.add("STD_OPTS=-Wall -Wwrite-strings -Wno-unknown-pragmas $(NVCC_OPTS)\n");
		if (!turbo) {
			makefile.writeTo(new File("Makefile"));
		} else {
			makefileT.writeTo(new File("Makefile"));
		}

		// write config file
		globalDefine.add("");
		globalDefine.writeTo(new File("Makefile.h"));

		// print error messages at the end... so nobody will miss them
		for (String err : errorMessages) {
			System.err.println(err);
		}
	}

	private void init(File targetFile) throws Exception {
		if (hCache == null) {
			targetBase = targetFile.getParentFile().getParent().substring(rootlen);
			targetBin = targetBase + FS + "script";
			targetLib = targetBase + FS + "lib";

			// cache h files
			System.out.println("Caching local .h-files");
			hCache = new MakeFileHCache(HOME.getAbsolutePath(), makefile, descrBuilderBin, descrBuilderBin, tempBuildPathBase);
			makefile.add("");
			makefileT.addAll(makefile);  // duplicate
		}
	}

	private void build(final BuildEntity be, final boolean immediately) throws Exception {

		System.out.println("Processing " + be.name);

		// init paths
		File targetFile = be.getTarget();
		init(targetFile);

		String rootDir = be.rootDir.getAbsolutePath().substring(rootlen);
		String tempBuildPath = tempBuildPathBase + FS + rootDir;
		String target = targetFile.getAbsolutePath().substring(rootlen);

		CodeBlock cb = new CodeBlock();

		// add to categories
		for (String cat : be.categories) {
			List<String> catList = categories.get(cat);
			if (catList == null) {
				catList = new ArrayList<String>();
				categories.put(cat, catList);
			}
			catList.add(target);
		}

		// process entity
		List<MakeFileHCache.HFile> hs = new ArrayList<MakeFileHCache.HFile>();
		for (String h : be.hs) {
			// might appear slightly confusing...
			if (!h.toLowerCase().contains(".h")) {
				continue;
			}
			String source2 = be.rootDir + FS + h;
			String curDir = source2.substring(rootlen);
			curDir = curDir.substring(0, curDir.lastIndexOf(FS));
			MakeFileHCache.HFile hf = hCache.findInclude(h, curDir);
			hs.add(hf);
			if (hf == null) {
				throw new RuntimeException("Could not find " + source2);
			}
		}

		String tempFile = tempBuildPath + FS + be.name + "temp.o";
		String tempFileCpp = tempBuildPath + FS + be.name + "temp.cpp";
		String firstLine = target + " : ";

		String secondLine = "";

		// generate compiler options
		/*String addOpts = "-I" + tempBuildPathBase + FS + "libraries -I" + tempBuildPathBase + FS + "projects -I" + tempBuildPathBase + FS + rootDir;
		String gccopts = " $(CFLAGS) -Wall -Wwrite-strings -Wno-unknown-pragmas -L" + targetLib + " " + MCAOPTS + " " + addOpts + " " + be.opts + " ";*/
		String gccopts = " $(CFLAGS) $(STD_OPTS) -I$(BUILD_DIR)/" + rootDir + " " + be.opts + " ";
		String nvccopts = " $(NVCC_OPTS) -I$(BUILD_DIR)/" + rootDir + " " + be.opts + " ";
		String addOpts = "";

		String rootDirTmp = rootDir;
		while(rootDirTmp.contains(FS)) {
			addOpts += "-I" + rootDirTmp + " ";
			rootDirTmp = rootDirTmp.substring(0, rootDirTmp.lastIndexOf(FS));
		}
		if (!be.compileToExecutable()) {
			gccopts += "-shared -fPIC ";
		}

		for (BuildEntity s : be.dependencies) {
			firstLine += s.getTarget().getAbsolutePath().substring(rootlen) + " ";
			addOpts += "-l" + s.toString() + " ";
		}
		for (LibDB.ExtLib s : be.extlibs) {
			addOpts += s.options + " ";
		}

		// create different variants of compiler options
		String addOptsCompileOnly = removeOptions(removeOptions(addOpts, "-l"), "-L");
		String gccoptsLight = gccopts + addOptsCompileOnly;
		nvccopts += addOptsCompileOnly;
		gccopts += addOpts;
        nvccopts = nvccopts.replace(" -fopenmp", "");

		// for turbo
		MakeFileBlacklist.Element blacklist = MakeFileBlacklist.getInstance().get(be.name);
		CodeBlock turboCb = new CodeBlock();
		makefileT.add(turboCb);
		boolean tmpC = false;
		String turboDeps = firstLine + be.sconscript + " ";
		String turboCompiles = "";
		String turboC = TEMPDIR + FS + be.name + ".c";
		String turboCpp = TEMPDIR + FS + be.name + ".cpp";

		mkdir(turboCb, turboCpp);
		turboCb.add("\techo \\/\\/ generated > " + turboCpp);
		turboCompiles += turboCpp + " ";
		String turboCompile = "";

		String importString = (blacklist != null && blacklist.importMode) ? " | sed -e 's/#include \"/#import \"/'" : "";

		// compile c files
		for (String c : be.cs) {
			String raw = c.substring(0, c.lastIndexOf("."));
			String o = tempBuildPath + FS + raw + ".o";
			String cAbs = rootDir + FS + c;
			String cFirst = o + " : " + cAbs;
			firstLine += o + " ";
			secondLine += o + " ";
			cFirst += getHDeps(hs, c, be);
			cb.add(cFirst);
			mkdir(cb, o);
			cb.add("\t$(CC) -c " + cAbs + " -o " + o + " " + gccoptsLight);

			// turbo
			if (blacklist == null || !blacklist.contains(c)) {
				if (!tmpC) {
					mkdir(turboCb, turboC);
					turboCb.add("\techo \\/\\/ generated > " + turboC);
					tmpC = true;
					turboCompiles += turboC + " ";
				}
				turboCb.add("\t@echo \\#line 1 \\\"" + cAbs + "\\\" >> " + turboC);
				//turboCb.add("\tcat " + cAbs + " | sed -e 's/#include \"/#import \"/' >> " + turboC);
				turboCb.add("\tcat " + cAbs + importString + " >> " + turboC);
				//turboCb.add("\techo \\#undef LOCAL_DEBUG\\\\n\\#undef MODULE_DEBUG >> " + turboC);
				turboCb.add("\t@echo \\#undef LOCAL_DEBUG >> " + turboC);
				turboCb.add("\t@echo \\#undef MODULE_DEBUG >> " + turboC);
			} else {
				turboCompiles += cAbs + " ";
			}
			turboDeps += cAbs + " ";
			turboCompile = cAbs;

			if (c.contains(FS)) {
				String cPath = "-I" + cAbs.substring(0, cAbs.lastIndexOf(FS)) + " ";
				if (!gccopts.contains(cPath)) {
					gccopts += cPath;
					gccoptsLight += cPath;
				}
			}
		}

		// compile cpp files
		for (String c : be.cpps) {
			String raw = c.substring(0, c.lastIndexOf("."));
			String o = tempBuildPath + FS + raw + ".o";
			String cAbs = rootDir + FS + c;
			String cFirst = o + " : " + cAbs;
			firstLine += o + " ";
			secondLine += o + " ";
			cFirst += getHDeps(hs, c, be);
			cb.add(cFirst);
			mkdir(cb, o);
			cb.add("\t$(CC) -c " + cAbs + " -o " + o + " " + gccoptsLight);

			// turbo
			if (blacklist == null || !blacklist.contains(c)) {
				turboCb.add("\t@echo \\#line 1 \\\"" + cAbs + "\\\" >> " + turboCpp);
				turboCb.add("\tcat " + cAbs + importString + " >> " + turboCpp);
				//turboCb.add("\techo \\#undef LOCAL_DEBUG\\\\n\\#undef MODULE_DEBUG >> " + turboCpp);
				turboCb.add("\t@echo \\#undef LOCAL_DEBUG >> " + turboCpp);
				turboCb.add("\t@echo \\#undef MODULE_DEBUG >> " + turboCpp);
			} else {
				turboCompiles += cAbs + " ";
			}
			turboDeps += cAbs + " ";
			turboCompile = cAbs;

			if (c.contains(FS)) {
				String cPath = "-I" + cAbs.substring(0, cAbs.lastIndexOf(FS)) + " ";
				if (!gccopts.contains(cPath)) {
					gccopts += cPath;
					gccoptsLight += cPath;
				}
			}
		}

		// include precompiled object files
		for (String o : be.os) {
			String o2 = rootDir + FS + o + " ";
			firstLine += o2;
			secondLine += o2;
			turboCompiles += o2;
			turboDeps += o2;
			turboCompile = o2;
		}

		// compile CUDA files
		for (String c : be.cudas) {
			String raw = c.substring(0, c.lastIndexOf("."));
			String o = tempBuildPath + FS + raw + ".o";
			String cAbs = rootDir + FS + c;
			String cFirst = o + " : " + cAbs;
			firstLine += o + " ";
			secondLine += o + " ";
			cFirst += getHDeps(hs, c, be);
			cb.add(cFirst);
			mkdir(cb, o);
			//cb.add("\t/usr/local/cuda/bin/nvcc -c -D_DEBUG " + cAbs + " -o " + o);
			//cb.add("\tnvcc -c -D_DEBUG " + cAbs + " -o " + o + " " + nvccopts);
            cb.add("\tnvcc -c " + cAbs + " -o " + o + " " + nvccopts);

			// turbo
			makefileT.add(cFirst);
			mkdir(makefileT, o);
			//makefileT.add("\t/usr/local/cuda/bin/nvcc -c -D_DEBUG " + cAbs + " -o " + o);
			//makefileT.add("\tnvcc -c -D_DEBUG " + cAbs + " -o " + o + " " + nvccopts);
            makefileT.add("\tnvcc -c " + cAbs + " -o " + o + " " + nvccopts);
			turboDeps += o + " ";
			turboCompiles += o + " ";
		}


		// create temp file containing generated code
		CodeBlock hdr2 = new CodeBlock();
		hdr2.add("\tmkdir -p " + tempBuildPath);
		hdr2.add("\techo \\/\\* \\*\\/ > " + tempFileCpp);

		// process libs
		if (blacklist != null && blacklist.linkLibs) {
			CodeBlock link = new CodeBlock();
			link.add("\tln -s -f " + be.rootDir.getAbsolutePath() + FS + "*.so " + targetLib);
			hdr2.add(link);
			turboCb.add(link);
		}

		CodeBlock hdr = new CodeBlock();
		hdr.add(firstLine);
		hdr.add("\tmkdir -p " + target.substring(0, target.lastIndexOf("/")));
		boolean generatedPrinted = false;
		for (MakeFileHCache.HFile inc : hs) {
			// perform necessary operations on h files
			if (includeGeneratedCode(inc, be.cpps, rootDir, be)) {
				if (!generatedPrinted) {
					turboCb.add("\techo \\#line `cat " + turboCpp + " | wc -l` \\\"" + turboCpp + "\\\" >> " + turboCpp);
					generatedPrinted = true;
				}
				if (!inc.template && inc.descr) {
					hdr2.add("\t" + descrBuilderBin + inc.relFile + " >> " + tempFileCpp);
					turboCb.add("\t" + descrBuilderBin + inc.relFile + importString + " >> " + turboCpp);
				}
				if (inc.moc) {
					String qtCall = LibDB.getLib("moc-qt4").options.trim();
					String call = qtCall + " " + inc.relFile;
					hdr2.add("\t" + call + " >> " + tempFileCpp);
					turboCb.add("\t" + call + importString + " >> " + turboCpp);
					//hdr2.add("\t" + descrBuilderBinExt + inc.relFile + " >> " + tempFileCpp);
				}
			}

			turboDeps += inc.relFile + " ";
		}
		hdr2.add("\t$(CC) -c -I. " + tempFileCpp + " -o " + tempFile + " " + gccoptsLight);
		if (hdr2.size() > 3) {
			hdr.add(hdr2);
		}
		hdr.add("\t$(CC) -o " + target + (hdr2.size() > 3 ? " " + tempFile : "") + " " + secondLine + " " + gccopts + " -lc -lm -lz -lcrypt -lpthread -lstdc++ -Wl,-rpath," + targetLib + be.getLinkerOpts() + " ");
		cb.add(0,hdr);
		cb.add("");
		makefile.add(cb);

		// turbo
		if (countCat(turboCb) > 1 || turboCompiles.trim().split(" ").length > 1) {
			turboCb.add(0, turboDeps);
			mkdir(turboCb, target);
			turboCb.add("\t$(CC) -o " + target + " " + turboCompiles + " " + gccopts + " -lc -lm -lz -lcrypt -lpthread -lstdc++ -Wl,-rpath," + targetLib + be.getLinkerOpts() + " ");
		} else {
			turboCb.clear();
			turboCb.add(turboDeps);
			mkdir(turboCb, target);
			turboCb.add("\t$(CC) -o " + target + " " + turboCompile + " " + gccopts + " -lc -lm -lz -lcrypt -lpthread -lstdc++ -Wl,-rpath," + targetLib + be.getLinkerOpts() + " ");
		}
	}

	/**
	 * Remove options from options string
	 *
	 * @param original Original string
	 * @param option Options with this prefix are removed (e.g. -L)
	 * @return New String
	 */
	private String removeOptions(String original, String option) {
		String opt = " " + option;
		while (original.contains(opt)) {
			String temp1 = original.substring(0, original.indexOf(opt));
			String temp2 = original.substring(original.indexOf(opt) + 3);
			if (temp2.contains(" ")) {
				original = temp1 + temp2.substring(temp2.indexOf(" "));
			} else {
				original = temp1;
			}
		}
		return original;
	}

	private int countCat(CodeBlock turboCb) {
		int result = 0;
		for (Object s : turboCb) {
			if (s.toString().startsWith("\techo") || s.toString().startsWith("\t@echo") || s.toString().startsWith("\tmkdir")) {
				continue;
			}
			result++;
		}
		return result;
	}

	public static void mkdir(CodeBlock cb, String h) {
		cb.add("\tmkdir -p " + h.substring(0, h.lastIndexOf(FS)));
	}

	boolean includeGeneratedCode(MakeFileHCache.HFile inc, List<String> sources, String rootDir, BuildEntity be) {
		if (!(inc.moc || inc.descr)) {
			return false;
		}
		if (inc.template) {
			return true;
		}
		if (inc.relFile.endsWith("MCACommunication.h")) {
			return be.name.equals("kernel");
		}
		if (inc.relFile.startsWith(rootDir)) {
			String s = inc.relFile.substring(rootDir.length() + 1);
			String cpps = s.substring(0, s.lastIndexOf(".")) + ".cpp";
			File cpp = new File(be.rootDir + FS + cpps);
			if (!cpp.exists()) {
				return true;
			}
			return sources.contains(cpps);
		}
		if (inc.relFile.startsWith(tempBuildPathBase + FS + rootDir)) {
			return true;
		}
		return false;
	}

	List<MakeFileHCache.HFile> includes2 = new ArrayList<MakeFileHCache.HFile>();

	private String getHDeps(List<MakeFileHCache.HFile> includes, String source, BuildEntity be) throws Exception {
		String result = " ";
		String source2 = be.rootDir + FS + source;
		String curDir = source2.substring(rootlen);
		curDir = curDir.substring(0, curDir.lastIndexOf(FS));
		includes2.clear();

		int y = 0;
		List<String> x = Util.readLinesWithoutComments(new File(source2), false);
		for (String s : x) {
			if (s == null) {
				break;
			}
			MakeFileHCache.HFile include = hCache.getInclude(s, curDir);
			if (include != null) {
				result += includeAll(include, includes) + " ";
			}
			y++;
		}

		return result;
	}

	private String includeAll(HFile include, List<HFile> includes) {
		String result = "";
		if (!includes2.contains(include)) {
			includes2.add(include);
			result += include.relFile + " ";
			if (!includes.contains(include)) {
				includes.add(include);
			}

			for (MakeFileHCache.HFile hfile : include.includes) {
				result += includeAll(hfile, includes) + " ";
			}
		}

		return result;
	}

	/**
	 * @param string Add define to list of global defines
	 */
	private void addDefine(String string) {
		globalDefine.add("#define " + string);
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
}
