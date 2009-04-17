package makebuilder.ext.mca;

import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;

/**
 * @author max
 *
 * Copies etc directories from libraries (including generated ones in build) to export/.../etc
 * (MCA-specific; only needed for system-installs)  
 */
public class EtcDirCopier extends SourceFileHandler.Impl {

	/** Destination path */
	private final String destPath;
	
	public EtcDirCopier(String destPath) {
		this.destPath = destPath;
	}
	
	@Override
	public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
		if (file.dir.relative.endsWith("/etc") || file.dir.relative.contains("/etc/")) {
			if (file.relative.startsWith("libraries")) {
				String target = destPath + "/" + file.relative.substring(file.relative.indexOf('/') + 1);
				Makefile.Target t = makefile.addTarget(target, false);
				t.addDependency(file);
				t.addCommand("cp " + file.relative + " " + target, true);
				t.addToPhony("sysinstall", "libs", "tools");
			}
		}
	}
}
