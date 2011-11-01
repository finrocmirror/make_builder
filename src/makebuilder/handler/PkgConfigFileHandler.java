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
package makebuilder.handler;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SrcFile;
import makebuilder.util.ToStringComparator;

/**
 * @author max
 *
 * Handler to generate pkgconfig (.pc) files for all C++ libraries that
 * are created using makebuilder.
 */
public class PkgConfigFileHandler extends SourceFileHandler.Impl {

    /** Directory to place generated .pc files in */
    private final String outputDir;

    /** Root directory of system installation - typically "/usr" or "/usr/local" */
    private final String systemInstallationRoot;

    /**
     * @param outputDir Directory to place generated .pc files in
     * @param systemInstallationRoot Root directory of system installation - typically "/usr" or "/usr/local"
     */
    public PkgConfigFileHandler(String outputDir, String systemInstallationRoot) {
        this.outputDir = outputDir;
        this.systemInstallationRoot = systemInstallationRoot;
    }

    @Override
    public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {
        if (!be.isLibrary() || (!(be.getFinalHandler() == CppHandler.class))) {
            return;
        }

        String beTarget = be.getTargetFilename();
        beTarget = beTarget.substring(0, beTarget.length() - 3);

        final String pcFile = outputDir + File.separator + beTarget.substring(3) + ".pc";
        Makefile.Target t = makefile.addTarget(pcFile, false, be.getRootDir());
        be.target.addOrderOnlyDependency(pcFile);

        // add all build files as dependencies - and collect all external libraries
        Set<SrcFile> buildFiles = new TreeSet<SrcFile>(ToStringComparator.instance);
        collectBuildFilesAndExtLibs(be, buildFiles);
        for (SrcFile sf : buildFiles) {
            t.addDependency(sf.relative);
        }

        // C++ target?
        boolean cxx = false;
        for (SrcFile sf : be.sources) {
            if (sf.getExtension().equalsIgnoreCase("cpp")) {
                cxx = true;
            }
        }

        String cflags = be.opts.createOptionString(true, false, cxx).replaceAll(" -fPIC", "");
        String ldflags = be.opts.createOptionString(false, true, cxx).replaceAll("-shared ", "");

        // create .pc file
        t.addCommand("echo 'prefix=" + systemInstallationRoot + "' >> " + pcFile, false);
        t.addCommand("echo 'exec_prefix=$${prefix}' >> " + pcFile, false);
        t.addCommand("echo 'libdir=$${exec_prefix}/lib' >> " + pcFile, false);
        t.addCommand("echo 'includedir=$${prefix}/include' >> " + pcFile, false);
        t.addCommand("echo '' >> " + pcFile, false);
        t.addCommand("echo 'Name: " + beTarget + "' >> " + pcFile, false);
        t.addCommand("echo 'Description: " + beTarget + "' >> " + pcFile, false);
        t.addCommand("echo 'Version: 1.0' >> " + pcFile, false);
        t.addCommand("echo 'Libs: -L$${libdir} -l" + beTarget.substring(3) + " " + ldflags + "' >> " + pcFile, false);
        t.addCommand("echo 'Cflags: -I$${includedir} " + cflags + "' >> " + pcFile, false);
    }

    /**
     * Collect build files of this build entity and all of its dependencies
     * (recursive function)
     *
     * @param be Current build entity
     * @param buildFiles Set with results
     */
    private void collectBuildFilesAndExtLibs(BuildEntity be, Set<SrcFile> buildFiles) {
        buildFiles.add(be.buildFile);
        for (BuildEntity be2 : be.dependencies) {
            collectBuildFilesAndExtLibs(be2, buildFiles);
        }
    }
}
