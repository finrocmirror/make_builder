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
package makebuilder.ext.mca;

import java.io.File;
import java.util.List;

import makebuilder.BuildEntity;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SrcFile;
import makebuilder.ext.finroc.FinrocBuildEntity;
import makebuilder.ext.finroc.FinrocSystemLibLoader;
import makebuilder.handler.CppHandler;
import makebuilder.util.Files;

/**
 * @author Max Reichardt
 *
 * MCA build entity.
 *
 * This can be an MCA program or an MCA library.
 * Objects of this class are the result of parsing MCA2 SConscripts.
 */
public abstract class MCABuildEntity extends BuildEntity {

    @Override
    public void initTarget(Makefile makefile) {
        super.initTarget(makefile);
        String rootDir2 = getRootDir().relative;
        if (rootDir2.startsWith("sources/cpp/mca2-legacy/")) {
            rootDir2 = rootDir2.substring("sources/cpp/mca2-legacy/".length());
        }
        boolean lib = rootDir2.startsWith("libraries");
        boolean tool = rootDir2.startsWith("tools");
        if (lib || tool) {
            target.addToPhony("libs");
        }
        if (tool) {
            target.addToPhony("tools");
        }

        String targetFile = getTarget();
        targetFile = targetFile.substring(targetFile.lastIndexOf(File.separator) + 1);
        target.addToPhony(targetFile + (isLibrary() ? "" : "-bin"));
    }

    @Override
    public Class <? extends SourceFileHandler > getFinalHandler() {
        return CppHandler.class;
    }

    @Override
    protected String getHintForMissingDependency(SrcFile sf) {
        String miss = sf.getMissingDependency();
        try {
            Process p = Runtime.getRuntime().exec(FinrocBuildEntity.SEARCH_BIN + " -f " + miss);
            p.waitFor();
            List<String> lines = Files.readLines(p.getInputStream());
            if (lines.size() > 0) {
                return lines.get(0).split(" ")[0] + " repository";
            } else {
                return "file is not known";
            }
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    public void computeOptions() {
        super.computeOptions();
        if (FinrocSystemLibLoader.areSystemLibsLoaded()) {
            FinrocSystemLibLoader.processOptions(this);
        }
    }

}
