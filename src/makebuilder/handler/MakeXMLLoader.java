package makebuilder.handler;

import java.util.List;

import makebuilder.BuildEntity;
import makebuilder.BuildFileLoader;
import makebuilder.MakeFileBuilder;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;

/**
 * @author max
 *
 * This handler processes/loads make.xml files and creates BuildEntity instances for them
 */
public class MakeXMLLoader implements BuildFileLoader {

	/** build entity subclasses known to loader and instantiated when their simple name is found in XML tag */
	private final Class<?>[] buildEntityClasses;
	
	public MakeXMLLoader(Class<?>... buildEntityClasses) {
		this.buildEntityClasses = buildEntityClasses;
	}

	@Override
	public void process(SrcFile file, List<BuildEntity> result, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
	}
}
