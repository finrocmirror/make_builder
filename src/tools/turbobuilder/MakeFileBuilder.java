package tools.turbobuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import tools.turbobuilder.MakeFileHCache.HFile;

/**
 * @author max
 *
 * Build makefile instead of compiling
 */
public class MakeFileBuilder extends TurboBuilder {

	CodeBlock makefile = new CodeBlock();
	CodeBlock firstBlock = new CodeBlock();
	CodeBlock globalDefine = new CodeBlock();

	// "Turbo" version of above
	CodeBlock makefileT = new CodeBlock();
	
	String tempBuildPathBase;  // temporary build path where .o files are placed
	int rootlen;
	
	String descrBuilderBin;
	String descrBuilderBinExt;
	String targetBase, targetLib, targetBin;
	SortedMap<String, List<String>> categories = new TreeMap<String, List<String>>();
	
	// List with commands necessary for h files
	// Map<String, CodeBlock> hCodeGen = new HashMap<String, CodeBlock>();
	MakeFileHCache hCache;
	
	final boolean turbo;
	
	final String TEMPDIR = "/tmp/mbuild_" + Util.whoami();
	
	public static final String MCAOPTS = "-include Makefile.h -Ilibraries -Iprojects -Itools -I. ";

	public MakeFileBuilder(Options opts) {
		super(opts);
		makefile.add(firstBlock);
		tempBuildPathBase = "build" + FS + opts.build;
		rootlen = HOME.getAbsolutePath().length() + 1;
		
		globalDefine.add("#define _MCA_VERSION_ \"2.4.1\"");
		globalDefine.add("#define _MCA_DEBUG_");
		globalDefine.add("#define _MCA_PROFILING_");
		globalDefine.add("#define _MCA_LINUX_");
		
		turbo = opts.combineCppFiles;
	}
	
	protected void loadCachedData() throws Exception {}
	
	protected void storeCachedData() throws Exception {
		
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
		if (!turbo) {
			makefile.writeTo(new File("Makefile"));
		} else {
			makefileT.writeTo(new File("Makefile"));
		}
		
		globalDefine.add("");
		globalDefine.writeTo(new File("Makefile.h"));
	}

	public void init(File targetFile) throws Exception {
		if (descrBuilderBin == null) {
			targetBase = targetFile.getParentFile().getParent().substring(rootlen);
			targetBin = targetBase + FS + "bin";
			targetLib = targetBase + FS + "lib";
			descrBuilderBin = targetBin + FS + "descriptionbuilder ";
			descrBuilderBinExt = "MCAHOME=" + HOME.getAbsolutePath() + " " + descrBuilderBin;

			// cache h files
			System.out.println("Caching local .h-files");
			hCache = new MakeFileHCache(HOME.getAbsolutePath(), makefile, descrBuilderBin, descrBuilderBinExt, tempBuildPathBase);
			makefile.add("");
			makefileT.addAll(makefile);  // duplicate
		}
	}
	
