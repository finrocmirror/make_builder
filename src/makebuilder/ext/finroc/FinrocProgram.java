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
package makebuilder.ext.finroc;

import makebuilder.Makefile;
import makebuilder.StartScript;
import makebuilder.handler.JavaHandler;

/**
 * @author Max Reichardt
 *
 * Finroc library
 */
public class FinrocProgram extends FinrocBuildEntity {

    public FinrocProgram() {
    }

    @Override
    public String getTargetPrefix() {
        return "";
    }

    public boolean isLibrary() {
        return false;
    }

    @Override
    public String getTarget() {
        String result = "$(TARGET_BIN)/" + getTargetPrefix() + name;
        if (getFinalHandler() == JavaHandler.class) {
            return result.replace("$(TARGET_BIN)", "$(TARGET_JAVA)") + ".jar";
        }
        return result;
    }

    @Override
    public void initTarget(Makefile makefile) {
        if (getFinalHandler() == JavaHandler.class) {
            if (startScripts.size() == 0 && params.containsKey("main-class")) {
                startScripts.add(new StartScript(getTargetFilename().replaceAll("[.]jar$", ""), null));
            }
        }
        super.initTarget(makefile);
    }
}
