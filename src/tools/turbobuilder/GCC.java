package tools.turbobuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import tools.turbobuilder.LibDB.ExtLib;

public class GCC {
	
	static class GCCThread extends LoopThread {

		volatile boolean ready = true;
		
		public GCCThread() {
			super(100, false);
		}

		@Override
		public void mainLoopCallback() throws Exception {
			while(true) {
				GCCCall task = queue.getNextCall();
				if (task == null) {
					break;
				}
				ready = false;
				try {
					compileEx(task);
				} catch (Exception e) {
					e.printStackTrace();
					TurboBuilder.printErrorAdvice();
					System.exit(1);
				}
			}
			ready = true;
		}
	}

	static class GCCCall {
		String gccCall;
		File libDir;
		BuildEntity be;
		List<BuildEntity> dependencies;
		List<GCCCall> dependencies2;
		boolean completed;

		public GCCCall(String gccCall, File libDir, BuildEntity be, List<BuildEntity> dependencies) {
			this.gccCall = gccCall;
			this.libDir = libDir;
			this.be = be;
			this.dependencies = dependencies;
		}

		public void createDependencies2() {
			dependencies2 = new ArrayList<GCCCall>();
			for (BuildEntity be : dependencies) {
				dependencies2.addAll(be.gccCalls);
			}
		}
		
		public boolean ready() {
			for (int i = 0, n = dependencies2.size(); i < n; i++) {
				GCCCall call = dependencies2.get(i);
				if (!call.completed) {
					return false;
				}
			}
			return true;
		}
	}
	
	static class CallQueue {

		LinkedList<GCCCall> queue = new LinkedList<GCCCall>();

		public void add(GCCCall call) {
			call.be.gccCalls.add(call);
			call.createDependencies2();
			synchronized(queue) {
				queue.add(call);
			}
		}

		public GCCCall getNextCall() {
			synchronized(queue) {
				for (int i = 0, n = queue.size(); i < n; i++) {
					GCCCall call = queue.get(i);
					if (call.ready()) {
						queue.remove(i);
						return call;
					}
				}
				return null;
			}
		}

		public boolean isEmpty() {
			return queue.size() <= 0;
		}
	}
	
	public static List<GCCThread> threads = new ArrayList<GCCThread>();
	public static CallQueue queue = new CallQueue();
	
	public static synchronized void initThreads(int number) {
		for (int i = 0; i < number; i++) {
			GCCThread gt = new GCCThread();
			threads.add(gt);
			gt.start();
		}
	}
	
