package makebuilder.ext.mca;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import makebuilder.BuildEntity;
import makebuilder.BuildFileLoader;
import makebuilder.MakeFileBuilder;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;
import makebuilder.handler.MakeXMLLoader;
import makebuilder.util.Files;

/**
 * @author max
 *
 * This classes parses SConscripts
 */
public class SConscriptParser implements BuildFileLoader {

	/** Short cut to file separator */
	public static final String FS = File.separator;

	@Override
	public void process(SrcFile file, List<BuildEntity> result, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
		if (file.getName().equals("SConscript")) {
			if (scanner.find(file.dir.relative + FS + MakeXMLLoader.MAKE_XML_NAME) != null) { // favour make.xml files
				return;
			}
			result.addAll(parse(file, scanner, builder));
		}
	}
	
	/** 
	 * parses SConscripts and returns a set of build entities 
	 * 
	 * @param sconscript SConscript file
	 * @param tb MakefileBuilder entity
	 * @return set of build entities
	 */
	public static Collection<BuildEntity> parse(SrcFile sconscript, SourceScanner sources, MakeFileBuilder tb) throws Exception {
		List<BuildEntity> result = new ArrayList<BuildEntity>();
		List<String> lines = Files.readLines(sconscript.absolute);

		int curLine = 0;

		final boolean DEBUG = MakeFileBuilder.getOptions().getProperty("DEBUG_SCONSCRIPT_PARSING") != null; 
		if (DEBUG) {
			System.out.println(sconscript);
		}

		try {
			while(curLine < lines.size()) {
				String line = normalize(lines.get(curLine));
				curLine++;
				if (line.trim().startsWith("#")) {
					continue;
				}
				if (line.contains(" MCALibrary(") || line.contains(" MCAProgram(") || line.contains(" MCAPlugin(")) {
					BuildEntity be = null;
					if (line.contains(" MCALibrary")) {
						be = new MCALibrary();
					} else if (line.contains(" MCAProgram(")) {
						be = new MCAProgram();
					} else {
						be = new MCAPlugin();
					}
					line = line.replace('"', '\'');
					be.name = line.substring(line.indexOf("'") + 1, line.lastIndexOf("'"));
					String sconsID = line.substring(0, line.indexOf("=")).trim();
					be.buildFile = sconscript;
					if (DEBUG) {
						System.out.println("found build task " + be.toString());
					}

					while(curLine < lines.size()) {
						String s = normalize(lines.get(curLine));
						curLine++;
						if (s.trim().startsWith("#")) {
							continue;
						}

						if (s.contains(sconsID + ".BuildIt()")) {
							try {
								checkAllFilesExist(be);
								result.add(be);
							} catch (Exception e) { 
								tb.printErrorLine(e.getMessage());
								//e.printStackTrace();
							}
							break;
						}
						if (s.contains(" MCALibrary(") || s.contains(" MCAProgram(") || s.contains(" MCAPlugin(")) {
							curLine--;
							break;
						}

						if (s.contains(sconsID + ".AddSourceFiles(\"\"\"")) {
							while(true) {
								s = lines.get(curLine).trim();
								curLine++;
								if (s.trim().startsWith("#")) {
									continue;
								}

								if (s.contains("\"\"\")")) {
									break;
								}
								if (s.length() < 1) {
									continue;
								}
								SrcFile sf = sources.find(be.getRootDir().relative + File.separator + s.trim());
								if (sf == null) {
									tb.printErrorLine(sconscript.relative + ": Cannot find " + s.trim());
								}
								be.sources.add(sf);
								
								//be.sources.add(be.getRootDir().s.trim());
							}
						}

						curLine = checkFor(sources, sconscript, s, lines, curLine, sconsID, "AddCudaFiles", be.sources, tb);
						curLine = checkFor(sources, sconscript, s, lines, curLine, sconsID, "AddHeaderFiles", be.sources, tb);
						curLine = checkFor(sconscript.absolute, s, lines, curLine, sconsID, "AddLibs", be.libs);
						curLine = checkFor(sconscript.absolute, s, lines, curLine, sconsID, "AddOptionalLibs", be.optionalLibs);
						curLine = checkFor(sources, sconscript, s, lines, curLine, sconsID, "AddUicFiles", be.sources, tb);

						if (s.contains(sconsID + ".build_env.Append")) {
							while(!s.contains(")")) {
								s += " " + lines.get(curLine);
								curLine++;
							}
							String s2 = s.substring(s.indexOf("(") + 1, s.lastIndexOf(")"));
							String[] s3 = s2.split("=");
							for (int i = 1; i < s3.length; i++) {
								String key = s3[i-1].trim();
								key = key.substring(key.lastIndexOf(i == 1 ? "(" : ",") + 1).trim();
								String[] values = null;
								if (s3[i].contains("[")) {
									String value = s3[i].substring(s3[i].indexOf("[") + 1, s3[i].indexOf("]")).trim();
									if (value.contains("os.path.join(mcahome")) {
										values = new String[]{MakeFileBuilder.HOME + File.separator + value.substring(value.indexOf("'") + 1, value.lastIndexOf("'"))};
									} else {
										values = value.split(",");
										for (int j = 0; j < values.length; j++) {
											values[j] = values[j].substring(values[j].indexOf("'") + 1, values[j].lastIndexOf("'"));
										}
									}
								} else {
									s3[i] = s3[i].replace('"', '\'');
									s3[i] = s3[i].replace("#", MakeFileBuilder.HOME + File.separator);
									values = new String[]{s3[i].substring(s3[i].indexOf("'") + 1, s3[i].lastIndexOf("'"))};
								}
								for (String val : values) {
									if (key.equals("LIBS")) {
										if (val.startsWith("lib")) {
											val = val.substring(3);
										}
										be.opts.libs.add(val);
									}
									if (key.equals("LIBPATH")) {
										be.opts.libPaths.add(val);
									}
									if (key.equals("CPPPATH")) {
										//be.addIncludes.add(val);
										be.opts.includePaths.add(val);
									}
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			curLine--;
			System.err.println("Error parsing " + sconscript.toString() + ", line " + curLine + ": " + lines.get(curLine));
			e.printStackTrace();
			System.exit(1);
		}
		
		return result;
	}

	/**
	 * Check that all file in build entity exist (throws Exception otherwise)
	 * 
	 * @param be Build entity
	 */
	private static void checkAllFilesExist(BuildEntity be) throws Exception {
		for (SrcFile s : be.sources) {
			if (s == null) {
				throw new FileNotFoundException("Skipping '" + be.name + "' because some source file doesn't exist. Please check SConscript.");
			}
		}
	}

	/**
	 * Normalize SCons string (removes spaces)
	 * 
	 * @param s Original string
	 * @return Normalized string
	 */
	private static String normalize(String s) {
		return s.replace(" (", "(").replace(" '", "'").replace(" \"", "\"");
	}

	private static int checkFor(SourceScanner sources, SrcFile sconscript, String s, List<String> lines, int curLine, String sconsID, String methodCall, List<SrcFile> resultList, MakeFileBuilder tb) {
		List<String> tmp = new ArrayList<String>();
		int result = checkFor(sconscript.absolute, s, lines, curLine, sconsID, methodCall, tmp);
		for (String s2 : tmp) {
			SrcFile sf = sources.find(sconscript.dir.relative + File.separator + s2);
			if (sf == null) {
				tb.printErrorLine(sconscript.relative + ": Cannot find " + s2.trim());
			}
			resultList.add(sf);
		}
		return result;
	}
	
	/**
	 * Check Sconscript for string list - possibly extract it
	 * 
	 * @param sconscript Sconscript file (name)
	 * @param s current line
	 * @param lines all lines 
	 * @param curLine index of current line
	 * @param sconsID Scons/Python variable name
	 * @param methodCall Scons method call to search for
	 * @param resultList List to put results to 
	 * @return New current line index
	 */
	private static int checkFor(File sconscript, String s, List<String> lines, int curLine, String sconsID, String methodCall, List<String> resultList) {
		try {
			if (s.contains(sconsID + "." + methodCall + "(\"\"\"")) {
				while(true) {
					s = lines.get(curLine);
					curLine++;
					if (s.trim().startsWith("#")) {
						continue;
					}

					if (s.contains("\"\"\")")) {
						break;
					}
					String[] strings = s.split("\\s");
					for (String s2 : strings) {
						if (s2.trim().length() > 0) {
							resultList.add(s2.trim());								
						}
					}
				}
			}

			if (s.contains(sconsID + "." + methodCall + "('")) {
				String[] libs = s.substring(s.indexOf("'") + 1, s.lastIndexOf("'")).split("\\s");
				for (String lib : libs) {
					lib = lib.trim();
					if (lib.length() > 0) {
						resultList.add(lib);
					}
				}
			}

			else if (s.contains(sconsID + "." + methodCall + "(\"")) {
				String[] libs = s.substring(s.indexOf("\"") + 1, s.lastIndexOf("\"")).split("\\s");
				for (String lib : libs) {
					lib = lib.trim();
					if (lib.length() > 0) {
						resultList.add(lib);
					}
				}
			}

		} catch (Exception e) {
			curLine--;
			System.err.println("Error parsing " + sconscript.toString() + ", line " + curLine + ": " + lines.get(curLine));
			e.printStackTrace();
			System.exit(1);
		}

		return curLine;
	}
}
