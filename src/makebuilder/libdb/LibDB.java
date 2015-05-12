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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import makebuilder.BuildEntity;
import makebuilder.util.Files;
import makebuilder.util.Util;

/**
 * @author Max Reichardt
 *
 * Contains specific information about external libraries
 *
 * Handles/manages entries in libdb.txt
 */
public class LibDB {

    /** Mapping: Library name => library */
    private static Map<String, ExtLib> libs = new HashMap<String, ExtLib>();

    /** File names of files to process */
    static public final String LIBDB_RAW = "libdb.raw", LIBDB_TXT = "libdb.txt", LIBDB_JAVA = "libdb.java";

    /**
     * reads and processes libdb.txt file
     * may be called again, if file changes
     *
     * @param libdb File with libdb. Default libdb is used if null.
     * @return Reference to this LibDB (for convenience)
     */
    public static void reinit(File libdb) {
        libs.clear();
        try {
            if (libdb == null) {
                loadLibDb(Util.getFileInEtcDir(LIBDB_TXT), true);
            } else if (libdb.exists()) {
                loadLibDb(libdb, true);
            }
            if (Util.getFileInEtcDir(LIBDB_JAVA).exists()) {
                loadLibDb(Util.getFileInEtcDir(LIBDB_JAVA), false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @return Does LibDB have any entries?
     */
    public static boolean hasEntries() {
        return libs.size() > 0;
    }

    /**
     * Loads libdb file
     *
     * @param libdbTxt file name (relative to make_builder/etc directory)
     * @param cpp c/c++ library?
     */
    private static void loadLibDb(File libdbTxt, boolean cpp) throws IOException {

        // external libraries
        File f = libdbTxt;
        List<String> lines = Files.readLines(f);
        for (String s : lines) {
            if (s.trim().length() <= 1) {
                continue;
            }
            //s = s.replace("$MCAHOME$", MakeFileBuilder.HOME.getAbsolutePath());
            int split = s.indexOf(":");
            String name =  s.substring(0, split).trim();
            String flags = s.substring(split + 1).trim();
            libs.put(name, new ExtLib(name, flags, cpp));
        }
    }

    /**
     * Add all available libraries to specified lines.
     * Entries of the form '_LIB_???_PRESENT_' are added to the list.
     *
     * @param defines List with defines
     */
    public static void addDefines(List<String> defines) {
        for (Map.Entry<String, ExtLib> e : libs.entrySet()) {
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
        ExtLib el = libs.get(lib);
        if (el != null) {
            return libs.get(lib);
        }
        throw new Exception("cannot find entry for external library " + lib);
    }

    /**
     * @param lib Library name
     * @return Is library with this name available?
     */
    public static boolean available(String lib) {
        ExtLib el = libs.get(lib);
        if (el != null) {
            return el.available();
        }
        return false;
    }

    /**
     * Find local dependencies in external libraries
     * having this is ugly... but sometimes occurs
     *
     * @param bes All build entities
     */
    public static void findLocalDependencies(Collection<BuildEntity> bes) {
        for (ExtLib el : libs.values()) {
            el.findLocalDependencies(bes);
        }
    }
}
