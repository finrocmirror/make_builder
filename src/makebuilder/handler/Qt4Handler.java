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
package makebuilder.handler;

import java.util.HashMap;
import java.util.Map;

import makebuilder.BuildEntity;
import makebuilder.SourceFileHandler;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;
import makebuilder.libdb.LibDB;

/**
 * @author max
 *
 * Handler for qt4 files.
 *
 * Handles .ui files as well as headers requiring call to moc
 */
public class Qt4Handler extends SourceFileHandler.Impl {

    /** moc executable */
    private final String MOC_CALL;

    /** uic executable */
    private final String UIC_CALL;

    /** Contains a makefile target for each build entity with files to moc */
    private Map<BuildEntity, Makefile.Target> mocTargets = new HashMap<BuildEntity, Makefile.Target>();

    public Qt4Handler() {
        try {
            MOC_CALL = LibDB.getLib("moc-qt4").options.trim();
            UIC_CALL = LibDB.getLib("uic-qt4").options.trim();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
        if (file.hasExtension("h", "hpp")) {

            // find qt macros
            if (!file.isInfoUpToDate()) {
                for (String s : file.getCppLines()) {
                    if (s.contains("Q_OBJECT") || s.contains("Q_PROPERTY") || s.contains("Q_CLASSINFO")) {
                        file.mark("moc");
                        break;
                    }
                }
            }

            // moc file?!
            if (file.hasMark("moc")) {
                BuildEntity be = file.getOwner();
                if (be == null) { // we don't know where generated code belongs
                    //System.out.println("warning: found qt macros in " + file.relative + " but don't know which build entity it belongs to => won't process it");
                    return;
                }

                // get or create target
                Makefile.Target target = mocTargets.get(be);
                if (target == null) {
                    SrcFile sft = builder.getTempBuildArtifact(be, "cpp", "qt_generated"); // sft = "source file target"
                    target = makefile.addTarget(sft.relative, true, file.dir);
                    target.addDependency(be.buildFile);
                    target.addMessage("Creating " + sft.relative);
                    target.addCommand("echo \\/\\/ generated > " + target.getName(), false);
                    be.sources.add(sft);
                    mocTargets.put(be, target);
                }
                target.addDependency(file);
                target.addCommand(MOC_CALL + " " + file.relative + " >> " + target.getName(), false);
            }

        } else if (file.hasExtension("ui")) { // run uic?
            SrcFile hdr = builder.getTempBuildArtifact(file, "h");
            Makefile.Target t = makefile.addTarget(hdr.relative, false, file.dir);
            t.addDependency(file);
            t.addCommand(UIC_CALL + " " + file.relative + " -o " + hdr.relative, true);
        }
    }
}
