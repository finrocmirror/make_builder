package makebuilder.ext.mca;

import java.io.File;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.SourceScanner;
import makebuilder.SrcDir;
import makebuilder.SrcFile;
import makebuilder.handler.CppHandler;
import makebuilder.handler.CppMerger;
import makebuilder.handler.MakeXMLLoader;
import makebuilder.handler.NvccHandler;
import makebuilder.handler.Qt4Handler;
import makebuilder.util.CodeBlock;

/**
 * @author max
 *
 * MakeFileBuilder customization for MCA
 */
public class MCABuilder extends MakeFileBuilder {

	/** Global definitions - e.g. */
	private final CodeBlock globalDefine = new CodeBlock();

	/** Target directory for libraries, Target directory for binaries */
	public final SrcDir targetLib, targetBin;

	/** Standard compiler options for MCA */
	public static final String MCAOPTS = "-include Makefile.h -Ilibraries -Iprojects -Itools -I. ";

	/** System library installation handler */
	public final MCASystemLibLoader systemInstall;
	
	public MCABuilder() {
		super("export" + FS + opts.getProperty("build"), "build" + FS + opts.getProperty("build"));

		// init target paths
		targetBin = buildPath.getSubDir("bin");
		targetLib = buildPath.getSubDir("lib");
		makefile.addVariable("TARGET_BIN=$(TARGET_DIR)/bin");
		makefile.addVariable("TARGET_LIB=$(TARGET_DIR)/lib");
		
		// init global defines
		globalDefine.add("#define _MCA_VERSION_ \"2.4.1\"");
		globalDefine.add("#define _MCA_DEBUG_");
		globalDefine.add("#define _MCA_PROFILING_");
		globalDefine.add("#define _MCA_LINUX_");
		
		// init handlers
		addLoader(new SConscriptParser());
		addLoader(new MakeXMLLoader(MCALibrary.class, MCAPlugin.class, MCAProgram.class));
		addHandler(new Qt4Handler());
		addHandler(new NvccHandler("-include Makefile.h"));
		addHandler(new DescriptionBuilderHandler());
		if (getOptions().combineCppFiles) {
			addHandler(new CppMerger("#undef LOCAL_DEBUG", "#undef MODULE_DEBUG"));
		}
		addHandler(new CppHandler("-Wall -Wwrite-strings -Wno-unknown-pragmas -include Makefile.h", 
				"-lm -lz -lcrypt -lpthread -lstdc++ -L" + targetLib.relative + " -Wl,-rpath," + targetLib.relative, 
				!opts.combineCppFiles));

		// is MCA installed system-wide?
		systemInstall = new MCASystemLibLoader();
		addHandler(systemInstall);
		
		// generate library info files?
		if (getOptions().containsKey("systeminstall")) {
			makefile.addVariable("TARGET_INFO=$(TARGET_DIR)/info");
			makefile.addVariable("TARGET_INCLUDE=$(TARGET_DIR)/include");
			makefile.addVariable("TARGET_ETC=$(TARGET_DIR)/etc");
			addHandler(new LibInfoGenerator("$(TARGET_INFO)"));
			addHandler(new HFileCopier("$(TARGET_INCLUDE)"));
			addHandler(new EtcDirCopier("$(TARGET_ETC)"));
		}
	}

	@Override
	public void setDefaultIncludePaths(SrcDir dir, SourceScanner sources) {
		dir.defaultIncludePaths.add(sources.findDir(".", true));
		dir.defaultIncludePaths.add(sources.findDir("projects", true));
		dir.defaultIncludePaths.add(sources.findDir("libraries", true));
		dir.defaultIncludePaths.add(sources.findDir("tools", true));
		
		// add system include paths - in case MCA is installed system-wide
		if (systemInstall.systemInstallExists) {
			String p = systemInstall.MCA_SYSTEM_INCLUDE.getAbsolutePath();
			dir.defaultIncludePaths.add(sources.findDir(p, true));
			dir.defaultIncludePaths.add(sources.findDir(p + "/projects", true));
			dir.defaultIncludePaths.add(sources.findDir(p + "/libraries", true));
			dir.defaultIncludePaths.add(sources.findDir(p + "/tools", true));
		}
		
		if (dir.relative.startsWith(tempBuildPath.relative)) {
			return;
		}
		dir.defaultIncludePaths.add(sources.findDir(tempBuildPath.relative + FS + "projects", true));
		dir.defaultIncludePaths.add(sources.findDir(tempBuildPath.relative + FS + "libraries", true));
		dir.defaultIncludePaths.add(sources.findDir(tempBuildPath.relative + FS + dir.relative, true));
		SrcDir parent = dir;
		while(parent.relative.contains(FS) && (parent.relative.charAt(0) != '/')) { // add all parent directories... not nice but somehow required :-/
			dir.defaultIncludePaths.add(parent);
			parent = parent.getParent();
		}
	}

	@Override
	public String[] getSourceDirs() {
		return new String[]{"libraries", "projects", "tools"};
	}
	
	public void run() {
		super.run();
		
		// create additional defines
		for (BuildEntity be : buildEntities) {
			if (!be.missingDep && be instanceof MCALibrary) {
				// _LIB_MCA2_COMPUTER_VISION_BASE_PRESENT_
				globalDefine.add("#define _LIB_MCA2_" + be.name.toUpperCase() + "_PRESENT_");
			}
		}
		
		// write defines to makefile.h
		globalDefine.add("");
		try {
			globalDefine.writeTo(new File("Makefile.h"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public SrcFile getTempBuildArtifact(SrcFile source, String targetExtension) {
		SrcDir targetDir = tempBuildPath.getSubDir(source.dir.relative);
		if (source.getExtension().equals("ui")) {
			return sources.registerBuildProduct(targetDir.relative + FS + "ui_" + source.getRawName() + ".h");
		} else if (targetExtension.equals("hpp")) { // description builder template
			return sources.registerBuildProduct(targetDir.relative + FS + "descr_h_" + source.getRawName() + ".hpp");
		}
		return sources.registerBuildProduct(targetDir.relative + FS + source.getRawName() + "." + targetExtension);
	}
	
	public SrcFile getTempBuildArtifact(BuildEntity source, String targetExtension, String suggestedPrefix) {
		return sources.registerBuildProduct(tempPath + FS + source.name + "_" + suggestedPrefix + "." + targetExtension);
	}

}
