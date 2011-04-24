//
// You received this file as part of RRLib
// Robotics Research Library
//
// Copyright (C) AG Robotersysteme TU Kaiserslautern
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
//----------------------------------------------------------------------
/*!\file    UnitTest.java
 *
 * \author  Tobias Foehst
 *
 * \date    2011-03-24
 *
 * \brief
 *
 * \b
 *
 */
//----------------------------------------------------------------------
package makebuilder.ext.finroc;

import makebuilder.SourceFileHandler;
import makebuilder.handler.CppHandler;

public class UnitTest extends FinrocBuildEntity {

    @Override
    public String getTargetPrefix() {
        String rootDir2 = this.getRootDir().relative;
        if (FinrocBuilder.BUILDING_FINROC) {
            assert(rootDir2.startsWith("sources"));
            rootDir2 = rootDir2.substring(9);
            rootDir2 = rootDir2.substring(rootDir2.indexOf("/") + 1);
        }
        if (rootDir2.startsWith("libraries")) {
            return "finroc_library_" + getSecondDir(rootDir2) + "_unit_test_";
        } else if (rootDir2.startsWith("plugins")) {
            return "finroc_plugin_" + getSecondDir(rootDir2) + "_unit_test_";
        } else if (rootDir2.startsWith("rrlib")) {
            return "rrlib_" + getSecondDir(rootDir2) + "_unit_test_";
        } else if (rootDir2.startsWith("core")) {
            return "finroc_core_unit_test_";
        } else if (rootDir2.startsWith("jcore")) {
            return "finroc_jcore_unit_test_";
        }
        return "";
    }

    @Override
    public String getTarget() {
        return "$(TARGET_BIN)/" + getTargetPrefix() + name;
    }

    private String getSecondDir(String rootDir2) {
        String[] parts = rootDir2.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "";
    }

    @Override
    public Class <? extends SourceFileHandler > getFinalHandler() {
        return CppHandler.class;
    }

    @Override
    public boolean isUnitTest() {
        return true;
    }
}
