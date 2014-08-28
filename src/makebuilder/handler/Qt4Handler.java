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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import makebuilder.BuildEntity;
import makebuilder.SourceFileHandler;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcDir;
import makebuilder.SrcFile;
import makebuilder.libdb.LibDB;
import makebuilder.util.ToStringComparator;

/**
 * @author Max Reichardt
 *
 * Handler for qt4 files.
 *
 * Handles .ui files as well as headers requiring call to moc
 */
public class Qt4Handler extends SourceFileHandler.Impl {

    /** Single target for .cpp descr files */
    class CppQtTarget {

        /** Makefile target for descr file */
        final Makefile.Target target;

        /** List of .cpp files that descr file is generated from */
        final ArrayList<SrcFile> originalSourceFiles = new ArrayList<SrcFile>();

        /** Descr file */
        final SrcFile descrFile;

        public CppQtTarget(Makefile.Target target, SrcFile descrFile) {
            this.target = target;
            this.descrFile = descrFile;
        }
    }

    /** moc executable */
    private final String MOC_CALL;

    /** uic executable */
    private final String UIC_CALL;

    /** Contains a makefile target for each build entity with files to moc */
    private Map<BuildEntity, CppQtTarget> mocTargets = new HashMap<BuildEntity, CppQtTarget>();

    /** Dependency buffer */
    private final TreeSet<SrcFile> dependencyBuffer = new TreeSet<SrcFile>(ToStringComparator.instance);

    public Qt4Handler() {
        try {
            MOC_CALL = LibDB.getInstance("native").getLib("moc-qt4").options.trim();
            UIC_CALL = LibDB.getInstance("native").getLib("uic-qt4").options.trim();
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
                CppQtTarget target = mocTargets.get(be);
                if (target == null) {
                    SrcFile sft = builder.getTempBuildArtifact(be, "cpp", "qt_generated"); // sft = "source file target"
                    target = new CppQtTarget(makefile.addTarget(sft.relative, true, file.dir), sft);
                    target.target.addDependency(be.buildFile);
                    target.target.addMessage("Creating " + sft.relative);
                    target.target.addCommand("echo \\/\\/ generated > " + target.target.getName(), false);
                    be.sources.add(sft);
                    mocTargets.put(be, target);
                }
                target.target.addDependency(file);
                target.originalSourceFiles.add(file);
                target.target.addCommand(MOC_CALL + " -p " + getIncludePath(file) + " " + file.relative + " >> " + target.target.getName(), false);
            }

        } else if (file.hasExtension("ui")) { // run uic?
            SrcFile hdr = builder.getTempBuildArtifact(file, "h");
            Makefile.Target t = makefile.addTarget(hdr.relative, false, file.dir);
            t.addDependency(file);
            t.addCommand(UIC_CALL + " " + file.relative + " -o " + hdr.relative, true);
        }
    }

    /**
     * @param file Source file
     * @return Include path to best include this file from
     */
    private String getIncludePath(SrcFile file) {
        String best = "";
        for (SrcDir path : file.dir.defaultIncludePaths) {
            if (path.isParentOf(file.dir) && (best.length() < path.relative.length())) {
                best = path.relative;
            }
        }
        if (best.equals(".")) {
            return file.dir.relative;
        } else {
            return file.dir.relative.substring(best.length() + 1);
        }
    }

    @Override
    public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {
        CppQtTarget target = mocTargets.get(be);
        if (target != null) {

            // Add all dependencies of original files to generated .descr cpp file
            dependencyBuffer.clear();
            for (SrcFile sf : target.originalSourceFiles) {
                sf.getAllDependencies(dependencyBuffer);
            }
            target.descrFile.dependencies.addAll(dependencyBuffer);
        }
    }
}
