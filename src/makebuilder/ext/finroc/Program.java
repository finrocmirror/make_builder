/**
 * You received this file as part of an experimental
 * build tool ('makebuilder') - originally developed for MCA2.
 *
 * Copyright (C) 2008-2014 Max Reichardt,
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
 * New target for all kinds of executables used in the context of Finroc
 * (programs, test programs, examples)
 */
public class Program extends FinrocBuildEntity {

    public Program() {
    }

    @Override
    public String getTargetPrefix() {
        if (isExampleTarget()) {
            return createTargetPrefix() + "_example";
        } else if (isTestTarget()) {
            return createTargetPrefix() + "_test";
        }
        return "";
    }

    @Override
    public boolean isLibrary() {
        return false;
    }

    @Override
    public boolean isOptional() {
        if (isExampleTarget() || isTestTarget()) {
            return true;
        }
        return super.isOptional();
    }

    @Override
    public boolean isUnitTest() {
        if (this.params != null && this.params.containsKey("autorun")) {
            if (this.params.get("autorun").toString().equalsIgnoreCase("false")) {
                return false;
            } else if (this.params.get("autorun").toString().equalsIgnoreCase("true")) {
                return true;
            } else if (!this.params.get("autorun").toString().equalsIgnoreCase("default")) {
                throw new RuntimeException("Invalid autorun parameter '" + this.params.get("autorun") + "' in " + this.buildFile.relative);
            }
        }
        if (isTestTarget()) {
            return true;
        }
        return super.isUnitTest();
    }

    @Override
    public String getTarget() {
        String result = "$(TARGET_BIN)/" + getTargetPrefix() + (isExampleTarget() || isTestTarget() ? createNameString() : name);
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
