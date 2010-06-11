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

import java.util.Properties;

/**
 * @author max
 *
 * Builder (command line) options
 * Class is used for parsing and managing them.
 */
public class Options extends Properties {

    /** UID */
    private static final long serialVersionUID = 1234625724856L;

    /** combine cpp files in one large file? */
    public boolean combineCppFiles;

    /** start build entity calculations? */
    public boolean calculateDependencies;

    /** Main class to instantiate (optional) */
    Class <? extends Runnable > mainClass = MakeFileBuilder.class;

    /** String with concatenated command line arguments */
    public final String args;

//  /** compile binaries to shared libraries? */
//  boolean compileBinsToSO = false;

    /**
     * @param args Command line arguments
     */
    @SuppressWarnings("unchecked")
    public Options(String[] args) {
        String tmp = "";

        // parse command line parameters
        for (String s : args) {
            tmp += " " + s;
            if (s.startsWith("--combine") || s.startsWith("--hugecpps")) {
                combineCppFiles = true;
            } else if (s.startsWith("--dependency")) {
                calculateDependencies = true;
            } else if (s.startsWith("--")) {
                String key = s.contains("=") ? s.substring(2, s.indexOf("=")) : s.substring(2);
                String value = s.contains("=") ? s.substring(s.indexOf("=") + 1) : "N/A";
                this.put(key, value);
            } else {
                try {
                    mainClass = (Class <? extends Runnable >)Class.forName(s);
                } catch (Exception e) {
                    System.out.println("Cannot find specified main class: " + s);
                }
            }
//          if (s.startsWith("--so")) {
//              compileBinsToSO = true;
//          }
        }

        this.args = tmp.trim();
    }
}
