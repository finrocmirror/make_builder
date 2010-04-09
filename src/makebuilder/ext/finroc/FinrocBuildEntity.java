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
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;

/**
 * @author max
 *
 * Finroc build entity.
 * Decides which target entity belongs to.
 */
public abstract class FinrocBuildEntity extends BuildEntity {

	@Override
	public void initTarget(Makefile makefile) {
		super.initTarget(makefile);
		String rootDir2 = getRootDir().relative;
		boolean project = rootDir2.startsWith("projects");
		boolean lib = rootDir2.startsWith("libraries");
		boolean tool = rootDir2.startsWith("tools");
		boolean plugin = rootDir2.startsWith("tools");
		boolean rrlib = rootDir2.startsWith("rrlib");
		boolean sysInstall = MakeFileBuilder.getOptions().containsKey("usesysteminstall");
		if (lib || rrlib || plugin) {
			target.addToPhony("libs");
		}
		if (tool) {
			target.addToPhony("tools");
		}
		if (plugin) {
			target.addToPhony("plugins");
		}
		if (lib) {
			if (sysInstall) {
				target.addToPhony(rootDir2.substring(rootDir2.lastIndexOf(FS) + 1));
			} else {
				target.addToPhony(rootDir2.substring(rootDir2.lastIndexOf(FS) + 1), "tools");
			}
		}
		if (project || tool) {
			String projectx = rootDir2.substring(rootDir2.indexOf(FS) + 1);
			if (projectx.contains(FS)) {
				if (sysInstall) {
					target.addToPhony(projectx);
				} else {
					target.addToPhony(projectx, "tools");
				}
				projectx = projectx.substring(0, projectx.indexOf(FS));
			}
			if (sysInstall) {
				target.addToPhony(projectx);
			} else {
				target.addToPhony(projectx, "tools");
			}
		}
		
		String targetFile = getTarget();
		targetFile = targetFile.substring(targetFile.lastIndexOf(File.separator) + 1);
		target.addToPhony(targetFile + ((isLibrary() || targetFile.endsWith(".jar")) ? "" : "-bin"));
	}
}
