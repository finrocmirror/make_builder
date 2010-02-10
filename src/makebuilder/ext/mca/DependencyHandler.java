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
 * @author max
 *
 * Responsible for calculation of build entity dependencies files (the files are named ".dependencies" and include one line with the repository name for each depending entity)
 */
public class DependencyHandler extends SourceFileHandler.Impl {

	// Dependencies for all directories
	private Map<String, TreeSet<String>> dependencies = new HashMap<String, TreeSet<String>>();
	
	// Libraries to exclude
	private final List<String> EXCLUDE = new ArrayList<String>(Arrays.asList(new String[]{"kernel", "general", "math", "qt", "fileio", "gui", "browser", "libraries", "etc", ".", "..", "common"}));
	
	@Override
	public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {
		String libDir = getLibDir(be.getRootDir());
		
		// get/create tree set
		TreeSet<String> depSet = getTreeSet(libDir);
		
		// add all dependencies to dependency set
		for (BuildEntity dep : be.dependencies) {
			String depDir = getLibDir(dep.getRootDir());
			String name = getLibraryDirName(dep.getRootDir());
            if (!EXCLUDE.contains(name)) {
			    depSet.add(depDir);
            }
		}
	}
	
	public void writeFiles(String print) throws Exception {
		for (Map.Entry<String, TreeSet<String>> entry : dependencies.entrySet()) {
			TreeSet<String> result = new TreeSet<String>();
			TreeSet<String> visited = new TreeSet<String>();
			visited.add(entry.getKey());
			collectDependencies(result, visited, entry.getValue(), 0, print != null && print.equals(entry.getKey()));
			
			// (re)write .dependencies
			File output = new File(entry.getKey() + "/.dependencies");
			if (!output.getParentFile().exists()) {
				continue;
			}
			result.remove(entry.getKey());
			
			PrintStream ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(output)));
			for (String s : result) {
				ps.println("mcal_" + s.substring(s.indexOf("/") + 1));
			}
			ps.close();
		}
	}

	private void collectDependencies(TreeSet<String> result, TreeSet<String> visited, TreeSet<String> value, int level, boolean print) {
		result.addAll(value);
		for (String s : value) {
			TreeSet<String> set = dependencies.get(s);
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

	private TreeSet<String> getTreeSet(String libDir) {
		TreeSet<String> depSet = dependencies.get(libDir);
		if (depSet == null) {
			depSet = new TreeSet<String>();
			dependencies.put(libDir, depSet);
		}
		return depSet;
	}

	private String getLibDir(SrcDir rootDir) {
		String[] tokens = rootDir.relative.split("/");
		return tokens[0] + "/" + tokens[1];
	}

	@Override
	public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
		String f = file.relative;
		if (!(f.endsWith(".c") || f.endsWith(".cpp") || f.endsWith(".h") || f.endsWith(".hpp"))) {
			return;
		}
		
		String libDir = getLibDir(file.dir);
		TreeSet<String> depSet = getTreeSet(libDir);
		for (String dep : file.rawDependencies) {
			if (!dep.contains("/")) {
				continue;
			}
			String name = dep.substring(0, dep.indexOf("/"));
			
			//String name = getLibraryDirName(file.dir);
            if (!EXCLUDE.contains(name)) {
			    depSet.add("libraries/" + name);
            }
        }
	}
	

	public String getLibraryDirName(SrcDir dep) {
		String libDir = getLibDir(dep);
		return libDir.substring(libDir.indexOf("/") + 1);
	}

	@Override
	public void init(Makefile makefile) {
		File projectDir = new File("projects");
		for (File f : projectDir.listFiles()) {
			if (f.isDirectory()) {
				EXCLUDE.add(f.getName());
			}
		}
	}
	
}
