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

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SrcFile;

/**
 * @author max
 *
 * Copies h files from libraries and tools (including generated ones in build) to export/.../include
 * (MCA-specific; only needed for system-installs) 
 */
public class HFileCopier extends SourceFileHandler.Impl {

	/** Have copy targets been created in Makefile */
	private boolean copied = false;
	
	/** Destination path */
	private final String destPath;
	
	public HFileCopier(String destPath) {
		this.destPath = destPath;
	}
	
	@Override
	public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {
		// do this only once
		if (copied) {
			return;
		}
		copied = true;
		
		for (SrcFile sf : builder.getSources().getAllFiles()) {
			if (sf.hasExtension("h", "hpp", "cc", "inl")) { // .cc and .inl because we have such headers in the mca repositories :-/
				String tmp = sf.relative;
				if (tmp.startsWith(builder.tempBuildPath.relative)) {
					tmp = tmp.substring(builder.tempBuildPath.relative.length() + 1);
				}
				if (tmp.startsWith("libraries") || tmp.startsWith("tools")) {
					String target = destPath + "/" + tmp.substring(tmp.indexOf('/') + 1);
					Makefile.Target t = makefile.addTarget(target, false);
					t.addDependency(sf);
					t.addCommand("cp " + sf.relative + " " + target, true);
					t.addToPhony("sysinstall");
				}
			}
		}
	}
}
