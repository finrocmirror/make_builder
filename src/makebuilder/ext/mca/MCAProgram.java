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
 * MCA executable build entity
 */
public class MCAProgram extends MCABuildEntity {

    public MCAProgram() {
        opts.addOptions("-Wl,--no-as-needed");
    }

    @Override
    public String getTarget() {
        String rootDir2 = this.getRootDir().relative;
        if (rootDir2.startsWith("sources/cpp/mca2-legacy/")) {
            rootDir2 = rootDir2.substring("sources/cpp/mca2-legacy/".length());
        }
        if (rootDir2.startsWith("libraries")) {
            String[] parts = rootDir2.split("/");
            if (parts.length >= 2) {
                String prefix = "mcal_" + parts[1];
                if (!name.startsWith(prefix)) {
                    String n = name;
                    if (n.startsWith("mcal_")) {
                        n = n.substring(5);
                    } else if (n.startsWith(parts[1])) {
                        n = n.substring(parts[1].length() + 1);
                    }
                    return "$(TARGET_BIN)/" + prefix + (n.length() > 0 ? ("_" + n) : "");
                }
            }
        }
        return "$(TARGET_BIN)/" + name;
    }
}
