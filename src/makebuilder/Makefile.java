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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import makebuilder.util.Files;

/**
 * @author max
 *
 * This class represents a makefile.
 * It encapsulates the Makefile syntax
 */
public class Makefile {

    /** List with variables that are located at the beginning of the makefile */
    private final List<String> variables = new ArrayList<String>();

    /** List with variables that are located at the beginning of the makefile */
    //private final List<String> initCommands = new ArrayList<String>();

    /** List of PHONY targets (virtual targets/categories) */
    private final SortedMap<String, Target> phonyTargets = new TreeMap<String, Target>();

    /** List of targets */
    private final List<Target> targets = new ArrayList<Target>();

    /** Variable name of message that is displayed, when build process finishes successfully */
    public static final String DONE_MSG_VAR = "DONE_MSG";

    /** Directories that targets are built to. Will be removed with make clean command */
    private final ArrayList<String> buildDirs;

    /** PHONY target for building everything */
    private final Target all = new Target("all", null);

    /** Dummy target - can be useful for certain non-standard stuff - will not be added to any makefile */
    public final Target DUMMY_TARGET = new Target("dummy target", null);

    /** Prefix for lines in Target.commands that should not be indented */
    private final String NO_INDENT_PREFIX = "###";

    /**
     * @param buildDirs Directories that targets are built to. Will be removed with make clean command
     */
    public Makefile(String... buildDirs) {
        this.buildDirs =  new ArrayList<String>(Arrays.asList(buildDirs));
        addVariable(DONE_MSG_VAR + "=done");
    }

    /**
     * @param other Makefile to copy initial values from
     */
    public Makefile(Makefile other) {
        buildDirs = new ArrayList<String>(other.buildDirs);
        variables.addAll(other.variables);
        phonyTargets.putAll(other.phonyTargets);
        targets.addAll(other.targets);
    }

