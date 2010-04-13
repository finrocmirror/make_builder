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
package makebuilder;

import makebuilder.handler.CppHandler;

/**
 * @author max
 *
 * This class is a build entity that will be compiled to a c/c++ (shared) library.
 */
public class Library extends BuildEntity {

	@Override
	public String getTarget() {
		return "($TARGET_DIR)/lib" + name + ".so";
	}

	@Override
	public Class<? extends SourceFileHandler> getFinalHandler() {
		return CppHandler.class;
	}

}
