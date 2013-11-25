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

import java.util.Set;
import java.util.TreeSet;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SrcFile;
import makebuilder.handler.CppHandler;
import makebuilder.libdb.LibDB;
import makebuilder.util.ToStringComparator;
import makebuilder.libdb.ExtLib;

/**
 * @author max
 *
 * Generates information file for every library (.so)
 * This information currently includes the required closures for compiling this library
 * (and using its headers!).
 * Output is one file per .so: <target-name> + ".info"
 *
 * (MCA-specific; only needed for system-installs)
 */
public class LibInfoGenerator extends SourceFileHandler.Impl {

    /** Directory that will contain library information files */
    private final String outputDir;

    /** File extension for information files */
    public final static String EXT = ".info";

    /**
     * @param outputDir Directory that will contain library information files
     */
    public LibInfoGenerator(String outputDir) {
        this.outputDir = outputDir;
    }

    @Override
    public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {
        if (!be.isLibrary() || (!(be.getFinalHandler() == CppHandler.class))) {
            return;
        }

        String beTarget = be.getTarget();
        final String infoFile = outputDir + File.separator + beTarget.substring(beTarget.lastIndexOf(File.separator) + 1) + EXT;
        Makefile.Target t = makefile.addTarget(infoFile, false, be.getRootDir());
        be.target.addOrderOnlyDependency(infoFile);

        // add all build files as dependendencies
        Set<SrcFile> buildFiles = new TreeSet<SrcFile>(ToStringComparator.instance);
        collectBuildFiles(be, buildFiles);
        for (SrcFile sf : buildFiles) {
            t.addDependency(sf.relative);
        }

        // string with all closure names
        String extlibs = "";
        for (ExtLib el : be.extlibs) {
            extlibs += " " + el.name;
        }
        t.addCommand("echo '" + extlibs.trim() + "' > " + infoFile, false);

        // find all header files belonging to target
        StringBuilder sb = new StringBuilder();
        for (SrcFile sf : builder.getSources().getAllFiles()) {
            if (sf.getOwner() == be && (sf.relative.endsWith(".h") || sf.relative.endsWith(".hpp"))) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                String s = sf.relative;
                if (s.startsWith("sources/cpp/")) {
                    s = s.substring("sources/cpp/".length());
                }
                sb.append(s);
            }
        }
        t.addCommand("echo '" + sb.toString() + "' >> " + infoFile, false);
    }

    /**
     * Collect build files of this build entity and all of its dependencies
     * (recursive function)
     *
     * @param be Current build entity
     * @param buildFiles Set with results
     */
    private void collectBuildFiles(BuildEntity be, Set<SrcFile> buildFiles) {
        buildFiles.add(be.buildFile);
        for (BuildEntity be2 : be.dependencies) {
            collectBuildFiles(be2, buildFiles);
        }
    }
}
