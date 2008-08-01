/**
 * 
 */
package tools.turbobuilder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import bsh.Interpreter;

/**
 * @author max
 *
 */
public abstract class BuildEntity {

	static final String FS = File.separator;
	static final String TEMPDIR;
	static final Charset INPUT_CHARSET = Charset.forName("ISO-8859-1");
	static final AtomicInteger redefineIndex = new AtomicInteger(); 
	
	static {
		// determine temp directory
		String x = "";
		try {
			x = File.createTempFile("xyz", ".cpp").getParent();
		} catch (IOException e) {
			e.printStackTrace();
		}
		TEMPDIR = x;
	}
	
	String name;    // name of entity to be built
	String sconsID; // variable name of entity in SConscript
	File rootDir;   // root directory of SConscript
	List<String> categories = new ArrayList<String>();  // targets/groups in makefiles this build entity belongs to
	
	/** Involved source files */
	List<String> cpps = new ArrayList<String>();
	List<String> cs = new ArrayList<String>();
	List<String> hs = new ArrayList<String>();
	List<String> os = new ArrayList<String>();
	List<String> cudas = new ArrayList<String>();
	List<String> addIncludes = new ArrayList<String>();
	List<String> uics = new ArrayList<String>();
	String opts = "";
	
	/** Involved libraries */
	List<String> libs = new ArrayList<String>();
	List<String> optionalLibs = new ArrayList<String>();
	List<LibDB.ExtLib> extlibs = new ArrayList<LibDB.ExtLib>();
	List<BuildEntity> dependencies = new ArrayList<BuildEntity>();
	boolean qt3, qt4;
	
	/** Collections for preprocessing and merging (so that not so many parameters need to be passed among methods) */
	class BuildTemps {
		List<SourceFile> includes = new ArrayList<SourceFile>();
		List<SourceFile> includesInThisSourceFile = new ArrayList<SourceFile>();
		List<SourceFile> addedDescr = new ArrayList<SourceFile>();
		DefineManager.Defines defines;
		List<String> declarations = new ArrayList<String>();
		List<String> relevantFiles = new ArrayList<String>();
		Interpreter ip;

		public BuildTemps(TurboBuilder tb) {
			defines = tb.includeCache.defineManager.new Defines();
		}

		public void reset() {
			includes.clear();
			includesInThisSourceFile.clear();
			addedDescr.clear();
			defines.clear();
			declarations.clear();
			relevantFiles.clear();
		}
	}
	
	final static ThreadLocal<BuildTemps> buildTemps = new ThreadLocal<BuildTemps>();
	BuildTemps temps;
	
	TurboBuilder tb;
	
	// has entity been built?
	boolean built = false;
	
	File target;
	
	// are any dependencies missing?
	public boolean missingDep;
	
	// for up to date check
	boolean upToDateChecked = false;
	boolean upToDate = false;
	
	List<GCC.GCCCall> gccCalls = new ArrayList<GCC.GCCCall>();
	
	public BuildEntity(TurboBuilder tb) {
		this.tb = tb;
	}
	
	public void checkUpToDate() {
		if (upToDateChecked) {
			return;
		}
		
		for (BuildEntity be : dependencies) {
			be.checkUpToDate();
			if (!be.upToDate) {
				upToDate = false;
				upToDateChecked = true;
				return;
			}
		}
		
		File target = getTarget();
		File targetInfo = getTargetInfo();
		if (target.exists() && targetInfo.exists()) {
			try {
				List<String> info = Files.readLines(targetInfo);
				List<FileInfo> relevantFiles = new ArrayList<FileInfo>();
				for (String s : info) {
					if (s.trim().length() > 0) {
						relevantFiles.add(new FileInfo(s));
					}
				}
				for (FileInfo fi : relevantFiles) {
					if (!fi.upToDate()) {
						upToDate = false;
						upToDateChecked = true;
						return;
					}
				}
				upToDateChecked = true;
				upToDate = true;
			} catch (Exception e) {
				e.printStackTrace();
				upToDateChecked = true;
				upToDate = false;
				return;
			}
		}
		upToDateChecked = true;
	}

