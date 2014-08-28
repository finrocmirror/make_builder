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

import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;

/**
 * @author Max Reichardt
 *
 * Copies etc directories from libraries (including generated ones in build) to export/.../etc
 * (MCA-specific; only needed for system-installs)
 */
public class EtcDirCopier extends SourceFileHandler.Impl {

    /** Destination path */
    private final String destPath;

    public EtcDirCopier(String destPath) {
        this.destPath = destPath;
    }

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
        if (file.dir.relative.endsWith("/etc") || file.dir.relative.contains("/etc/")) {
            if (file.relative.startsWith("libraries")) {
                String target = destPath + "/" + file.relative.substring(file.relative.indexOf('/') + 1);
                Makefile.Target t = makefile.addTarget(target, false, file.dir);
                t.addDependency(file);
                t.addCommand("cp " + file.relative + " " + target, true);
                t.addToPhony("sysinstall");
            }
        }
    }
}
