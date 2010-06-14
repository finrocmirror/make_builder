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

import java.util.ArrayList;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.StartScript;
import makebuilder.handler.JavaHandler;

/**
 * @author max
 *
 * Generates a script that echos a LD_PRELOAD=<libs> to the console.
 * This is required to load native code with static TLS in fingui.
 */
public class LdPreloadScriptHandler extends SourceFileHandler.Impl {

    /** Name of Preload script */
    public static final String FILENAME = "$(TARGET_BIN)/fingui_preload_helper";

    /** Target for preload script */
    private Makefile.Target target;

    /** .so files that we already have in preload string */
    private final ArrayList<String> preloads = new ArrayList<String>();

    @Override
    public void init(Makefile makefile) {
        target = makefile.addTarget(FILENAME, false, null);
        target.addCommand("echo echo -n export LD_PRELOAD=>" + FILENAME, false);
        target.addCommand("chmod +x " + FILENAME, false);

        makefile.addVariable("PRELOAD_HELPER=`$$FINROC_HOME/$(TARGET_BIN)/fingui_preload_helper` && ");
    }

    @Override
    public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {

        // collect .so files to preload
        if (be.getFinalHandler() == JavaHandler.class) {
            for (BuildEntity b : be.dependencies) {
                if (b.getTarget().endsWith(".so")) { // c++ library
                    if (!preloads.contains(b.getTargetFilename())) {
                        target.addDependency(b.getTarget());
                        target.addCommand("echo echo -n " + (preloads.size() > 0 ? ":" : "") + b.getTargetFilename() + ">>" + FILENAME, false);
                        preloads.add(b.getTargetFilename());
                    }
                }
            }
        }

        // add dependency to us in any scripts which uses us
        for (StartScript scr : be.startScripts) {
            String pre = scr.getParameter("pre");
            if (pre != null && pre.contains("$(PRELOAD_HELPER)")) {
                scr.getTarget().addDependency(FILENAME);
            }
        }
    }


}
