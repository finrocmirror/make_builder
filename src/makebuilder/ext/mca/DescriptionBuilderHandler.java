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

import java.util.HashMap;
import java.util.Map;

import makebuilder.BuildEntity;
import makebuilder.SourceFileHandler;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;

/**
 * @author max
 *
 * Responsible for calling MCA descriptionbuilder on relevant files
 */
public class DescriptionBuilderHandler extends SourceFileHandler.Impl {

    /** Description builder script */
    public static String DESCRIPTION_BUILDER_BIN = "script/description_builder.pl ";

    /** Contains a makefile target for each build entity with files to call description build upon */
    private Map<BuildEntity, Makefile.Target> descrTargets = new HashMap<BuildEntity, Makefile.Target>();

    /** Target for all description from template classes */
    public static final String TEMPLATE_DESCRIPTION_TARGET = "template_descriptions";
    
    /** Has template description target been created? */
    public boolean templateDescriptionTargetCreated = false;
    
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
                t.addToPhony(TEMPLATE_DESCRIPTION_TARGET);
                templateDescriptionTargetCreated = true;

                // normal description?
            } else if (file.hasMark("DESCR")) {
                BuildEntity be = file.getOwner();
                if (be == null) { // we don't know where generated code belongs
                    //System.out.println("warning: found DESCR macros in " + file.relative + " but don't know which build entity it belongs to => won't process it");
                    return;
                }

                // get or create target
                Makefile.Target target = descrTargets.get(be);
                if (target == null) {
                    SrcFile sft = builder.getTempBuildArtifact(be, "cpp", "descriptions"); // sft = "source file target"
                    target = makefile.addTarget(sft.relative, true, file.dir);
                    target.addDependency(be.buildFile);
                    target.addDependency(TEMPLATE_DESCRIPTION_TARGET);
                    target.addMessage("Creating " + sft.relative);
                    target.addCommand("echo \\/\\/ generated > " + target.getName(), false);
                    be.sources.add(sft);
                    descrTargets.put(be, target);
                    
                    if (!templateDescriptionTargetCreated) {
                        makefile.addPhonyTarget(TEMPLATE_DESCRIPTION_TARGET);
                        templateDescriptionTargetCreated = true;
                    }
                }
                target.addDependency(file);
                target.addCommand(DESCRIPTION_BUILDER_BIN + file.relative + " >> " + target.getName(), false);
            }
        }
    }
}
