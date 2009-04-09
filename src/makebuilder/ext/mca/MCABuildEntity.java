package makebuilder.ext.mca;

import makebuilder.BuildEntity;
import makebuilder.Makefile;

/**
 * @author max
 *
 * MCA build entity.
 * 
 * This can be an MCA program or an MCA library.
 * Objects of this class are the result of parsing MCA2 SConscripts.
 */
public abstract class MCABuildEntity extends BuildEntity {

	@Override
	public void initTarget(Makefile makefile) {
		super.initTarget(makefile);
		String rootDir2 = getRootDir().relative;
		boolean project = rootDir2.startsWith("projects");
		boolean lib = rootDir2.startsWith("libraries");
		boolean tool = rootDir2.startsWith("tools");
		if (lib || tool) {
			target.addToPhony("libs");
		}
		if (tool) {
			target.addToPhony("tools");
		}
		if (lib) {
			target.addToPhony(rootDir2.substring(rootDir2.lastIndexOf(FS) + 1), "tools");
		}
		if (project || tool) {
			String projectx = rootDir2.substring(rootDir2.indexOf(FS) + 1);
			if (projectx.contains(FS)) {
				projectx = projectx.substring(0, projectx.indexOf(FS));
			}
			target.addToPhony(projectx, "tools");
		}
	}
}
