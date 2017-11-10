/**
 * You received this file as part of an experimental
 * build tool ('makebuilder') - originally developed for MCA2.
 *
 * Copyright (C) 2008-2010 Max Reichardt,
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
package makebuilder.ext.finroc;

import java.io.File;
import java.util.List;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SrcFile;
import makebuilder.StartScript;
import makebuilder.handler.CppHandler;
import makebuilder.handler.JavaHandler;
import makebuilder.handler.PkgConfigFileHandler;
import makebuilder.util.Files;
import makebuilder.util.Util;

/**
 * @author Max Reichardt
 *
 * Finroc build entity.
 * Decides which target entity belongs to.
 */
public abstract class FinrocBuildEntity extends BuildEntity {

    protected Makefile.Target startScriptTarget;

    /** Final handler for this build entity */
    private Class <? extends SourceFileHandler > finalHandler;

    /** Has the final handler been determined? */
    private boolean finalHandlerDetermined = false;

    public static final String SEARCH_BIN = FinrocBuilder.BUILDING_FINROC ? "finroc_search" : "mca_search";

    /** Has target type already been determined? */
    private boolean typeDetermined = false;

    /** Is this an example target? */
    private boolean example;

    /** Is this a (unit) test target */
    private boolean test;

    /**
     * @return Is this an example target?
     */
    public boolean isExampleTarget() {
        determineType();
        return example;
    }

    /**
     * @return Is this a test target?
     */
    public boolean isTestTarget() {
        determineType();
        return test;
    }

    @Override
    public Class <? extends SourceFileHandler > getFinalHandler() {
        if (!finalHandlerDetermined) {
            if (finalHandler == null) {
                for (SrcFile sf : sources) {
                    if (sf.hasExtension("java")) {
                        finalHandler = JavaHandler.class;
                        break;
                    } else if (sf.hasExtension("c", "cpp", "h", "hpp", "cu")) {
                        finalHandler = CppHandler.class;
                        break;
                    }
                }
            }
            finalHandlerDetermined = true;
            if (finalHandler == null) {
                MakeFileBuilder.getInstance().printCannotBuildError(this, ": Target contains no source files to compile", Util.Color.RED);
            }
        }
        return finalHandler;
    }

    @Override
    public void initTarget(Makefile makefile) {
        super.initTarget(makefile);
        String rootDir2 = getRootDir().relative;
        if (rootDir2.startsWith("sources")) {
            rootDir2 = rootDir2.substring(9);
            rootDir2 = rootDir2.substring(rootDir2.indexOf("/") + 1);
        }
        boolean lib = rootDir2.startsWith("libraries");
        boolean tool = rootDir2.startsWith("tools");
        boolean plugin = rootDir2.startsWith("plugins");
        boolean rrlib = rootDir2.startsWith("rrlib");
        if (lib || rrlib || plugin) {
            addToPhony("libs");
        }
        if (tool) {
            addToPhony("tools");
        }
        if (plugin) {
            addToPhony("plugins");
        }

        String targetFile = getTarget();
        targetFile = targetFile.substring(targetFile.lastIndexOf(File.separator) + 1);
        addToPhony(targetFile + ((isLibrary() || targetFile.endsWith(".jar")) ? "" : "-bin"));

        if (getFinalHandler() == JavaHandler.class) {
            target.addDependency("$(TARGET_DIR)/share/java.created");
        }

        if (isLibrary() && getFinalHandler() == CppHandler.class) {
            setParameter(PkgConfigFileHandler.PARAMETER_KEY_CUSTOM_PKG_CONFIG_FILE_CONTENT, "component=" + createTargetPrefix());
        }
    }

    /**
     * Adds main target to specified phony target
     *
     * (see Makefile.Target.addToPhony for details on parameters)
     * (may be overridden for customization)
     */
    public void addToPhony(String phony, String... phonyDefaultDependencies) {
        if (startScripts.size() == 0) {
            target.addToPhony(phony, phonyDefaultDependencies);
        } else {
            for (StartScript scr : startScripts) {
                scr.getTarget().addToPhony(phony, phonyDefaultDependencies);
            }
        }
    }

