package makebuilder;

import java.util.List;

/**
 * @author max
 *
 * Any class that loads build files (make.xml, SConscript etc.)
 * only needs to implement this interface
 */
public interface BuildFileLoader {

	/**
	 * Called for every file in source directory.
	 * Loader should process it, if it finds it interesting and should write resulting build entities
	 * to result list.
	 * 
	 * @param file Current source file
	 * @param result List with results - should only be used to add new build entities
	 * @param scanner SourceScanner instance
	 * @param builder MakeFileBuilder instance
	 */
	public void process(SrcFile file, List<BuildEntity> result, SourceScanner scanner, MakeFileBuilder builder) throws Exception;
}
