package makebuilder.ext.mca;

/**
 * @author max
 *
 * MCA executable build entity
 */
public class MCAProgram extends MCABuildEntity {

	@Override
	public String getTarget() {
		return "$(TARGET_BIN)/" + name;
	}
}
