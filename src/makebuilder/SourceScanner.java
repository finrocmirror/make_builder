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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.SortedMap;
import java.util.TreeMap;

import makebuilder.util.ToStringComparator;

/**
 * @author max
 *
 * Scans local source files for dependencies to other local source files
 */
public class SourceScanner {

    /** Relevant extensions for scanning */
    //private final static String[] RELEVANT_EXTENSIONS = new String[]{"h", "H", "hpp", "ui", "cpp", ".c", ".C", ".cu"};

    /** Make builder information cache file */
    public static final String CACHE_FILE = ".makeBuilderCache";

    /** Makefile/home directory ($MCAHOME) */
    public final SrcDir homeDir;

    /** Home directory with File.separator appended */
    public final String homeDirExt;

    /** Reference to builder instance that this SourceScanner instance was created from */
    public final MakeFileBuilder builder;

    /** All relevant directories (relative name => dir) */
    private final SortedMap<String, SrcDir> dirs = new TreeMap<String, SrcDir>(ToStringComparator.instance);

    /** All source files (relative name => file) */
    private final SortedMap<String, SrcFile> files = new TreeMap<String, SrcFile>(ToStringComparator.instance);

//  /** candidates for a include - class attribute for efficiency reasons */
//  private final List<SrcFile> candidates = new ArrayList<SrcFile>();
//
//  /** Possible (root) include directories */
//  static final List<String> includeDirs = Arrays.asList(new String[]{"libraries", "", "tools", "projects"});

    /** for deferred initialization of default include paths of SrcDir instances - null when this is not necessary anymore */
    public ArrayList<SrcDir> needIncludePaths = new ArrayList<SrcDir>();

    /** ShortCut to File.separator */
    public static final String FS = File.separator;

    /**
     *
     * @param home Makefile/home directory ($MCAHOME)
     * @param builder Makefile builder instance
     */
    public SourceScanner(File home, MakeFileBuilder builder) {
        this.builder = builder;

        // init variables
        homeDirExt = home.getAbsolutePath() + File.separator;
        homeDir = createSrcDirInstance(home);
    }