	private File getTargetInfo() {
		return new File(getTarget().getAbsolutePath() + ".info");
	}

	File getTarget() {
		if (target != null) {
			return target;
		}
		String target = tb.buildPath.getAbsolutePath() + FS;
		if (!compileToExecutable()) {
			target += "lib" + FS + getLibName();
		} else {
			target += "bin" + FS + toString();
		}
		return new File(target);
	}
	
	public void build(boolean compileImmediately) throws Exception {
		
		TurboBuilder.println("Building " + toString() + "...");
		//System.out.println("Freemem: " + Runtime.getRuntime().freeMemory());
		
		// get temporary variable cluster
		temps = buildTemps.get();
		if (temps == null) {
			temps = new BuildTemps(tb);
			buildTemps.set(temps);
		}
		temps.reset();
		
		// compile cuda files
		for (String cuda : cudas) {
			String cudaName = new File(cuda).getName();
			File cudaf = new File(rootDir.getAbsolutePath() + FS + cuda);
			GCC.buildCuda(cudaf, "lib" + cudaName.substring(0, cudaName.lastIndexOf(".")) + ".a", this);
			temps.relevantFiles.add(new FileInfo(cudaf).toString());
		}		
		
		// compile uic files
		for (String uic : uics) {
			File uicf = new File(rootDir.getAbsolutePath() + FS + uic);
			CodeBlock cb = runUic(uicf);
			File dest = new File(rootDir.getAbsolutePath() + FS + uic.substring(0, uic.lastIndexOf(".")) + ".h");
			if (qt4) {
				dest = new File(dest.getParent() + FS + "ui_" + dest.getName());
			}
			SourceFile.Local uicInclude = tb.includeCache.createVirtualInclude(dest, cb);
			
			if (!qt4) {
				// moc .h
				uicInclude.descr = callMoc(cb);

				// create impl
				cb = runUicImpl(uicf, dest);
				File dest2 = new File(rootDir.getAbsolutePath() + FS + uic.substring(0, uic.lastIndexOf(".")) + ".cc");
				dest2 = new File(dest.getParent() + FS + "uic_" + dest2.getName());
				cpps.add(dest2.getName());
				tb.includeCache.createVirtualInclude(dest2, cb);
			}
			
			temps.relevantFiles.add(new FileInfo(uicf).toString());
		}
		
		List<File> buildFiles = new ArrayList<File>();

		// merge .cpps
		List<List<String>> source = LibDB.partition(cpps);
		for (int i = 0; i < source.size(); i++) {
			List<String> partition = source.get(i);
			File cppX = new File(TEMPDIR + FS + toString() + i + ".cpp");
			if (mergeAndProcessSourceFiles(partition, cppX, true)) {
				buildFiles.add(cppX);
			}
		}
		
		// merge .cs
		source = LibDB.partition(cs);
		for (int i = 0; i < source.size(); i++) {
			List<String> partition = source.get(i);
			File cX = new File(TEMPDIR + FS + toString() + i + ".c");
			if (mergeAndProcessSourceFiles(partition, cX, false)) {
				buildFiles.add(cX);
			}
		}
		
		for (String o : os) {
			buildFiles.add(new File(rootDir.getAbsolutePath() + FS + o));
		}

		if (buildFiles.size() == 0) {
			buildFiles.add(TurboBuilder.CONFIGH_FILE);
		}
		
		// compile
		gccCall(buildFiles, compileImmediately);
		
		// save info about relevant files
		Files.writeLines(getTargetInfo(), temps.relevantFiles);
	}

