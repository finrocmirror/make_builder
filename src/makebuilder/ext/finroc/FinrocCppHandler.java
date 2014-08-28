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
package makebuilder.ext.finroc;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.TreeMap;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;
import makebuilder.handler.CppHandler;
import makebuilder.util.ToStringComparator;

/**
 * @author Max Reichardt
 *
 * Special CppHandler for Finroc.
 * Derived from standard CppHandler and extended to handle optional dependencies
 * in incremental builds.
 */
public class FinrocCppHandler extends CppHandler {

    /** Temporary variable */
    private ArrayList<String> macros = new ArrayList<String>();

    /** List with all libraries that are declared optional in some source file */
    private TreeMap<String, SrcFile> optionalLibraries = new TreeMap<String, SrcFile>(new ToStringComparator());

    public FinrocCppHandler(String cCompileOptions, String cxxCompileOptions, String compileOptionsLib, String compileOptionsBin, String linkOptions, String linkOptionsLib, String linkOptionsBin, boolean separateCompileAndLink) {
        super(cCompileOptions, cxxCompileOptions, compileOptionsLib, compileOptionsBin, linkOptions, linkOptionsLib, linkOptionsBin, separateCompileAndLink);
    }

    @Override
    public void init(Makefile makefile) {
        super.init(makefile);
        makefile.addVariable("PRESENCE_DIR:=presence/$(FINROC_ARCHITECTURE)");
    }

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner sources, MakeFileBuilder builder) {
        super.processSourceFile(file, makefile, sources, builder);

        // Add 'presence' file dependencies to source files
        if (file.hasExtension("c", "cpp", "h", "hpp")) {
            CodeTreeNode codeTreeRoot = (CodeTreeNode)file.properties.get(CPP_MODEL_KEY);
            if (codeTreeRoot != null) {
                macros.clear();
                getAllMacros(macros, codeTreeRoot);

                for (String macro : macros) {
                    if (macro.startsWith("_LIB_") && macro.endsWith("_PRESENT_")) {
                        macro = macro.substring("_LIB_".length(), macro.length() - "_PRESENT_".length());
                        if (macro.startsWith("RRLIB_") || macro.startsWith("FINROC_")) {
                            String lib = "lib" + macro.toLowerCase();
                            SrcFile dependency = optionalLibraries.get(lib);
                            if (dependency == null) {
                                dependency = sources.registerBuildProduct("build/$(PRESENCE_DIR)/" + lib);
                                optionalLibraries.put(lib, dependency);
                            }
                            if (!file.dependencies.contains(dependency)) {
                                file.dependencies.add(dependency);
                            }
                        }
                    }
                }
            } else {
                System.out.println("Warning: " + file.toString() + " does not have a code tree attached");
            }
        }
    }

    /**
     * Generate/update 'presence' files
     * If I'm not mistaken, library presence depends on architecture, so we use filenames ./build/presence/<arch>/<target-name>
     */
    public void updatePresenceFiles() throws Exception {
        File presencePath = new File("build/presence/" + System.getenv("FINROC_ARCHITECTURE"));
        if ((!presencePath.exists()) && (!presencePath.mkdirs())) {
            throw new Exception("Cannot create path " + presencePath.toString());
        }

        // Create/update presence files that are required
        for (String requiredFile : optionalLibraries.keySet()) {
            File presenceFile = new File(presencePath.getPath() + File.separator + requiredFile);
            boolean present = false;
            requiredFile += ".$(LIB_EXTENSION)";
            for (BuildEntity be : MakeFileBuilder.getInstance().buildEntities) {
                if (requiredFile.equals(be.getTargetFilename())) {
                    present = !be.missingDep;
                    break;
                }
            }

            boolean write = true;
            if (presenceFile.exists()) {
                FileReader reader = new FileReader(presenceFile);
                boolean oldPresent = (reader.read() == '1');
                write = (oldPresent != present);
                reader.close();
            }

            if (write) {
                FileWriter writer = new FileWriter(presenceFile);
                writer.write(present ? '1' : '0');
                writer.close();
            }
        }

        // Delete obsolete presence files
        for (File f : presencePath.listFiles()) {
            if (!optionalLibraries.containsKey(f.getName())) {
                f.delete();
            }
        }
    }

    /**
     * Helper method to extract all '#ifdef <macro>' macros from code tree (recursive)
     *
     * @param result Result list to store all macros that were found in
     * @param currentNode Node to start searching from
     */
    private void getAllMacros(ArrayList<String> result, CodeTreeNode currentNode) {
        if (currentNode.makroName != null) {
            if (!result.contains(currentNode.makroName)) {
                result.add(currentNode.makroName);
            }
        }

        for (CodeTreeNode childNode : currentNode.children) {
            getAllMacros(result, childNode);
        }
        for (CodeTreeNode childNode : currentNode.altChildren) {
            getAllMacros(result, childNode);
        }
    }



}
