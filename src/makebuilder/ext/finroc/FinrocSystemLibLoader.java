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
package makebuilder.ext.finroc;

import java.io.File;
import java.util.List;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;
import makebuilder.util.Files;

/**
 * @author max
 *
 * Handles system-wide installation of Finroc
 *
 * (Implementation note: This is a handler, because all local libraries need to be loaded
 *  before - to make sure we do not load any duplicates here)
 */
public class FinrocSystemLibLoader extends SourceFileHandler.Impl {

    /** Have system libraries been loaded ? */
    boolean loaded = false;

    /** Location of pkgconfig files */
    private static final String PKG_CONFIG_DIR = "/usr/lib/pkgconfig";

    public FinrocSystemLibLoader() {}

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
        // Do this only once
        if (loaded) {
            return;
        }
        loaded = true;

        // find/load all libraries that contain system information
        for (File f : new File(PKG_CONFIG_DIR).listFiles()) {
            if (f.getName().startsWith("rrlib_") || f.getName().startsWith("finroc_") || f.getName().startsWith("mca2_")) {
                String rawName = f.getName().substring(0, f.getName().length() - 3);
                String libName = "lib" + rawName + ".so";
                String includeDir = "/usr/include";
                if (rawName.startsWith("finroc_")) {
                    includeDir += "/finroc";
                } else if (rawName.startsWith("mca2_")) {
                    includeDir += "/mca2";
                }
                boolean exists = false;
                for (BuildEntity be : builder.buildEntities) {
                    exists |= be.getTargetFilename().equals(libName);
                }
                if (!exists) {
                    SystemLibrary be = new SystemLibrary();
                    be.name = libName;
                    be.buildFile = scanner.registerBuildProduct(f.getAbsolutePath());

                    // Get compile options
                    Process p = Runtime.getRuntime().exec("pkg-config --libs --cflags " + rawName);
                    p.waitFor();
                    be.opts.addOptions(Files.readLines(p.getInputStream()).get(0));

                    // Get headers belonging to lib
                    List<String> lines = Files.readLines(f);
                    for (String line : lines) {
                        if (line.startsWith("headerlist=")) {
                            String[] headers = line.substring("headerlist=".length()).split(" ");
                            for (String header : headers) {
                                String hdr = includeDir + "/" + header;
                                SrcFile sf = scanner.registerBuildProduct(hdr);
                                sf.setOwner(be);
                                if (!new File(hdr).exists()) {
                                    System.err.println("Cannot find system header " + hdr + " from " + f.getPath());
                                }
                            }
                            break;
                        }
                    }
                    be.targetName = "/usr/lib/" + libName;
                    builder.buildEntities.add(be);
                }
            }
        }
    }

    public class SystemLibrary extends BuildEntity {

        private String targetName;

        public String getTarget() {
            return targetName;
        }

        public void initTarget(Makefile makefile) {
            target = makefile.DUMMY_TARGET;
        }

        @Override
        public Class <? extends SourceFileHandler > getFinalHandler() {
            return null;
        }
    }
}
