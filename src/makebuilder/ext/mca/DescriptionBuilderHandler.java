package makebuilder.ext.mca;

import java.util.HashMap;
import java.util.Map;

import makebuilder.BuildEntity;
import makebuilder.SourceFileHandler;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;

/**
 * @author max
 *
 * Responsible for calling MCA descriptionbuilder on relevant files
 */
public class DescriptionBuilderHandler extends SourceFileHandler.Impl {

	/** Description builder script */
	public final static String DESCRIPTION_BUILDER_BIN = "script/description_builder.pl ";

	/** Contains a makefile target for each build entity with files to call description build upon */
	private Map<BuildEntity, Makefile.Target> descrTargets = new HashMap<BuildEntity, Makefile.Target>();

	@Override
	public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
		if (file.hasExtension("h")) {

			// find _DESCR_ macro
			if (!file.isInfoUpToDate()) {
				for (String s : file.getCppLines()) {
					s = s.trim();
					if (s.startsWith("_DESCR_")) {
						// template headers with _DESCR_ need to be handled differently
						file.mark(s.contains("<") ? "DESCR_TEMPLATE" : "DESCR");
						break;
					}
				}
			}
			
			// template description?
			if (file.hasMark("DESCR_TEMPLATE")) {
				SrcFile target = builder.getTempBuildArtifact(file, "hpp");
				Makefile.Target t = makefile.addTarget(target.relative, false);
				t.addDependency(file.relative);
				t.addDependency(DESCRIPTION_BUILDER_BIN);
				t.addCommand(DESCRIPTION_BUILDER_BIN + file.relative + " > " + target.relative, true);

			// normal description?
			} else if (file.hasMark("DESCR")) {
				BuildEntity be = file.getOwner();
				if (be == null) { // we don't know where generated code belongs
					System.out.println("warning: found DESCR macros in " + file.relative + " but don't know which build entity it belongs to => won't process it");
					return;
				}

				// get or create target
				Makefile.Target target = descrTargets.get(be);
				if (target == null) {
					SrcFile sft = builder.getTempBuildArtifact(be, "cpp", "descriptions"); // sft = "source file target"
					target = makefile.addTarget(sft.relative, true);
					target.addDependency(be.buildFile);
					target.addMessage("Creating " + sft.relative);
					target.addCommand("echo \\/\\/ generated > " + target.getName(), false);
					be.sources.add(sft);
					descrTargets.put(be, target);
				}
				target.addDependency(file);
				target.addCommand(DESCRIPTION_BUILDER_BIN + file.relative + " >> " + target.getName(), false);
			}
		}
	}
}
