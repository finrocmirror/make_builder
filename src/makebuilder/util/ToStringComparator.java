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

import java.io.Serializable;
import java.util.Comparator;

/**
 * @author max
 *
 */
public class ToStringComparator implements Comparator<Object>, Serializable {

	/** UID */
	private static final long serialVersionUID = 4304587237585840299L;
	
	/** instance of comparator - if you don't want to instantiate it */
	public static final ToStringComparator instance = new ToStringComparator();
	
	@Override
	public int compare(Object o1, Object o2) {
		return o1.toString().compareTo(o2.toString());
	}
}
