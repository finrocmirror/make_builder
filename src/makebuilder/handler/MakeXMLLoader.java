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
package makebuilder.handler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;

import makebuilder.BuildEntity;
import makebuilder.BuildFileLoader;
import makebuilder.MakeFileBuilder;
import makebuilder.SourceScanner;
import makebuilder.SrcDir;
import makebuilder.SrcFile;
import makebuilder.StartScript;
import makebuilder.util.Util;

/**
 * @author Max Reichardt
 *
 * This handler processes/loads make.xml files and creates BuildEntity instances for them
 */
public class MakeXMLLoader implements BuildFileLoader {

    /** Name of xml file with target info */
    public final static String MAKE_XML_NAME = "make.xml";

    /** build entity subclasses known to loader and instantiated when their simple name is found in XML tag */
    private final Class<?>[] buildEntityClasses;

    /** Objects for parsing XML */
    static private final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    static private final SAXParserFactory saxFactory = SAXParserFactory.newInstance();
    static private DocumentBuilder dbuilder;

    static {

        // Initialize document builder
        try {
            dbuilder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public MakeXMLLoader(Class<?>... buildEntityClasses) {
        this.buildEntityClasses = buildEntityClasses;
    }

    /**
     * Is this a make.xml file?
     *
     * @param file File
     * @return Answer
     */
    public static boolean isMakeXMLFile(SrcFile file) {
        return file.getName().equalsIgnoreCase(MAKE_XML_NAME);
    }

    @Override
    public void process(SrcFile file, final List<BuildEntity> result, SourceScanner scanner, MakeFileBuilder builder) throws Exception {
        if (!isMakeXMLFile(file)) {
            return;
        }
        final int originalElementCount = result.size();

        // parse XML
        Document doc = null;
        /*try {
            doc = dbuilder.parse(file.absolute);
        } catch (UnknownHostException ex) {
            System.out.println(Util.color("Disabling DTD parsing, because there seems to be no internet connection available.", Util.Color.Y, false));*/

        // disable DTD
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbuilder = factory.newDocumentBuilder();
        doc = dbuilder.parse(file.absolute);
        //}

        for (Class<?> c : buildEntityClasses) {
            NodeList nl = doc.getElementsByTagName(c.getSimpleName().toLowerCase()); // all build entities of type c
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);

                // create build entity
                BuildEntity be = (BuildEntity)c.newInstance();
                be.buildFile = file;
                be.autoDependencies = true;
                result.add(be);

                // collect parameters - first attributes, then subtags
                SortedMap<String, String> params = getAllAttributes(n);
                NodeList children = n.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node child = children.item(j);
                    if (child.getNodeName().equals("script")) {
                        SortedMap<String, String> sparams = getAllAttributes(child);
                        if (sparams.containsKey("name")) {
                            be.startScripts.add(new StartScript(sparams.get("name"), sparams));
                        } else {
                            System.err.println("warning: start script with no name in " + file.relative);
                        }
                    } else {
                        params.put(child.getNodeName(), child.getTextContent().trim());
                        if (child.getNodeName().equals("sources")) {
                            Node ex = child.getAttributes().getNamedItem("exclude");
                            if (ex != null) {
                                params.put("exclude", ex.getNodeValue());
                            }
                        }
                    }
                }

                // make sure we have a name and source files
                if (!(params.containsKey("sources"))) {
                    throw new Exception("Build entities in " + file.relative + " need at least sources");
                }

                // okay... now process parameters
                be.name = params.get("name");
                be.libs.addAll(asStringList(params.get("libs")));
                be.optionalLibs.addAll(asStringList(params.get("optionallibs")));
                be.opts.addOptions(params.get("cxxflags"), be.opts.cxxCompileOptions);
                be.opts.addOptions(params.get("cflags"), be.opts.cCompileOptions);
                be.opts.addOptions(params.get("ldflags"), be.opts.linkOptions);
                be.params = params;
                if (params.get("uses-broken-proprietary-libraries-and-requires-old-style-linking") != null) {
                    System.err.println("Target " + be.name + " uses broken proprietary libraries and requires old-style linking. Note, that compilation will fail on newer operating systems with linking 'as-needed' set by default (e.g. Ubuntu >= 11.10).");
                    BuildEntity.LINKING_AS_NEEDED = false;
                }

                // Source files are a little more complicated
                for (String s : asStringList(params.get("sources"))) {
                    be.sources.addAll(new FileSet(be, file.dir, s, scanner, true).files);
                }
                for (String s : asStringList(params.get("exclude"))) {
                    be.sources.removeAll(new FileSet(be, file.dir, s, scanner, false).files);
                }
            }
        }

