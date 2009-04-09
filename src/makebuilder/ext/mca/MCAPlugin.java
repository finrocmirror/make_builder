package makebuilder.ext.mca;

import makebuilder.libdb.LibDB;

/**
 * @author max
 *
 * MCA Plugin build entity
 */
public class MCAPlugin extends MCABuildEntity {

	public MCAPlugin() {
		try {
			this.extlibs.add(LibDB.getLib("ltdl"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		opts.addOptions("-shared -fPIC");
	}

	@Override
	public String getTarget() {
		return "$(TARGET_LIB)/pluginmca2_" + name + ".so";
	}
}
