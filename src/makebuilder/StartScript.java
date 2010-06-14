/**
 * You received this file as part of an experimental
 * build tool ('makebuilder') - originally developed for MCA2.
 *
 * Copyright (C) 2008-2010 Max Reichardt,
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

import java.util.SortedMap;

import makebuilder.handler.ScriptHandler;

/**
 * @author max
 *
 * This class represents a start script for a build entity.
 * A build entity may have multiple start scripts.
 */
public class StartScript {

    /** Filename (relative to repository root) of start script */
    private String filename;

    /** Attributes for this script specified in make.xml */
    private SortedMap<String, String> params;

    /** Makefile target (is initialized when build entity it belongs to is */
    private Makefile.Target target;

    public StartScript(String filename, SortedMap<String, String> params) {
        this.filename = filename;
        this.params = params;
    }

    /**
     * @return Makefile target
     */
    public Makefile.Target getTarget() {
        assert(target != null);
        return target;
    }

    /**
     * Initializes target
     *
     * @param mf Makefile
     * @param prefix Prefix for script file name
     * @param rootDir
     */
    void initTarget(Makefile mf, String prefix, SrcDir rootDir) {
        target = mf.addTarget("$(" + ScriptHandler.SCRIPT_DIR_VAR + ")/" + prefix + filename, false, rootDir);
    }

    /**
     * Get custom attribute/parameter/tag that was set in make.xml for this script
     *
     * @param key Key
     * @return Value (null if it wasn't set)
     */
    public String getParameter(String key) {
        return params == null ? null : params.get(key);
    }
}