	public static void waitForThreads() {
		while(true) {
			if (queue.isEmpty()) {
				boolean ready = true;
				for (GCCThread gt : threads) {
					ready &= gt.ready;
				}
				if (ready) {
					return;
				}
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static synchronized void stopThreads() {
		for (GCCThread t : threads) {
			t.stopLoop();
		}
		threads.clear();
	}
	
	public static final File TMP = new File("/tmp");
	
	public static void compile(List<File> sourceFiles, String[] includeDirs, List<BuildEntity> libs, List<ExtLib> extlibs, File output, boolean executable, String opts, BuildEntity be, boolean compileNow) throws Exception {
		//String options = "-D _MCA_LINUX_ -D _MCA_QT_3_ -D _MCA_VERSION_ -lc -lm -lz -lcrypt -lpthread -lstdc++ -shared -Wl,-soname," + output + " -o " + output + " ";
		File libDir = new File(be.tb.buildPath.getAbsolutePath() + File.separator + "lib");
		String gccCall = "gcc "; 
		String options = executable ?
				"-L. -fpermissive -lc -lm -lz -lcrypt -lpthread -lstdc++ -Wl,-rpath," + libDir.getAbsolutePath() + be.getLinkerOpts() + " -o " + output.getAbsolutePath() + " " :
//				"-L. -fpermissive -lc -lm -lz -lcrypt -lpthread -lstdc++ -shared -Wl,-soname," + output.getName() + ",-rpath," + libDir.getAbsolutePath() + " -o " + output.getAbsolutePath() + " ";
				"-L. -fpermissive -lc -lm -lz -lcrypt -lpthread -lstdc++ -Wl,-rpath," + libDir.getAbsolutePath() + be.getLinkerOpts() + " -fPIC -shared -o " + output.getAbsolutePath() + " ";
//				"-L/tmp -lcrypt -lpthread -o " + output + " " :
//				"-L/tmp -lcrypt -lpthread -shared -Wl,-soname," + output + " -o " + output + " ";
		options += opts + " ";
		for (String s : includeDirs) {
			options += "-I" + s + " ";
		}
		for (BuildEntity s : libs) {
			options += "-l" + s.toString() + " ";
		}
		for (LibDB.ExtLib s : extlibs) {
			options += s.options + " ";
		}
		
		for (File f : sourceFiles) {
			/*if (f.getParentFile().equals(sourceFiles.get(0).getParentFile())) {
				gccCall += f.getName() + " ";
			} else {*/
				gccCall += f.getAbsolutePath() + " ";
			//}
		}
		gccCall += options;
		
		output.getParentFile().mkdirs();
		if (!libDir.exists()) {
			libDir.mkdir();
		}
		
		enqueueGCCCall(new GCCCall(gccCall, libDir, be, libs), compileNow);
	}
	
	private static void enqueueGCCCall(GCCCall call, boolean compileNow) throws Exception {
		if (call.be.tb.opts.gccThreads <= 0 || compileNow) {
			try {
				compileEx(call);
			} catch (Exception e) {
				e.printStackTrace();
				TurboBuilder.printErrorAdvice();
				System.exit(1);
			}
		} else {
			queue.add(call);
		}
	}

	public static void compileEx(GCCCall call) throws Exception {
		TurboBuilder.println(call.gccCall);
		Process p = Runtime.getRuntime().exec(call.gccCall, null, call.libDir);
		for (String s : Files.readLines(p.getErrorStream())) {
			TurboBuilder.println(s);
		}
		int error = p.waitFor();
		if (error != 0) {
			throw new Exception("Compile error");
		}
		call.completed = true;
	}

	public static DefineManager.Defines getStdDefines(DefineManager dm) {
		try {
			System.out.println("Getting standard defines...");
			Files.writeLines(new File("/tmp/foo.cpp"), new ArrayList<String>());
			Process p = Runtime.getRuntime().exec("cpp -dM foo.cpp", null, TMP);
			
			DefineManager.Defines d = dm.new Defines();
			for (String s : Files.readLines(p.getInputStream())) {
				s = s.substring(8);
				int idx = s.indexOf(" ");
				String def = s.substring(0, idx);
				String val = s.substring(idx + 1);
				d.define(def, val, DefineManager.DEFAULT, DefineManager.SYSTEM);
			}
			return d;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
		
		/*try {
			// copy .cpp file to temp-directory
			Map<String, String> result = new HashMap<String, String>();
			byte[] lines = Files.readStreamFully(GCC.class.getResourceAsStream("stddefines.cpp"));
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("/tmp/stdddefines.cpp"));
			bos.write(lines);
			bos.close();
			
			// call gcc
			String gccCall = "gcc -E -o stddefines.result stdddefines.cpp";
			Process p = Runtime.getRuntime().exec(gccCall, null, new File("/tmp"));
			p.waitFor();
			boolean error = false;
			for (String s : Files.readLines(p.getErrorStream())) {
				System.out.println(s);
				if (s.toLowerCase().contains("fehler:") || s.toLowerCase().contains("error:")) {
					error = true;
				}
			}
			if (error) {
				System.exit(1);
			}

			// interpret result
			List<String> cpp = Files.readLines(new ByteArrayInputStream(lines)); 
			List<String> parsed = Files.readLines(new File("/tmp/stddefines.result"));
			for (int i = 1; i < parsed.size(); i+=2) {
				while (parsed.get(i).toString().length() > 0) {
					String r = parsed.remove(i);
					parsed.set(i - 1, parsed.get(i - 1) + " " + r);
				}
			}
			for (int i = 0; i < cpp.size(); i+=2) {
				if (!cpp.get(i).equals(parsed.get(i))) {
					result.put(cpp.get(i), parsed.get(i));
				}
			}
			return result;
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;*/
	}

	public static void getDefines(String reduced, DefineManager.Defines resultBuffer, File f) {
		try {
			TurboBuilder.println("Caching define changes for " + f.toString());
			//List<String> source = reduced.changesFromDefault();
			List<String> source = new ArrayList<String>();
			source.add(reduced);
			source.add("#include \"" + f.getAbsolutePath() + "\"");
			source.add("");
			Files.writeLines(new File("/tmp/defineTest.cpp"), source);
			
			Process p = Runtime.getRuntime().exec("cpp -dM defineTest.cpp", null, TMP);
			//p.waitFor();
			
			resultBuffer.clear();
			for (String s : Files.readLines(p.getInputStream())) {
				s = s.substring(8);
				int idx = s.indexOf(" ");
				String def = s.substring(0, idx);
				String val = s.substring(idx + 1);
				if (def.contains("(")) {
					// macro
					//def = def.substring(0, def.indexOf("("));
					//resultBuffer.define(def, "", DefineManager.MACRO);
				} else {
					resultBuffer.define(def, val, DefineManager.SYSTEM);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	public static List<String> getSystemIncludeDirs() {
		System.out.println("Getting system include directories...");
		List<String> result = new ArrayList<String>();
		getSystemIncludeDirs("fstream", result);
		getSystemIncludeDirs("iostream.h", result);
		return result;
	}

	public static void getSystemIncludeDirs(String include, List<String> result) {
		
		try {
			List<String> source = new ArrayList<String>();
			source.add("#include <" + include + ">");
			source.add("");
			File f = new File("/tmp/sysIncludeTest.cpp");
			Files.writeLines(f , source);
			
			Process p = Runtime.getRuntime().exec("cpp -shared -o sysIncludeTest.txt sysIncludeTest.cpp", null, TMP);
			p.waitFor();
			
			List<String> txt = Files.readLines(new File("/tmp/sysIncludeTest.txt"));
			for (String line : txt) {
				if (line.startsWith("#")) {
					String[] s = line.split(" ");
					if (s.length > 2 && s[2].startsWith("\"/")) {
						String path = s[2].substring(1, s[2].lastIndexOf("/"));
						if (!result.contains(path)) {
							result.add(path);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static List<BuildEntity> NO_BUILD_ENTITY = new ArrayList<BuildEntity>();
	
	public static void buildCuda(File file, String lib, BuildEntity be) throws Exception {
//		String options = executable ?
//				"-L/tmp -lc -lm -lz -lcrypt -lpthread -lstdc++ -o " + output + " " :
//				"-L/tmp -lc -lm -lz -lcrypt -lpthread -lstdc++ -shared -Wl,-soname," + output + " -o " + output + " ";
//				"-L/tmp -lcrypt -lpthread -o " + output + " " :
//				"-L/tmp -lcrypt -lpthread -shared -Wl,-soname," + output + " -o " + output + " ";
/*		options += opts + " ";
		for (String s : includeDirs) {
			options += "-I" + s + " ";
		}
		for (BuildEntity s : libs) {
			options += "-l" + s.toString() + " ";
		}
		for (String s : extlibs) {
			options += LibDB.getLibOptions(s) + " ";
		}*/
		File libDir = new File(be.tb.buildPath.getAbsolutePath() + File.separator + "lib");
		String gccCall = "/usr/local/cuda/bin/nvcc -D_DEBUG " + file.getAbsolutePath() + " -lib -o " + lib;
		
		enqueueGCCCall(new GCCCall(gccCall, libDir, be, NO_BUILD_ENTITY), true);
	}
}
