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

import makebuilder.SourceFileHandler;
import makebuilder.handler.CppHandler;

/**
 * @author max
 *
 * Finroc library
 */
public class FinrocProgram extends FinrocBuildEntity {

	@Override
	public String getTarget() {
		if (this.getRootDir().relative.startsWith("libraries")) {
			return prefix("finroc_library_");
		} else if (this.getRootDir().relative.startsWith("plugins")) {
			return prefix("finroc_plugin_");
		} else if (this.getRootDir().relative.startsWith("core")) {
			return "$(TARGET_BIN)/finroc_core_" + name;
		}
		return "$(TARGET_BIN)/" + name;
	}

	private String prefix(String prefixBase) {
		String[] parts = this.getRootDir().relative.split("/");
		if (parts.length >= 2) {
			String prefix = prefixBase + parts[1];
			if (!name.startsWith(prefix)) {
				String n = name;
				if (n.startsWith(prefixBase)) {
					n = n.substring(prefixBase.length());
				} else if (n.startsWith(parts[1])) {
					n = n.substring(parts[1].length() + 1);
				}
				return ("$(TARGET_BIN)/" + prefix + (n.length() > 0 ? ("_" + n) : "")).replace("__", "_");
			}
		}
		return "$(TARGET_BIN)/" + name;
	}
	
	@Override
	public Class<? extends SourceFileHandler> getFinalHandler() {
		return CppHandler.class;
	}
}