    /**
     * Write makefile to File
     *
     * @param target File to write makefile to
     */
    public void writeTo(File target) throws Exception {
        PrintStream ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(target)));

        // write default target (must be first target in the file)
        Target default_target = new Target("default", null);
        default_target.addDependency("all");
        default_target.writeTo(ps);

        // write double-colon rules that allow to hook into the build process
        ps.println("pre-build-hook::");
        ps.println("\t@echo -n");
        ps.println("");
        ps.println("post-build-hook::");
        ps.println("\t@echo -n");
        ps.println("");

        // write variable declarations
        for (String s : variables) {
            ps.println(s + "\n");
        }

        // write PHONY target list
        ps.print(".PHONY: default pre-build-hook post-build-hook clean all");
        for (Target t : phonyTargets.values()) {
            if (!t.name.startsWith(".")) {
                ps.print("\t");
                ps.print(t.name);
                ps.print(" ");
                ps.print("clean-" + t.name);
                ps.print(" \\\n");
            }
        }
        ps.println("\n");

        // write/create 'all' target (which is default)
        all.addCommand("echo $(" + DONE_MSG_VAR + ")", false);
        all.writeTo(ps);

        // write 'clean' target
        Target clean = new Target("clean", null);
        for (String s : buildDirs) {
            clean.addCommand("rm -R -f " + s, true);
        }
        clean.addCommand("rm -f " + SourceScanner.CACHE_FILE, true);
        clean.writeTo(ps);

        //      // write 'init' target
        //      Target init = new Target("init");
        //      for (String s : initCommands) {
        //          init.addCommand(s);
        //      }
        //      init.writeTo(ps);

        // write other PHONY targets
        for (Target t : phonyTargets.values()) {
            t.writeTo(ps);

            // write clean-targets
            if (t.name.startsWith(".") || t.name.startsWith("clean") || getPhonyTarget("clean-" + t.name) != null) {
                continue;
            }
            ps.println("clean-" + t.name + ":");
            for (String s : t.dependencies) {
                if (!phonyTargets.containsKey(s)) {
                    ps.println("\trm -f " + s);
                }
            }
            ps.println();
        }

        // write ordinary targets
        for (Target t : targets) {
            t.writeTo(ps);
        }

        ps.close();
    }

    /**
     * Add variable to beginning of Makefile
     *
     * @param variable Variable to add
     */
    public void addVariable(String variable) {
        variables.add(variable);
    }

    /**
     * Change variable at beginning of makefile
     *
     * @param variable Variable to change
     */
    public void changeVariable(String newVariable) {
        String varname = extractVarName(newVariable);
        for (int i = 0; i < variables.size(); i++) {
            String varname2 = extractVarName(variables.get(i));
            if (varname.equals(varname2)) {
                variables.set(i, newVariable);
                return;
            }
        }
        //System.out.println("attempt to change non-existing variable " + newVariable + "; adding instead");
        addVariable(newVariable);
    }

    /**
     * Extract variable name from variable string
     *
     * @param s Variable string
     * @return Variable name
     */
    private static String extractVarName(String s) {
        if (!s.contains("=") || s.startsWith("=")) {
            return null; // actually no variable
        }
        s = s.substring(0, s.indexOf('=') + 1);
        while (s.length() != 0 && (!Character.isJavaIdentifierPart(s.charAt(s.length() - 1)))) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * Add Phony target to makefile
     *
     * @param name Target Name
     * @param dependencies Dependencies
     */
    public Target addPhonyTarget(String name, String... dependencies) {
        Target t = new Target(name, null);
        for (String s : dependencies) {
            t.dependencies.add(s);
        }
        phonyTargets.put(name, t);
        return t;
    }

    /**
     * @param name Target name
     * @return Phony target with specified name - or null if none exists
     */
    public Target getPhonyTarget(String name) {
        return phonyTargets.get(name);
    }

    /**
     * Add target with specified name
     *
     * @param target Target name
     * @param secondary Secondary Target (intermediate file - doesn't matter if it is deleted; see GNU documentation for details ".SECONDARY")
     * @param srcRootDir directory containing relevant source files (used for .phony target; must be in the same repository!)
     * @return Created Target object
     */
    public Target addTarget(String name, boolean secondary, Object srcRootDir) {
        return addTarget(name, secondary, srcRootDir, true);
    }

    /**
     * Add target with specified name
     *
     * @param target Target name
     * @param secondary Secondary Target (intermediate file - doesn't matter if it is deleted; see GNU documentation for details ".SECONDARY")
     * @param srcRootDir directory containing relevant source files (used for .phony target; must be in the same repository!)
     * @param includeInAllTarget Include this target in 'all' phony target (usually true)
     * @return Created Target object
     */
    public Target addTarget(String name, boolean secondary, Object srcRootDir, boolean includeInAllTarget) {
        Target t = new Target(name, srcRootDir == null ? null : srcRootDir.toString());
        targets.add(t);
        if (secondary) {
            t.addToPhony(".SECONDARY");
        } else if (includeInAllTarget) {
            all.addDependency(name);
        }
        return t;
    }

    /**
     * Add initialization command
     *
     * @param cmd Command
     */
    //  public void addInitCommand(String cmd) {
    //      initCommands.add(cmd);
    //  }

    public void applyVariablesFromFile(File targetFile) {
        try {
            for (String s : Files.readLines(targetFile)) {
                if (s.trim().length() > 0 && s.contains("=")) {
                    changeVariable(s);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Add build directory (will be deleted with make clean)
     *
     * @param dir Directory to add
     */
    public void addBuildDir(String dir) {
        buildDirs.add(dir);
    }

    /**
     * @return List of primary targets
     */
    public List<Target> getTargets() {
        return targets;
    }

    /**
     * Single makefile target
     * Encapsulates syntax
     */
    public class Target {

        /** Target name */
        private final String name;

        /** Phony target? */
        private final boolean phony;

        /** Target dependencies */
        private final TreeSet<String> dependencies = new TreeSet<String>();

        /** Order-only Target dependencies */
        private final TreeSet<String> ooDependencies = new TreeSet<String>();

        /** commands to execute in target */
        private final List<String> commands = new ArrayList<String>();

        /** directory containing sources for this target (in same svn/mercurial etc. repository) */
        private final String srcDir;

        private Target(String name, String srcRootDir) {
            this.name = name;
            this.srcDir = srcRootDir;
            phony = !name.contains(File.separator);
        }

        /** Write target to makefile */
        private void writeTo(PrintStream ps) {
            ps.print(name + " :");
            for (String dep : dependencies) {
                ps.print(" \\\n\t");
                ps.print(dep);
            }
            if (!ooDependencies.isEmpty()) {
                ps.print(" |");
            }
            for (String dep : ooDependencies) {
                ps.print(" ");
                ps.print(dep);
            }
            ps.println();

            // do we need to create directory for target?
            if (!phony) {
                String dir = name.substring(0, name.lastIndexOf(File.separator));
                boolean found = false;
                for (String s : dependencies) {
                    if (s.startsWith(dir + File.separator)) {
                        found = true;
                    }
                }
                if (!found) {
                    ps.println("\t@mkdir -p " + dir);
                }
            }

            for (String cmd : commands) {
                if (cmd.startsWith(NO_INDENT_PREFIX)) {
                    ps.println(cmd.substring(NO_INDENT_PREFIX.length()));
                } else {
                    ps.print("\t");
                    ps.println(cmd);
                }
            }
            ps.println();
        }

        /**
         * @return directory containing sources
         */
        public String getSrcDir() {
            return srcDir;
        }

        /**
         * Add dependency to target
         *
         * @param dep Name of dependency (toString() will be called on object)
         */
        public void addDependency(Object dep) {
            dependencies.add(dep.toString());
        }

        /**
         * Add command to target
         *
         * @param cmd Command
         * @param consoleOutput Output this command on console?
         * @param noindent Do not indentate? (this is necessary for lines such as ifeq ***, else, endif)
         */
        public void addCommand(String cmd, boolean consoleOutput) {
            addCommand(cmd, consoleOutput, false);
        }

        /**
         * Add command to target
         *
         * @param cmd Command
         * @param consoleOutput Output this command on console?
         * @param noindent Do not indentate? (this is necessary for lines such as ifeq ***, else, endif)
         */
        public void addCommand(String cmd, boolean consoleOutput, boolean noindent) {
            commands.add((noindent ? NO_INDENT_PREFIX : "") + (noindent || consoleOutput ? "" : "@") + cmd);
        }

        /**
         * Add message to target
         * Message will be output on console
         *
         * @param msg message
         */
        public void addMessage(String msg) {
            addCommand("echo '" + msg + "'", false);
        }

        /**
         * Add this target to a phony ("virtual") target
         *
         * @param phonyName Name of phony target
         * @param phonyDefaultDependencies Default dependencies of phony target - in case it is created with this call
         */
        public void addToPhony(String phonyName, String... phonyDefaultDependencies) {
            Target phony = phonyTargets.get(phonyName);
            if (phony == null) {
                phony = new Target(phonyName, null);
                phony.addCommand("echo $(" + DONE_MSG_VAR + ")", false);
                for (String s : phonyDefaultDependencies) {
                    phony.addDependency(s);
                }
                phonyTargets.put(phonyName, phony);
            }
            phony.addDependency(name);
        }

        public String toString() {
            return name;
        }

        /**
         * Add a set of dependencies to target
         *
         * @param dependencies Set of dependencies(toString() will be called on these objects and this string added as dependency)
         */
        public void addDependencies(Collection <? extends Object > dependencies) {
            for (Object o : dependencies) {
                addDependency(o);
            }
        }

        /**
         * @return Target name
         */
        public String getName() {
            return name;
        }

        /**
         * Add order-only dependency to target (see GNU-make documentation for details)
         * (THIS IS NOT SUPPORTED BY pmake)
         *
         * @param dep Name of dependency (toString() will be called on object)
         */
        public void addOrderOnlyDependency(Object dep) {
            ooDependencies.add(dep.toString());
        }
    }
}
