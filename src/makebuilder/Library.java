package makebuilder;

/**
 * @author max
 *
 * This class is a build entity that will be compiled to a (shared) library.
 */
public class Library extends BuildEntity {

	@Override
	public String getTarget() {
		return "($TARGET_DIR)/lib" + name + ".so";
	}

}
