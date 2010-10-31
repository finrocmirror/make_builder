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

/**
 * @author max
 *
 * Post-processor for makefiles.
 *
 * Creates phony targets for each repository.
 *
 * Needs to be subclassed to fit to project.
 */
public abstract class RepositoryTargetCreator {

    /**
     * Postprocess Makefile
     *
     * @param mf Makefile
     * @param alwaysDependOn Targets we always depend on
     */
    public void postprocess(Makefile mf, String... alwaysDependOn) {
        Makefile.Target clean = mf.addPhonyTarget("always-clean");
        clean.addCommand("rm -rf $(TEMP_DIR)", true);

        for (Makefile.Target target : mf.getTargets()) {
            if (target.getSrcDir() == null) {
                continue;
            }

            // names
            String longRep = getRepositoryName(target.getSrcDir());
            String shortRep = getShortRepositoryName(target.getSrcDir());

            // create target dependencies
            target.addToPhony(longRep, alwaysDependOn);
            if (shortRep != null) {
                Makefile.Target shortTarget = mf.getPhonyTarget(shortRep);
                if (shortTarget == null) {
                    mf.addPhonyTarget(shortRep, longRep);
                } else {
                    shortTarget.addDependency(longRep);
                }
            }

            // create clean targets
            String longRepClean = "clean-" + longRep;
            Makefile.Target longClean = mf.getPhonyTarget(longRepClean);
            if (longClean == null) {
                longClean = mf.addPhonyTarget(longRepClean, "always-clean");
                if (shortRep != null) {
                    mf.addPhonyTarget("clean-" + shortRep, longRepClean);
                }
            }
            longClean.addCommand("rm -f " + target.getName(), true);
        }
    }

    /**
     * @param srcDir Source directory
     * @return Short Repository name of that source (or null)
     */
    public abstract String getShortRepositoryName(String srcDir);

    /**
     * @param srcDir Source directory
     * @return Normal Repository name of that source
     */
    public abstract String getRepositoryName(String srcDir);
}
