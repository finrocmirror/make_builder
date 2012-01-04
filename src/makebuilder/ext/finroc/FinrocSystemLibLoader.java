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
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;
import makebuilder.util.Files;
import makebuilder.util.Util;

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

    /** Have any system libraries been loaded ? */
    private static boolean systemLibsLoaded = false;

    /** Have system libs been printed to console ? */
    private static boolean systemLibsPrinted = false;

    public FinrocSystemLibLoader() {}

    /**
     * @return Have any system libraries been loaded ?
     */
    public static boolean areSystemLibsLoaded() {
        return systemLibsLoaded;
    }

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
        // Do this only once
        if (loaded) {
            return;
        }
        loaded = true;

        // find all locally installed components
        Process p = Runtime.getRuntime().exec("finroc_status -c ");
        p.waitFor();
        List<String> localComponents = Files.readLines(p.getInputStream());

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

                List<String> pkglines = Files.readLines(f);
                String component = null;
                for (String line : pkglines) {
                    if (line.startsWith("component=")) {
                        component = line.substring("component=".length());
                        break;
                    }
                }
                if (component == null) {
                    System.err.println("Cannot find component that " + f.getAbsolutePath() + " belongs to. Skipping.");
                    continue;
                }

                boolean exists = false;
                for (String localComponent : localComponents) {
                    exists |= localComponent.split(" ")[0].equals(component);
                }

                if (!exists) {
                    SystemLibrary be = new SystemLibrary();
                    be.name = libName;
                    be.buildFile = scanner.registerBuildProduct(f.getAbsolutePath());

                    // Get compile options
                    p = Runtime.getRuntime().exec("pkg-config --libs --cflags " + rawName);
                    p.waitFor();
                    be.opts.addOptions(Files.readLines(p.getInputStream()).get(0).replace("_PRESENT_ _LIB_", "_PRESENT_ -D _LIB_")); // string replacement because of pkgconfig glitch

                    // Get headers belonging to lib
                    for (String line : pkglines) {
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

        public SystemLibrary() {
            systemLibsLoaded = true;
        }

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

        @Override
        public void resolveDependencies(List<BuildEntity> buildEntities, MakeFileBuilder builder) throws Exception {
            super.resolveDependencies(buildEntities, builder);
            if (!systemLibsPrinted) {
                printSystemLibs(builder);
                systemLibsPrinted = true;
            }
        }

    }

    public static void printSystemLibs(MakeFileBuilder builder) {
        SortedSet<String> set = new TreeSet<String>();
        for (BuildEntity be : builder.buildEntities) {
            if (be instanceof FinrocSystemLibLoader.SystemLibrary) {
                set.add(be.getReferenceName());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(s);
        }
        System.out.println(Util.color("Using system libraries: " + sb.toString(), Util.Color.Y, false));
    }

}
