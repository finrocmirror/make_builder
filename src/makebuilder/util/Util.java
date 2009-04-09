package makebuilder.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import makebuilder.MakeFileBuilder;
import makebuilder.libdb.LibDB;

/**
 * @author max
 *
 * Various utility functions
 */
public class Util {

	/**
	 * Read all lines from a C++/Java file and remove all comments and strings
	 * 
	 * @param f File
	 * @param setLineMacro set ling macros (currently only false)
	 * @return List with lines that were read
	 */
	public static List<String> readLinesWithoutComments(File f, boolean setLineMacro) throws Exception {
		char[] data = Files.readStreamFully(new InputStreamReader(new FileInputStream(f), MakeFileBuilder.INPUT_CHARSET));
		
		List<String> lines = new ArrayList<String>();
		String curFile = f.getAbsolutePath();
		StringBuilder sb = new StringBuilder();
		
		boolean comment1 = false;
		boolean comment2 = false;
		final int NO = 0, YES = 1, ELSE = 2; 
		int preProcessorComment = NO;
		boolean string1 = false;
		boolean string2 = false;
		boolean notEmpty = false;
		boolean skipLineMacro = false;
		int curLine = 1;
		for (int i = 0; i < data.length; i++) {
			char c = data[i];
			char c1 = i < data.length - 1 ? data[i + 1] : ' ';
			if (c == '\n') {
				comment1 = false;
				if (notEmpty) {
					String s = sb.toString();
					if (s.trim().equals("#if 0")) {
						preProcessorComment = YES;
					}
					if (preProcessorComment != YES) {
						if (setLineMacro && (!skipLineMacro)) {
							lines.add("#line " + curLine + " \"" + curFile + "\"");
						}
						lines.add(s);
						skipLineMacro = s.endsWith("\\"); 
					}
					if (preProcessorComment != NO) {
						if (s.trim().equals("#endif")) {
							preProcessorComment = NO;
						}
						if (s.trim().equals("#else")) {
							preProcessorComment = ELSE;
						}
					}
				}
				sb.setLength(0);
				notEmpty = false;
				curLine++;
				continue;
			}

			if (comment1) {
				continue;
			}
			if (comment2) {
				if (c == '*' && c1 == '/') {
					comment2 = false;
					i++;
				}
				continue;
			}
			if (!string1 && c == '/' && c1 == '/') {
				comment1 = true;
				i++;
				continue;
			}
			if (!string1 && c == '/' && c1 == '*') {
				comment2 = true;
				i++;
				continue;
			}
			if ((string1 || string2) && c == '\\') {
				sb.append(c);
				sb.append(c1);
				i++;
				continue;
			}
			if (!string2 && c == '"') {
				string1 = !string1;
			}
			if (!string1 && c == '\'') {
				string2 = !string2;
			}
			
			if (c == '\t') {
				c = ' ';
			}
			if (c != ' ') {
				notEmpty = true;
			}
			sb.append(c);
		}
		
		// add last line
		if (notEmpty) {
			lines.add(sb.toString());
			sb.setLength(0);
		}
		
		return lines;
	}

	/**
	 * Return File in make_builder etc directory 
	 * 
	 * @param filename file name without path
	 * @return file with path
	 */
	public static File getFileInEtcDir(String filename) {
		return new File(new File(Files.getRootDir(LibDB.class)).getParent() + File.separator + "etc" + File.separator + filename);
	}

	/**
	 * @return Name of user currently logged in (only works on Unix/Linux)
	 */
	public static String whoami() {
		try {
			Process p = Runtime.getRuntime().exec("whoami");
			return Files.readLines(p.getInputStream()).get(0);
		} catch (Exception e) {
			e.printStackTrace();
			return "" + System.currentTimeMillis();
		}
	}
}
