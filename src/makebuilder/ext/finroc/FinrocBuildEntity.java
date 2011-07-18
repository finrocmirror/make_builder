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

import java.io.File;

import makebuilder.BuildEntity;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SrcFile;
import makebuilder.StartScript;
import makebuilder.handler.CppHandler;
import makebuilder.handler.JavaHandler;

/**
 * @author max
 *
 * Finroc build entity.
 * Decides which target entity belongs to.
 */
public abstract class FinrocBuildEntity extends BuildEntity {

    protected Makefile.Target startScriptTarget;

    private Class <? extends SourceFileHandler > finalHandler;

    @Override
    public Class <? extends SourceFileHandler > getFinalHandler() {
        if (finalHandler == null) {
            for (SrcFile sf : sources) {
                if (sf.hasExtension("java")) {
                    finalHandler = JavaHandler.class;
                    break;
                } else if (sf.hasExtension("c", "cpp", "h", "hpp")) {
                    finalHandler = CppHandler.class;
                    break;
                }
            }
        }
        if (finalHandler == null) {
            System.out.println("warning: cannot determine final handler for target " + toString());
        }
        return finalHandler;
    }

    @Override
    public void initTarget(Makefile makefile) {
        super.initTarget(makefile);
        String rootDir2 = getRootDir().relative;
        if (rootDir2.startsWith("sources")) {
            rootDir2 = rootDir2.substring(9);
            rootDir2 = rootDir2.substring(rootDir2.indexOf("/") + 1);
        }
        boolean lib = rootDir2.startsWith("libraries");
        boolean tool = rootDir2.startsWith("tools");
        boolean plugin = rootDir2.startsWith("plugins");
        boolean rrlib = rootDir2.startsWith("rrlib");
        if (lib || rrlib || plugin) {
            addToPhony("libs");
        }
        if (tool) {
            addToPhony("tools");
        }
        if (plugin) {
            addToPhony("plugins");
        }

        String targetFile = getTarget();
        targetFile = targetFile.substring(targetFile.lastIndexOf(File.separator) + 1);
        addToPhony(targetFile + ((isLibrary() || targetFile.endsWith(".jar")) ? "" : "-bin"));
    }

    /**
     * Adds main target to specified phony target
     *
     * (see Makefile.Target.addToPhony for details on parameters)
     * (may be overridden for customization)
     */
    public void addToPhony(String phony, String... phonyDefaultDependencies) {
        if (startScripts.size() == 0) {
            target.addToPhony(phony, phonyDefaultDependencies);
        } else {
            for (StartScript scr : startScripts) {
                scr.getTarget().addToPhony(phony, phonyDefaultDependencies);
            }
        }
    }
}