    @Override
    protected String getHintForMissingDependency(SrcFile sf) {
        String miss = sf.getMissingDependency();
        if (miss.startsWith("core/") && (!new File("sources/cpp/core").exists())) {
            return "finroc_core repository";
        } else if (miss.startsWith("libraries/") || miss.startsWith("projects/") || miss.startsWith("plugins/") || miss.startsWith("rrlib/")) {
            int cutOffIndex = miss.indexOf('/', miss.indexOf('/') + 1);
            if (cutOffIndex > 0) {
                String relevantDir = miss.substring(0, cutOffIndex);
                if (!new File("sources/cpp/" + relevantDir).exists()) {
                    return (relevantDir.startsWith("rrlib") ? "" : "finroc_") + relevantDir.replace('/', '_') + " repository";
                }
            }
        }
        return "erroneous include";
    }

    @Override
    public void computeOptions() {
        super.computeOptions();
        if (FinrocSystemLibLoader.areSystemLibsLoaded()) {
            FinrocSystemLibLoader.processOptions(this);
        }
    }

    /**
     * @return Prefix for target file (e.g. finroc_libraries_laser_scanner) as used for test programs and .so files
     */
    public String createTargetPrefix() {
        String rootDir2 = this.getRootDir().relative;
        if (FinrocBuilder.BUILDING_FINROC) {
            assert(rootDir2.startsWith("sources"));
            rootDir2 = rootDir2.substring(9);
            rootDir2 = rootDir2.substring(rootDir2.indexOf("/") + 1);
        }
        if (rootDir2.startsWith("libraries")) {
            return "finroc_libraries_" + getSecondDir(rootDir2);
        } else if (rootDir2.startsWith("plugins")) {
            return "finroc_plugins_" + getSecondDir(rootDir2);
        } else if (rootDir2.startsWith("projects")) {
            return "finroc_projects_" + getSecondDir(rootDir2);
        } else if (rootDir2.startsWith("rrlib")) {
            return "rrlib_" + getSecondDir(rootDir2);
        } else if (rootDir2.startsWith("core")) {
            return "finroc_core";
        } else if (rootDir2.startsWith("org")) {
            return rootDir2.substring(4).replace('/', '_');
        }
        return "";
    }
    /**
     * @return Suffix for target file (e.g. -java if it is a Java build entity)
     */
    public String createTargetSuffix() {
        if (this.getFinalHandler() == JavaHandler.class) {
            return "-java";
        } else {
            return "";
        }
    }

    /**
     * Helper function for createTargetPrefix()
     */
    private String getSecondDir(String rootDir2) {
        String[] parts = rootDir2.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "";
    }

    /**
     * @return Returns empty string if no name has been set - otherwise "_name". This function is useful for creating target names
     */
    public String createNameString() {
        return (name == null || name.length() == 0) ? "" : ("_" + name);
    }

    /**
     * Determines whether this target is an example or test target
     */
    private void determineType() {
        if (typeDetermined) {
            return;
        }

        try {
            boolean allTest = true;
            boolean allExample = true;
            boolean oneTest = false;
            boolean oneExample = false;

            for (SrcFile sf : sources) {
                if (!sf.buildProduct) {
                    if (sf.relative.contains("/tests/")) {
                        oneTest = true;
                    } else {
                        allTest = false;
                    }
                    if (sf.relative.contains("/examples/")) {
                        oneExample = true;
                    } else {
                        allExample = false;
                    }
                }
            }

            if (oneExample && allExample) {
                example = true;
            } else if (oneTest && allTest) {
                test = true;
            } else if (oneTest) {
                throw new RuntimeException("Cannot determine whether " + this.toString() + " is a test program as only some source files are from 'tests' directory. Please clean this up.");
            } else if (oneExample) {
                throw new RuntimeException("Cannot determine whether " + this.toString() + " is an example program as only some source files are from 'examples' directory. Please clean this up.");
            }
            typeDetermined = true;
        } catch (RuntimeException e) {
            System.out.println(this.buildFile + ":" + this.lineNumber + ": error: " + e.getMessage());
            System.exit(-1);
        }

    }
}
