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

/**
 * @author max
 *
 * Handles and transforms source files in some way.
 * 
 * This is a central abstraction in the MakeBuilder.
 */
public interface SourceFileHandler {

	/**
	 * Initialize content handler
	 * 
	 * @param makefile Makefile (e.g. for adding variables and stuff)
	 */
	public void init(Makefile makefile);
	
	/**
	 * Scan source files for relevant information (optional operation)
	 * (e.g. QT macros, DESCR macros, process build files)
	 * 
	 * @param file File to process
	 * @param makefile Makefile - targets may already be added for source files not directly dependent on build entities 
	 * @param scanner Source scanner instance
	 * @param builder Builder instance
	 */
	public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception;
	
	/**
	 * Process/transform/build build entity (optional operation)
	 * (quite abstract)
	 * (Add targets to Makefile, remove processed files from build entity - add new ones (target results))  
	 * 
	 * @param be Build Entity to process
	 * @param makefile Makefile to add targets to
	 * @param builder Builder instance
	 */
	public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception;
	
	/**
	 * @author max
	 * 
	 * Empty implementation of above
	 */
	public static class Impl implements SourceFileHandler {

		@Override
		public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {}

		@Override
		public void init(Makefile makefile) {}

		@Override
		public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {}
	}
}
