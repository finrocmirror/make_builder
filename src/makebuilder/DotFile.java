/**
 * You received this file as part of an experimental
 * build tool ('makebuilder') - originally developed for MCA2.
 *
 * Copyright (C) 2010-2013 Max Reichardt,
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

import makebuilder.util.Files;


/**
 * @author max
 *
 * Dumps dependency graphs to dot file
 */
public class DotFile {

    private static final boolean SLOCCOUNT_AVAILABLE = new File("/usr/bin/sloccount").exists();

    /**
     * Write dot file
     *
     * @param file File to write to
     * @param buildEntities List of build entities
     */
    public static void write(File file, List<BuildEntity> buildEntities, SourceScanner scanner) {
        try {
            for (BuildEntity be : buildEntities) {
                be.params.put("sloc", "" + getSlocCount(be, scanner));
            }

            System.out.println("Writing dot file");
            PrintStream ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)));
            ps.println("digraph makebuilder");
            ps.println("{");
            for (BuildEntity be : buildEntities) {
                if (!be.isLibrary() || be.missingDep) {
                    continue;
                }
                for (BuildEntity dep : be.dependencies) {
                    if (!hasIndirectDependency(be, dep, be)) {
                        ps.println("    \"" + createName(be) + "\" -> \"" + createName(dep) + "\";");
                    }
                }
                for (BuildEntity odep : be.optionalDependencies) {
                    if (!be.dependencies.contains(odep) && (!hasIndirectDependency(be, odep, be))) {
                        ps.println("    \"" + createName(be) + "\" -> \"" + createName(odep) + "\";");
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

    private static String createName(BuildEntity be) {
        if (SLOCCOUNT_AVAILABLE) {
            return be.toString() + "\\n" + be.params.get("sloc") + " SLOC";
        }
        return be.toString();
    }

    private static boolean hasIndirectDependency(BuildEntity be, BuildEntity dep, BuildEntity orgBe) {
        if (be == dep) {
            return true;
        }
        boolean result = false;
        for (BuildEntity d : be.dependencies) {
            if (!(be == orgBe && d == dep)) {
                result |= hasIndirectDependency(d, dep, orgBe);
            }
        }
        for (BuildEntity d : be.optionalDependencies) {
            if (!(be == orgBe && d == dep)) {
                result |= hasIndirectDependency(d, dep, orgBe);
            }
        }
        return result;
    }

    private static String getSlocCount(BuildEntity be, SourceScanner scanner) {
        String fileList = "";
        for (SrcFile sf : scanner.getAllFiles()) {
            if (sf.getOwner() == be) {
                fileList += " " + sf.relative;
            }
        }

        try {
            System.out.println("sloccount " + fileList);
            Process p = Runtime.getRuntime().exec("sloccount " + fileList);
            p.waitFor();
            List<String> lines = Files.readLines(p.getInputStream());
            boolean start = false;
            int count = 0;
            for (String line : lines) {
                start |= line.equals("Totals grouped by language (dominant language first):");
                if (start && (line.startsWith("cpp:") || line.startsWith("ansic:"))) {
                    line = line.substring(line.indexOf(":") + 1);
                    line = line.substring(0, line.indexOf("(") - 1);
                    count += Integer.parseInt(line.trim());
                }
            }
            return "" + count;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "error";
    }

}
