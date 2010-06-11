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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 *
 * Source directory
 */
public class SrcDir {

    /** Source Scanner instance that this directory belongs to and was created from */
    public final SourceScanner sources;

    /** Absolute directory */
    public final File absolute;

    /** Directory relative to Makefile-Directory - possibly absolute if not a subdirectory */
    public final String relative;

    /** Parent directory - may be lazily initialized */
    private SrcDir parent;

    /** Default include paths for C/C++ files in this directory */
    public final List<SrcDir> defaultIncludePaths = new ArrayList<SrcDir>();

    /** Root directory for source files? */
    public boolean srcRoot = false;

    /**
     * @param sourceScanner Source Scanner instance that this directory belongs to and was created from
     * @param dir Absolute directory
     * @param homeDir Home/Makefile-Directory (e.g. $MCAHOME)
     */
    public SrcDir(SourceScanner sourceScanner, File dir) {
        sources = sourceScanner;
        absolute = dir.getAbsoluteFile();
        relative = relativeDirName(absolute, sourceScanner);
    }

    /**
     * Returns relative directory name if directory is subdirectory of home directory
     *
     * @param dir (absolute) Directory
     * @param sourceScanner Source Scanner instance that directory belongs to
     * @return Relative Directory name
     */
    public static String relativeDirName(File dir, SourceScanner sourceScanner) {
        if (dir.getAbsolutePath().startsWith(sourceScanner.homeDirExt)) {
            return dir.getAbsolutePath().substring(sourceScanner.homeDirExt.length());
        } else if (sourceScanner.homeDirExt.equals(dir.getAbsolutePath() + File.separator)) { // home directory
            return ".";
        }
        return dir.getAbsolutePath();
    }

    /**
     * @return Returns parent directory
     */
    public SrcDir getParent() {
        if (parent == null) {
            parent = sources.findDir(relativeDirName(absolute.getParentFile(), sources), true);
        }
        return parent;
    }

    public String toString() {
        return relative;
    }

    /**
     * @param name Subdirectory name
     * @return Subdirectory with specified name
     */
    public SrcDir getSubDir(String name) {
        return sources.findDir((relative.equals(".") ? "" : relative + File.separator) + name, true);
    }

    /**
     * @return Get source root directory of this directory (e.g. ./src/) - null if it has no parent which is source directory
     */
    public SrcDir getSrcRoot() {
        SrcDir current = this;
        while (!current.srcRoot) {
            if (current.relative.equals(".") || current.relative.startsWith("/")) {
                return null;
            }
        }
        return current;
    }

    /**
     * Get relative path to specified parent directory
     *
     * @param parent Parent directory
     * @return path (without "/" at beginning)
     */
    public String relativeTo(SrcDir parent) {
        if (!relative.startsWith(parent.relative)) {
            throw new RuntimeException("Not a parent directory");
        }
        return relative.substring(parent.relative.length() + 1);
    }

    /**
     * @return Is this a temp directory (usually located in /tmp) ?
     */
    public boolean isTempDir() {
        return relative.startsWith(sources.builder.tempPath.relative);
    }
}