        saxFactory.setValidating(false);
        saxFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        SAXParser saxParser = saxFactory.newSAXParser();
        saxParser.parse(file.absolute, new DefaultHandler() {

            Locator l;

            @Override
            public void setDocumentLocator(Locator locator) {
                super.setDocumentLocator(locator);
                l = locator;
            }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) {
                for (int i = originalElementCount; i < result.size(); i++) {
                    String name1 = result.get(i).name;
                    String name2 = attributes.getValue("name");
                    if (qName.equals(result.get(i).getClass().getSimpleName().toLowerCase()) &&
                            ((name1 == null && name2 == null) || (name1 != null && name1.equals(name2)))) {
                        result.get(i).lineNumber = l.getLineNumber();
                    }
                }
            }
        });
    }

    /**
     * @param n XML node
     * @return All attributes of XML node in map
     */
    private static SortedMap<String, String> getAllAttributes(Node n) {
        SortedMap<String, String> params = new TreeMap<String, String>();
        NamedNodeMap nnm = n.getAttributes();
        for (int j = 0; j < nnm.getLength(); j++) {
            Node it = nnm.item(j);
            params.put(it.getNodeName(), it.getNodeValue().trim());
        }
        return params;
    }

    /**
     * Divide string at whitespaces - and return fragments in list
     *
     * @param string String - may be null
     * @return List of string fragments
     */
    private List<String> asStringList(String string) {
        List<String> result = new ArrayList<String>();
        if (string != null) {
            String[] s = string.split("\\s");
            for (String s2 : s) {
                if (s2.trim().length() > 0) {
                    result.add(s2.trim());
                }
            }
        }
        return result;
    }

    /** Represents a set of files */
    private class FileSet {

        /** List of Src files that this file set consists of */
        private List<SrcFile> files = new ArrayList<SrcFile>();

        /**
         * @param be BuildEntity files will be added to
         * @param dir Directory to search in
         * @param pattern Pattern (* means anything except of / - ** means anything - similar as in Apache Ant)
         * @param critical Is it critical for build entity if no files matching pattern exist?
         */
        private FileSet(BuildEntity be, SrcDir dir, String pattern, SourceScanner sources, boolean critical) {

            // no-wildcard optimization
            if (!pattern.contains("*")) {
                SrcFile sf = sources.find(dir, pattern);
                if (sf != null) {
                    files.add(sf);
                } else {
                    be.missingDep = true;
                    sources.builder.printCannotBuildError(be, ": " + dir + "/" + pattern + " not found", Util.Color.RED);
                }
                return;
            }

            // collect all files that match specified wildcard pattern
            String rp = pattern.replace("**", "$$TMP$$");
            rp = rp.replace("*", "[\\S&&[^/]]*");
            rp = rp.replace("$$TMP$$", "[\\S]*");
            Pattern p = Pattern.compile(rp);
            String dirExt = dir.relative + File.separator;
            for (SrcFile sf : sources.getAllFilesStartingWith(dirExt)) {
                String rel = sf.relative.substring(dirExt.length());
                if (p.matcher(rel).matches()) {
                    files.add(sf);
                }
            }

            if (files.isEmpty() && critical) {
                be.missingDep = true;
                sources.builder.printCannotBuildError(be, ": Pattern '" + pattern + "': no files found", Util.Color.RED);
            }
        }
    }
}
