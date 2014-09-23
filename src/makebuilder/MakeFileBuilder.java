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

import java.io.File;
import java.io.FilenameFilter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import makebuilder.handler.CppHandler;
import makebuilder.handler.MakeXMLLoader;
import makebuilder.libdb.LibDB;
import makebuilder.util.ActivityLog;
import makebuilder.util.Util;

/**
 * @author Max Reichardt
 *
 * Main class for Building makefiles
 */
public class MakeFileBuilder implements FilenameFilter, Runnable {

    /** Input Character set */
    public static final Charset INPUT_CHARSET = Charset.forName("ISO-8859-1");

    /** Current working directory - should be $MCAHOME */
    public static final File HOME = new File(".").getAbsoluteFile().getParentFile();

    /** Shortcut to file separator */
    protected static final String FS = File.separator;

    /** Libraries and executables that need to be built */
    public final List<BuildEntity> buildEntities = new ArrayList<BuildEntity>();

    /** Options from command line */
    protected static Options opts;

    /** List of build file loaders used int this builder */
    private final List<BuildFileLoader> buildFileLoaders = new ArrayList<BuildFileLoader>();

    /** List of content handlers used in this builder - need to have correct order */
    private final List<SourceFileHandler> contentHandlers = new ArrayList<SourceFileHandler>();

    /** build path where compiled binaries are placed */
    public final SrcDir buildPath;

    /** persistent temporary build path where generated files can be stored */
    public final SrcDir tempBuildPath;

    /** temp-path - non-persistent temporary build artifacts belong here */
    public final SrcDir tempPath;

    /** Makefile */
    public final Makefile makefile;

    //  /** Categorization of make targets (Target/Category => dependencies) */
    //  private final SortedMap<String, List<String>> categories = new TreeMap<String, List<String>>();

    /** Cache for source files */
    protected SourceScanner sources;

    /** Temporary directory for merged files */
    private final String TEMPDIR = "/tmp/mbuild_" + Util.whoami() + "_" + Math.abs(HOME.getAbsolutePath().hashCode());

    /** Error message for console - are collected and presented at the end */
    private final List<String> errorMessages = new ArrayList<String>();

    /** Single MakefileBuilder instance */
    private static MakeFileBuilder instance;

    /** Activity log */
    private final ActivityLog activityLog;

    /**
     * @return Single MakefileBuilder instance
     */
    public static MakeFileBuilder getInstance() {
        return instance;
    }

    public static void main(String[] args) {

        // Parse command line options
        opts = new Options(args);

        try {
            opts.mainClass.newInstance().run();
        } catch (Exception e) {
            e.printStackTrace();
            printErrorAdvice();
        }
    }

    /**
     * @return Parsed options from command line
     */
    public static Options getOptions() {
        return opts;
    }

    public MakeFileBuilder() throws Exception {
        this("dist", "build");
    }

    /**
     * Performs diverse initializations
     */
    public MakeFileBuilder(String relBuildDir, String relTempBuildDir) {
        instance = this;
        activityLog = new ActivityLog("Makebuilder");
        activityLog.addActivity("Initialization");

        // init source scanner and paths
        sources = new SourceScanner(HOME, this);
        buildPath = sources.findDir(relBuildDir, true);
        tempBuildPath = sources.findDir(relTempBuildDir, true);
        tempPath = sources.findDir(TEMPDIR, true);

        // create makefile object
        makefile = new Makefile("$(TARGET_DIR)", "$(TEMP_BUILD_DIR)", "$(TEMP_DIR)");
        //      makefile.addInitCommand("mkdir -p " + "$(TARGET_DIR)");
        //      makefile.addInitCommand("mkdir -p " + "$(TEMP_BUILD_DIR)");
        //      makefile.addInitCommand("mkdir -p " + "$(TEMP_DIR)");

        // add variables
        makefile.addVariable("TARGET_DIR=" + buildPath.relative);
        makefile.addVariable("TEMP_BUILD_DIR=" + tempBuildPath.relative);
        makefile.addVariable("TEMP_DIR=" + tempPath.relative);
    }

