package tools.turbobuilder;

/**
 * @author max
 *
 * Some helper functions to access the gcc compiler directly...
 * Most of it was deleted, because not needed any longer
 */
public class GCC {

	/**
	 * @return GCC version as string
	 */
	public static String getGCCVersion() {
		try {
			Process p = Runtime.getRuntime().exec("cpp --version");
			p.waitFor();
			for (String s : Files.readLines(p.getInputStream())) {
				s = s.substring(s.indexOf(")") + 1);
				return s.substring(0, s.indexOf("(")).trim();
			}
		} catch (Exception e) {
			// no gcc version found
		}
		return null;
	}
}
