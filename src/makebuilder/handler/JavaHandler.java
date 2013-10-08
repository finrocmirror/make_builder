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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import makebuilder.BuildEntity;
import makebuilder.SourceFileHandler;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SrcDir;
import makebuilder.SrcFile;
import makebuilder.libdb.LibDB;
import makebuilder.util.Files;
import makebuilder.libdb.ExtLib;

/**
 * @author max
 *
 * Responsible for building executables and libraries from Java source files
 */
public class JavaHandler extends SourceFileHandler.Impl {

    private static final Pattern packagePattern = Pattern.compile("\\s*package\\s+(.*)\\s*;");

    @Override
    public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) {

        if (be.getFinalHandler() != JavaHandler.class) {
            return;
        }

        // main target (.jar file)
        Makefile.Target mainTarget = be.target;

        // collect .jar files and copy .jar files to export/java
        String jars = ""; // line in manifest
        String cpJars = ""; // class path
        ArrayList<SrcFile> copy = new ArrayList<SrcFile>(be.sources);
        for (SrcFile sf : copy) {
            if (sf.hasExtension("jar")) {
                be.sources.remove(sf);
                String name = be.getTargetPath() + "/lib/" + sf.getName();
                jars += " lib/" + sf.getName();
                cpJars += ":" + name;

                // create .jar copy target in makefile
                Makefile.Target jar = makefile.addTarget(name, false, be.getRootDir());
                jar.addCommand("cp " + sf.relative + " " + jar.getName(), false);
                jar.addDependency(sf.relative);
                mainTarget.addDependency(jar);
            }
        }

        // add dependencies to jars
        for (ExtLib el : be.extlibs) {
            if (el.name.endsWith(".jar")) { // C++ dependencies are only relevant at runtime
                jars += " " + el.options;
                cpJars += ":" + el.options;
            }
        }
        for (BuildEntity dep : be.dependencies) {
            if (dep.getTarget().endsWith(".jar")) { // C++ dependencies are only relevant at runtime
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
        copy = new ArrayList<SrcFile>(be.sources);
        String javaFiles = "";
        for (SrcFile javaFile : copy) {
            if (javaFile.hasExtension("java")) {
                javaFiles += " " + javaFile.relative;
                be.sources.remove(javaFile);
                mainTarget.addDependency(javaFile);
            }
        }
        cpJars = buildDir + cpJars;
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
}
