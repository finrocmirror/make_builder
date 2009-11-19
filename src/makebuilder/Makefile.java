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

	/** Directories that targets are built to. Will be remove with make clean command */
	private final String[] buildDirs;

	/** PHONY target for building everything */
	private final Target all = new Target("all");
	
	/** Dummy target - can be useful for certain non-standard stuff - will not be added to any makefile */
	public final Target DUMMY_TARGET = new Target("dummy target");
	
	/**
	 * @param buildDirs Directories that targets are built to. Will be removed with make clean command
	 */
	public Makefile(String... buildDirs) {
		this.buildDirs = buildDirs;
		addVariable(DONE_MSG_VAR + "=done");
	}
	
	/**
	 * @param other Makefile to copy initial values from 
	 */
	public Makefile(Makefile other) {
		buildDirs = other.buildDirs;
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
		
		// write variable declarations
		for (String s : variables) {
			ps.println(s + "\n");
		}
		
		// write PHONY target list
		ps.print(".PHONY: clean all init");
		for (Target t : phonyTargets.values()) {
			if (!t.name.startsWith(".")) {
				ps.print(" " + t.name);
				ps.print(" clean-" + t.name);
			}
		}
		ps.println("\n");
		
		// write/create 'all' target (which is default)
		all.addCommand("echo $(" + DONE_MSG_VAR + ")", false);
		all.writeTo(ps);
		
		// write 'clean' target
		Target clean = new Target("clean");
		for (String s : buildDirs) {
			clean.addCommand("rm -R -f " + s, true);
		}
		clean.addCommand("rm -f " + SourceScanner.CACHE_FILE, true);
		clean.writeTo(ps);
		
//		// write 'init' target
//		Target init = new Target("init");
//		for (String s : initCommands) {
//			init.addCommand(s);
//		}
//		init.writeTo(ps);
		
		// write other PHONY targets
		for (Target t : phonyTargets.values()) {
			t.writeTo(ps);
			
			// write clean-targets
			if (t.name.startsWith(".")) {
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
	 * @param variable Variable to add
	 */
	public void changeVariable(String newVariable) {
		String varname = newVariable.substring(0, newVariable.indexOf('=') + 1);
		for (int i = 0; i < variables.size(); i++) {
			if (variables.get(i).startsWith(varname)) {
				variables.set(i, newVariable);
				return;
			}
		}
		//System.out.println("attempt to change non-existing variable " + newVariable + "; adding instead");
		addVariable(newVariable);
	}
	
	/**
	 * Add Phony target to makefile
	 * 
	 * @param name Target Name
	 * @param dependencies Dependencies
	 */
	public Target addPhonyTarget(String name, String... dependencies) {
		Target t = new Target(name);
		for (String s : dependencies) {
			t.dependencies.add(s);
		}
		phonyTargets.put(name, t);
		return t;
	}
	
	/**
	 * Add target with specified name
	 * 
	 * @param target Target name
	 * @param secondary Secondary Target (intermediate file - doesn't matter if it is deleted; see GNU documentation for details ".SECONDARY")
	 * @return Created Target object
	 */
	public Target addTarget(String name, boolean secondary) {
		Target t = new Target(name);
		targets.add(t);
		if (secondary) {
			t.addToPhony(".SECONDARY");
		} else {
			all.addDependency(name);
		}
		return t;
	}
	
	/**
	 * Add initialization command
	 * 
	 * @param cmd Command
	 */
//	public void addInitCommand(String cmd) {
//		initCommands.add(cmd);
//	}
	
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

		private Target(String name) {
			this.name = name;
			phony = !name.contains(File.separator);
		}
		
		/** Write target to makefile */
		private void writeTo(PrintStream ps) {
			ps.print(name + " :");
			for (String dep : dependencies) {
				ps.print(" ");
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
				ps.print("\t");
				ps.println(cmd);
			}
			ps.println();
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
		 */
		public void addCommand(String cmd, boolean consoleOutput) {
			commands.add((consoleOutput ? "" : "@") + cmd);
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
				phony = new Target(phonyName);
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
		public void addDependencies(Collection<? extends Object> dependencies) {
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
		 * 
		 * @param dep Name of dependency (toString() will be called on object)
		 */
		public void addOrderOnlyDependency(Object dep) {
			ooDependencies.add(dep.toString());
		}
	}
}
