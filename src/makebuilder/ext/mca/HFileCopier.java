package makebuilder.ext.mca;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SrcFile;

/**
 * @author max
 *
 * Copies h files from libraries and tools (including generated ones in build) to export/.../include
 * (MCA-specific; only needed for system-installs) 
 */
public class HFileCopier extends SourceFileHandler.Impl {

	/** Have copy targets been created in Makefile */
	private boolean copied = false;
	
	/** Destination path */
	private final String destPath;
	
	public HFileCopier(String destPath) {
		this.destPath = destPath;
	}
	
	@Override
	public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {
		// do this only once
		if (copied) {
			return;
		}
		copied = true;
		
		for (SrcFile sf : builder.getSources().getAllFiles()) {
			if (sf.hasExtension("h", "hpp")) {
				String tmp = sf.relative;
				if (tmp.startsWith(builder.tempBuildPath.relative)) {
					tmp = tmp.substring(builder.tempBuildPath.relative.length() + 1);
				}
				if (tmp.startsWith("libraries") || tmp.startsWith("tools")) {
					String target = destPath + "/" + tmp.substring(tmp.indexOf('/') + 1);
					Makefile.Target t = makefile.addTarget(target, false);
					t.addDependency(sf);
					t.addCommand("cp " + sf.relative + " " + target, true);
					t.addToPhony("sysinstall", "libs", "tools");
				}
			}
		}
	}
}
