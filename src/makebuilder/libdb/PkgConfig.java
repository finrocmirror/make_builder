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
package makebuilder.libdb;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.util.Files;
import makebuilder.util.Util;

/**
 * Get infos on libraries using the pkg-config tool
 *
 * @author Michael Arndt <m_arndt@cs.uni-kl.de>
 */
public class PkgConfig {

    /** Mapping: Library name => library */
    private static Map<String, ExtLib> known_packages = new HashMap<String, ExtLib>();

    /** Alternative system environment for cross-compiling */
    private static String[] environmentVariables = null;

    static {
        reinit(null, null);
    }

    /**
     * reads and processes libdb.txt file
     * may be called again, if file changes
     *
     * @param systemRoot System root directory
     * @param pkgConfigExtraPath Any extra paths for pkgconfig relative to systemRoot separated by colons. null for now extra path.
     */
    public static void reinit(String systemRoot, String pkgConfigExtraPath) {
        if (systemRoot == null) {
            environmentVariables = null;
        } else {
            environmentVariables = new String[] { "PKG_CONFIG_DIR=",
                                                  "PKG_CONFIG_LIBDIR=" + systemRoot + "/usr/lib/pkgconfig:" + systemRoot + "/usr/share/pkgconfig" + (pkgConfigExtraPath != null ? (":" + pkgConfigExtraPath) : ""),
                                                  "PKG_CONFIG_SYSROOT_DIR=" + systemRoot
                                                };
        }
        known_packages.clear();
        try {
            Process p = Runtime.getRuntime().exec("pkg-config --list-all", environmentVariables);
            p.waitFor();
            if (p.exitValue() != 0) {
                System.out.println(Util.color("Could not call pkg-config, will not attempt to make use of it.", Util.Color.RED, false));
            }
            for (String s : Files.readLines(p.getInputStream())) {
                String[] spl = s.split("\\s");
                if (spl.length > 0) {
                    //System.out.println("Got package: " + spl[0]);
                    known_packages.put(spl[0], null); // perform lazy lookup
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Add all available libraries to specified lines.
     * Entries of the form '_LIB_???_PRESENT_' are added to the list.
     *
     * @param defines List with defines
     */
    public static void addDefines(List<String> defines) {
        for (Map.Entry<String, ExtLib> e : known_packages.entrySet()) {
            if (e.getValue() == null)
                continue;
            String opts = e.getValue().options;
            if (!opts.contains("N/A")) {
                // _LIB_OPENCV_PRESENT_
                defines.add("_LIB_" + e.getKey().toUpperCase() + "_PRESENT_");
            }
        }
    }

    /**
     * @param lib Library name
     * @return Library with this name
     * @throws Exception Thrown when not found
     */
    public static ExtLib getLib(String lib) throws Exception {

        ExtLib el = known_packages.get(lib);
        if (el != null) {
            return el;
        } else {
            // get the info from pkg-config
            Process p = Runtime.getRuntime().exec(new String[] {"pkg-config", "--cflags", MakeFileBuilder.getInstance().isStaticLinkingEnabled() ? "--static" : "", "--libs", lib}, environmentVariables);
            String options = "";
            p.waitFor();
            if (p.exitValue() != 0) {
                // looks like pkg-config has failed, that can e.g. happen if dependencies specified
                // with "Requires:" cannot be satisfied. In this case, makeBuilder should fail
                System.out.println(Util.color("Calling pkg-config for " + lib + " failed:", Util.Color.RED, true));
                for (String s : Files.readLines(p.getErrorStream())) {
                    System.out.println(Util.color(" " + s, Util.Color.RED, true));
                }
                System.out.println(Util.color("It is not safe to continue, so I am going to bail out now ...", Util.Color.RED, true));
                System.exit(1);

            }
            for (String s : Files.readLines(p.getInputStream())) {
                options = options + s + " ";
            }
            System.out.println(Util.color("Options for package " + lib + ": " + options, Util.Color.GREEN, false));
            ExtLib library = new ExtLib(lib, options, true);
            known_packages.put(lib, library);
            return library;

        }

    }

    /**
     * @param lib Library name
     * @return Is library with this name available?
     */
    public static boolean available(String lib) {
        return known_packages.containsKey(lib);
    }

    /**
     * Find local dependencies in external libraries
     * having this is ugly... but sometimes occurs
     *
     * @param bes All build entities
     */
    public static void findLocalDependencies(Collection<BuildEntity> bes) {
        for (ExtLib el : known_packages.values()) {
            if (el != null)
                el.findLocalDependencies(bes);
        }
    }

}
