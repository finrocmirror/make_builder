package tools.turbobuilder;

/**
 * @author max
 *
 * MCA Library build entity
 */
public class MCALibrary extends BuildEntity {

	public MCALibrary(MakeFileBuilder tb) {
		super(tb);
	}

	public String toString() {
		return "mca2_" + name;
	}
	
	/**
	 * @return Raw Compiled Library name (lib*.so)
	 */
	public String getLibName() {
		return "lib" + toString() + ".so";
	}
}
