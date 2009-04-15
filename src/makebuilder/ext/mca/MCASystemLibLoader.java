package makebuilder.ext.mca;

import java.io.File;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;
import makebuilder.util.Files;

/**
 * @author max
 *
 * Handles system-wide installation of MCA
 */
public class MCASystemLibLoader extends SourceFileHandler.Impl {

	/** Have system libraries been loaded ? */ 
	boolean loaded = false;
	
	/** system MCA include path if MCA is installed system-wide - otherwise null */
	public final File MCA_SYSTEM_INCLUDE; 

	/** MCA library-info path if MCA is installed system-wide - otherwise null */
	public final File MCA_SYSTEM_INFO; 

	/** MCA library path if MCA is installed system-wide - otherwise null */
	public final File MCA_SYSTEM_LIB; 
	
	/** Has MCA been installed system-wide? */
	public final boolean systemInstallExists;
	
	/** relative mca system-include path */
	public static final String MCA_SYSTEM_INCLUDE_DIR = "/include/mca";

	/** relative mca system-lib-info path */
	public static final String MCA_SYSTEM_INFO_DIR = "/share/mca";

	public MCASystemLibLoader() {
		File h1 = new File("/usr" + MCA_SYSTEM_INCLUDE_DIR);
		File h2 = new File("/usr/local" + MCA_SYSTEM_INCLUDE_DIR);
		File i1 = new File("/usr" + MCA_SYSTEM_INFO_DIR);
		File i2 = new File("/usr/local" + MCA_SYSTEM_INFO_DIR);
		MCA_SYSTEM_INCLUDE = h1.exists() ? h1 : (h2.exists() ? h2 : null);
		MCA_SYSTEM_INFO = i1.exists() ? i1 : (i2.exists() ? i2 : null);
		systemInstallExists = (MCA_SYSTEM_INCLUDE != null && MCA_SYSTEM_INFO != null);
		MCA_SYSTEM_LIB = systemInstallExists ? new File(MCA_SYSTEM_INFO.getParentFile().getParent() + "/lib") : null;
	}
	
	@Override
	public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
		
		// Do this only once
		if (loaded || (!systemInstallExists)) {
			return;
		}
		loaded = true;
		
		// find/load all libraries that contain system information
		for (File f : MCA_SYSTEM_INFO.listFiles()) {
			if (f.getName().endsWith(LibInfoGenerator.EXT)) {
				String libName = f.getName().substring(0, f.getName().lastIndexOf("."));
				String libName2 = File.separator + libName;
				boolean exists = false;
				for (BuildEntity be : builder.buildEntities) {
					exists |= be.getTarget().endsWith(libName2);
				}
				if (!exists) {
					SystemLibrary be = new SystemLibrary();
					be.name = libName.substring(8, libName.lastIndexOf("."));
					be.buildFile = scanner.registerBuildProduct(f.getAbsolutePath());
					for (String extlib : Files.readLines(f).get(0).split("\\s")) {
						if (extlib.trim().length() > 0) {
							be.libs.add(extlib);
						}
					}
					be.targetName = MCA_SYSTEM_LIB + "/" + libName;
					builder.buildEntities.add(be);
				}
			}
		}
	}

	private class SystemLibrary extends MCALibrary {
	
		private String targetName;
		
		public String getTarget() {
			return targetName;
		}
		
		public void initTarget(Makefile makefile) {
			target = makefile.DUMMY_TARGET;
		}
	}
}
