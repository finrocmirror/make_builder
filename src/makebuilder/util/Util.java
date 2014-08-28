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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import makebuilder.MakeFileBuilder;
import makebuilder.libdb.LibDB;

/**
 * @author Max Reichardt
 *
 * Various utility functions
 */
public class Util {

    /**
     * Read all lines from a C++/Java file and remove all comments and strings
     *
     * @param f File
     * @param setLineMacro set line macros (currently only false)
     * @return List with lines that were read
     */
    public static List<String> readLinesWithoutComments(File f, boolean setLineMacro) throws Exception {
        char[] data = Files.readStreamFully(new InputStreamReader(new FileInputStream(f), MakeFileBuilder.INPUT_CHARSET));

        List<String> lines = new ArrayList<String>();
        String curFile = f.getAbsolutePath();
        StringBuilder sb = new StringBuilder();

        boolean comment1 = false;
        boolean comment2 = false;
        final int NO = 0, YES = 1, ELSE = 2;
        int preProcessorComment = NO;
        boolean string1 = false;
        boolean string2 = false;
        boolean notEmpty = false;
        boolean skipLineMacro = false;
        int curLine = 1;
        for (int i = 0; i < data.length; i++) {
            char c = data[i];
            char c1 = i < data.length - 1 ? data[i + 1] : ' ';
            if (c == '\n') {
                comment1 = false;
                if (notEmpty) {
                    String s = sb.toString();
                    if (s.trim().equals("#if 0")) {
                        preProcessorComment = YES;
                    }
                    if (preProcessorComment != YES) {
                        if (setLineMacro && (!skipLineMacro)) {
                            lines.add("#line " + curLine + " \"" + curFile + "\"");
                        }
                        lines.add(s);
                        skipLineMacro = s.endsWith("\\");
                    }
                    if (preProcessorComment != NO) {
                        if (s.trim().equals("#endif")) {
                            if (preProcessorComment == ELSE) {
                                lines.remove(lines.size() - 1);
                            }
                            preProcessorComment = NO;
                        }
                        if (s.trim().equals("#else")) {
                            preProcessorComment = ELSE;
                        }
                    }
                }
                sb.setLength(0);
                notEmpty = false;
                curLine++;
                continue;
            }

            if (comment1) {
                continue;
            }
            if (comment2) {
                if (c == '*' && c1 == '/') {
                    comment2 = false;
                    i++;
                }
                continue;
            }
            if (!string1 && c == '/' && c1 == '/') {
                comment1 = true;
                i++;
                continue;
            }
            if (!string1 && c == '/' && c1 == '*') {
                comment2 = true;
                i++;
                continue;
            }
            if ((string1 || string2) && c == '\\') {
                sb.append(c);
                sb.append(c1);
                i++;
                continue;
            }
            if (!string2 && c == '"') {
                string1 = !string1;
            }
            if (!string1 && c == '\'') {
                string2 = !string2;
            }

            if (c == '\t') {
                c = ' ';
            }
            if (c != ' ') {
                notEmpty = true;
            }
            sb.append(c);
        }

        // add last line
        if (notEmpty) {
            lines.add(sb.toString());
            sb.setLength(0);
        }

        return lines;
    }

    /**
     * Return File in make_builder etc directory
     *
     * @param filename file name without path
     * @return file with path
     */
    public static File getFileInEtcDir(String filename) {
        return new File(new File(Files.getRootDir(LibDB.class)).getParent() + File.separator + "etc" + File.separator + filename);
    }

    /**
     * @return Name of user currently logged in (only works on Unix/Linux)
     */
    public static String whoami() {
        try {
            Process p = Runtime.getRuntime().exec("whoami");
            return Files.readLines(p.getInputStream()).get(0);
        } catch (Exception e) {
            e.printStackTrace();
            return "" + System.currentTimeMillis();
        }
    }

    /** Color for output */
    public enum Color { NONE, RED, GREEN, X, Y }

    /** Control Strings for colors */
    private static String[] COLOR_STRING = {"", "\033[;2;31m", "\033[;2;32m", "\033[;2;34m", "\033[;2;33m"};

    /**
     * Create colored string for console output
     *
     * @param s String to color
     * @param color Color to use
     * @param fat Use fat font
     * @return The string containing the color control sequences
     */
    public static String color(String s, Color color, boolean fat) {
        if (color == null || color == Color.NONE) {
            return s;
        }
        String c = COLOR_STRING[color.ordinal()];
        if (fat || color == Color.X) {
            c = c.replace("[;2;", "[;1;");
        }
        return c + s + "\033[;0m";
    }
}
