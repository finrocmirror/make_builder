package tools.turbobuilder;

/**
 * @author max
 *
 * MCA Plugin build entity
 */
public class MCAPlugin extends BuildEntity {

	public MCAPlugin(MakeFileBuilder tb) {
		super(tb);
		try {
			this.extlibs.add(LibDB.getLib("ltdl"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String toString() {
		return "pluginmca2_" + name;
	}
}
