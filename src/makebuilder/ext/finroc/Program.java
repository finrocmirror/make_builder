/**
 * You received this file as part of an experimental
 * build tool ('makebuilder') - originally developed for MCA2.
 *
 * Copyright (C) 2008-2014 Max Reichardt,
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

import makebuilder.Makefile;
import makebuilder.SrcFile;
import makebuilder.StartScript;
import makebuilder.handler.JavaHandler;

/**
 * @author Max Reichardt
 *
 * New target for all kinds of executables used in the context of Finroc
 * (programs, test programs, examples)
 */
public class Program extends FinrocBuildEntity {

    /** Has program type already been determined? */
    private boolean typeDetermined = false;

    /** Is this an example program? */
    private boolean example;

    /** Is this a (unit) test */
    private boolean test;

    public Program() {
        //opts.addOptions("-Wl,--no-as-needed");
    }

    @Override
    public String getTargetPrefix() {
        determineType();
        if (example) {
            return createTargetPrefix() + "_example";
        } else if (test) {
            return createTargetPrefix() + "_unit_test";
        }
        return "";
    }

    @Override
    public boolean isLibrary() {
        return false;
    }

    @Override
    public boolean isOptional() {
        determineType();
        if (example) {
            return true;
        }
        return super.isOptional();
    }

    @Override
    public boolean isUnitTest() {
        determineType();
        if (test) {
            return true;
        }
        return super.isUnitTest();
    }

    @Override
    public String getTarget() {
        determineType();
        String result = "$(TARGET_BIN)/" + getTargetPrefix() + (example || test ? createNameString() : name);
        if (getFinalHandler() == JavaHandler.class) {
            return result.replace("$(TARGET_BIN)", "$(TARGET_JAVA)") + ".jar";
        }
        return result;
    }

    @Override
    public void initTarget(Makefile makefile) {
        if (getFinalHandler() == JavaHandler.class) {
            if (startScripts.size() == 0 && params.containsKey("main-class")) {
                startScripts.add(new StartScript(getTargetFilename().replaceAll("[.]jar$", ""), null));
            }
        }
        super.initTarget(makefile);
    }

    private void determineType() {
        if (!typeDetermined) {
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
        }
    }
}
