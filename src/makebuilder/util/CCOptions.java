/**
 * You received this file as part of an experimental
 * build tool ('makebuilder') - originally developed for MCA2.
 *
 * Copyright (C) 2008-2009 Max Reichardt,
 *   Robotics Research Lab, University of Kaiserslautern
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package makebuilder.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Max Reichardt
 *
 * This is a helper class to deal with calls to the C Compiler.
 * It wraps a set of GCC command line options.
 *
 * (implements String comparator for better path sorting: local paths should be output first)
 */
public class CCOptions implements Comparator<String> {

    /** C++ Options that are only relevant for linking */
    public final static String[] LINK_ONLY_OPTIONS = new String[] {"-shared"};

    /** C++ Options that are only relevant for compiling */
    public final static String[] COMPILE_ONLY_OPTIONS = new String[] {"-fpermissive"};

    /** Libraries for linking */
    public final AddOrderSet<String> libs = new AddOrderSet<String>();

    /** Library paths */
    public final TreeSet<String> libPaths = new TreeSet<String>(this);

    /** Include paths */
    public final TreeSet<String> includePaths = new TreeSet<String>(this);

    /** Options only for linking */
    public final TreeSet<String> linkOptions = new TreeSet<String>();

    /** Options only for c++ compiling */
    public final TreeSet<String> cxxCompileOptions = new TreeSet<String>();

    /** Options only for c compiling */
    public final TreeSet<String> cCompileOptions = new TreeSet<String>();

    public CCOptions() {}

    /** Parse C compiler options from string */
    public CCOptions(String options) {
        addOptions(options, null);
    }

    /**
     * Add a set of options
     *
     * @param options Options as string (will be parsed)
     */
    public void addOptions(String options) {
        addOptions(options, null);
    }


    /**
     * Add a set of options
     *
     * @param options Options as string (will be parsed)
     * @param optionType (optional - may be null) specify what kind of option this is (should be)
     */
    public void addOptions(String options, Set<String> optionType) {
        if (options != null) {
            String[] opts = options.split("\\s-");

            // check for groups
            ArrayList<String> optsWithoutGroups = new ArrayList<String>();
            boolean inGroup = false;
            StringBuilder groupEntries = new StringBuilder();
            for (String opt : opts) {
                if (opt.length() < 1)
                    continue;
                String checked = opt.startsWith("-") ? opt : ("-" + opt);
                if (inGroup && checked.startsWith("-l")) {
                    check("link", checked, optionType, libs, linkOptions);
                    groupEntries.append(" ");
                    groupEntries.append(checked);
                } else if (checked.contains("Wl,--start-group")) {
                    inGroup = true;
                    groupEntries.append(checked);
                } else if (checked.contains("Wl,--end-group")) {
                    inGroup = false;
                    groupEntries.append(" ");
                    groupEntries.append(checked);
                    linkOptions.add(groupEntries.toString());
                    groupEntries.setLength(0);
                } else if (!inGroup) {
                    optsWithoutGroups.add(opt);
                }
            }
            for (String opt : optsWithoutGroups) {
                opt = opt.trim();
                if (opt.length() > 0) {
                    addOption(opt.startsWith("-") ? opt : "-" + opt, optionType);
                }
            }
        }
    }

    /**
     * Add a single option
     *
     * @param opt Options a string (will be parsed)
     */
    @SuppressWarnings("unchecked")
    public void addOption(String opt, Set<String> type) {
        opt = opt.trim();
        if (opt.startsWith("-l")) {
            check("link", opt, type, libs, linkOptions);
            libs.add(opt.substring(2));
        } else if (opt.startsWith("-L")) {
            check("link", opt, type, libPaths, linkOptions);
            libPaths.add(opt.substring(2));
        } else if (opt.startsWith("-I")) {
            check("compile", opt, type, cCompileOptions, cxxCompileOptions, includePaths);
            includePaths.add(opt.substring(2));
        } else if (opt.startsWith("-Wl")) {
            check("link", opt, type, libs, linkOptions);
            linkOptions.add(opt);
        } else if (opt.startsWith("-D") || opt.startsWith("-f")) {
            check("compile", opt, type, cCompileOptions, cxxCompileOptions);
            cxxCompileOptions.add(opt);
            cCompileOptions.add(opt);
        } else {
            if (type != null) {
                type.add(opt);
                return;
            }

            // some other option
            for (String s : LINK_ONLY_OPTIONS) {
                if (s.equals(opt)) {
                    linkOptions.add(opt);
                    return;
                }
            }
            for (String s : COMPILE_ONLY_OPTIONS) {
                if (s.equals(opt)) {
                    return;
                }
            }

            //System.out.println("Considering a common option: " + opt);
            linkOptions.add(opt);
            cxxCompileOptions.add(opt);
            cCompileOptions.add(opt);
        }
    }

    /**
     * Check whether type can be correct (throws Exception otherwise)
     *
     * @param expectString Valid types as string
     * @param opt Option that is checked
     * @param requested Type requested from user
     * @param expected Valid types in this case
     */
    private void check(String expectString, String opt, Set<String> requested, Set<String>... expected) {
        if (requested == null) {
            return;
        }

        for (Set<String> exp : expected) {
            if (exp == requested) {
                return;
            }
        }
        throw new RuntimeException("Invalid " + expectString + " option: " + opt);
    }

