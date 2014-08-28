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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import makebuilder.BuildEntity;
import makebuilder.SourceFileHandler;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcDir;
import makebuilder.SrcFile;

/**
 * @author Max Reichardt
 *
 * Responsible for calculation of build entity dependencies files (the files are named ".dependencies" and include one line with the repository name for each depending entity)
 */
public class DependencyHandler extends SourceFileHandler.Impl {

    /** Dependencies for all directories (repository roots) */
    private Map<SrcDir, TreeSet<SrcDir>> dependencies = new HashMap<SrcDir, TreeSet<SrcDir>>();

    /** Optional dependencies for all directories (repository roots) */
    private Map<SrcDir, TreeSet<SrcDir>> optDependencies = new HashMap<SrcDir, TreeSet<SrcDir>>();

    //TODO maybe... private Map<String, String> repoRoots = new HashMap<String, String>();

    /** Libraries to exclude */
    private final List<String> EXCLUDE = new ArrayList<String>(Arrays.asList(new String[] {/*"kernel", "general", "math", "qt", "fileio",*/ "gui", "browser", "libraries", "etc", ".", "..", "common"}));

    @Override
    public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {

        // skip sources not in svn
        if (be.getRootDir().getRepository() == null) {
            return;
        }

        // get/create tree set
        TreeSet<SrcDir> depSet = getTreeSet(be.getRootDir(), be.isTestProgram());
        TreeSet<SrcDir> optDepSet = getTreeSet(be.getRootDir(), true);

        // add all dependencies to dependency set
        for (BuildEntity dep : be.dependencies) {
            String name = getLibraryDirName(dep.getRootDir());
            if (!EXCLUDE.contains(name) && (!be.optionalDependencies.contains(dep))) {
                depSet.add(be.getRootDir().getRepositoryRoot());
            }
        }

        // add all dependencies to dependency set
        for (BuildEntity dep : be.optionalDependencies) {
            String name = getLibraryDirName(dep.getRootDir());
            if (!EXCLUDE.contains(name)) {
                optDepSet.add(be.getRootDir().getRepositoryRoot());
            }
        }
    }

    public void writeFiles(String print) throws Exception {
        writeFilesHelper(print, dependencies, ".dependencies");
        writeFilesHelper(print, optDependencies, ".optional_dependencies");
    }

    public void writeFilesHelper(String print, Map<SrcDir, TreeSet<SrcDir>> deps, String file) throws Exception {
        for (Map.Entry<SrcDir, TreeSet<SrcDir>> entry : deps.entrySet()) {
            TreeSet<SrcDir> result = new TreeSet<SrcDir>();
            TreeSet<SrcDir> visited = new TreeSet<SrcDir>();
            visited.add(entry.getKey());
            collectDependencies(result, visited, entry.getValue(), 0, print != null && print.equals(entry.getKey()));

            // (re)write .dependencies
            File output = new File(entry.getKey() + "/" + file);
            if (!output.getParentFile().exists()) {
                continue;
            }
            result.remove(entry.getKey());

            PrintStream ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(output)));
            for (SrcDir s : result) {
                String repo = s.getRepository();
                if (repo == null) {
                    System.out.println("warning: dir " + s.relative + " has no repository");
                } else {
                    if (deps == dependencies || (!dependencies.get(entry.getKey()).contains(s))) {
                        repo = repo.substring(repo.lastIndexOf("/") + 1);
                        ps.println(repo);
                    }
                }
            }
            ps.close();
        }
    }

    private void collectDependencies(TreeSet<SrcDir> result, TreeSet<SrcDir> visited, TreeSet<SrcDir> value, int level, boolean print) {
        result.addAll(value);
        for (SrcDir s : value) {
            TreeSet<SrcDir> set = dependencies.get(s);
            if (print) {
                for (int i = 0; i < level; i++) {
                    System.out.print("  ");
                }
                System.out.println(s);
            }
            if (set != null && (!visited.contains(s))) {
                visited.add(s);
                collectDependencies(result, visited, set, level + 1, print);
            }
        }
    }

    private TreeSet<SrcDir> getTreeSet(SrcDir dir, boolean optional) {
        Map<SrcDir, TreeSet<SrcDir>> deps = optional ? optDependencies : dependencies;
        dir = dir.getRepositoryRoot();
        TreeSet<SrcDir> depSet = deps.get(dir);
        if (depSet == null) {
            depSet = new TreeSet<SrcDir>();
            deps.put(dir, depSet);
        }
        return depSet;
    }

    private String getRepositoryDir(SrcDir rootDir) {
        //String[] tokens = rootDir.relative.split("/");
        //return tokens[0] + "/" + tokens[1];
        return rootDir.getRepositoryRoot().relative;
    }

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
        String f = file.relative;
        if (!(f.endsWith(".c") || f.endsWith(".cpp") || f.endsWith(".h") || f.endsWith(".hpp"))) {
            return;
        }

        boolean testProgram = file.getOwner() == null || file.getOwner().isTestProgram();
        TreeSet<SrcDir> depSet = getTreeSet(file.dir, testProgram);
        for (SrcFile sf : file.dependencies) {
            //String name = dep.substring(0, dep.indexOf("/"));

            //String name = getLibraryDirName(file.dir);
            //if (!EXCLUDE.contains(name)) {
            SrcDir root = sf.dir.getRepositoryRoot();
            if (root != null) {
                depSet.add(root);
            } else {
                //System.out.println("warning: " + sf.relative + " no source repository found");
            }
            //}
        }
    }


    public String getLibraryDirName(SrcDir dep) {
        String libDir = getRepositoryDir(dep);
        return libDir.substring(libDir.indexOf("/") + 1);
    }

    @Override
    public void init(Makefile makefile) {
        /*File projectDir = new File("projects");
        for (File f : projectDir.listFiles()) {
            if (f.isDirectory()) {
                EXCLUDE.add(f.getName());
            }
        }*/
    }

}
