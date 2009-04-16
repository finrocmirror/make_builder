package makebuilder.ext.mca.virtualrepo;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import makebuilder.BuildFileLoader;
import makebuilder.MakeFileBuilder;
import makebuilder.SourceFileHandler;
import makebuilder.SourceScanner;
import makebuilder.SrcDir;
import makebuilder.SrcFile;

/**
 * @author max
 *
 * This experimental class builds a virtual mca repository in the /tmp/ folder 
 */
public class VirtualRepositoryBuilder implements Runnable {

	/** Directory for virtual repository */
	public static final String VIRTUAL_REPO_DIR = "/tmp/virtualmca";
	
	public static void main(String[] args) {
		new VirtualRepositoryBuilder().run();
	}
	
	public void run() {
		try {
			MakeFileBuilder dummy = new MakeFileBuilder();
			SourceScanner localRepo = new SourceScanner(MakeFileBuilder.HOME, dummy);
			SourceScanner serverRepo = new SourceScanner(new File("/usr/local/mca"), dummy);
			localRepo.scan(dummy.makefile, new ArrayList<BuildFileLoader>(), new ArrayList<SourceFileHandler>(), false, new String[]{""});
			serverRepo.scan(dummy.makefile, new ArrayList<BuildFileLoader>(), new ArrayList<SourceFileHandler>(), false, new String[]{""});
			
			// create repository
			Runtime.getRuntime().exec("rm -rf " + VIRTUAL_REPO_DIR).waitFor();
			File vmca = new File(VIRTUAL_REPO_DIR);
			vmca.mkdirs();
			File build = new File(MakeFileBuilder.HOME.getAbsolutePath() + "/build");
			File export = new File(MakeFileBuilder.HOME.getAbsolutePath() + "/export");
			build.mkdir();
			export.mkdir();
			createLink(new File(vmca.getAbsolutePath() + "/build"), build);
			createLink(new File(vmca.getAbsolutePath() + "/export"), export);
			
			// build link to every file in server repository
			Map<SrcDir, List<SrcFile>> files = new HashMap<SrcDir, List<SrcFile>>();
			for (SrcDir dir : serverRepo.getAllDirs()) {
				files.put(dir, new ArrayList<SrcFile>());
			}
			for (SrcFile sf : serverRepo.getAllFiles()) {
				files.get(sf.dir).add(sf);
			}
			for (Map.Entry<SrcDir, List<SrcFile>> entry : files.entrySet()) {
				StringBuilder files2 = new StringBuilder();
				for (SrcFile sf : entry.getValue()) {
					files2.append(" ");
					files2.append(sf.absolute.getAbsolutePath());
				}
				File dir = new File(vmca.getAbsolutePath() + "/" + entry.getKey().relative);
				dir.mkdirs();
				if (files2.length() > 0) {
					Runtime.getRuntime().exec("ln -s" + files2.toString() + " " + dir.getAbsolutePath());
				}
			}
			
			// replace outdated links
			for (SrcFile sf : localRepo.getAllFiles()) {
				File f = new File(vmca.getAbsolutePath() + "/" + sf.relative);
				if (!f.exists() || f.length() != sf.absolute.length()) {
					System.out.println("replaced " + f.getName() + " " + f.exists() + " " + f.lastModified() + " " + sf.absolute.lastModified());
					f.getParentFile().mkdirs();
					f.delete();
					createLink(f, sf.absolute);
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void createLink(File linkName, File target) throws Exception {
		Runtime.getRuntime().exec("ln -s " + target.getAbsolutePath() + " " + linkName.getAbsolutePath()).waitFor();
	}
}
