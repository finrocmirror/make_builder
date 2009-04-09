package makebuilder.handler;

import java.util.HashMap;
import java.util.Map;

import makebuilder.BuildEntity;
import makebuilder.SourceFileHandler;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;
import makebuilder.libdb.LibDB;

/**
 * @author max
 *
 * Handler for qt4 files.
 * 
 * Handles .ui files as well as headers requiring call to moc 
 */
public class Qt4Handler extends SourceFileHandler.Impl {

	/** moc executable */
	private final String MOC_CALL;
	
	/** uic executable */
	private final String UIC_CALL;
	
	/** Contains a makefile target for each build entity with files to moc */
	private Map<BuildEntity, Makefile.Target> mocTargets = new HashMap<BuildEntity, Makefile.Target>();
	
	public Qt4Handler() {
		try {
			MOC_CALL = LibDB.getLib("moc-qt4").options.trim();
			UIC_CALL = LibDB.getLib("uic-qt4").options.trim();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
		if (file.hasExtension("h", "hpp")) {

			// find qt macros
			if (!file.isInfoUpToDate()) {
				for (String s : file.getCppLines()) {
					if (s.contains("Q_OBJECT") || s.contains("Q_PROPERTY") || s.contains("Q_CLASSINFO")) {
						file.mark("moc");
						break;
					}
				}
			}
			
			// moc file?!
			if (file.hasMark("moc")) {  
				BuildEntity be = file.getOwner();
				if (be == null) { // we don't know where generated code belongs
					System.out.println("warning: found qt macros in " + file.relative + " but don't know which build entity it belongs to => won't process it");
					return;
				}

				// get or create target
				Makefile.Target target = mocTargets.get(be);
				if (target == null) {
					SrcFile sft = builder.getTempBuildArtifact(be, "cpp", "qt_generated"); // sft = "source file target"
					target = makefile.addTarget(sft.relative, true);
					target.addDependency(be.buildFile);
					target.addMessage("Creating " + sft.relative);
					target.addCommand("echo \\/\\/ generated > " + target.getName(), false);
					be.sources.add(sft);
					mocTargets.put(be, target);
				}
				target.addDependency(file);
				target.addCommand(MOC_CALL + " " + file.relative + " >> " + target.getName(), false);
			}
			
		} else if (file.hasExtension("ui")) { // run uic?
			SrcFile hdr = builder.getTempBuildArtifact(file, "h");
			Makefile.Target t = makefile.addTarget(hdr.relative, false);
			t.addDependency(file);
			t.addCommand(UIC_CALL + " " + file.relative + " -o " + hdr.relative, true);
		}
	}
}