    /** Create makefile */
    public void run() {
        try {
            build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void build() throws Exception {

        // init content handlers
        if (buildFileLoaders.isEmpty()) {
            buildFileLoaders.add(new MakeXMLLoader());
        }
        if (contentHandlers.isEmpty()) { // add default content handlers
            contentHandlers.add(new CppHandler("", "", !opts.combineCppFiles));
        }
        for (SourceFileHandler ch : contentHandlers) {
            ch.init(makefile);
        }

        // read/process/cache source files
        System.out.println("Caching and processing local source files...");
        try {
            sources.scan(makefile, buildFileLoaders, contentHandlers, true, getSourceDirs());
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(Util.color("Error scanning files. A corrupted cache can cause errors. Trying again without cache.", Util.Color.RED, true));
            try {
                sources = new SourceScanner(HOME, this);
                sources.scan(makefile, buildFileLoaders, contentHandlers, false, getSourceDirs());
            } catch (Exception e2) {
                e2.printStackTrace();
                System.out.println(Util.color("Error still occured. Exiting.", Util.Color.RED, true));
                System.exit(-1);
            }
        }

        // find local dependencies in "external libraries" (note: ugly hack only required for some mca2 libraries with external .so files checked in)
        activityLog.addActivity("find local dependencies in external libraries");
        LibDB.findLocalDependencies(buildEntities);

        // Check for duplicate targets
        activityLog.addActivity("Check for duplicate targets");
        for (BuildEntity be : buildEntities) {
            for (BuildEntity be2 : buildEntities) {
                if (be != be2 && be.getTarget().equals(be2.getTarget())) {
                    System.out.println(Util.color("Two build entities with same target: " + be.getTarget() + "  (from " + be.buildFile.toString() + " and " + be2.buildFile.toString() + ")", Util.Color.RED, true));
                    System.exit(1);
                }
            }
        }

        // Check for files without owner
        activityLog.addActivity("Check for files without owner");
        if (opts.get("report-unmanaged-files") != null) {
            ArrayList<SrcFile> ownerLess = new ArrayList<SrcFile>();
            for (SrcFile sf : sources.getAllFiles()) {
                String e = sf.getExtension();
                if (sf.getOwner() == null && sf.relative.startsWith("sources/cpp/") && (e.equals("h") || e.equals("hpp") || e.equals("cpp") || e.equals("java"))) {
                    ownerLess.add(sf);
                }
            }
            if (ownerLess.size() > 0) {
                System.err.println(Util.color("Found " + ownerLess.size() + " source files that do not belong to a target. These will not be (properly) included in a system installation:", Util.Color.Y, true));
                for (SrcFile sf : ownerLess) {
                    System.err.println(Util.color("  " + sf.relative, Util.Color.Y, false));
                }
            }
        }

        // process dependencies
        activityLog.addActivity("Processing dependencies");
        System.out.println("Processing dependencies...");
        for (BuildEntity be : buildEntities) {
            be.resolveDependencies(buildEntities, this);
        }

        // dump dependency graph to dot file?
        if (getOptions().outputDotFile) {
            DotFile.write(new File("targets.dot"), buildEntities, sources);
            for (String err : errorMessages) {
                System.err.println(err);
            }
            return;
        }

        // check whether all dependencies are met
        activityLog.addActivity("check whether all dependencies are met");
        for (BuildEntity be : buildEntities) {
            be.checkForCycles(1);
            be.checkDependencies(this);
        }

        // add available optional libs
        activityLog.addActivity("add available optional libs");
        for (BuildEntity be : buildEntities) {
            be.addOptionalLibs();
        }

        // check for new cycles
        activityLog.addActivity("check for new cycles");
        for (BuildEntity be : buildEntities) {
            be.checkForCycles(2);
        }

        // collect external libraries needed for building
        activityLog.addActivity("collect external libraries needed for building");
        for (BuildEntity be : buildEntities) {
            be.mergeExtLibs();
        }

        // add build commands for entity to makefile
        activityLog.addActivity("add build commands for entity to makefile");
        for (BuildEntity be : buildEntities) {
            if (be.missingDep) {
                continue;
            }
            build(be);
        }

        // Write makefile
        activityLog.addActivity("Write makefile");
        writeMakefile();

        // print error messages at the end... so nobody will miss them
        //Collections.sort(errorMessages);
        for (String err : errorMessages) {
            System.err.println(err);
        }

        // completed
        System.out.println(Util.color("Creating Makefile successful.", Util.Color.GREEN, true));

        if (getOptions().printActivityLog) {
            System.out.println("\nActivity Log:\n");
            activityLog.print();
        }
    }

    /**
     * Writes makefile to disk (may be overridden for custom adjustments)
     */
    protected void writeMakefile() throws Exception {

        makefile.writeTo(new File(opts.generatedMakefileName));
    }

    /**
     * @param handler Source file handler to add to this build entity
     */
    protected void addHandler(SourceFileHandler handler) {
        contentHandlers.add(handler);
    }

    /**
     * @param loader Build file loader to add to this build entity
     */
    protected void addLoader(BuildFileLoader loader) {
        buildFileLoaders.add(loader);
    }

    /**
     * @return Get source directories (relative) - should be overidden
     */
    public String[] getSourceDirs() {
        return new String[] {"src"};
    }

    /**
     * Build single build entity
     *
     * @param be Build entity to build
     */
    private void build(final BuildEntity be) throws Exception {
        if (!be.getTargetPath().startsWith("/")) {
            System.out.println(Util.color("Processing " + be.getReferenceName(), Util.Color.GREEN, false));
        }
        be.initTarget(makefile);
        be.computeOptions();

        for (SourceFileHandler ch : contentHandlers) {
            ch.build(be, makefile, this);
        }
    }

    /**
     * Create and name intermediate temporary build artifact for a single source file
     * (can be overridden to perform custom naming)
     * (creates files in persistent temporary building directory)
     *
     * @param source Source file
     * @param targetExtension Suggested extension for target
     * @return
     */
    public SrcFile getTempBuildArtifact(SrcFile source, String targetExtension) {
        String srcDir = source.dir.relativeTo(source.dir.getSrcRoot());
        return sources.registerBuildProduct(tempBuildPath.relative + FS + srcDir + FS + source.getRawName() + "." + targetExtension);
    }

    /**
     * Create and name intermediate temporary build artifact for multiple source files
     * (can be overridden to perform custom naming)
     * (creates files in persistent temporary building directory)
     *
     * @param source Source file
     * @param targetExtension Suggested extension for target
     * @return
     */
    public SrcFile getTempBuildArtifact(BuildEntity source, String targetExtension, String suggestedPostfix) {
        String srcDir = source.getRootDir().relativeTo(source.getRootDir().getSrcRoot());
        return sources.registerBuildProduct(tempBuildPath.relative + FS + srcDir + FS + source.name + "_" + suggestedPostfix + "." + targetExtension);
    }

    /**
     * Get directory (normally in persistent temporary building directory) for build files
     * (can be overridden to perform custom naming)
     *
     * @param be Build Entity
     * @return Directory
     */
    public String getTempBuildDir(BuildEntity source) {
        String srcDir = source.getRootDir().relativeTo(source.getRootDir().getSrcRoot());
        return tempBuildPath.relative + FS + srcDir;
    }

    /**
     * Print error line deferred (when tool exits)
     *
     * @param s line to print
     */
    public void printErrorLine(String s) {
        errorMessages.add(s);
    }

    /**
     * Print error line which says that target cannot be built
     *
     * @param be Entity that cannot be built
     * @param string Error string
     * @param c Color to print "Cannot build ..." in
     */
    public void printCannotBuildError(BuildEntity be, String string, Util.Color c) {
        be.errorMessageId = errorMessages.size() + 1;
        printErrorLine(Util.color("(" + be.errorMessageId + ") ", c, false) + Util.color("Cannot build " + (be.isOptional() ? "optional " : "") + be.getReferenceName(), c, true) + " (" + be.buildFile + (be.lineNumber != 0 ? (":" + be.lineNumber) : "") + ")" + Util.color(string, c, false));
    }


    /** Print advice if an error occured */
    public static void printErrorAdvice() {
        System.out.println("An error was encountered during the build process.");
        System.out.println("Make sure you have the current version of this tool, called ant, and");
        System.out.println("the libdb.txt file is up to date (call updatelibdb),");
    }

    public boolean accept(File dir, String name) {
        File f = new File(dir.getAbsolutePath() + File.separator + name);
        return (f.isDirectory() || name.equals("SConscript"));
    }

    /**
     * Set default include paths for directory (used for finding/resolving .h dependencies)
     * (may/should be overridden)
     *
     * @param dir SrcDir instance of which to set default include paths
     */
    public void setDefaultIncludePaths(SrcDir dir, SourceScanner sources) {
        dir.defaultIncludePaths.add(dir);
    }

    /**
     * @return Cache for source files
     */
    public SourceScanner getSources() {
        return sources;
    }

    /**
     * @return Returns true when cross-compiling (may be overridden by subclass)
     */
    public boolean isCrossCompiling() {
        return false;
    }

    /**
     * @return Returns true if binaries are to be linked statically (may be overridden by subclass)
     */
    public boolean isStaticLinkingEnabled() {
        return false;
    }

    /**
     * @return Returns libdb to use for actual compiling (may be overridden by subclass)
     */
    public LibDB getTargetLibDB() {
        return LibDB.getInstance("native");
    }

    /**
     * @return Activity log
     */
    public ActivityLog getActivityLog() {
        return activityLog;
    }
}
