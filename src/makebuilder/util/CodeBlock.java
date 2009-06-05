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
package makebuilder.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;

/**
 * @author max
 *
 * Block of code
 */
public class CodeBlock extends ArrayList<Object> {

	/** uid */
	private static final long serialVersionUID = 8641893763578667982L;
	
	public CodeBlock() {}

	/**
	 * Write contents of CodeBlock to Writer/Stream
	 * 
	 * @param w Writer/Stream
	 * @throws Exception
	 */
	public void writeTo(Writer w) throws Exception {
		PrintWriter pw = new PrintWriter(w);
		writeToInternal(pw);
		pw.close();
	}

	/**
	 * Helper for above
	 * 
	 * @param pw Writer/Stream
	 */
	private void writeToInternal(PrintWriter pw) throws Exception {
		for (Object element : this) {
			if (element instanceof String) {
				pw.println(element.toString());
			} else if (element instanceof CodeBlock) {
				//CodeBlock cb = (CodeBlock)element;
				//if (!cb.globalNamespace) {
					((CodeBlock)element).writeToInternal(pw);
				/*} else {
					File f = new File("/tmp/temp" + x.getAndIncrement() + ".h");
					pw.println("#include \"" + f.getAbsolutePath() + "\"");
					cb.writeTo(f);
				}*/
			}
		}
	}

	public void removeRange(int start, int end) {
		int elements = end - start + 1;
		for (int i = 0; i < elements; i++) {
			remove(start);
		}
	}

	/**
	 * Write contents of CodeBlock to file
	 * 
	 * @param file File
	 */
	public void writeTo(File file) throws Exception {
		PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
		writeTo(pw);
		pw.close();
	}
}
