package makebuilder;

import java.util.Properties;

/**
 * @author max
 *
 * Builder (command line) options
 * Class is used for parsing and managing them.
 */
public class Options extends Properties {
	
	/** UID */
	private static final long serialVersionUID = 1234625724856L;
	
	/** combine cpp files in one large file? */
	public boolean combineCppFiles;
	
	/** Main class to instantiate (optional) */ 
	Class<? extends Runnable> mainClass = MakeFileBuilder.class;
	
	/** String with concatenated command line arguments */
	public final String args;
	
//	/** compile binaries to shared libraries? */
//	boolean compileBinsToSO = false;
	
	/**
	 * @param args Command line arguments
	 */
	@SuppressWarnings("unchecked")
	public Options(String[] args) {
		String tmp = "";
		
		// parse command line parameters
		for (String s : args) {
			tmp += " " + s;
			if (s.startsWith("--combine") || s.startsWith("--hugecpps")) {
				combineCppFiles = true;
			} else if (s.startsWith("--")){
				String key = s.contains("=") ? s.substring(2, s.indexOf("=")) : s.substring(2);
				String value = s.contains("=") ? s.substring(s.indexOf("=") + 1) : "N/A";
				this.put(key, value);
			} else {
				try {
					mainClass = (Class<? extends Runnable>)Class.forName(s);
				} catch (Exception e) {
					System.out.println("Cannot find specified main class: " + s);
				}
			}
//			if (s.startsWith("--so")) {
//				compileBinsToSO = true;
//			}
		}
		
		this.args = tmp.trim();
	}
}
