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
package makebuilder.handler;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import makebuilder.BuildEntity;
import makebuilder.SourceFileHandler;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcDir;
import makebuilder.SrcFile;
import makebuilder.util.Files;
import makebuilder.libdb.ExtLib;

/**
 * @author Max Reichardt
 *
 * Responsible for building executables and libraries from Java source files
 */
public class JavaHandler implements SourceFileHandler {

    private static final Pattern packagePattern = Pattern.compile("\\s*package\\s+(.*)\\s*;");

    /** Path where .jar files are located that were installed to the system */
    private static final File SYSTEM_LIBRARY_PATH = new File("/usr/share/java");

    /** Key for code model in source file properties */
    public static final String JAVA_INFO_KEY = "javaInfo";

    /** Debug Java handler? */
    private final boolean debug = MakeFileBuilder.getOptions().containsKey("debug_java_handler");

    /** Dependency Resolver to use */
    private final ImportDependencyResolver dependencyResolver;

    public JavaHandler() {
        this(null);
    }

    /**
     * @param dependencyResolver Dependency Resolver to use (may be null if no resolver is to used)
     */
    public JavaHandler(ImportDependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    /**
     * Resolves dependencies to .jar file from import statements
     * (does not need to handle all import statements;
     *  unhandled dependencies need to be added to make.xml files in 'libs' attribute)
     */
    public static interface ImportDependencyResolver {

        /**
         * @return Version of dependency resolver (a newer version than in cached info will udpate info on all files)
         */
        public short getVersion();

        /**
         * @param file Source file that contains imports
         * @param imports List of imports (e.g. "org.finroc.core.FrameworkElement"; any whitespace is removed)
         * @return List of .jar files
         */
        public ArrayList<String> getDependencies(SrcFile file, List<String> imports);
    }

    /**
     * Info on Java source files to save to cache
     */
    private static class JavaInfo implements Serializable {

        /** UID */
        private static final long serialVersionUID = -115096753307172576L;

        /** Dependencies to .jar files resolved from import statements (without path - e.g. "finroc_core.jar") */
        ArrayList<String> resolvedDependencies;

        /** Package that Java file is in */
        //String package2 = "";

        /** Version of dependency resolver that is used (newer version will udpate info on all files) */
        short dependencyResolverVersion;
    }

    @Override
    public void init(Makefile makefile) {
    }

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
        if (file.hasExtension("java")) {
            if ((!file.isInfoUpToDate()) || file.properties.get(JAVA_INFO_KEY) == null ||
                    (dependencyResolver != null && ((JavaInfo)file.properties.get(JAVA_INFO_KEY)).dependencyResolverVersion < dependencyResolver.getVersion())) {
                processFile(file, scanner);
            }

            // Add dependencies to build entity
            JavaInfo info = (JavaInfo)file.properties.get(JAVA_INFO_KEY);
            if (info != null && info.resolvedDependencies != null && file.getOwner() != null) {
                for (String dependency : info.resolvedDependencies) {
                    if (!file.getOwner().libs.contains(dependency)) {
                        file.getOwner().libs.add(dependency);
                    }
                }
            }

            if (debug && info != null) {
                System.out.println("\nSource File '" + file.relative + "' resolved dependencies:");
                for (String dep : info.resolvedDependencies) {
                    System.out.println(" " + dep);
                }
            }
        }
    }

    /**
     * Find imports in Java files and add dependencies to source files
     *
     * @param file File to scan
     * @param sources SourceScanner instance that contains possible includes
     */
    public void processFile(SrcFile file, SourceScanner sources) {

        JavaInfo info = new JavaInfo();

        // parse code and add dependencies
        ArrayList<String> imports = new ArrayList<String>();
        for (String line : file.getCppLines()) {
            if (line.trim().startsWith("import ") && line.trim().endsWith(";")) {
                line = line.trim().substring("import ".length());
                line = line.substring(0, line.length() - 1).trim();
                imports.add(line.replaceAll("\\s+", ""));
//            } else if (line.trim().startsWith("package ") && line.trim().endsWith(";")) {
//                line = line.trim().substring("package ".length());
//                info.package2 = line.substring(0, line.length() - 1).trim().replaceAll("\\s+","");
            }
        }

        // finish info
        if (dependencyResolver != null) {
            info.dependencyResolverVersion = dependencyResolver.getVersion();
            info.resolvedDependencies = dependencyResolver.getDependencies(file, imports);
        }
        file.properties.put(JAVA_INFO_KEY, info);
    }