    /**
     * Scan source files
     *
     * @param makefile Makefile - to add entries for descriptionbuilder, uic etc.
     * @param loaders List of BuildFileLoaders to execute
     * @param handler List of SourceFileHandlers to execute
     * @param useCache Use cache for file information?
     * @param sourceDirs Relative source directories
     */
    public void scan(Makefile makefile, Collection<BuildFileLoader> loaders, Collection<SourceFileHandler> handlers, boolean useCache, String... sourceDirs) {

        // find/register all source directories and files
        LinkedList<SrcDir> dirsToScan = new LinkedList<SrcDir>();
        for (String dir : sourceDirs) {
            SrcDir sd = homeDir.getSubDir(dir);
            if (sd.absolute.exists()) {
                dirsToScan.add(sd);
                sd.srcRoot = true;
            }
        }
        while (!dirsToScan.isEmpty()) {
            SrcDir dir = dirsToScan.removeFirst();
            for (File f : dir.absolute.listFiles()) {
                if (f.getName().startsWith(".")) { // hidden file or standard directory entries
                    continue;
                } else if (f.isDirectory()) {
                    dirsToScan.add(createSrcDirInstance(f));
                } else if (f.isFile()) {
                    SrcFile sf = new SrcFile(dir, f, false);
                    files.put(sf.relative, sf);
                }
            }
        }

        // init include paths of scanned directories
        for (int i = 0; i < needIncludePaths.size(); i++) {
            builder.setDefaultIncludePaths(needIncludePaths.get(i), this);
        }
        needIncludePaths = null;

        // load and apply cached information about files
        if (useCache) {
            SortedMap<String, SrcFile> cachedFileInfo = loadCachedInfo();
            if (cachedFileInfo != null) {
                for (SrcFile sf : files.values()) {
                    sf.applyCachedInfo(cachedFileInfo.get(sf.relative));
                }
            }
        }

        try {
            ArrayList<SrcFile> tempFiles = new ArrayList<SrcFile>(files.values()); // make copy

            // load build files
            for (SrcFile file : tempFiles) {
                for (BuildFileLoader loader : loaders) {
                    loader.process(file, builder.buildEntities, this, builder);
                }
            }

            // set ownership of files: relate SrcFile instances to BuildEntity instances
            // heuristic: all files with same base name (no extension) as one of build entity's source files belong to build entity
            for (BuildEntity be : builder.buildEntities) {
                for (SrcFile sf : be.sources) {
                    String baseName = sf.dir.relative + FS + sf.getRawName();
                    for (SrcFile sf2 : files.subMap(baseName + ".aaa", baseName + ".zzz").values()) {
                        sf2.setOwner(be);
                    }
                }
            }

            // scan/process source files
            for (SourceFileHandler handler : handlers) {
                for (SrcFile file : tempFiles) {
                    if (!file.relative.startsWith("/")) {
                        handler.processSourceFile(file, makefile, this, builder);
                    }
                }
            }

            // release resources (cached lines)
            for (SrcFile file : tempFiles) {
                file.scanCompleted();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // save cached info
        if (useCache) {
            System.out.print("Saving cache... ");
            saveCachedInfo();
            System.out.println("done");
        }
    }

    /**
     * Load cached file information from hdd
     *
     * @return Returns loaded information or null if no such information exists
     */
    @SuppressWarnings("unchecked")
    private SortedMap<String, SrcFile> loadCachedInfo() {
        try {
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(homeDirExt + CACHE_FILE)));
            System.out.print("Loading cached file info... ");
            Object result = ois.readObject();
            ois.close();
            System.out.println("done");
            return (SortedMap<String, SrcFile>)result;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Save acquired file information to hdd
     * (Should be called by MakeFileBuilder only)
     */
    void saveCachedInfo() {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(homeDirExt + CACHE_FILE)));
            oos.writeObject(files);
            oos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Create SrcDir instance
     *
     * @param dir Directory to create instance for
     * @return created and registered SrcDir instance
     */
    public SrcDir createSrcDirInstance(File dir) {
        SrcDir sd = new SrcDir(this, dir);
        dirs.put(sd.relative, sd);
        if (needIncludePaths == null) {
            builder.setDefaultIncludePaths(sd, this);
        } else {
            needIncludePaths.add(sd);
        }
        return sd;
    }

    /**
     * Create/Register/Introduce SrcFile object for source file that will be created
     * during the build process (e.g. generated code)
     *
     * @param file File that will be created (relative file name)
     * @return SrcFile instance that was created
     */
    public SrcFile registerBuildProduct(String relFile) {
        File abs = new File(homeDirExt + relFile);
        String relDir = relFile.substring(0, relFile.lastIndexOf(File.separator));
        SrcDir sd = findDir(relDir, true);
        SrcFile sf = new SrcFile(sd, abs, true);
        files.put(sf.relative, sf);
        return sf;
    }

    /**
     * Find directory
     *
     * @param relativeName Relative directory name
     * @param createIfNonExistent Create new SrcDir object if no such object exists yet?
     * @return Directory
     */
    public SrcDir findDir(String relativeName, boolean createIfNonExistent) {
        SrcDir result = dirs.get(relativeName);
        if (result == null && createIfNonExistent) {
            result = createSrcDirInstance(relativeName.startsWith(File.separator) ? new File(relativeName) : new File(homeDirExt + relativeName));
        }
        return result;
    }

    /**
     * Find source file
     *
     * @param relFileName Relative file name
     * @return SrcFile instance if file exists - otherwise null
     */
    public SrcFile find(String relFileName) {
        SrcFile result = files.get(relFileName);
        if (result != null) {
            return result;
        } else if (relFileName.startsWith(".")) {
            return files.get(relFileName.substring(2));
        }
        return null;
    }

    /**
     * Find source file
     *
     * @param dir Directory
     * @param filename File name (may include File.separators)
     * @return SrcFile instance if file exists - otherwise null
     */
    public SrcFile find(SrcDir dir, String filename) {
        String s = dir.relative.equals(".") ? filename : dir.relative + File.separator + filename; // string we're looking for
        s.replace("/./", "/");

        // eliminate any ".." in filename
        while (s.contains("/../")) {
            int i = s.indexOf("/../");
            String part1 = s.substring(0, i);
            String part2 = s.substring(i + 4);
            s = (part1.contains("/") ? part1.substring(0, part1.lastIndexOf("/") + 1) : "") + part2;
        }

        return find(s);
    }

    /**
     * Return all files whose relative name starts with specified string
     *
     * @param startString Specified string
     * @return List of source files
     */
    public Collection<SrcFile> getAllFilesStartingWith(String startString) {
        String endString = startString.substring(0, startString.length() - 1) + (startString.charAt(startString.length() - 1) + 1);
        return files.subMap(startString, endString).values();
    }

    /**
     * @return Collection of all source files
     */
    public Collection<SrcFile> getAllFiles() {
        return files.values();
    }

    /**
     * @return Collection of all processed directories
     */
    public Collection<SrcDir> getAllDirs() {
        return dirs.values();
    }

//  /**
//   * Create and initialize h file cache
//   *
//   * @param home $MCAHOME directory
//   * @param makefile Makefile - to add entries for descriptionbuilder, uic etc.
//   * @param buildBase Base directory for building ($MCAHOME/build)
//   */
//  public SourceScanner(String home, Makefile makefile, String buildBase) throws Exception {
//      List<File> hfiles = new ArrayList<File>();
//      hfiles.addAll(Files.getAllFiles(new File(home + "/libraries"), RELEVANT_EXTENSIONS, false, false));
//      hfiles.addAll(Files.getAllFiles(new File(home + "/projects"), RELEVANT_EXTENSIONS, false, false));
//      hfiles.addAll(Files.getAllFiles(new File(home + "/tools"), RELEVANT_EXTENSIONS, false, false));
//
//      // parse
//      List<SrcFile> hfiles2 = new ArrayList<SrcFile>();
//      for (int i = 0; i < hfiles.size(); i++) {
//          if (!hfiles.get(i).getPath().endsWith(".ui")) {
//
//              // ordinary h/cpp file - parse
//              SrcFile hfile = new SrcFile(hfiles.get(i), home);
//              hfiles2.add(hfile);
//              hfile.parse(hfiles2, makefile, buildBase);
//          } else {
//
//              // ui file - assuming qt4
//              String c = hfiles.get(i).getAbsolutePath().substring(home.length() + 1);
//              String raw = c.substring(0, c.lastIndexOf("."));
//              String qtCall = LibDB.getLib("uic-qt4").options.trim();
//
//              // add target to make file
//              String h = buildBase + FS + raw + ".h";
//              h = h.substring(0, h.lastIndexOf(FS) + 1) + "ui_" + h.substring(h.lastIndexOf(FS) + 1);
//              Target t = makefile.addTarget(h);
//              t.addDependency(c);
//              t.addCommand(MakeFileBuilder.mkdir(h));
//              t.addCommand(qtCall + " " + c + " -o " + h);
//
//              // add virtual h
//              SrcFile hf = new SrcFile(h);
//              hf.moc = true;
//              hfiles2.add(hf);
//          }
//      }
//
//      // create h file objects
//      hs = hfiles2.toArray(new SrcFile[0]);
//      Arrays.sort(hs, this);
//
//      // read and parse
//      for (SrcFile h : hs) {
//          h.resolveDeps();
//      }
//  }

//  /**
//   * Find include file
//   *
//   * @param inc Include file name - relative to $MCAHOME
//   * @return Include file
//   */
//  public SrcFile find(String inc) {
//      String incRev = new StringBuilder(inc).reverse().toString();
//      int result = Arrays.binarySearch(hs, incRev, this);
//      if (result >= 0) {
//          return hs[result];
//      }
//      throw new RuntimeException("Source file not found");
//  }
//
//
//  /**
//   * Find include file (heuristics for best match)
//   *
//   * @param inc Include file name
//   * @param curDir Current directory
//   * @return Include file
//   */
//  public SrcFile find(String inc, String curDir) {
//
//      // efficiently find candidates
//      String incRev = new StringBuilder(inc).reverse().toString();
//      candidates.clear();
//      int result = Arrays.binarySearch(hs, incRev, this);
//      if (result >= 0) {
//          candidates.add(hs[result]);
//          result++;
//      } else {
//          result = -result;
//          result--;
//      }
//      while(result < hs.length && hs[result].relFile.endsWith(inc)) {
//          candidates.add(hs[result]);
//          result++;
//      }
//
//      // evaluate candidates
//      String exactMatch = curDir + FS + inc;  // best
//      SrcFile pathStart = null; int pathMatch = 0; // 2nd best
//      SrcFile any = null; int anyLen = Integer.MAX_VALUE; // 3rd best
//
//      for (SrcFile hf : candidates) {
//          if (hf.relFile.equals(exactMatch)) {
//              return hf;
//          }
//
//          // How many paths are in common at beginning?
//          int matchCount = 1;
//          for (int i = 0; i < curDir.length(); i++) {
//              char c = curDir.charAt(i);
//              if (c != hf.relFile.charAt(i)) {
//                  matchCount--;
//                  break;
//              } else if (c == FS) {
//                  matchCount++;
//              }
//          }
//          if (matchCount > pathMatch) {
//              pathStart = hf;
//              pathMatch = matchCount;
//          }
//
//          if (hf.fsCount < anyLen) {
//              any = hf;
//              anyLen = hf.fsCount;
//          }
//      }
//
//      return (pathStart != null) ? pathStart : any;
//  }
//
//  /**
//   * Parse line - searching for file in any include statement
//   * (slightly ugly method)
//   *
//   * @param s Line
//   * @return Filename - or null, if not a include
//   */
//  public static String getIncludeString(String s) throws Exception {
//      if (s.startsWith("#")) {
//          s = s.substring(1).trim();
//
//          // relevant include statement ?
//          if (s.startsWith("include ")) {
//              s = s.substring(8).trim();
//              if (s.startsWith("\"")) {
//                  return s.substring(1, s.lastIndexOf("\""));
//              } else if (s.startsWith("<")) {
//                  return s.substring(1, s.indexOf(">"));
//              } else {
//                  throw new Exception("Error getting include string");
//              }
//          }
//      }
//      return null;
//  }

//  public HFile getInclude(String s, String curDir) throws Exception {
//      String inc = getIncludeString(s);
//      if (inc != null) {
//          return findInclude(inc, curDir);
//      }
//      return null;
//  }
}
