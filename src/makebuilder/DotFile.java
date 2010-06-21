/**
 * You received this file as part of an experimental
 * build tool ('makebuilder') - originally developed for MCA2.
 *
 * Copyright (C) 2010 Max Reichardt,
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.List;

/**
 * @author max
 *
 * Dumps dependency graphs to dot file
 */
public class DotFile {

    /**
     * Write dot file
     *
     * @param file File to write to
     * @param buildEntities List of build entities
     */
    public static void write(File file, List<BuildEntity> buildEntities) {
        try {
            System.out.println("Writing dot file");
            PrintStream ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)));
            ps.println("digraph makebuilder");
            ps.println("{");
            for (BuildEntity be : buildEntities) {
                if (!be.isLibrary()) {
                    continue;
                }
                for (BuildEntity dep : be.dependencies) {
                    ps.println("    \"" + be.name + "\" -> \"" + dep.name + "\";");
                }
                for (BuildEntity odep : be.optionalDependencies) {
                    if (!be.dependencies.contains(odep)) {
                        ps.println("    \"" + be.name + "\" -> \"" + odep.name + "\";");
                    }
                }
            }
            ps.println("}");
            ps.close();
            System.out.println("done");
        } catch (Exception e) {
            System.err.println("Error writing .dot file");
            e.printStackTrace();
        }
    }

}
