/**
 * You received this file as part of Jmcagui - a universal
 * (Web-)GUI editor for Robotic Systems.
 *
 * Copyright (C) 2007 Max Reichardt
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
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
package tools.turbobuilder;

import java.io.File;
import java.io.FilenameFilter;

import javax.swing.filechooser.FileFilter;


/**
 * FilenameFilter custom file Extensions.
 * 
 * @author Max Reichardt
 *
 */
public class ExtensionFilenameFilter extends FileFilter implements java.io.FileFilter, FilenameFilter {

	/**
	 * FilenameFilter accepts files with Extensions in list
	 */
	private String[] accepts;
	
	/** Description of FileFilter */
	private String descr;
	
	/**
	 * @param acceptsExtensions Extensions to accept
	 */
	public ExtensionFilenameFilter(String[] acceptsExtensions) {
		this("", acceptsExtensions);
	}
	
	/**
	 * @param description Description of FileFilter
	 * @param acceptsExtensions Extensions to accept
	 */
	public ExtensionFilenameFilter(String description, String[] acceptsExtensions) {
		accepts = new String[acceptsExtensions.length];
		descr = description;
		for (int i = 0; i < acceptsExtensions.length; i++) {
			accepts[i] = acceptsExtensions[i].toLowerCase();
		}
	}
	
	/* (non-Javadoc)
	 * @see java.io.FilenameFilter#accept(java.io.File, java.lang.String)
	 */
	public boolean accept(File file) {
		if (file.isDirectory()) {
			return true;
		}
		String name = file.getName();
		String ext = name.substring(name.lastIndexOf(".") + 1).toLowerCase();
		for (int i = 0; i < accepts.length; i++) {
			if (ext.equals(accepts[i])) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String getDescription() {
		String tmp = "";
		for (String s : accepts) {
			tmp += ",*." + s;
		}
		tmp = tmp.substring(1);
		if (descr != null) {
			return descr + " (" + tmp + ")";
		}
		return tmp;
	}

	public boolean accept(File dir, String name) {
		return accept(new File(dir.getAbsolutePath() + File.separator + name));
	}
}
