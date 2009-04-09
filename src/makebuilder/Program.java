package makebuilder;

/**
 * @author max
 *
 * This class is a build entity that will be compiled to an executable.
 */
public class Program extends BuildEntity {

	@Override
	public String getTarget() {
		return "($TARGET_DIR)/" + name;
	}

}