    /**
     * Merge options with other options
     *
     * @param mergeLibs Merge library/linker options also?
     */
    public void merge(CCOptions other, boolean mergeLibs) {
        cCompileOptions.addAll(other.cCompileOptions);
        cxxCompileOptions.addAll(other.cxxCompileOptions);
        includePaths.addAll(other.includePaths);
        if (mergeLibs) {
            libPaths.addAll(other.libPaths);
            libs.addAll(other.libs);
        }
        linkOptions.addAll(other.linkOptions);
    }

    /**
     * Create string with options
     *
     * @param compile Is this a compiling operation?
     * @param link Is this a linking operation?
     * @param cpp C++ options? (rather than C)
     * @return String with options
     */
    public String createOptionString(boolean compile, boolean link, boolean cpp) {
        return createOptionString(compile, link, cpp, true);
    }


    /**
     * Create string with options
     *
     * @param compile Is this a compiling operation?
     * @param link Is this a linking operation?
     * @param cpp C++ options? (rather than C)
     * @param addLibs Add libraries? (-l*** parameters)
     * @return String with options
     */
    private String createOptionString(boolean compile, boolean link, boolean cpp, boolean addLibs) {
        String result = "";
        if (compile) {
            for (String s : cpp ? cxxCompileOptions : cCompileOptions) {
                result += " " + s;
            }
        }
        if (link) {
            for (String s : linkOptions) {
                result += " " + s;
            }
        }
        if (compile) {
            for (String s : includePaths) {
                if (!(s.equals("/usr/include") || s.equals("/usr/local/include"))) {
                    result += " -I" + s;
                }
            }
        }
        if (link && addLibs) {
            for (String s : libPaths) {
                result += " -L" + s;
            }
            for (String s : libs) {
                result += " -l" + s;
            }
        }
        return result.trim();
    }

    /**
     * @return String with options required by nvcc compiler
     */
    public String createCudaString() {
        String compilerOptions = "";
        String result = "";
        for (String s : cxxCompileOptions) {
            if (s.startsWith("-D") || s.startsWith("$")) {
                result += " " + s;
            } else {
                compilerOptions += "," + s;
            }
        }
        for (String s : includePaths) {
            result += " -I" + s;
        }
        if (compilerOptions.length() > 0) {
            result += " --compiler-options " + compilerOptions.substring(1);
        }
        return result;
    }

    /**
     * @param cxx Use C++ compiler? (instead of c)
     * @return Compiler call to write to Makefile
     */
    private String getCompiler(boolean cxx) {
        return (cxx ? "$(CXX)" : "$(CC)");
    }

    /**
     * Create Cpp compiler call for linking only
     *
     * @param inputs Input files (divided by whitespace)
     * @param output Output file
     * @param cxx Use C++ compiler? (instead of c)
     * @return GCC Compiler call for makefile
     */
    public String createLinkCommand(String inputs, String output, boolean cxx) {
        return cleanCommand(getCompiler(cxx) + " -o " + output + " " + inputs + " " + createOptionString(false, true, cxx));
    }

    /**
     * Create Cpp compiler call for static linking only
     * (excluding libraries - as they need to be added in the correct order)
     *
     * @param inputs Input files (divided by whitespace)
     * @param output Output file
     * @param cxx Use C++ compiler? (instead of c)
     * @return GCC Compiler call for makefile
     */
    public String createStaticLinkCommand(String inputs, String output, boolean cxx) {
        return cleanCommand(getCompiler(cxx) + " -o " + output + " " + inputs + " " + createOptionString(false, true, cxx, false));
    }

    /**
     * Create Cpp compiler call for compiling only
     *
     * @param inputs Input files (divided by whitespace)
     * @param output Output file
     * @param cxx Use C++ compiler? (instead of c)
     * @param compiler Compiler to use (optional; null to use default compiler)
     * @return GCC Compiler call for makefile
     */
    public String createCompileCommand(String inputs, String output, boolean cxx) {
        return cleanCommand(getCompiler(cxx) + " -c " + createOptionString(true, false, cxx)) + " -o " + output + " " + inputs;
    }

    /**
     * Create Cpp compiler call for compiling and linking
     *
     * @param inputs Input files (divided by whitespace)
     * @param output Output file
     * @param cxx Use C++ compiler? (instead of c)
     * @return GCC Compiler call for makefile
     */
    public String createCompileAndLinkCommand(String inputs, String output, boolean cxx) {
        return cleanCommand(getCompiler(cxx) + " -o " + output + " " + inputs + " " + createOptionString(true, true, cxx));
    }

    /**
     * Remove double spaces etc. from call
     *
     * @param s call
     * @return "Cleaner" call command
     */
    public static String cleanCommand(String s) {
        s = s.replace("  ", " ");
        return s;
    }

    @Override
    public int compare(String s1, String s2) {
        if (s1.startsWith("/") && (!s2.startsWith("/"))) {
            return 1;
        } else if (!s1.startsWith("/") && s2.startsWith("/")) {
            return -1;
        }
        return s1.compareTo(s2);
    }
}

