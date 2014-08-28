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
package makebuilder.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * @author Max Reichardt
 *
 * Contains helpful functions for files and streams
 */
public class Files {

    /**
     * Reads All lines from a text file
     *
     * @param f Text File
     * @return Lines
     */
    public static List<String> readLines(File f) throws IOException {
        return readLines(new FileInputStream(f));
    }

    /**
     * Reads All lines from a text file
     *
     * @param f Text File
     * @param cs Character set of input stream
     * @return Lines
     */
    public static List<String> readLines(File f, Charset cs) throws IOException {
        return readLines(new FileInputStream(f), cs);
    }

    /**
     * Reads All lines from a text stream
     *
     * @param is Text Stream
     * @return Lines
     */
    public static List<String> readLines(InputStream is) throws IOException {
        return readLines(is, Charset.defaultCharset());
    }

    /**
     * Reads All lines from a text stream
     *
     * @param is Text Stream
     * @param cs Character set of input stream
     * @return Lines
     */
    public static List<String> readLines(InputStream is, Charset cs) throws IOException {
        List<String> result = new ArrayList<String>();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, cs));
        while (true) {
            String s = br.readLine();
            if (s == null) {
                break;
            }
            result.add(s);
        }
        br.close();
        return result;
    }

    /**
     * Writes All lines to a text file
     *
     * @param f Text File
     * @param lines Lines
     */
    public static void writeLines(File f, List<String> lines) throws IOException {
        writeLines(new FileOutputStream(f), lines);
    }

    /**
     * Writes All lines to a text file
     *
     * @param f Text File
     * @param cs Character set of input stream
     * @param lines Lines
     */
    public static void writeLines(File f, Charset cs, List<String> lines) throws IOException {
        writeLines(new FileOutputStream(f), cs, lines);
    }

    /**
     * Writes All lines to a text stream
     *
     * @param is Text Stream
     * @param lines Lines
     */
    public static void writeLines(OutputStream os, List<String> lines) throws IOException {
        writeLines(os, Charset.defaultCharset(), lines);
    }

    /**
     * Writes All lines to a text stream
     *
     * @param is Text Stream
     * @param cs Character set of input stream
     * @param lines Lines
     */
    public static void writeLines(OutputStream os, Charset cs, List<String> lines) throws IOException {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(new BufferedOutputStream(os), cs));
        for (int i = 0, n = lines.size(); i < n; i++) {
            pw.println(lines.get(i));
        }
        pw.close();
    }

    /**
     * Gets all files in a directory and all of it's subdirectories
     * that have the specified extensions.
     *
     * @param path Path to get all files from
     * @param filter filename extensions that are
     * @param includeDirs Include directories in returned list?
     * @return Returns List with File objects
     */
    public static List<File> getAllFiles(File path, String[] extensions, boolean includeDirs, boolean processHiddenFiles) {
        return getAllFiles(path, new ExtensionFilenameFilter(extensions), includeDirs, processHiddenFiles);
    }

    /**
     * Gets all files in a directory and all of it's subdirectories
     * that are accepted by the specified filter.
     *
     * @param path Path to get all files from
     * @param filter Filter that files have to pass to be accepted
     * @param includeDirs Include directories in returned list?
     * @return Returns List with File objects
     */
    public static List<File> getAllFiles(File path, FilenameFilter filter, boolean includeDirs, boolean processHiddenFiles) {
        ArrayList<File> files = new ArrayList<File>();
        if (!path.isDirectory()) {
            return files;
        }
        for (File f : path.listFiles(filter)) {
            if (!processHiddenFiles && (f.getName().startsWith("."))) {
                continue;
            }
            if (f.isDirectory()) {
                if (includeDirs) {
                    files.add(f);
                }
                files.addAll(getAllFiles(f, filter, includeDirs, processHiddenFiles));
            } else {
                files.add(f);
            }
        }
        return files;
    }

    /**
     * Read content of stream and store it in byte array.
     * Closes stream.
     *
     * @param is Stream
     * @return Byte Array
     */
    public static byte[] readStreamFully(InputStream is) throws Exception {
        return readStreamFully(is, true);
    }

    public static char[] readStreamFully(Reader r) throws Exception {
        return readStreamFully(r, true);
    }


    /**
     * Read content of stream and store it in byte array.
     *
     * @param is Stream
     * @param closeStream close Stream afterwards
     * @return Byte Array
     */
    public static byte[] readStreamFully(InputStream is, boolean closeStream) throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        BufferedInputStream bis = new BufferedInputStream(is, 10000);
        byte[] buffer = new byte[10000];
        while (true) {
            int read = bis.read(buffer);
            if (read < 0) { // stream finished
                break;
            }
            data.write(buffer, 0, read);
        }
        if (closeStream) {
            bis.close();
        }
        return data.toByteArray();
    }

    public static char[] readStreamFully(Reader r, boolean closeStream) throws Exception {
        CharArrayWriter data = new CharArrayWriter();
        BufferedReader br = new BufferedReader(r, 10000);
        char[] buffer = new char[10000];
        while (true) {
            int read = br.read(buffer);
            if (read < 0) { // stream finished
                break;
            }
            data.write(buffer, 0, read);
        }
        if (closeStream) {
            br.close();
        }
        return data.toCharArray();
    }

    /**
     * Get all classes/files/folders in a package.
     * Independant of whether the classes are in the file system or packaged
     * in a .jar file.
     *
     * @param caller Calling class
     * @param folder Path relative to the calling class.
     * @return list of filenames (do not include path name)
     */
    public static List<String> getPackageContents(Class<?> caller, String folder) throws Exception {
        //System.out.println("get package contents: " + caller.getName() + ", " + folder);
        List<String> result = new ArrayList<String>();
        URL folder2 = caller.getResource(folder);
        if (folder2 != null && !folder2.toString().startsWith("jar:")) {
            BufferedReader br = new BufferedReader(new InputStreamReader(folder2.openStream()));
            while (true) {
                String s = br.readLine();
                if (s == null) {
                    break;
                }
                result.add(s);
            }
            br.close();
        } else {

            // open jar
            String folder3 = caller.getPackage().getName().replaceAll("[.]", "/");
            String temp = folder;
            while (temp.startsWith("../")) {
                folder3 = folder3.substring(0, folder3.lastIndexOf('/'));
                temp = temp.substring(3);
            }
            folder3 += "/" + temp;
            //System.out.println("folder3: " + folder3);
            URL classurl = caller.getResource(caller.getSimpleName() + ".class");
            //System.out.println("classurl: " + classurl);
            URL jar = ((JarURLConnection)classurl.openConnection()).getJarFileURL();
            //System.out.println("jar: " + jar);
            JarInputStream jis = new JarInputStream(new BufferedInputStream(jar.openStream()));
            while (true) {
                JarEntry je = jis.getNextJarEntry();
                if (je == null) {
                    break;
                }
                if (je.getName().startsWith(folder3)) {
                    result.add(je.getName().substring(je.getName().lastIndexOf("/") + 1));
                }
            }
            jis.close();
        }
        return result;
    }

    /**
     * Get all classes in a package.
     * Independant of whether the classes are in the file system or packaged
     * in a .jar file.
     *
     * @param caller Calling class
     * @param folder Path relative to the calling class.
     * @return List of Java classes found in the package
     */
    public static List < Class<? >> getPackageClasses(Class<?> caller, String folder) throws Exception {

        // determine package name
        String pack = caller.getPackage().getName();
        String temp = folder;
        while (temp.startsWith("../")) {
            pack = pack.substring(0, pack.lastIndexOf('.'));
            temp = temp.substring(3);
        }
        pack += "." + temp;

        List < Class<? >> result = new ArrayList < Class<? >> ();

        for (String s : getPackageContents(caller, folder)) {
            if (s.endsWith(".class")) {
                Class<?> c = Class.forName(pack + "." + s.substring(0, s.length() - 6));
                result.add(c);
            }
        }

        return result;
    }

    /**
     * Get root Url of application.
     * More precise: Root directory of the classpath the calling class is in.
     * This is independant on whether the application is in the file system
     * or packaged in a .jar file.
     *
     * @param caller Calling class
     * @return root Url
     */
    public static URL getRootUrl(Class<?> caller) {

        String dirName = caller.getResource(caller.getSimpleName() + ".class").toString();
        String packageName = caller.getName().replaceAll("[.]", "/");
        dirName = dirName.substring(0, dirName.indexOf(packageName));
        if (dirName.contains(".jar!")) {
            dirName = dirName.substring(0, dirName.indexOf(".jar!"));
            dirName = dirName.substring(dirName.indexOf("jar:/") + 5, dirName.lastIndexOf("/") + 1);
        }

        try {
            return new URL(dirName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get root directory of application.
     * More precise: Root directory of the classpath the calling class is in.
     * This is independant on whether the application is in the file system
     * or packaged in a .jar file.
     *
     * @param caller Calling class
     * @return root directory
     */
    public static String getRootDir(Class<?> caller) {

        String dirName = caller.getResource(caller.getSimpleName() + ".class").toString();
        try {
            dirName = URLDecoder.decode(dirName, "utf8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String packageName = caller.getName().replaceAll("[.]", "/");
        dirName = dirName.substring(0, dirName.indexOf(packageName));
        if (dirName.contains(".jar!")) {
            dirName = dirName.substring(0, dirName.indexOf(".jar!"));
            dirName = dirName.substring(dirName.indexOf("jar:/") + 5, dirName.lastIndexOf("/") + 1);
        }

        if (File.separator.equals("\\")) { // Windows
            dirName = dirName.substring(dirName.indexOf("file:/") + 6);
            dirName = dirName.replaceAll("/", "\\\\");
        } else { // Unix/Linux
            dirName = dirName.substring(dirName.indexOf("file:/") + 5);
        }

        return dirName.substring(0, dirName.length() - 1); // cut off proceeding File.separator
    }

    /**
     * Get directory of calling class (only works in file system with .class files)
     *
     * @param caller Calling class
     * @return directory
     */
    public static File getDir(Class<?> caller) {
        String dir = getRootDir(caller);
        String cpackage = caller.getPackage().getName().replaceAll("[.]", File.separator);
        return new File(dir + File.separator + cpackage);
    }
}
