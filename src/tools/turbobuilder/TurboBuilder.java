package tools.turbobuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author max
 *
 * (outdated:)
 * MCA core & test project compiles in 47 seconds instead of
 * 3:45 min (225s) running SCons (on my dual core notebook).
 */
public class TurboBuilder implements FilenameFilter {

	// libraries and executables that need to be built
	final List<BuildEntity> buildEntities = new ArrayList<BuildEntity>();
	
	// options
	Options opts;
	File buildPath;					  // build path where compiled binaries are placed
	File projectPath;		  		  // path of project to compile
	
	static final File HOME = new File(".").getAbsoluteFile().getParentFile();
	
	// config.h file
	static final File CONFIGH_FILE = new File("/tmp/config.h");
	static final List<String> CONFIGH;
	static final String FS = File.separator;
	
	// statistics
	AtomicInteger sourceFiles = new AtomicInteger();

	// cache
	SourceCache includeCache = null;
	List<String> defines = new ArrayList<String>();
	
	static {
		// cache config.h
		List<String> configHTmp = null;
		try {
			configHTmp = Files.readLines(Util.getFileInEtcDir("configH"));
			if (!CONFIGH_FILE.exists()) {
				Files.writeLines(CONFIGH_FILE, configHTmp);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		CONFIGH = configHTmp;
		CONFIGH.remove(0); // #ifndef BUILD_I686_LINUX_DEBUG_CONFIG_H_SEEN
		CONFIGH.remove(0); // #define BUILD_I686_LINUX_DEBUG_CONFIG_H_SEEN
		CONFIGH.remove(CONFIGH.size() - 1); // #endif /* BUILD_I686_LINUX_DEBUG_CONFIG_H_SEEN */
	}
	
	public static void main(String[] args) {
		
		if (args.length > 0 && args[0].equals("--updatelibs")) {
			LibDBBuilder.main(args);
			System.exit(0);
		}
		
		Options options = new Options(args);
		File kernelDir = new File(HOME.getAbsolutePath() + FS + "libraries" + FS + "kernel");
		File altKernelDir = new File(HOME.getAbsolutePath() + FS + "libraries" + FS + "kernel.original");
		try {
			//tb.kernel = new File(tb.home.getAbsolutePath() + File.separator + "libraries" + File.separator + "kernel");
			
			if (options.project == null) {
				System.out.println("");
				for (String s : Files.readLines(Util.getFileInEtcDir("help.txt"))) {
					System.out.println(s);
				}
				System.exit(0);
			}

			TurboBuilder tb = options.makeFile ? new MakeFileBuilder(options) : new TurboBuilder(options);
			
			if (!tb.projectPath.exists() && (!options.project.equals("all"))) {
				options.libPath = new File(HOME.getAbsolutePath() + FS + "libraries" + FS + options.project);
				options.compileOnlyDeps = true;
				if (!options.libPath.exists()) {
					throw new Exception("Invalid project/library");
				}
			}
			
			// symlink kernel library to alternative location
			if (options.kernel != null) {
				kernelDir.renameTo(altKernelDir);
				String call = "ln -s " + options.kernel.getAbsolutePath() + " " + kernelDir.getAbsolutePath(); 
				Runtime.getRuntime().exec(call);
			}
		
			GCC.initThreads(options.gccThreads);
			
			tb.opts = options;
			tb.build();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (options.kernel != null) {
			kernelDir.delete(); 
			altKernelDir.renameTo(kernelDir);
		}
		GCC.stopThreads();
	}
	
	public TurboBuilder(Options options) {
		opts = options;
		buildPath = new File(HOME.getAbsolutePath() + FS + "export" + FS + options.build);
		projectPath = new File(HOME.getAbsolutePath() + FS + "projects" + FS + options.project);
	}

	private void build() throws Exception {

		//System.out.println("Turbo-charged MCA Builder - Copyright 2008 Max Reichardt");
		
		// Load cached data
		loadCachedData();
		
		long startTime = System.currentTimeMillis();
		
		// Parse Scons scripts
		System.out.println("Parsing SCons scripts...");
		String projectsRoot = HOME.getAbsolutePath() + FS + "projects";
		String projectRoot = projectPath.getAbsolutePath();
		Collection<BuildEntity> buildOnlyThose = null;
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
				
				// only compile selected project
				if (!opts.project.equals("all")) {
					if (sconscript.getAbsolutePath().startsWith(projectsRoot) && (opts.libPath != null || !sconscript.getAbsolutePath().startsWith(projectRoot))) {
						continue;
					}
				}
				
				Collection<BuildEntity> temp = SConscript.parse(sconscript, this);
				buildEntities.addAll(temp);
				if (opts.libPath != null && sconscript.getParentFile().equals(opts.libPath)) {
					buildOnlyThose = temp;
				}
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
					System.err.println(e.getMessage());
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
		LibDB.addDefines(defines);
		
		// check whether there are any build entities which need not be built
		for (BuildEntity be : buildEntities) {
			if (!opts.makeFile) {
				be.checkUpToDate();
			} else {
				be.upToDateChecked = true;
				be.upToDate = false;
			}
		}
		
		if (opts.compileOnlyDeps) {
			for (BuildEntity be : buildEntities) {
				be.built = true;
			}
			for (BuildEntity be : buildOnlyThose) {
				be.markNotBuilt();
			}
		}
			
		// build descriptionbuilder first
		/*for (int i = 0; i < buildEntities.size(); i++) {
			BuildEntity be = buildEntities.get(i);
			if (be.name.equals("descriptionbuilder")) {
				build(be, true);
				break;
			}
		}*/
		
		// build entities
		while(true) {
			BuildEntity be = getBuildEntity();
			if (be == null) {
				break;
			}
			while(true) {
				try {
					build(be, false);
					break;
				} catch (Exception e) {
					System.err.print("[" + Thread.currentThread().getId() + "] ");
					e.printStackTrace();
					LibDB.reinit();
					//System.gc();
					printErrorAdvice();
					System.exit(1);
				}
			}
		}

		// wait for GCC Threads to complete
		GCC.waitForThreads();
		
		// completed
		long time = System.currentTimeMillis() - startTime;
		System.out.println("Building successful (" + sourceFiles.toString() + " source files in " + (time/1000) + "s)");
		
		// Store cached data
		storeCachedData();
	}
	
	protected void addDefine(String string) {
		defines.add(string);
	}

	protected void build(BuildEntity be, boolean immediately) throws Exception {
		if (!be.upToDate) {
			be.build(immediately);
			be.built = true;
			sourceFiles.getAndAdd(be.cpps.size() + be.cs.size());
		}
	}

	BuildEntity getBuildEntity() {
		while(true) {
			boolean allBuilt = true;
			for (BuildEntity be : buildEntities) {
				if (!be.built) {
					allBuilt = false;
					if (be.missingDep || be.upToDate || ((!opts.compileTestParts) && be.isTestPart())) {
						be.built = true;
						continue;
					}
					boolean ready = true;
					for (BuildEntity dep : be.dependencies) {
						if (!dep.built) {
							ready = false;
						}
					}
					if (ready) {
						return be;
					}
				}
			}
			if (allBuilt) {
				return null;
			}
		}
	}

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
			be.qt3 |= lib.equals("qt");
			be.qt4 |= lib.equals("qt4");
			return;
		}
		boolean found = false;
		for (BuildEntity be2 : buildEntities) {
			if ((be2 instanceof MCALibrary) && be2.toString().equals(lib)) {
				be.dependencies.add(be2);
				found = true;
				break;
			}
		}
		if (!found && (!optional)) {
			throw new Exception("Not building " + be.name + " due to missing MCA library " + lib);
		}
	}
	
	public boolean accept(File dir, String name) {
		File f = new File(dir.getAbsolutePath() + File.separator + name);
		return (f.isDirectory() || name.equals("SConscript"));
	}
	
	public String getDescriptionBuilderCall() {
		return HOME.getAbsolutePath() + FS + "script" + FS + "description_builder.pl";
	}

	public static void println(String s) {
		System.out.println("[" + Thread.currentThread().getId() + "] " + s);
	}

	public static void printerrln(String s) {
		System.err.println("[" + Thread.currentThread().getId() + "] " + s);
	}
	
	public static void printErrorAdvice() {
		System.out.println("An error was encountered during the build process.");
		System.out.println("Make sure your libdb.txt file is up to date (you might need to call updatelibdb)");
		System.out.println("If you replaced the build tool with a newer version, the turbobuildcache file in your home directory might needs to be deleted.");
		System.out.println("In rare cases, source files need to be compiled separately. These should be added to 'critical.txt' (see help)");
	}
	
	protected void loadCachedData() throws Exception {
		System.out.println("Loading cached data...");
		File includeCacheFile = new File(System.getProperty("user.home") + FS + "turbobuildcache");
		if (includeCacheFile.exists()) {
			try {
				ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(includeCacheFile)));
				includeCache = (SourceCache)ois.readObject();
				ois.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (includeCache == null) {
			includeCache = new SourceCache();
		}
	}
	
	protected void storeCachedData() throws Exception {
		File includeCacheFile = new File(System.getProperty("user.home") + FS + "turbobuildcache");
		System.out.print("Storing cached data...");
		ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(includeCacheFile)));
		oos.writeObject(includeCache);
		oos.close();
		System.out.println(" done");
	}
}
