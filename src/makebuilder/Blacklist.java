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
package makebuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import makebuilder.util.Files;
import makebuilder.util.Util;

/**
 * @author Max Reichardt
 *
 * Manages list of sources that cannot be combined in one great file without problems
 */
public class Blacklist {

    /** List of sources (Scons name => Special settings) */
    private Map<String, Element> list = new HashMap<String, Element>();

    /** Singleton instance */
    private static Blacklist instance;

    /** @return Singleton instance */
    public static Blacklist getInstance() {
        if (instance == null) {
            instance = new Blacklist();
            try {
                File f = Util.getFileInEtcDir("blacklist.txt");
                List<String> lines = Files.readLines(f);
                for (String s : lines) {
                    if (s.trim().length() <= 1) {
                        continue;
                    }
                    Element el = instance.new Element(s);
                    instance.list.put(el.name, el);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return instance;
    }

    /**
     * @param beName Scons entity (name)
     * @return Special settings for this Scons entity (name)
     */
    public Element get(String beName) {
        return list.get(beName);
    }

    /**
     * Special make settings for Scons entity
     */
    public class Element {

        /** name of Scons entity */
        public final String name;

        /** List of files to compile separately */
        public final List<String> compileSeparately = new ArrayList<String>();

        /** Compile all files separately? */
        public boolean compileAllSeparately = false;

        /** Replace all #includes with #imports - necessary when .h files are not guarded */
        public boolean importMode = false;

        /** unused - create symbolic links in build dir to binary libraries (.so) in sources */
        //public boolean linkLibs = false;

        /**
         * @param s blacklist.txt line
         */
        public Element(String s) {
            name = s.substring(0, s.indexOf(":"));
            String rest = s.substring(s.indexOf(":") + 1).trim();
            if (rest.contains("#")) {
                rest = rest.substring(0, rest.indexOf("#")).trim();
            }
            for (String s2 : rest.split("\\s")) {
                String s3 = s2.trim();
                if (s3.startsWith("-import")) {
                    importMode = true;
//              } else if (s3.startsWith("-linklibs")) {
//                  linkLibs = true;
                } else if (s3.startsWith("-safe")) {
                    compileAllSeparately = true;
                } else if (s3.length() > 0) {
                    compileSeparately.add(s3);
                }
            }
        }

        /**
         * Compile this file seperately?
         *
         * @param c filename
         * @return answer
         */
        public boolean contains(String c) {
            return compileAllSeparately || compileSeparately.contains(c);
        }
    }
}
