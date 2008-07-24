package tools.turbobuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author max
 *
 */
public class MakeFileHCache implements Comparator<Object> {

	HFile[] hs; // sorted by reverse relative file names
	List<HFile> candidates = new ArrayList<HFile>();
	static final List<String> includeDirs = Arrays.asList(new String[]{"libraries", "", "tools", "projects"});
	public static final char FS = File.separator.toCharArray()[0];
	
	public MakeFileHCache(String home, CodeBlock makefile, String descrBuilderBin, String descrBuilderBinExt, String buildBase) throws Exception {
		List<File> hfiles = new ArrayList<File>();
		hfiles.addAll(Files.getAllFiles(new File(home + "/libraries"), new String[]{"h", "H", "hpp", "ui"}, false, false));
		hfiles.addAll(Files.getAllFiles(new File(home + "/projects"), new String[]{"h", "H", "hpp", "ui"}, false, false));
		hfiles.addAll(Files.getAllFiles(new File(home + "/tools"), new String[]{"h", "H", "hpp", "ui"}, false, false));

		// parse
		List<HFile> hfiles2 = new ArrayList<HFile>();
		for (int i = 0; i < hfiles.size(); i++) {
			if (!hfiles.get(i).getPath().endsWith(".ui")) {
				HFile hfile = new HFile(hfiles.get(i), home);
				hfiles2.add(hfile);
				hfile.parse(hfiles2, makefile, descrBuilderBin, descrBuilderBinExt, buildBase);
			} else {
				// ui file
				String c = hfiles.get(i).getAbsolutePath().substring(home.length() + 1);
				BufferedReader br = new BufferedReader(new FileReader(c));
				boolean qt4 = br.readLine().contains("version=\"4");
				br.close();
				
				String raw = c.substring(0, c.lastIndexOf("."));
				String qtCall = LibDB.getLib(qt4 ? "uic-qt4" : "uic-qt3").options.trim();
				if (!qt4) {
					String h = buildBase + FS + raw + ".h";
					String cc = buildBase + FS + c + ".cc";
					makefile.add(h + " : " + c);
					MakeFileBuilder.mkdir(makefile, h);
					makefile.add("\t" + qtCall + " " + c + " > " + h);

					makefile.add(cc + " : " + h + " " + c);
					makefile.add("\t" + qtCall + " -impl " + h + " " + c + " > " + cc);
					
					// add virtual h
					HFile hf = new HFile(h);
					hf.moc = true;
					hfiles2.add(hf);
				} else {
					String h = buildBase + FS + raw + ".h";
					h = h.substring(0, h.lastIndexOf(FS) + 1) + "ui_" + h.substring(h.lastIndexOf(FS) + 1);
					makefile.add(h + " : " + c);
					MakeFileBuilder.mkdir(makefile, h);
					makefile.add("\t" + qtCall + " " + c + " -o " + h);

					// add virtual h
					HFile hf = new HFile(h);
					hf.moc = true;
					hfiles2.add(hf);
				}
			}
		}
		
		// create h file objects
		hs = hfiles2.toArray(new HFile[0]);
		Arrays.sort(hs, this);
		
		// read and parse
		for (HFile h : hs) {
			h.parse2();
		}
	}
	
	public int compare(Object o1, Object o2) {
		return o1.toString().compareTo(o2.toString());
	}

	
	public HFile findInclude(String inc, String curDir) {
		
		// efficiently find candidates
		String incRev = new StringBuilder(inc).reverse().toString();
		candidates.clear();
		int result = Arrays.binarySearch(hs, incRev, this);
		if (result >= 0) {
			candidates.add(hs[result]);
			result++;
		} else {
			result = -result;
			result--;
		}
		while(result < hs.length && hs[result].relFile.endsWith(inc)) {
			candidates.add(hs[result]);
			result++;
		}
		
		// evaluate candidates
		String exactMatch = curDir + FS + inc;  // best
		HFile pathStart = null; int pathMatch = 0; // 2nd best
		HFile any = null; int anyLen = Integer.MAX_VALUE; // 3rd best
		
		for (HFile hf : candidates) {
			if (hf.relFile.equals(exactMatch)) {
				return hf;
			}
			
			// How many paths are in common at beginning?
			int matchCount = 1;
			for (int i = 0; i < curDir.length(); i++) {
				char c = curDir.charAt(i);
				if (c != hf.relFile.charAt(i)) {
					matchCount--;
					break;
				} else if (c == FS) {
					matchCount++;
				}
			}
			if (matchCount > pathMatch) {
				pathStart = hf;
				pathMatch = matchCount;
			}
			
			if (hf.fsCount < anyLen) {
				any = hf;
				anyLen = hf.fsCount;
			}
		}
		
		return (pathStart != null) ? pathStart : any;
	}
	
