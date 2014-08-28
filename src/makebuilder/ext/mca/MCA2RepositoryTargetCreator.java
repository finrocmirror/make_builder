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
package makebuilder.ext.mca;

import java.util.ArrayList;

import makebuilder.RepositoryTargetCreator;

/**
 * @author Max Reichardt
 *
 */
public class MCA2RepositoryTargetCreator extends RepositoryTargetCreator {

    /**
     * Type of target
     */
    protected class ClassEntry {

        /** Directory such target are in */
        public String directory;

        /** Prefix of svn repository */
        public String repoPrefix;

        /** Is next directory relevant? */
        public boolean nextDir;

        public ClassEntry(String directory, String repoPrefix, boolean nextDir) {
            this.directory = directory;
            this.repoPrefix = repoPrefix;
            this.nextDir = nextDir;
        }

        /**
         * @param srcDir Src directory
         * @return Repository name (or null if not this kind of target)
         */
        public String getRepositoryName(String srcDir, boolean shortName) {
            String prefix = shortName ? "" : (repoPrefix + "_");
            if (((!nextDir) && srcDir.equals(directory)) || srcDir.startsWith(directory + "/")) {
                if (!nextDir) {
                    if (srcDir.contains("/")) {
                        srcDir = srcDir.substring(0, srcDir.indexOf("/"));
                    }
                    return shortName ? srcDir : repoPrefix;
                } else {
                    srcDir = srcDir.substring(directory.length() + 1);
                    if (srcDir.contains("/")) {
                        srcDir = srcDir.substring(0, srcDir.indexOf("/"));
                    }
                    return prefix + srcDir;
                }
            }
            return null;
        }
    }

    /**
     * All types of targets that can be associated with repository
     */
    protected ArrayList<ClassEntry> classEntries = new ArrayList<ClassEntry>();

    public MCA2RepositoryTargetCreator() {
        classEntries.add(new ClassEntry("libraries", "mcal", true));
        classEntries.add(new ClassEntry("projects", "mcap", true));
        classEntries.add(new ClassEntry("tools", "mcat", true));
    }

    @Override
    public String getRepositoryName(String srcDir) {
        return getRepositoryName(srcDir, false);
    }

    public String getRepositoryName(String srcDir, boolean shortName) {
        if (srcDir.startsWith("sources/cpp/")) {
            srcDir = srcDir.substring("sources/cpp/".length());
        } else if (srcDir.startsWith("sources/java/")) {
            srcDir = srcDir.substring("sources/java/".length());
        }
        for (ClassEntry ce : classEntries) {
            String name = ce.getRepositoryName(srcDir, shortName);
            if (name != null) {
                return name;
            }
        }
        return "unknown-repository";
    }

    @Override
    public String getShortRepositoryName(String srcDir) {
        return getRepositoryName(srcDir, true);
    }
}
