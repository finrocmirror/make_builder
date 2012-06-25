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
package makebuilder.handler;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.StartScript;

/**
 * @author max
 *
 * Creates start scripts for build entities
 */
public class ScriptHandler extends SourceFileHandler.Impl {

    /** Home/root directory relative to script dir */
    private final String homeDirFromScriptDir;

    /** Directory to place scripts in */
    private final String scriptDir;

    /** Makefile variable name for script dir */
    public final static String SCRIPT_DIR_VAR = "SCRIPT_DIR";

    /**
     * @param scriptDir Directory to place scripts in
     * @param homeDirFromScriptDir Home/root directory relative to script dir
     */
    public ScriptHandler(String scriptDir, String homeDirFromScriptDir) {
        this.scriptDir = scriptDir;
        this.homeDirFromScriptDir = homeDirFromScriptDir;
    }

    @Override
    public void init(Makefile makefile) {
        makefile.addVariable(SCRIPT_DIR_VAR + "=" + scriptDir);
    }

    @Override
    public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {
        for (StartScript scr : be.startScripts) {
            String file = scr.getTarget().getName();
            String pre = scr.getParameter("pre");
            String post = scr.getParameter("post");
            pre = pre == null ? "" : (pre + " ");
            post = post == null ? "" : (post + " ");
            scr.getTarget().addCommand("echo Creating start script " + file, false);
            scr.getTarget().addCommand("echo '#!/bin/bash' > " + file, false);
            if (be.getFinalHandler() == JavaHandler.class) {
                String mainClass = scr.getParameter("main-class");
                if (mainClass == null) {
                    scr.getTarget().addCommand("echo '" + pre + "java -jar \"`dirname \"$$0\"`/../share/java/" + be.getTargetFilename() + "\" " + post + "\"$$@\"' >> " + file, false);
                } else {
                    scr.getTarget().addCommand("echo '" + pre + "java -cp \"`dirname \"$$0\"`/../share/java/" + be.getTargetFilename() + "\" " + mainClass + " " + post + "\"$$@\"' >> " + file, false);
                }
            } else {
                // normal executable
                scr.getTarget().addCommand("echo '" + pre + be.getTargetFilename() + " " + post + "\"$$@\"' >> " + file, false);
            }
            scr.getTarget().addCommand("chmod +x " + file, false);
        }
    }
}
