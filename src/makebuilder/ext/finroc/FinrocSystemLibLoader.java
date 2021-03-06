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
 * @author Max Reichardt
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

    /**
     * Collects CFLAGS and LDFLAGS from any System library the specified build entity depends on.
     *
     * @param be Build Entity
     */
    public static void processOptions(BuildEntity be) {
        ArrayList<BuildEntity> deps = new ArrayList<BuildEntity>(be.dependencies);
        boolean first = true;
        for (int i = 0; i < deps.size(); i++) {
            BuildEntity dep = deps.get(i);
            if (dep instanceof FinrocSystemLibLoader.SystemLibrary) {
                be.opts.merge(dep.opts, true);
                if (first) {
                    be.target.addDependency("export/$(TARGET)/lib/libenum_strings.so");
                    first = false;
                }
            } else {
                for (BuildEntity depdep : dep.dependencies) {
                    if (!deps.contains(depdep)) {
                        deps.add(depdep);
                    }
                }
            }
        }
    }

    /**
     * Helper function to collect all checked out repositories
     *
     * @param directory Directory to check
     * @param prefix Prefix to add to each directory found in specified directory
     * @param result Collection to add results to
     */
    public void getLocalRepositoriesFromDir(File directory, String prefix, List<String> result) {
        if (directory.exists()) {
            for (File f : directory.listFiles()) {
                if (f.isDirectory() && (!f.getName().startsWith("."))) {
                    result.add(prefix + f.getName());
                }
            }
        }
    }

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
        // Do this only once
        if (loaded) {
            return;
        }
        loaded = true;

        // find all locally installed components
        ArrayList<String> localComponents = new ArrayList<String>();
        if (new File("sources/cpp/core").exists()) {
            localComponents.add("finroc_core");
        }
        getLocalRepositoriesFromDir(new File("sources/cpp/libraries"), "finroc_libraries_", localComponents);
        getLocalRepositoriesFromDir(new File("sources/cpp/plugins"), "finroc_plugins_", localComponents);
        getLocalRepositoriesFromDir(new File("sources/cpp/projects"), "finroc_projects_", localComponents);
        getLocalRepositoriesFromDir(new File("sources/cpp/rrlib"), "rrlib_", localComponents);
        getLocalRepositoriesFromDir(new File("sources/cpp/tools"), "finroc_tools_", localComponents);
        for (String s : localComponents) {
            //System.out.println("Found local repository: " + s);
        }

        // find/load all libraries that contain system information
        File[] pkgconfigFiles = new File(PKG_CONFIG_DIR).exists() ? new File(PKG_CONFIG_DIR).listFiles() : new File[0];
        for (File f : pkgconfigFiles) {
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
                    Process p = Runtime.getRuntime().exec("pkg-config --libs --cflags " + rawName);
                    p.waitFor();
                    String options = Files.readLines(p.getInputStream()).get(0).replaceAll("\\s+", " ");

                    // string replacement because of pkgconfig glitch (add missing '-D's that are present in .pc files)
                    for (int i = 0; i < options.length() - 1; i++) {
                        if (options.charAt(i) == '-' && options.charAt(i + 1) == 'D') {
                            i += 3;
                        } else if (options.charAt(i) == ' ' && options.charAt(i + 1) != '-') {
                            options = options.substring(0, i) + " -D" + options.substring(i);
                        }
                    }
                    be.opts.addOptions(options);

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
