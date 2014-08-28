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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

import makebuilder.BuildEntity;
import makebuilder.BuildFileLoader;
import makebuilder.MakeFileBuilder;
import makebuilder.SourceFileHandler;
import makebuilder.SrcFile;
import makebuilder.handler.MakeXMLLoader;

/**
 * @author Max Reichardt
 *
 */
public class SConscriptConverter extends MakeFileBuilder {

    public SConscriptConverter() throws Exception {
        super();
    }

    /**
     * @param args
     */
    public void run() {
        System.out.println("Sconscript to make.xml converter");
        File src = new File("SConscript");
        File dest = new File(MakeXMLLoader.MAKE_XML_NAME);
        if (src.exists()) {
            try {
                new SConscriptConverter().convert(dest);
                System.out.println("Successfully created " + dest.getName());
            } catch (Exception e) {
                System.out.println("Something went wrong :-(");
                e.printStackTrace();
            }
        } else {
            System.out.println("This program needs to be run in directory that contains SConscript file");
        }
    }

    /**
     * @param dest Destination file (make.xml)
     */
    private void convert(File dest) throws Exception {

        // parse SConscript
        sources.scan(makefile, new ArrayList<BuildFileLoader>(), new ArrayList<SourceFileHandler>(), false, HOME.getAbsolutePath());
        Collection<BuildEntity> entities = SConscriptParser.parse(sources.find("./SConscript"), sources, this);

        // write result to make.xml
        PrintStream ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(dest)));
        ps.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        ps.println("<!DOCTYPE targets PUBLIC \"-//RRLIB//DTD make 1.0\" \"http://finroc.org/xml/rrlib/1.0/make.dtd\">");
        ps.println("<targets>\n");
        for (BuildEntity be : entities) {
            String type = be.getClass().getSimpleName().toLowerCase();
            ps.print("  <" + type + " name=\"" + be.name + "\"");

            // add libraries
            if (be.libs.size() > 0) {
                ps.print("\n        libs=\"");
                boolean first = true;
                for (String lib : be.libs) {
                    ps.print((first ? "" : " ") + lib);
                    first = false;
                }
                ps.print("\"");
            }

            // add optional libraries
            if (be.optionalLibs.size() > 0) {
                ps.print("\n        optionallibs=\"");
                boolean first = true;
                for (String lib : be.optionalLibs) {
                    ps.print((first ? "" : " ") + lib);
                    first = false;
                }
                ps.print("\"");
            }

            // add CC options
            addOptions("cxxflags", be.opts.createOptionString(true, false, true), ps);
            addOptions("cflags", be.opts.createOptionString(true, false, false), ps);
            addOptions("ldflags", be.opts.createOptionString(false, true, true), ps);

            ps.println(">");

            ps.println("    <sources>");
            for (SrcFile sf : be.sources) {
                String name = sf.relative;
                if (name.startsWith("./")) {
                    name = name.substring(2);
                }
                ps.println("      " + name);
            }
            ps.println("    </sources>");
            ps.println("  </" + type + ">\n");
        }
        ps.println("</targets>");
        ps.close();
    }

    /**
     * @param attribute XML attribute to set
     * @param opts String with options
     * @param ps Stream to write to
     */
    private void addOptions(String attribute, String opts, PrintStream ps) {
        opts = opts.replace("-fPIC", "").replace("-shared", "").trim();
        if (opts.length() > 0) {
            ps.print("\n        " + attribute + "=\"" + opts + "\"");
        }
    }

}