    @Override
    public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) {

        if (be.getFinalHandler() != JavaHandler.class) {
            return;
        }

        // main target (.jar file)
        Makefile.Target mainTarget = be.target;

        String jars = ""; // line in manifest
        String cpJars = ""; // class path

        // collect .jar files and copy .jar files to export/javab (I do not think this should be supported - as external libraries should not be checked in as binary files)
//        ArrayList<SrcFile> copy = new ArrayList<SrcFile>(be.sources);
//        for (SrcFile sf : copy) {
//            if (sf.hasExtension("jar")) {
//                be.sources.remove(sf);
//                String name = be.getTargetPath() + "/lib/" + sf.getName();
//                jars += " lib/" + sf.getName();
//                cpJars += ":" + name;
//
//                // create .jar copy target in makefile
//                Makefile.Target jar = makefile.addTarget(name, false, be.getRootDir());
//                jar.addCommand("cp " + sf.relative + " " + jar.getName(), false);
//                jar.addDependency(sf.relative);
//                mainTarget.addDependency(jar);
//            }
//        }

        // add dependencies to jars
        boolean systemDependencies = false;
        for (ExtLib el : be.directExtlibs) {
            if (el.name.endsWith(".jar")) { // C++ dependencies are only relevant at runtime
                String manifestJar = removeSystemLibraryPath(el.options);
                boolean systemDependency = !manifestJar.equals(el.options);
                systemDependencies |= systemDependency;
                jars += (systemDependency ? " lib/" : " ") + manifestJar;
                cpJars += ":" + el.options;
            }
        }
        for (BuildEntity dep : be.dependencies) {
            if (dep.getFinalHandler() == JavaHandler.class) { // C++ dependencies are only relevant at runtime
                jars += " " + dep.getTargetFilename();
                cpJars += ":" + dep.getTarget();
                mainTarget.addDependency(dep.getTarget());
            } else {
                mainTarget.addDependency(dep.getTarget());
            }
        }

        String srcPath = findSourceRoot(be.getRootDir(), builder);

        // create manifest
        SrcFile mf = builder.getTempBuildArtifact(be, "mf", "");
        Makefile.Target mfTarget = makefile.addTarget(mf.relative, true, be.getRootDir());
        mfTarget.addDependency(be.buildFile);
        mainTarget.addDependency(mfTarget);
        mfTarget.addCommand("echo 'Manifest-Version: 1.0' > " + mfTarget.getName(), false);
        String main = be.getParameter("main-class");
        if (main == null && be.sources.size() == 1 && be.sources.get(0).hasExtension("java") && (!be.isLibrary())) {
            main = be.sources.get(0).relative;
            main = main.substring(srcPath.length() + 1);
            main = main.substring(0, main.length() - 5).replace('/', '.');
        }
        if (main != null) {
            mfTarget.addCommand("echo 'Main-Class: " + main + "' >> " + mfTarget.getName(), false);
        }
        String plugin = be.getParameter("plugin-class");
        if (plugin != null) {
            mfTarget.addCommand("echo 'Plugin-Class: " + plugin + "' >> " + mfTarget.getName(), false);
        }
        if (jars.length() > 0) {
            mfTarget.addCommand("echo 'Class-Path:" + jars + "' >> " + mfTarget.getName(), false);
        }

        // compile java files
        String buildDir = builder.getTempBuildDir(be) + "/bin";
        ArrayList<SrcFile> copy = new ArrayList<SrcFile>(be.sources);
        String javaFiles = "";
        for (SrcFile javaFile : copy) {
            if (javaFile.hasExtension("java")) {
                javaFiles += " " + javaFile.relative;
                be.sources.remove(javaFile);
                mainTarget.addDependency(javaFile);
            }
        }
        cpJars = buildDir + cpJars;
        mainTarget.addCommand("find " + buildDir + " -type f -name \\*.class -delete", false);
        mainTarget.addCommand("mkdir -p " + buildDir, false);
        mainTarget.addCommand("javac -sourcepath " + srcPath + " -d " + buildDir + " -cp " + cpJars + javaFiles, true);

        // copy any other files
        for (SrcFile other : be.sources) {
            String o = buildDir + "/" + other.relative.substring(srcPath.length() + 1);
            Makefile.Target t = makefile.addTarget(o, false, be.getRootDir());
            t.addDependency(other.relative);
            t.addCommand("cp " + other.relative + " " + o, false);
            mainTarget.addDependency(t);
        }

        // jar java files
        mainTarget.addCommand("jar cfm " + mainTarget.getName() + " " + mfTarget.getName() + "  -C " + buildDir + "/ .", false);

        // possibly create symlink for system libraries
        if (systemDependencies) {
            mainTarget.addCommand("if [ ! -e " + be.getTargetPath() + "/lib ]; then ln -s " + SYSTEM_LIBRARY_PATH + " " +  be.getTargetPath() + "/lib; fi", false);
        }

        // possibly create index (for applets mainly)
        if (be.params.containsKey("create_index")) {
            mainTarget.addCommand("( cd " + be.getTargetPath() + " ; jar i " + be.getTargetFilename() + " )", true);
        }
    }

    /**
     * Returns root dir of java source tree (dir which would have namespace "")
     *
     * @param dir directory to search for java files
     * @return dir
     */
    private String findSourceRoot(SrcDir dir, MakeFileBuilder builder) {
        List<File> files = Files.getAllFiles(dir.absolute, new String[] {"java"}, false, false);
        for (File f : files) {

            // is it in a sub-repository?
            File makeXMLPath = f;
            do {
                makeXMLPath = makeXMLPath.getParentFile();
            } while (!new File(makeXMLPath.getAbsolutePath() + "/make.xml").exists());
            if (!makeXMLPath.equals(dir.absolute)) {
                continue;
            }

            try {
                for (String line : Files.readLines(f)) {
                    Matcher m = packagePattern.matcher(line);
                    if (m.matches()) {
                        String pkg = m.group(1);
                        String root = f.getParentFile().getAbsolutePath();
                        root = root.substring(0, root.length() - (pkg.length() + 1));
                        return SrcDir.relativeDirName(new File(root), builder.getSources());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        throw new RuntimeException("Could not find package declaration in java source tree " + dir.relative);
    }

    /**
     * Removes path from .jar file if it is located in the system library path
     *
     * @param jarFile Fully-qualified name of .jar file
     * @return Provided .jar file if it is not located in system library path; file name (only) of .jar file if it is in system library path
     */
    private String removeSystemLibraryPath(String jarFile) {
        File jar = new File(jarFile);
        if (SYSTEM_LIBRARY_PATH.exists() && jar.getParentFile().equals(SYSTEM_LIBRARY_PATH)) {
            return jar.getName();
        }
        return jarFile;
    }
}