	private Interpreter getInterpreter() {
		if (temps.ip != null) {
			return temps.ip;
		}
		try {
			temps.ip = new Interpreter();
			temps.ip.eval("undefined = new Object();");
			temps.ip.eval("boolean defined(Object d) { return d != undefined; }");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return temps.ip;
	}
	
	public void getExtLibsDefines(List<String> addDefines) {
		for (LibDB.ExtLib s : extlibs) {
			addDefines.addAll(s.libDefines);
		}
	}
	
	private boolean mergeAndProcessSourceFiles(List<String> sources, File result, boolean cppSources) throws Exception {
		
		if (sources.size() == 0) {
			return false;
		}
		
		temps.includes.clear();
		temps.declarations.clear();
		List<String> addDefines = new ArrayList<String>();
		getExtLibsDefines(addDefines);
		addDefines.addAll(tb.defines);
		
		CodeBlock completeCode = new CodeBlock();
		
		// Put all .cpp files in one file
		for (String cpp : sources) {
			//List<String> diff = includeCache.STD_DEFINES.from(defines);
			//if (diff.size() > 0) {
			//System.out.println(defines.toString());
			File ccFile = new File(rootDir.getAbsolutePath() + FS + cpp);
			if (temps.includes.contains(ccFile)) {
				continue;
			}
			completeCode.add(temps.defines.undefineLocalDefines());
			temps.defines.clear();
			temps.defines.addStdDefines();
			if (!cppSources) {
				temps.defines.undefine("__cplusplus");
			}
			for (String s : addDefines) {
				temps.defines.define(s, "");
			}
			//System.out.println(Runtime.getRuntime().freeMemory());
			//completeCode.add(new CodeBlock(diff));
			//}
			
			temps.includesInThisSourceFile.clear();
			/*if (cpp.endsWith(".h")) {
				includes.add(ccFile);
				includesInThisSourceFile.add(ccFile);
			}*/
			
			CodeBlock code = new CodeBlock();
			completeCode.add(code);
			
			// (re-)include config.h
			code.add("///////////////////////////////////////////");
			code.add("// " + cpp.toString());
			code.add("///////////////////////////////////////////");
			code.add("");
			code.add("// --> config.h");
			code.add(preprocess(((SourceFile.Local)tb.includeCache.getSourceFile(TurboBuilder.CONFIGH_FILE, extlibs)), true, 0));
			code.add("// <-- " + cpp.toString());
			code.add("");
			
			// preprocess c/c++ code 
			SourceFile.Local cFile = ((SourceFile.Local)tb.includeCache.getSourceFile(ccFile, extlibs));
			CodeBlock ccode = preprocess(cFile, false, 0);
			code.add(ccode);

			// delete lines
			if (!cpp.endsWith(".h")) {
				cFile.lines = null;
			}
			
			// handle anonymous enums
			preProcessAnonymousEnums(ccode);
			
			// handle double declarations of global variables
			//if (!cppSources) {
				preProcessGlobalDeclarations(ccode, cppSources);
			//}
		}
		
		if (cppSources) {
			
			for (SourceFile sf : temps.includes) {
				if (!(sf instanceof SourceFile.Local)) {
					continue;
				}
				SourceFile.Local inc = (SourceFile.Local)sf;

				if (includeGeneratedCode(inc, sources)) {
					if (!temps.addedDescr.contains(inc)) {
						completeCode.add(inc.getDescr(this));
						temps.addedDescr.add(inc);
					}
				}
			}
		}

		// write code to file
		completeCode.writeTo(new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(result)), INPUT_CHARSET));
		
