package tools.turbobuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 *
 * This classes parses SConscripts
 */
public class SConscript {

	public static final String FS = File.separator;
	
	/** parses SConscripts and returns a set of build entities */
	public static Collection<BuildEntity> parse(File sconscript, TurboBuilder tb) throws Exception {
		List<BuildEntity> result = new ArrayList<BuildEntity>();
		List<String> lines = Files.readLines(sconscript);

		int curLine = 0;

		if (tb.opts.DEBUG_SCONSCRIPT_PARSING) {
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
						be = new MCALibrary(tb);
					} else if (line.contains(" MCAProgram(")) {
						be = new MCAProgram(tb);
					} else {
						be = new MCAPlugin(tb);
					}
					line = line.replace('"', '\'');
					be.name = line.substring(line.indexOf("'") + 1, line.lastIndexOf("'"));
					be.sconsID = line.substring(0, line.indexOf("=")).trim();
					be.rootDir = sconscript.getParentFile();
					if (tb.opts.DEBUG_SCONSCRIPT_PARSING) {
						System.out.println("found build task " + be.toString());
					}

					while(curLine < lines.size()) {
						String s = normalize(lines.get(curLine));
						curLine++;
						if (s.trim().startsWith("#")) {
							continue;
						}

						if (s.contains(be.sconsID + ".BuildIt()")) {
							try {
								checkAllFilesExist(be);
								result.add(be);
								processCategories(be);
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

						if (s.contains(be.sconsID + ".AddSourceFiles(\"\"\"")) {
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
								if (s.endsWith(".cpp") || s.endsWith(".C")) {
									be.cpps.add(s);
									//be.hs.add(s.substring(0, s.lastIndexOf(".")) + ".h");
								} else if (s.endsWith(".c")) {
									be.cs.add(s.trim());
									//be.hs.add(s.substring(0, s.lastIndexOf(".")) + ".h");
								} else if (s.endsWith(".o")) {
									be.os.add(s.trim());
								} else if (s.endsWith(".ui")) {
									be.uics.add(s.trim());
								} else {
									throw new Exception("Unrecogized source file format " + s);
								}
							}
						}

						curLine = checkFor(sconscript, s, lines, curLine, be.sconsID, "AddCudaFiles", be.cudas);
						curLine = checkFor(sconscript, s, lines, curLine, be.sconsID, "AddHeaderFiles", be.hs);
						curLine = checkFor(sconscript, s, lines, curLine, be.sconsID, "AddLibs", be.libs);
						curLine = checkFor(sconscript, s, lines, curLine, be.sconsID, "AddOptionalLibs", be.optionalLibs);
						curLine = checkFor(sconscript, s, lines, curLine, be.sconsID, "AddUicFiles", be.uics);

						if (s.contains(be.sconsID + ".build_env.Append")) {
							String s2 = s.substring(s.indexOf("(") + 1, s.lastIndexOf(")"));
							String[] s3 = s2.split("=");
							for (int i = 1; i < s3.length; i++) {
								String key = s3[i-1].trim();
								key = key.substring(key.lastIndexOf(i == 1 ? "(" : ",") + 1).trim();
								String[] values = null;
								if (s3[i].contains("[")) {
									String value = s3[i].substring(s3[i].indexOf("[") + 1, s3[i].indexOf("]")).trim();
									if (value.contains("os.path.join(mcahome")) {
										values = new String[]{TurboBuilder.HOME + File.separator + value.substring(value.indexOf("'") + 1, value.lastIndexOf("'"))};
									} else {
										values = value.split(",");
										for (int j = 0; j < values.length; j++) {
											values[j] = values[j].substring(values[j].indexOf("'") + 1, values[j].lastIndexOf("'"));
										}
									}
								} else {
									s3[i] = s3[i].replace('"', '\'');
									s3[i] = s3[i].replace("#", TurboBuilder.HOME + File.separator);
									values = new String[]{s3[i].substring(s3[i].indexOf("'") + 1, s3[i].lastIndexOf("'"))};
								}
								for (String val : values) {
									if (key.equals("LIBS")) {
										if (val.startsWith("lib")) {
											val = val.substring(3);
										}
										be.opts += " -l" + val;
									}
									if (key.equals("LIBPATH")) {
										be.opts += " -L" + val;
									}
									if (key.equals("CPPPATH")) {
										be.addIncludes.add(val);
										be.opts += " -I" + val;
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

	private static void checkAllFilesExist(BuildEntity be) throws Exception {
		checkAllFilesExist(be, be.cs);
		checkAllFilesExist(be, be.cpps);
		checkAllFilesExist(be, be.uics);
		checkAllFilesExist(be, be.cudas);
		checkAllFilesExist(be, be.os);
	}

	private static void checkAllFilesExist(BuildEntity be, List<String> cs) throws Exception {
		for (String s : cs) {
			File f = new File(be.rootDir.getAbsolutePath() + FS + s);
			if (!f.exists()) {
				throw new Exception("Skipping '" + be.name + "' because " + s + " doesn't exist. Please check SConscript.");
			}
		}
	}

	private static void processCategories(BuildEntity be) {
		String rootDir = be.rootDir.getAbsolutePath().substring(TurboBuilder.HOME.getAbsolutePath().length() + 1);
		be.categories.add("all");
		if (rootDir.startsWith("libraries") || rootDir.startsWith("tools")) {
			be.categories.add("libs");
		}
		if (rootDir.startsWith("tools")) {
			be.categories.add("tools");
		}
		if (rootDir.startsWith("libraries")) {
			be.categories.add(rootDir.substring(rootDir.lastIndexOf(FS) + 1));
		}
		if (rootDir.startsWith("projects") || rootDir.startsWith("tools")) {
			String project = rootDir.substring(rootDir.indexOf(FS) + 1);
			if (project.contains(FS)) {
				project = project.substring(0, project.indexOf(FS));
			}
			be.categories.add(project);
		}
	}

	private static String normalize(String s) {
		return s.replace(" (", "(").replace(" '", "'").replace(" \"", "\"");
	}

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
