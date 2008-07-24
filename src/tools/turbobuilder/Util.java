package tools.turbobuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 *
 */
public class Util {

	public static List<String> readLinesWithoutComments(File f, boolean setLineMacro) throws Exception {
		char[] data = Files.readStreamFully(new InputStreamReader(new FileInputStream(f), BuildEntity.INPUT_CHARSET));
		
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
	
	static String normalize(String s) {
//		s = s.trim().replace("\t", " ");
//		if (s.contains("//")) {
//			s = s.substring(0, s.indexOf("//")).trim();
//		}
//		while(s.contains("/*")) {
//			if (s.contains("*/")) {
//				s = s.substring(0, s.indexOf("/*")) + s.substring(s.indexOf("*/") + 2);
//			} else {
//				s = s.substring(0, s.indexOf("/*")).trim();
//			}
//		}
		return s.trim();
	}
	
	enum State { NEUTRAL, BLOCKED, ID, STRING1, STRING2 }
	
	static String findPossibleDefineTokens(String s, TokenCallback tc, boolean lowerCaseTokens) {
		State state = State.NEUTRAL;
		int start = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			
			// in String?
			if (c == '\\' && (state == State.STRING1 || state == State.STRING2)) {
				i++;
				continue;
			}
			if (!(state == State.STRING2) && c == '"') {
				state = (state == State.STRING1 ? State.NEUTRAL : State.STRING1);
			}
			if (!(state == State.STRING1) && c == '\'') {
				state = (state == State.STRING2 ? State.NEUTRAL : State.STRING2);
			}
			if (state == State.STRING1 || state == State.STRING2) {
				continue;
			}
			
			if ((!lowerCaseTokens) && c >= 'a' && c <= 'z') {
				if (state == State.NEUTRAL) {
					try {
						if (c == 'u' && s.charAt(i+1) == 'n' && s.charAt(i+2) == 'i' && s.charAt(i+3) == 'x' && !Character.isJavaIdentifierPart(s.charAt(4))) {
							s = possiblyReplace(s, i, i + 4, "unix", tc);
						}
						if (c == 'i' && s.charAt(i+1) == '3' && s.charAt(i+2) == '8' && s.charAt(i+3) == '6' && !Character.isJavaIdentifierPart(s.charAt(4))) {
							s = possiblyReplace(s, i, i + 4, "i386", tc);
						}
						if (c == 'l' && s.charAt(i+1) == 'i' && s.charAt(i+2) == 'n' && s.charAt(i+3) == 'u' && s.charAt(i+4) == 'x' && !Character.isJavaIdentifierPart(s.charAt(4))) {
							s = possiblyReplace(s, i, i + 5, "linux", tc);
						}
					} catch (Exception e) {}
				}
				state = State.BLOCKED;
				continue;
			}
			if (Character.isJavaIdentifierPart(c)) {
				if (state == State.BLOCKED) {
					continue;
				}
				if (state == State.NEUTRAL) {
					if (Character.isJavaIdentifierStart(c)) {
						state = State.ID;
						start = i;
						continue;
					} else {
						state = State.BLOCKED;
					}
				}
				if (state == State.ID) {
					continue;
				}
			}
			if (state == State.ID) {
				String define = s.substring(start, i);
				String tmp = possiblyReplace(s, start, i, define, tc);
				if (!tmp.equals(s)) {
					i = start - 1;
				}
				s = tmp;
			}
			state = State.NEUTRAL;
		}
		
		int i = s.length();
		if (state == State.ID) {
			String define = s.substring(start, i);
			String tmp = possiblyReplace(s, start, i, define, tc);
			if (!tmp.equals(s)) {
				i = start - 1;
			}
			s = tmp;
			if (i < s.length()) {
				s = findPossibleDefineTokens(tmp, tc, lowerCaseTokens);
			}
		}
		
		return s;
	}
	
	private static String possiblyReplace(String s, int start, int i, String def, TokenCallback tc) {
		String result = tc.tokenCallback(def);
		if (result != null) {
			s = s.substring(0, start) + result + s.substring(i);
		}
		return s;
	}
	
	public interface TokenCallback {
		String tokenCallback(String token);
	}
	
	public static File getFileInEtcDir(String filename) {
		return new File(new File(Files.getRootDir(LibDB.class)).getParent() + File.separator + "etc" + File.separator + filename);
	}
}
