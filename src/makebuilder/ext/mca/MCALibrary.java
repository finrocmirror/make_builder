package makebuilder.ext.mca;

/**
 * @author max
 *
 * MCA Library build entity
 */
public class MCALibrary extends MCABuildEntity {

	public MCALibrary() {
		opts.addOptions("-shared -fPIC");
	}
	
	@Override
	public String getTarget() {
		return "$(TARGET_LIB)/libmca2_" + name + ".so";
	}
}
