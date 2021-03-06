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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import makebuilder.BuildEntity;
import makebuilder.util.CCOptions;
import makebuilder.util.Files;
import makebuilder.util.Util;

/**
  * @author Max Reichardt
  *
  * Represents one external library
  */
public class ExtLib {

    /** library name */
    public final String name;

    /** compiler options to set for this library */
    public final String options;

    /** wrapped/more sophisticated version of above */
    public CCOptions ccOptions;

    /** Dependencies to other mca2 libraries (Strings: "mca2_*") */
    public final List<BuildEntity> dependencies = new ArrayList<BuildEntity>();

    /**
     * @param name Library name
     * @param options libdb.txt line
     * @param cpp C/C++ external library?
     */
    public ExtLib(String name, String options, boolean cpp) {
        this.name = name;
        this.options = options;
        ccOptions = new CCOptions(cpp ? options : "");
    }

    /**
     * @return Is library available on current system?
     */
    public boolean available() {
        return !options.contains("N/A");
    }

    public String toString() {
        return name;
    }

    /**
     * Find local dependencies in external libraries
     * having this is ugly... but sometimes occurs
     *
     * @param bes All build entities
     */
    public void findLocalDependencies(Collection<BuildEntity> bes) {
        ArrayList<String> libCopy = new ArrayList<String>(ccOptions.libs);
        for (String lib : libCopy) {
            for (BuildEntity be : bes) {
                if (be.getTarget().endsWith("/lib" + lib + ".so") || be.getTarget().endsWith("/lib" + lib + ".$(LIB_EXTENSION)")) {
                    ccOptions.libs.remove(lib);
                    dependencies.add(be);
                    break;
                }
            }
        }
    }
}