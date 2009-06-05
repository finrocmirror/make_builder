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

import java.util.List;

/**
 * @author max
 *
 * Any class that loads build files (make.xml, SConscript etc.)
 * only needs to implement this interface
 */
public interface BuildFileLoader {

	/**
	 * Called for every file in source directory.
	 * Loader should process it, if it finds it interesting and should write resulting build entities
	 * to result list.
	 * 
	 * @param file Current source file
	 * @param result List with results - should only be used to add new build entities
	 * @param scanner SourceScanner instance
	 * @param builder MakeFileBuilder instance
	 */
	public void process(SrcFile file, List<BuildEntity> result, SourceScanner scanner, MakeFileBuilder builder) throws Exception;
}
