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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import makebuilder.BuildEntity;
import makebuilder.SourceFileHandler;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;
import makebuilder.util.ToStringComparator;

/**
 * @author max
 *
 * Responsible for calling MCA descriptionbuilder on relevant files
 */
public class DescriptionBuilderHandler extends SourceFileHandler.Impl {

    /** Single target for .cpp descr files */
    class CppDescrTarget {
        
        /** Makefile target for descr file */
        final Makefile.Target target;
        
        /** List of .cpp files that descr file is generated from */
        final ArrayList<SrcFile> originalSourceFiles = new ArrayList<SrcFile>(); 
        
        /** Descr file */
        final SrcFile descrFile;
        
        public CppDescrTarget(Makefile.Target target, SrcFile descrFile) {
            this.target = target;
            this.descrFile = descrFile;
        }
    }
    
    /** Description builder script */
    public static String DESCRIPTION_BUILDER_BIN = "scripts/description_builder.pl ";

    /** Contains a makefile target for each build entity with files to call description build upon */
    private Map<BuildEntity, CppDescrTarget> descrTargets = new HashMap<BuildEntity, CppDescrTarget>();

    /** Dependency buffer */
    private final TreeSet<SrcFile> dependencyBuffer = new TreeSet<SrcFile>(ToStringComparator.instance);

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
        if (file.hasExtension("h")) {

            // find _DESCR_ macro
            if (!file.isInfoUpToDate()) {
                for (String s : file.getCppLines()) {
                    s = s.trim();
                    if (s.startsWith("_DESCR_")) {
                        // template headers with _DESCR_ need to be handled differently
                        file.mark(s.contains("<") ? "DESCR_TEMPLATE" : "DESCR");
                        break;
                    }
                }
            }

            // template description?
            if (file.hasMark("DESCR_TEMPLATE")) {
                SrcFile target = builder.getTempBuildArtifact(file, "hpp");
                Makefile.Target t = makefile.addTarget(target.relative, false, file.dir);
                t.addDependency(file.relative);
                t.addDependency(DESCRIPTION_BUILDER_BIN);
                t.addCommand(DESCRIPTION_BUILDER_BIN + file.relative + " > " + target.relative, true);

                // normal description?
            } else if (file.hasMark("DESCR")) {
                BuildEntity be = file.getOwner();
                if (be == null) { // we don't know where generated code belongs
                    //System.out.println("warning: found DESCR macros in " + file.relative + " but don't know which build entity it belongs to => won't process it");
                    return;
                }

                // get or create target
                CppDescrTarget target = descrTargets.get(be);
                if (target == null) {
                    SrcFile sft = builder.getTempBuildArtifact(be, "cpp", "descriptions"); // sft = "source file target"
                    target = new CppDescrTarget(makefile.addTarget(sft.relative, true, file.dir), sft);
                    target.target.addDependency(be.buildFile);
                    target.target.addMessage("Creating " + sft.relative);
                    target.target.addCommand("echo \\/\\/ generated > " + target.target.getName(), false);
                    be.sources.add(sft);
                    descrTargets.put(be, target);
                }
                target.target.addDependency(file);
                target.originalSourceFiles.add(file);
                target.target.addCommand(DESCRIPTION_BUILDER_BIN + file.relative + " >> " + target.target.getName(), false);
            }
        }
    }

    @Override
    public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {
        CppDescrTarget target = descrTargets.get(be);
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
