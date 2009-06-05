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

import java.util.ArrayList;
import java.util.TreeSet;

import makebuilder.Blacklist;
import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SrcFile;
import makebuilder.util.ToStringComparator;

/**
 * @author max
 *
 * Merges cpp files before building them
 */
public class CppMerger extends SourceFileHandler.Impl {

	/** Lines to append to each file */
	private final String[] appendLines;

	/** Dependency buffer */
	private final TreeSet<SrcFile> dependencyBuffer = new TreeSet<SrcFile>(ToStringComparator.instance);
	
	/**
	 * @param appendLines Lines to append to each file
	 */
	public CppMerger(String... appendLines) {
		this.appendLines = appendLines;
	}
	
	@Override
	public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {

		// get blacklist entry
		Blacklist.Element blacklist = Blacklist.getInstance().get(be.name); // blacklist entry
		if (blacklist != null && blacklist.compileAllSeparately) {
			return;
		}
		
		// collect c and c++ files
		ArrayList<SrcFile> cs = new ArrayList<SrcFile>(); // c files
		ArrayList<SrcFile> cpps = new ArrayList<SrcFile>(); // c++ files
		for (SrcFile sf : be.sources) {
			if (blacklist != null && blacklist.compileSeparately.contains(sf.getName())) {
				continue;
			} else if (sf.hasExtension("c")) {
				cs.add(sf);
			} else if (sf.hasExtension("cpp")) {
				cpps.add(sf);
			}
		}
		
		// merge c files
		if (cs.size() > 1) {
			makeMergeTarget(cs, be, makefile, builder, blacklist);
		}
		
		// merge cpp files
		if (cpps.size() > 1) {
			makeMergeTarget(cpps, be, makefile, builder, blacklist);
		}
	}

	/**
	 * @param files Files to merge
	 * @param be BuildEntity that merged files belong to
	 * @param makefile Makefile
	 * @param builder Makefile builder instance
	 */
	private void makeMergeTarget(ArrayList<SrcFile> files, BuildEntity be, Makefile makefile, MakeFileBuilder builder, Blacklist.Element blacklist) {

		// command addition if blacklist says we want to replace #include with #import
		String importString = (blacklist != null && blacklist.importMode) ? " | sed -e 's/#include \"/#import \"/'" : "";
		
		dependencyBuffer.clear();
		SrcFile sft = builder.getTempBuildArtifact(be, files.get(0).getExtension(), "merged"); // sft = "source file target"
		Makefile.Target target = makefile.addTarget(sft.relative, true);
		target.addDependency(be.buildFile);
		be.sources.add(sft);
		target.addMessage("Creating " + target.getName());
		target.addCommand("echo \\/\\/ generated > " + target.getName(), false);
		for (SrcFile sf : files) {
			target.addDependency(sf);
			be.sources.remove(sf);
			target.addCommand("echo \\#line 1 \\\"" + sf.relative + "\\\" >> " + target.getName(), false);
			target.addCommand("cat " + sf.relative + importString + " >> " + target.getName(), false);
			for (String s : appendLines) {
				target.addCommand("echo '" + s + "' >> " + target.getName(), false);
			}
			sf.getAllDependencies(dependencyBuffer);
			
			// add directories of all source files - not especially nice - but required for some MCA2 libraries
			if (!sf.dir.isTempDir()) {
				be.opts.includePaths.add(sf.dir.relative);
			}
		}
		target.addDependencies(dependencyBuffer);
	}
}
