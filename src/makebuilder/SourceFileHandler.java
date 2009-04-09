package makebuilder;

/**
 * @author max
 *
 * Handles and transforms source files in some way.
 * 
 * This is a central abstraction in the MakeBuilder.
 */
public interface SourceFileHandler {

	/**
	 * Initialize content handler
	 * 
	 * @param makefile Makefile (e.g. for adding variables and stuff)
	 */
	public void init(Makefile makefile);
	
	/**
	 * Scan source files for relevant information (optional operation)
	 * (e.g. QT macros, DESCR macros, process build files)
	 * 
	 * @param file File to process
	 * @param makefile Makefile - targets may already be added for source files not directly dependent on build entities 
	 * @param scanner Source scanner instance
	 * @param builder Builder instance
	 */
	public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception;
	
	/**
	 * Process/transform/build build entity (optional operation)
	 * (quite abstract)
	 * (Add targets to Makefile, remove processed files from build entity - add new ones (target results))  
	 * 
	 * @param be Build Entity to process
	 * @param makefile Makefile to add targets to
	 * @param builder Builder instance
	 */
	public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception;
	
	/**
	 * @author max
	 * 
	 * Empty implementation of above
	 */
	public static class Impl implements SourceFileHandler {

		@Override
		public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {}

		@Override
		public void init(Makefile makefile) {}

		@Override
		public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {}
	}
}