		return true;
	}
	
	boolean includeGeneratedCode(SourceFile.Local inc, List<String> sources) {
		if (inc.descr.isEmpty()) {
			return false;
		}
		if (inc.isTemplate()) {
			return true;
		}
		String root = rootDir.getAbsolutePath();
		if (inc.fileInfo.f.getAbsolutePath().startsWith(root)) {
			String s = inc.fileInfo.f.getAbsolutePath();
			File cpp = new File(s.substring(0, s.lastIndexOf(".")) + ".cpp");
			if (!cpp.exists()) {
				return true;
			}
			return sources.contains(cpp.getAbsolutePath().substring(root.length() + 1));
		}
		return false;
	}
	
	private CodeBlock preprocess(SourceFile.Local source, boolean alreadyIncluded, int level) throws Exception {
		
		//System.out.println("Preprocessing " + curFile.toString());
		
		CodeBlock result = new CodeBlock();
		
		LinkedList<StackEntry> ifStack = new LinkedList<StackEntry>();
		//System.out.println("Freemem: " + Runtime.getRuntime().freeMemory());
		boolean curBranchTaken = true;
		List<String> lines = source.getLines(this);

		if (!alreadyIncluded) {
			temps.relevantFiles.add(source.fileInfo.toString());
		}
		
		for (int i = 0; i < lines.size(); i++) {
			String s = lines.get(i);
			//source.set(i, s + " -->" + ifStack.size());
			
			if (s.startsWith("#") && (!s.startsWith("#line "))) {
				// preprocessor directive
				
				// get normalized form
				String pp = Util.normalize(s.substring(1));
				while(pp.endsWith("\\")) {
					i++;
					pp = pp.substring(0, pp.length() - 1) + " " + Util.normalize(lines.get(i)); 
				}
				pp = pp.replace("  ", " ");
				
				// handle #ifdef and #ifndef
				if (pp.startsWith("ifndef") || pp.startsWith("ifdef")) {
					boolean ndef = pp.startsWith("ifndef");
					String def = pp.trim().substring(6).trim();
					boolean bedingung = temps.defines.isDefined(def);
					bedingung = ndef ? !bedingung : bedingung;
					StackEntry se = new StackEntry();
					ifStack.add(se);
					
					if (bedingung) {
						se.branchTaken = true;
						se.curBranchTaken = true;
						curBranchTaken = updateCurBranchTaken(ifStack);
						continue;
					} else {
						curBranchTaken = false;
						continue;
					}
				}

				// handle #if
				if (pp.startsWith("if ")) {
					String condition = pp.substring(3);
					boolean bedingung = curBranchTaken ? testCondition(condition) : false;
					StackEntry se = new StackEntry();
					ifStack.add(se);
					
					if (bedingung) {
						se.branchTaken = true;
						se.curBranchTaken = true;
						curBranchTaken = updateCurBranchTaken(ifStack);
						continue;
					} else {
						curBranchTaken = false;
						continue;
					}
				}
				
				// handle #elif
				else if (pp.startsWith("elif")) {
					String def = pp.trim().substring(5).trim();
					boolean bedingung = updateCurBranchTakenWithoutLast(ifStack) ? testCondition(def) : false;
					StackEntry se = ifStack.getLast();
					
					if (se.branchTaken) {
						se.curBranchTaken = false;
						curBranchTaken = false;
						continue;
					}
					
					if (bedingung) {
						se.branchTaken = true;
						se.curBranchTaken = true;
						curBranchTaken = updateCurBranchTaken(ifStack);
						continue;
					} else {
						se.curBranchTaken = false;
						curBranchTaken = false;
						continue;
					}
				}
				
				// handle #else
				else if (pp.startsWith("else")) {
					StackEntry se = ifStack.getLast();
					
					if (se.branchTaken) {
						se.curBranchTaken = false;
						curBranchTaken = false;
						continue;
					} else {
						se.branchTaken = true;
						se.curBranchTaken = true;
						curBranchTaken = updateCurBranchTaken(ifStack);
						continue;
					}
				}
				
				// handle #endif
				else if (pp.startsWith("endif")) {
					try {
						ifStack.removeLast();
					} catch (Exception e) {
						Files.writeLines(new File("/tmp/error.cpp"), lines);
						e.printStackTrace();
					}
					
					curBranchTaken = updateCurBranchTaken(ifStack);
					continue;
				}
				
				else if (!curBranchTaken) {
					continue;
				}
				
				// #include
				else if (pp.startsWith("include")) {
					String inc = pp.replace(" ", "");
					inc = inc.substring(8, inc.length() - 1);
					SourceFile include = tb.includeCache.getSourceFile(inc, source.fileInfo.f.getParent(), extlibs, this);
					if (include == null) {
						continue;
					}
					if (temps.includesInThisSourceFile.contains(include)) {
						continue;
					}
					result.add("");
					result.add("// --> " + inc);
					temps.includesInThisSourceFile.add(include);
					boolean included = temps.includes.contains(include);
					/*if (include.getName().equals("jnimca.h")) {
						System.out.println("txdg");
					}*/
					//source.addInclude(include2);  // add relevant include to local includes 
					if (!included) {
						temps.includes.add(include);
					}
					if (include instanceof SourceFile.Local) {
						result.add(preprocess(((SourceFile.Local)include), included, level + 1));
					} else {
						((SourceFile.System)include).simulateDefines(temps.defines, tb.includeCache);
						if (!included) {
							result.add(s);
						}
					}
					result.add("// <-- " + source.fileInfo.f.getName());
					result.add("");
				}
				
				// handle #define
				else if (pp.startsWith("define")) {
					String def = pp.substring(7);
					String value = "";
					if (def.contains(" ")) {
						value = def.substring(def.indexOf(" ")).trim();
						def = def.substring(0, def.indexOf(" "));
					}
					if (def.contains("(")) {
						//def = def.substring(0, def.indexOf("("));
						//defines.define(def, "", DefineManager.LOCAL, DefineManager.MACRO);
						// macro
						//if (!alreadyIncluded) {
						result.add("#" + pp);
						//}
					} else {
						temps.defines.define(def, value, DefineManager.LOCAL);
						result.add("#" + pp);
					}
				}
				
				// handle #undef
				else if (pp.startsWith("undef")) {
					String def = pp.trim().substring(6).trim();
					temps.defines.undefine(def);
					result.add(s);
				}

				// untouched constructs
				else if (pp.startsWith("pragma")) {
					result.add("#" + pp);
				}

				else if (pp.length() == 0 && i == 0) {
					TurboBuilder.println("# at beginning of file " + source.fileInfo.f.getName());
				}

				// unhandled construct
				else {
					throw new Exception("Unhandled construct: " + s);
				}
				
				continue;
			}
			
			if (s.startsWith("using")) {
				result.globalNamespace = true;
			}
			
			if (alreadyIncluded || (!curBranchTaken)) {
				continue;
			}
			
			result.add(temps.defines.process(s));
		}
		
		return result;
	}

	private boolean testCondition(String condition) {
		
		String c2 = "result = " + temps.defines.processCondition(condition) + ";";
		c2 = c2.replace(" | ", " || ");
		try {
			Interpreter ip = getInterpreter();
			ip.eval(c2);
			Object o = ip.get("result");
			if (o instanceof Boolean) {
				return (Boolean)o;
			} else if (o instanceof Number) {
				return ((Number)o).intValue() != 0;
			} else {
				throw new Exception("Useless result: " + o.toString()); 
			}
		} catch (Exception e) {
			//e.printStackTrace();
			TurboBuilder.println("Error evaluating condition: " + condition);
		}
		return false;
	}

	private boolean updateCurBranchTaken(LinkedList<StackEntry> ifStack) {
		boolean taken = true;
		for (StackEntry se : ifStack) {
			taken &= se.curBranchTaken;
		}
		return taken;
	}
	
	private boolean updateCurBranchTakenWithoutLast(LinkedList<StackEntry> ifStack) {
		boolean taken = true;
		for (int i = 0; i < ifStack.size() - 1; i++) {
			taken &= ifStack.get(i).curBranchTaken;
		}
		return taken;
	}

	/* this method is reasonably fast, but obviously not absolutely clean */	
	private void preProcessGlobalDeclarations(CodeBlock lines, boolean cppSources) throws Exception {
		int brackets = 0;
		List<String> collisions = new ArrayList<String>();
		
		for (int i = 0; i < lines.size(); i++) {
			Object codePart = lines.get(i);
			if (!(codePart instanceof String)) {
				continue;
			}
			String line = Util.normalize(((String)codePart));
			
			// process brackets
			int bcount = bracketCount(line);
			int oldBrackets = brackets;
			if (bcount > Integer.MIN_VALUE) {
				brackets += bcount;
				//break;
			}
			
			// variable declaration(?)
			if ((!line.startsWith("typedef")) && (!line.startsWith("using")) && (brackets == 0) && (bcount == Integer.MIN_VALUE) && (!line.contains("extern ")) && line.endsWith(";") && line.length() >= 5) {
				if (line.contains("=")) {
					line = line.substring(0, line.indexOf("=")).trim() + ";";
				}
				if (temps.declarations.contains(line)) {
					String col = line.substring(line.lastIndexOf(" ") + 1, line.length() - 1);
					TurboBuilder.println("info: handling duplicate declaration of " + col + " (" + line + ")");
					collisions.add(col);
				} else {
					temps.declarations.add(line);
				}
			
			} else if ((!cppSources) && (!line.startsWith("typedef")) && (!line.startsWith("using")) && (!line.contains("extern ")) && (!line.endsWith(";")) 
					&& oldBrackets == 0 && line.contains("(") && (!line.contains("::")) && (!line.startsWith("#"))) {
				// method declaration?
				String oldLine = line;
				line = line.substring(0, line.indexOf("("));
				if (line.contains(" ")) {
					line = line.substring(line.lastIndexOf(" ") + 1);
				}
				if (temps.declarations.contains(line)) {
					TurboBuilder.println("info: handling duplicate declaration of method " + line + " (" + oldLine + ")");
					collisions.add(line);
				} else {
					temps.declarations.add(line);
				}
			}
		}
		
		if (brackets != 0) {
			//for (int i = 0; i < l)
			brackets = 0;
			for (int i = 0; i < lines.size(); i++) {
				Object codePart = lines.get(i);
				if (!(codePart instanceof String)) {
					continue;
				}
				String line = Util.normalize(((String)codePart));
				int bcount = bracketCount(line);
				if (bcount > Integer.MIN_VALUE) {
					brackets += bcount;
					//break;
				}
				lines.set(i, brackets + " " + line);
			}
			lines.writeTo(new File("/tmp/error.cpp"));
			throw new Exception("bracket inconsistency: " + brackets);
		}

		CodeBlock defines = new CodeBlock();
		CodeBlock undefines = new CodeBlock();
		lines.add(0, defines);
		lines.add(undefines);
		for (String s : collisions) {
			defines.add("#define " + s + " " + s + "_COLLISION_HANDLER_" + redefineIndex.getAndIncrement());
			undefines.add("#undef " + s);
		}
	}
	
	private int bracketCount(String line) {
		char[] x = line.toCharArray();
		boolean found = false;
		int result = 0;
		boolean string1 = false;
		boolean string2 = false;
		for (int i = 0; i < x.length; i++) {
			char c = x[i];
			if ((string1 || string2) && c == '\\') {
				i++;
				continue;
			}
			if (!string2 && c == '"') {
				string1 = !string1;
				continue;
			}
			if (!string1 && c == '\'') {
				string2 = !string2;
				continue;
			}
			if (string1 || string2) {
				continue;
			}
			if (c == '(' || c == '{' || c == '[') {
				found = true;
				result++;
			}
			if (c == ')' || c == '}' || c == ']') {
				found = true;
				result--;
			}
		}
		
		if (!found) {
			return Integer.MIN_VALUE;
		}
		return result;
	}

	CodeBlock callMoc(File hf) throws Exception {
		CodeBlock result = new CodeBlock();
		String qtCall = LibDB.getLib(qt4 ? "moc-qt4" : "moc-qt3").options.trim();
		String call = qtCall + " -i " + hf.getAbsolutePath();
		TurboBuilder.println(call);
		Process p = Runtime.getRuntime().exec(call, null, new File("/tmp"));
		p.waitFor();
		for (String s : Files.readLines(p.getErrorStream())) {
			TurboBuilder.println(s);
		}
		for (String s : Files.readLines(p.getInputStream())) {
			result.add(s);
		}
		return result;
	}
	
	CodeBlock callMoc(CodeBlock cb) throws Exception {
		CodeBlock result = new CodeBlock();
		String qtCall = LibDB.getLib(qt4 ? "moc-qt4" : "moc-qt3").options.trim();
		String call = qtCall + " -i";
		TurboBuilder.println(call);
		Process p = Runtime.getRuntime().exec(call, null, new File("/tmp"));
		cb.writeTo(new OutputStreamWriter(new BufferedOutputStream(p.getOutputStream())));
		p.waitFor();
		for (String s : Files.readLines(p.getErrorStream())) {
			TurboBuilder.println(s);
		}
		for (String s : Files.readLines(p.getInputStream())) {
			result.add(s);
		}
		return result;
	}
	
	CodeBlock runUic(File src) throws Exception {
		CodeBlock result = new CodeBlock();
		String qtCall = LibDB.getLib(qt4 ? "uic-qt4" : "uic-qt3").options.trim();
		String call1 = qtCall + " " + src.getAbsolutePath();
		TurboBuilder.println(call1);
		Process p = Runtime.getRuntime().exec(call1, null, new File("/tmp"));
		int presult = p.waitFor();
		if (presult != 0) {
			throw new Exception("UIC Error");
		}
		for (String s : Files.readLines(p.getErrorStream())) {
			TurboBuilder.println(s);
		}
		for (String s : Files.readLines(p.getInputStream())) {
			if (!(s.startsWith("#") && s.endsWith("_H"))) {
				result.add(s);
			}
		}
		return result;
	}

	CodeBlock runUicImpl(File src, File destH) throws Exception {
	
		if (qt4) {
			throw new Exception("not available for qt4");
		}

		CodeBlock result = new CodeBlock();
		String qtCall = LibDB.getLib("uic-qt3").options.trim();
		String call1 = qtCall + " -impl " + destH.getName() + " " + src.getAbsolutePath();
		TurboBuilder.println(call1);
		Process p = Runtime.getRuntime().exec(call1, null, new File("/tmp"));
		int presult = p.waitFor();
		if (presult != 0) {
			throw new Exception("UIC Error");
		}
		for (String s : Files.readLines(p.getErrorStream())) {
			TurboBuilder.println(s);
		}
		for (String s : Files.readLines(p.getInputStream())) {
			result.add(s);
		}
		return result;
	}

	CodeBlock callDescriptionBuilder(File hf) throws Exception {
		//File virtualInclude = new File(hf.getParent() + FS + "descr_h_" + hf.getName() + "pp");
		CodeBlock result = new CodeBlock();
		String call = tb.getDescriptionBuilderCall() + " " + hf.getAbsolutePath();
		TurboBuilder.println(call);
		Process p = Runtime.getRuntime().exec(call, new String[]{"MCAHOME=" + TurboBuilder.HOME.getAbsolutePath()}, new File("/tmp"));
		p.waitFor();
		for (String s : Files.readLines(p.getErrorStream())) {
			TurboBuilder.println(s);
		}
		for (String s : Files.readLines(p.getInputStream())) {
			if (!s.startsWith("#include ")) {
				result.add(s);
			}
		}
		//includeCache.createVirtualInclude(virtualInclude, result);
		return result;
	}

	public void gccCall(List<File> buildFiles, boolean compileNow) throws Exception {
		GCC.compile(buildFiles, new String[]{}, dependencies, extlibs, getTarget(), compileToExecutable(), opts + getCudaOpts(), this, compileNow);
	}
	
	public boolean compileToExecutable() {
		return (this instanceof MCAProgram) && (!tb.opts.compilePartsToSO || name.equals("descriptionbuilder"));
	}

	private void preProcessAnonymousEnums(CodeBlock code) {
		
		int brackets = 0;
		
		// detect anonymous enums
		for (int i = 0; i < code.size(); i++) {
			Object codePart = code.get(i);
			if (!(codePart instanceof String)) {
				continue;
			}
			String line = ((String)codePart).trim();
			
			// process brackets
			int bcount = bracketCount(line);
			if (bcount > Integer.MIN_VALUE) {
				brackets += bcount;
				//break;
			}
			if (brackets != 0) {
				continue;
			}
			
			int start = i;
			if (line.startsWith("enum ") || line.equals("enum")) {
				
				// get whole enum (without comments)
				StringBuilder enumLines = new StringBuilder(line.trim());
				while(!line.contains(";")) {
					i++;
					enumLines.append(" ");
					line = (String)code.get(i);
					if (line.contains("//")) {
						line = line.substring(0, line.indexOf("//"));
					}
					enumLines.append(line);
				}
				line = enumLines.toString();
				while (line.contains("/*")) {
					line = line.substring(0, line.indexOf("/*")) + line.substring(line.indexOf("*/") + 2);
				}
				
				char[] cs = line.toCharArray();
				boolean anonymous = true;
				for (int j = 4; j < cs.length; j++) {
					if (Character.isLetter(cs[j])) {
						anonymous = false;
						break;
					} else if (cs[j] == '{') {
						break;
					}
				}
				
				if (anonymous) {
					
					// delete enum lines
					code.removeRange(start, i);
					CodeBlock defines = new CodeBlock();
					code.add(start, defines);
					CodeBlock undefines = new CodeBlock();
					code.add(undefines);
					
					// replace enum constants in code
					String[] constants = line.substring(line.indexOf("{") + 1, line.indexOf("}")).split(",");
					int offset = 0;
					for (int j = 0; j < constants.length; j++) {
						String constant = constants[j].trim();
						String value = "" + (j + offset);
						if (constant.contains("=")) {
							String[] cv = constant.split("=");
							constant = cv[0].trim();
							value = cv[1].trim();
							if (!value.startsWith("0x")) {
								offset = Integer.parseInt(value);
							} else {
								offset = Integer.parseInt(value.substring(2), 16);
							}
						}
						if (constant.length() <= 0) {
							break;
						}
						defines.add("#define " + constant + " " + value);
						undefines.add("#undef " + constant);
					}
				}
			}
		}
	}
	
	public String toString() {
		return name;
	}
	
	private class StackEntry {
		boolean branchTaken = false;
		boolean curBranchTaken = false;
	}

	/** @param intial 
	 * @return Can be built ? */
	public void checkDepencies() {
		if (missingDep) {
			return;
		}
		for (BuildEntity be : dependencies) {
			be.checkDepencies();
			if (be.missingDep) {
				missingDep = true;
				tb.printErrorLine("Not building " + name + " due to dependency " + be.name + " which cannot be built");
				return;
			}
		}
	}
	
	public void mergeExtLibs() {
		List<LibDB.ExtLib> extLibs2 = new ArrayList<LibDB.ExtLib>();
		mergeExtLibs(extLibs2);
		extlibs = extLibs2;
	}

	private void mergeExtLibs(List<LibDB.ExtLib> extLibs2) {
		for (LibDB.ExtLib s : extlibs) {
			if (!extLibs2.contains(s)) {
				extLibs2.add(s);
			}
		}
		for (BuildEntity be : dependencies) {
			be.mergeExtLibs(extLibs2);
		}
	}

	public String getCudaOpts() {
		String result = "";
		for (String cuda : cudas) {
			String cudaName = new File(cuda).getName();
			result += " -l" + cudaName.substring(0, cudaName.lastIndexOf("."));
		}
		return result;
	}
	
	public boolean isTestPart() {
		return (this instanceof MCAProgram) && (name.contains("_test") || name.contains("test_"));
	}
	
	public String getLibName() {
		return toString() + ".so";
	}

	public String getLinkerOpts() {
		for (LibDB.ExtLib el : extlibs) {
			if (el.name.equals("ltdl")) {
				return ",--export-dynamic";
			}
		}
		return "";
	}

	public void markNotBuilt() {
		if (!built) {
			return;
		}
		built = false;
		for (BuildEntity be : dependencies) {
			be.markNotBuilt();
		}
	}
}