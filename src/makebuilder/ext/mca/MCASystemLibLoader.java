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
package makebuilder.ext.mca;

import java.io.File;
import java.util.List;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;
import makebuilder.ext.finroc.FinrocBuilder;
import makebuilder.util.Files;

/**
 * @author Max Reichardt
 *
 * Handles system-wide installation of MCA
 */
public class MCASystemLibLoader extends SourceFileHandler.Impl {

    /** Have system libraries been loaded ? */
    boolean loaded = false;

    /** system MCA include path if MCA is installed system-wide - otherwise null */
    public final File MCA_SYSTEM_INCLUDE;

    /** MCA library-info path if MCA is installed system-wide - otherwise null */
    public final File MCA_SYSTEM_INFO;

    /** MCA library path if MCA is installed system-wide - otherwise null */
    public final File MCA_SYSTEM_LIB;

    /** Has MCA been installed system-wide? */
    public final boolean systemInstallExists;

    /** relative mca system-include path */
    public static final String MCA_SYSTEM_INCLUDE_DIR = FinrocBuilder.BUILDING_FINROC ? "/include/finroc" : "/include/mca2";

    /** relative mca system-lib-info path */
    public static final String MCA_SYSTEM_INFO_DIR = FinrocBuilder.BUILDING_FINROC ? "/share/finroc/info" : "/share/mca2/info";

    public MCASystemLibLoader() {
        File h1 = new File("/usr" + MCA_SYSTEM_INCLUDE_DIR);
        File h2 = new File("/usr/local" + MCA_SYSTEM_INCLUDE_DIR);
        File i1 = new File("/usr" + MCA_SYSTEM_INFO_DIR);
        File i2 = new File("/usr/local" + MCA_SYSTEM_INFO_DIR);
        MCA_SYSTEM_INCLUDE = h1.exists() ? h1 : (h2.exists() ? h2 : null);
        MCA_SYSTEM_INFO = i1.exists() ? i1 : (i2.exists() ? i2 : null);
        systemInstallExists = (MCA_SYSTEM_INCLUDE != null && MCA_SYSTEM_INFO != null);
        MCA_SYSTEM_LIB = systemInstallExists ? (FinrocBuilder.BUILDING_FINROC ?
                                                new File(MCA_SYSTEM_INCLUDE.getParentFile().getParent() + "/share/finroc/lib") :
                                                new File(MCA_SYSTEM_INCLUDE.getParentFile().getParent() + "/lib")) : null;
    }

    @Override
    public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {

        // Do this only once
        if (loaded || (!systemInstallExists)) {
            return;
        }
        loaded = true;

        // find/load all libraries that contain system information
        for (File f : MCA_SYSTEM_INFO.listFiles()) {
            if (f.getName().endsWith(LibInfoGenerator.EXT)) {
                String libName = f.getName().substring(0, f.getName().lastIndexOf("."));
                String libName2 = File.separator + libName;
                boolean exists = false;
                for (BuildEntity be : builder.buildEntities) {
                    exists |= be.getTarget().endsWith(libName2);
                }
                if (!exists) {
                    SystemLibrary be = new SystemLibrary();
                    be.name = libName; /*.substring(8, libName.lastIndexOf("."));*/
                    be.buildFile = scanner.registerBuildProduct(f.getAbsolutePath());
                    List<String> lines = Files.readLines(f);
                    for (String extlib : lines.get(0).split("\\s")) {
                        if (extlib.trim().length() > 0) {
                            be.libs.add(extlib);
                        }
                    }
                    for (String header : lines.get(1).split("\\s")) {
                        String hdr = MCA_SYSTEM_INCLUDE + "/" + header;
                        SrcFile sf = scanner.find(hdr);
                        if (sf != null) {
                            sf.setOwner(be);
                        } else {
                            System.err.println("Cannot find system header " + hdr + " from " + f.getPath());
                        }
                    }
                    be.targetName = MCA_SYSTEM_LIB + "/" + libName;
                    builder.buildEntities.add(be);
                }
            }
        }
    }

    public class SystemLibrary extends BuildEntity {

        private String targetName;

        public String getTarget() {
            return targetName;
        }

        public void initTarget(Makefile makefile) {
            target = makefile.DUMMY_TARGET;
        }

        @Override
        public Class <? extends SourceFileHandler > getFinalHandler() {
            return null;
        }
    }
}
