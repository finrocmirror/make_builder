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

/**
 * @author Max Reichardt
 *
 * MCA Plugin build entity
 */
public class MCAPlugin extends MCABuildEntity {

    public MCAPlugin() {
        try {
            //this.extlibs.add(LibDB.getLib("ltdl"));
            libs.add("ltdl");
        } catch (Exception e) {
            e.printStackTrace();
        }
        opts.addOptions("-shared -fPIC");
    }

    @Override
    public String getTarget() {
        return "$(TARGET_LIB)/pluginmca2_" + name + ".so";
    }
}