	@Override
	protected void build(final BuildEntity be, final boolean immediately) throws Exception {

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
		
		String tempFile = tempBuildPath + FS + be.name + "temp.o";
		String tempFileCpp = tempBuildPath + FS + be.name + "temp.cpp";
		String firstLine = target + " : ";
		if (!target.endsWith("/descriptionbuilder")) {
			firstLine += descrBuilderBin + " ";
		}
		String secondLine = "";
		
		// generate compiler options
		String addOpts = "-I" + tempBuildPathBase + FS + "libraries -I" + tempBuildPathBase + FS + "projects -I" + tempBuildPathBase + FS + rootDir;
		String gccopts = " -g2 -Wall -Wwrite-strings -Wno-unknown-pragmas -L" + targetLib + " " + MCAOPTS + " " + addOpts + " " + be.opts + " ";
		String rootDirTmp = rootDir;
		while(rootDirTmp.contains(FS)) {
			gccopts += "-I" + rootDirTmp + " ";
			rootDirTmp = rootDirTmp.substring(0, rootDirTmp.lastIndexOf(FS));
		}
		if (!be.compileToExecutable()) {
			gccopts += "-shared -fPIC ";
		}
		
		for (BuildEntity s : be.dependencies) {
			firstLine += s.getTarget().getAbsolutePath().substring(rootlen) + " ";
			gccopts += "-l" + s.toString() + " ";
		}
		for (LibDB.ExtLib s : be.extlibs) {
			gccopts += s.options + " ";
		}
		
		String gccoptsLight = gccopts;
		while (gccoptsLight.contains(" -l")) {
			String temp1 = gccoptsLight.substring(0, gccoptsLight.indexOf(" -l"));
			String temp2 = gccoptsLight.substring(gccoptsLight.indexOf(" -l") + 3);
			if (temp2.contains(" ")) {
				gccoptsLight = temp1 + temp2.substring(temp2.indexOf(" "));
			} else {
				gccoptsLight = temp1;
			}
		}
		
		// for turbo
		MakeFileBlacklist.Element blacklist = MakeFileBlacklist.getInstance().get(be.name);
		CodeBlock turboCb = new CodeBlock();
		makefileT.add(turboCb);
		boolean tmpC = false;
		String turboDeps = firstLine;
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
			cb.add("\tgcc -c " + cAbs + " -o " + o + " " + gccoptsLight);
			
			// turbo
			if (blacklist == null || !blacklist.contains(c)) {
				if (!tmpC) {
					mkdir(turboCb, turboC);
					turboCb.add("\techo \\/\\/ generated > " + turboC);
					tmpC = true;
					turboCompiles += turboC + " ";
				}
				turboCb.add("\techo \\#line 1 \\\"" + cAbs + "\\\" >> " + turboC);
				//turboCb.add("\tcat " + cAbs + " | sed -e 's/#include \"/#import \"/' >> " + turboC);
				turboCb.add("\tcat " + cAbs + importString + " >> " + turboC);
				//turboCb.add("\techo \\#undef LOCAL_DEBUG\\\\n\\#undef MODULE_DEBUG >> " + turboC);
				turboCb.add("\techo \\#undef LOCAL_DEBUG >> " + turboC);
				turboCb.add("\techo \\#undef MODULE_DEBUG >> " + turboC);
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
			cb.add("\tg++ -c " + cAbs + " -o " + o + " " + gccoptsLight);
			
			// turbo
			if (blacklist == null || !blacklist.contains(c)) {
				turboCb.add("\techo \\#line 1 \\\"" + cAbs + "\\\" >> " + turboCpp);
				turboCb.add("\tcat " + cAbs + importString + " >> " + turboCpp);
				//turboCb.add("\techo \\#undef LOCAL_DEBUG\\\\n\\#undef MODULE_DEBUG >> " + turboCpp);
				turboCb.add("\techo \\#undef LOCAL_DEBUG >> " + turboCpp);
				turboCb.add("\techo \\#undef MODULE_DEBUG >> " + turboCpp);
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
			cb.add("\t/usr/local/cuda/bin/nvcc -c -D_DEBUG " + cAbs + " -o " + o);
			
			// turbo
			makefileT.add(cFirst);
			mkdir(makefileT, o);
			makefileT.add("\t/usr/local/cuda/bin/nvcc -c -D_DEBUG " + cAbs + " -o " + o);
			turboDeps += o + " ";
			turboCompiles += o + " ";
		}
		

		// create temp file containing generated code
		CodeBlock hdr2 = new CodeBlock();
		hdr2.add("\tmkdir -p " + tempBuildPath);
		hdr2.add("\techo \\/\\* \\*\\/ > " + tempFileCpp);
		
		// process UIC files
		for (String c : be.uics) {
			if (be.qt3) {
				String cAbs = rootDir + FS + c;
				String cc = tempBuildPathBase + FS + cAbs + ".cc";
				hdr2.add("\tcat " + cc + " >> " + tempFileCpp);
				firstLine += cc + " ";
				
				// turbo
				turboCb.add("\tcat " + cc + importString + " >> " + turboCpp);
				turboDeps += cc + " ";
			}
		}
		
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
					hdr2.add("\t" + descrBuilderBinExt + inc.relFile + " >> " + tempFileCpp);
					turboCb.add("\t" + descrBuilderBinExt + inc.relFile + importString + " >> " + turboCpp);
				}
				if (inc.moc) {
					String qtCall = LibDB.getLib(be.qt4 ? "moc-qt4" : "moc-qt3").options.trim();
					String call = qtCall + " " + inc.relFile;
					hdr2.add("\t" + call + " >> " + tempFileCpp);
					turboCb.add("\t" + call + importString + " >> " + turboCpp);
					//hdr2.add("\t" + descrBuilderBinExt + inc.relFile + " >> " + tempFileCpp);
				}
			}
			
			turboDeps += inc.relFile + " ";
		}
		hdr2.add("\tg++ -c -I. " + tempFileCpp + " -o " + tempFile + " " + gccoptsLight);
		if (hdr2.size() > 3) {
			hdr.add(hdr2);
		}
		hdr.add("\tgcc -o " + target + (hdr2.size() > 3 ? " " + tempFile : "") + " " + secondLine + " " + gccopts + " -lc -lm -lz -lcrypt -lpthread -lstdc++ -Wl,-rpath," + targetLib + be.getLinkerOpts() + " ");
		cb.add(0,hdr);
		cb.add("");
		makefile.add(cb);
		
		// turbo
		if (turboCb.size() >= 7) {
			turboCb.add(0, turboDeps);
			mkdir(turboCb, target);
			turboCb.add("\tgcc -o " + target + " " + turboCompiles + " " + gccopts + " -lc -lm -lz -lcrypt -lpthread -lstdc++ -Wl,-rpath," + targetLib + be.getLinkerOpts() + " ");
		} else {
			turboCb.clear();
			turboCb.add(turboDeps);
			mkdir(turboCb, target);
			turboCb.add("\tgcc -o " + target + " " + turboCompile + " " + gccopts + " -lc -lm -lz -lcrypt -lpthread -lstdc++ -Wl,-rpath," + targetLib + be.getLinkerOpts() + " ");
		}
		
		be.built = true;
		sourceFiles.getAndAdd(be.cpps.size() + be.cs.size());
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

	/*private List<String> getHDeps(String call, BuildEntity be) throws Exception {
		List<String> result = new ArrayList<String>();
		TurboBuilder.println(call);
		Process p = Runtime.getRuntime().exec(call);
		p.waitFor();
		for (String s : Files.readLines(p.getErrorStream())) {
			TurboBuilder.println(s);
		}
		for (String s : Files.readLines(p.getInputStream())) {
			if (s.endsWith("\\")) {
				s = s.substring(0, s.length() - 1);
			}
			if (s.contains(":")) {
				s = s.substring(s.indexOf(":") + 1).trim();
			}
			String[] hs = s.trim().split("[ ]");
			for (String h : hs) {
				h = h.trim();
				if (h.length() > 0 && (!h.startsWith("/")) && (!h.endsWith(".cpp"))) {
					result.add(h);
				}
			}
		}
		return result;
	}*/
	
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

	@Override
	protected void addDefine(String string) {
		globalDefine.add("#define " + string);
	}
}
