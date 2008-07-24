package tools.turbobuilder;


public class MCAPlugin extends BuildEntity {

	public MCAPlugin(TurboBuilder tb) {
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