	public class HFile {

		String relFile;
		String relFileReverse;
		int fsCount;
		File file;
		List<HFile> includes = new ArrayList<HFile>();
		List<String> includesTemp = new ArrayList<String>();
		boolean descr = false;
		boolean moc = false;
		boolean template = false;
		String templateGenHName;
		boolean guarded = false;
		boolean empty = true;

		public HFile(String virtualFileName) {
			relFile = virtualFileName;
			relFileReverse = new StringBuilder(relFile).reverse().toString();
			for (int i = 0; i < relFile.length(); i++) {
				if (relFile.charAt(i) == FS) {
					fsCount++;
				}
			}
		}
		
		public void parse2() {
			String curDir = relFile.substring(0, relFile.lastIndexOf(FS));
			for (String inc : includesTemp) {
				HFile inch = findInclude(inc, curDir);
				if (inch != null) {
					includes.add(inch);
				}
			}
		}

		public HFile(File f, String home) {
			this(f.getAbsolutePath().substring(home.length() + 1));
			file = f;
		}
		
		public String toString() {
			return relFileReverse;
		}

		public void parse(List<HFile> hfiles2, CodeBlock makefile, String descrBuilderBin, String descrBuilderBinExt, String buildBase) throws Exception {
			if (file == null) {
				return;
			}
			boolean classPassed = false;
			
			for (String s : Util.readLinesWithoutComments(file, false)) {
				if (s == null) {
					break;
				}
				s = s.trim();
				String include = getIncludeString(s);
				if (include != null) {
					includesTemp.add(include);
					continue;
				}
					
				if (!descr && s.startsWith("_DESCR_")) {
					//result.add("\t" + "MCAHOME=" + HOME.getAbsolutePath() + " " + targetBin + FS + "descriptionbuilder " + inc + " >> " + tempFileCpp);
					descr = true;
				}
				
				if (!guarded && (s.startsWith("#if") || s.startsWith("# if") || s.startsWith("#pragma once"))) {
					guarded = true;
				}
					
				if (!moc && (s.contains("Q_OBJECT") || s.contains("Q_PROPERTY") || s.contains("Q_CLASSINFO"))) {
					moc = true;
				}

				if (!classPassed && s.startsWith("template <")) {
					template = true;
				}
				
				if (!classPassed && s.startsWith("class")) {
					classPassed = true;
				}
				
				empty &= s.length() <= 1;
			}
			
			if (template && descr) {
				String tmp = buildBase + FS + relFile.substring(0, relFile.lastIndexOf(".")) + ".hpp";
				String templateGenHName = tmp.substring(0, tmp.lastIndexOf(FS) + 1) + "descr_h_" + tmp.substring(tmp.lastIndexOf(FS) + 1);
				hfiles2.add(new HFile(templateGenHName));
				makefile.add(templateGenHName + " : " + relFile + " " + descrBuilderBin);
				makefile.add("\tmkdir -p " + templateGenHName.substring(0, templateGenHName.lastIndexOf(FS)));
				makefile.add("\t" + descrBuilderBinExt + relFile + " > " + templateGenHName);
			}
			
			if (!empty && !guarded && !relFile.endsWith(".hpp")) {
				//System.out.println("warning: " + relFile + " not guarded");
				//System.out.println(relFile);
			}
		}

		@Override
		public int hashCode() {
			return relFile.hashCode();
		}
	}
	
	public static String getIncludeString(String s) throws Exception {
		if (s.startsWith("#")) {
			s = s.substring(1).trim();
			
			// relevant include statement ?
			if (s.startsWith("include ")) {
				s = s.substring(8).trim();
				if (s.startsWith("\"")) {
					return s.substring(1, s.lastIndexOf("\""));
				} else if (s.startsWith("<")) {
					return s.substring(1, s.indexOf(">"));
				} else {
					throw new Exception("Error getting include string");
				}
			}
		}
		return null;
	}
	
	public HFile getInclude(String s, String curDir) throws Exception {
		String inc = getIncludeString(s);
		if (inc != null) {
			return findInclude(inc, curDir);
		}
		return null;
	}
}
