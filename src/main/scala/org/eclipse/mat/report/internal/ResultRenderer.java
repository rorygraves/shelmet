/*******************************************************************************
 * Copyright (c) 2008, 2013 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - paths for icon localization
 *******************************************************************************/
package org.eclipse.mat.report.internal;

import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.report.IOutputter;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.TestSuite;
import org.eclipse.mat.util.FileUtils;
import org.eclipse.mat.util.HTMLUtils;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ResultRenderer {
    /* package */static final String DIR_PAGES = "pages";
    /* package */static final String DIR_ICONS = "icons";

    interface Key {
        String IS_EXPANDABLE = "isExpandable";
        String ARTEFACT = "artefact";
    }

    /* package */class HtmlArtefact {
        private File file;
        private PrintWriter writer;
        private String pathToRoot;
        private String relativeURL;

        private HtmlArtefact(AbstractPart part, File directory, String relativeURL, String title) throws IOException {
            this.file = new File(directory, relativeURL.replace('/', File.separatorChar));
            // Should the encoding be hard-coded to UTF-8?
            String encoding = System.getProperty("file.encoding");
            this.writer = new PrintWriter(file, encoding);

            this.pathToRoot = "";
            for (int ii = 0; ii < relativeURL.length(); ii++)
                if (relativeURL.charAt(ii) == '/')
                    pathToRoot += "../";

            this.relativeURL = relativeURL;

            artefacts.add(this);

            PageSnippets.beginPage(part, this, title, encoding);
        }

        public HtmlArtefact append(String s) {
            writer.append(s);
            return this;
        }

        public void close() {
            try {
                PageSnippets.endPage(this);
            } finally {
                writer.flush();
                writer.close();
                writer = null;
            }
        }

        @Override
        public String toString() {
            return file.getAbsolutePath();
        }

        public File getFile() {
            return file;
        }

        public String getPathToRoot() {
            return pathToRoot;
        }

        public String getRelativePathName() {
            return relativeURL;
        }
    }

    private TestSuite suite;
    private IOutputter html;

    private List<HtmlArtefact> artefacts = new ArrayList<HtmlArtefact>();

    private File directory;

    private Map<URI, String> icon2name = new HashMap<URI, String>();

    public ResultRenderer() {
        html = new HtmlOutputter();
    }

    public void beginSuite(TestSuite suite, AbstractPart part) throws IOException {
        this.suite = suite;

        prepareTempDirectory();

        HtmlArtefact index = new HtmlArtefact(part, directory, "index.html", part.spec().getName());
        suite.addResult(index.getFile());

        part.putObject(Key.ARTEFACT, index);
    }

    public void endSuite(AbstractPart part) throws IOException {
        renderTableOfContents(part);

        for (HtmlArtefact artefact : artefacts)
            artefact.close();

        copyIcons();

        zipResult();
    }

    private void copyIcons() throws IOException {
        if (!icon2name.isEmpty()) {
            File iconDir = new File(directory, DIR_ICONS);
            iconDir.mkdir();

            for (Map.Entry<URI, String> entry : icon2name.entrySet())
                copyResource(entry.getKey().toURL(), new File(iconDir, entry.getValue()));
        }
    }

    public void beginSection(SectionPart section) throws IOException {
        int order = 1;

        AbstractPart p = section;
        while (p.getParent() != null) {
            p = p.getParent();
            order++;
        }

        HtmlArtefact srcArtefact = (HtmlArtefact) section.getObject(Key.ARTEFACT);
        if (srcArtefact == null)
            srcArtefact = (HtmlArtefact) section.getParent().getObject(Key.ARTEFACT);

        HtmlArtefact artefact = createNewFileIfNecessary(srcArtefact, section, order);

        // do not create expansion if
        // (a) it is the top-level element
        // (b) it is a new file (e.g. the top-level element for a sub-page)
        if (order == 1 || srcArtefact != artefact) {
            PageSnippets.heading(artefact, section, order, false, true);
        } else {
            PageSnippets.heading(artefact, section, order, true, false);
            PageSnippets.beginExpandableDiv(artefact, section, false);
            section.putObject(Key.IS_EXPANDABLE, true);
        }
    }

    public void endSection(SectionPart section) {
        if (section.getObject(Key.IS_EXPANDABLE) != null) {
            HtmlArtefact artefact = (HtmlArtefact) section.getObject(Key.ARTEFACT);
            PageSnippets.endDiv(artefact);
        }
    }

    public void process(QueryPart test, IResult result, RenderingInfo rInfo) throws IOException {
        // determine output formatter
        String format = test.params().get(Params.FORMAT, "html");
        IOutputter outputter = html;

        if (result != null) {
            outputter = html;
        }

        // Also handle the case where the proper handler can't be found, so continue with html
        if ("html".equals(format) || outputter.equals(html))
            doProcess(outputter, test, result, rInfo, true);
    }

    public void processLink(LinkedPart linkedPart) {
        HtmlArtefact srcArtefact = (HtmlArtefact) linkedPart.getObject(Key.ARTEFACT);
        if (srcArtefact == null)
            srcArtefact = (HtmlArtefact) linkedPart.getParent().getObject(Key.ARTEFACT);

        String src = srcArtefact.getPathToRoot() + linkedPart.linkedTo.getDataFile().getUrl();

        srcArtefact.append("<a href=\"").append(src).append("\">")
                .append(linkedPart.spec().getName()).append("</a>");
    }

    private void doProcess(IOutputter outputter, QueryPart test, IResult result, RenderingInfo rInfo, boolean firstPass)
            throws IOException {
        HtmlArtefact srcArtefact = (HtmlArtefact) test.getObject(Key.ARTEFACT);
        if (srcArtefact == null)
            srcArtefact = (HtmlArtefact) test.getParent().getObject(Key.ARTEFACT);

        HtmlArtefact artefact = createNewFileIfNecessary(srcArtefact, test, 5);

        String pattern = test.params().shallow().get(Params.Rendering.PATTERN);
        boolean isOverviewDetailsPattern = firstPass && Params.Rendering.PATTERN_OVERVIEW_DETAILS.equals(pattern);

        if (!isOverviewDetailsPattern) {
            PageSnippets.queryHeading(artefact, test, srcArtefact != artefact);
            PageSnippets.beginExpandableDiv(artefact, test, srcArtefact != artefact);
        }

        boolean isImportant = test.params().shallow().getBoolean(Params.Html.IS_IMPORTANT, false);
        if (isImportant) {
            artefact.append("<div class=\"important\">");
        }

        outputter.embedd(rInfo, result, artefact.writer);

        if (isOverviewDetailsPattern) {
            String filename = test.getDataFile().getSuggestedFile();
            if (filename == null)
                filename = DIR_PAGES + '/' + test.getId() + ".html";

            artefact.append("<div>");
            PageSnippets.link(artefact, filename, Messages.ResultRenderer_Label_Details);
            artefact.append("</div>");

            // create new page for the details elements
            HtmlArtefact details = new HtmlArtefact(test.getParent(),
                    directory,
                    filename,
                    test.getParent().spec().getName());

            test.getDataFile().setUrl(details.getRelativePathName());

            // assign output page to all other children
            for (AbstractPart part : test.getParent().getChildren())
                part.putObject(Key.ARTEFACT, details);

            // process this child again (repeat on details page)
            doProcess(outputter, test, result, rInfo, false);
        }

        if (isImportant)
            artefact.append("</div>");

        if (!isOverviewDetailsPattern) {
            PageSnippets.endDiv(artefact);
        }
    }

    // //////////////////////////////////////////////////////////////
    // context interface
    // //////////////////////////////////////////////////////////////

    /* package */String addIcon(URL icon, AbstractPart part) {
        if (icon == null)
            return null;

        // URLs are bad for maps as the host has to be resolved by the name server
        URI iconKey;
        try {
            iconKey = icon.toURI();
        } catch (URISyntaxException e) {
            // Safe enough for a bad icon
            return null;
        }
        String name = icon2name.get(iconKey);
        if (name == null) {
            String f = icon.getFile();
            int p = f.lastIndexOf('.');

            String extension = p < 0 ? f : f.substring(p);
            icon2name.put(iconKey, name = "i" + icon2name.size() + extension);
        }

        HtmlArtefact artefact = ((HtmlArtefact) part.getObject(Key.ARTEFACT));

        return artefact.getPathToRoot() + DIR_ICONS + "/" + name;
    }

    /* package */File getOutputDirectory(AbstractPart part) {
        HtmlArtefact artefact = (HtmlArtefact) part.getObject(Key.ARTEFACT);
        return artefact == null ? directory : artefact.getFile().getParentFile();
    }

    /* package */String getPathToRoot(AbstractPart part) {
        HtmlArtefact artefact = (HtmlArtefact) part.getObject(Key.ARTEFACT);
        return artefact == null ? "" : artefact.getPathToRoot();
    }

    /* package */IQueryContext getQueryContext() {
        return suite.getQueryContext();
    }

    // //////////////////////////////////////////////////////////////
    // private parts
    // //////////////////////////////////////////////////////////////
    private static final String PREFIX = "/html/";

    @SuppressWarnings("nls")
    private void prepareTempDirectory() throws IOException {
        directory = FileUtils.createTempDirectory("report", null);

        copyResource(PREFIX + "styles.css", new File(directory, "styles.css"));
        copyResource(PREFIX + "code.js", new File(directory, "code.js"));

        File imgDir = new File(directory, "img");
        imgDir.mkdir();

        copyResource(PREFIX + "img/open.gif", new File(imgDir, "open.gif"));
        copyResource(PREFIX + "img/success.gif", new File(imgDir, "success.gif"));
        copyResource(PREFIX + "img/warning.gif", new File(imgDir, "warning.gif"));
        copyResource(PREFIX + "img/error.gif", new File(imgDir, "error.gif"));
        copyResource(PREFIX + "img/empty.gif", new File(imgDir, "empty.gif"));
        copyResource(PREFIX + "img/fork.gif", new File(imgDir, "fork.gif"));
        copyResource(PREFIX + "img/line.gif", new File(imgDir, "line.gif"));
        copyResource(PREFIX + "img/corner.gif", new File(imgDir, "corner.gif"));

        copyResource(PREFIX + "img/opened.gif", new File(imgDir, "opened.gif"));
        copyResource(PREFIX + "img/closed.gif", new File(imgDir, "closed.gif"));
        copyResource(PREFIX + "img/nochildren.gif", new File(imgDir, "nochildren.gif"));

        File pagesDir = new File(directory, DIR_PAGES);
        pagesDir.mkdir();
    }

    private void copyResource(String resource, File target) throws IOException {

        InputStream resourceStream = Object.class.getResourceAsStream(resource);
        if (resourceStream == null)
            throw new FileNotFoundException(resource);
        try {
            try (OutputStream out = new FileOutputStream(target)) {
                FileUtils.copy(resourceStream, out);
            }
        } finally {
            resourceStream.close();
        }
    }

    private void copyResource(URL resource, File target) throws IOException {
        try (InputStream in = resource.openStream()) {
            try (OutputStream out = new FileOutputStream(target)) {
                FileUtils.copy(in, out);
            }
        }
    }

    private HtmlArtefact createNewFileIfNecessary(HtmlArtefact artefact, AbstractPart part, int order)
            throws IOException {
        boolean isSeparateFile = part.params().shallow().getBoolean(Params.Html.SEPARATE_FILE, false);
        boolean isEmbedded = part.params().shallow().getBoolean("$embedded", false);
        if (isSeparateFile || isEmbedded) {
            String filename = part.getDataFile().getSuggestedFile();
            if (filename == null)
                filename = part.params().shallow().get(Params.FILENAME);
            if (filename == null)
                filename = DIR_PAGES + '/' + FileUtils.toFilename(part.spec().getName(), part.getId(), "html");
            part.getDataFile().setUrl(filename);

            HtmlArtefact newArtefact = new HtmlArtefact(part, directory, filename, part.spec().getName());

            if (!isEmbedded)
                PageSnippets.linkedHeading(artefact, part, order, newArtefact.getRelativePathName());

            artefact = newArtefact;
        }

        part.putObject(Key.ARTEFACT, artefact);
        part.getDataFile().setUrl(artefact.getRelativePathName());
        return artefact;
    }

    // //////////////////////////////////////////////////////////////
    // render table of contents into HTML page
    // //////////////////////////////////////////////////////////////

    private void renderTableOfContents(AbstractPart part) throws IOException {
        HtmlArtefact toc = new HtmlArtefact(null, directory, "toc.html", Messages.ResultRenderer_Label_TableOfContents);

        toc.append("<h1>" + Messages.ResultRenderer_Label_TableOfContents + "</h1>\n");

        renderResult(toc, part, 0);
    }

    @SuppressWarnings("nls")
    private void renderResult(HtmlArtefact toc, AbstractPart parent, int depth) {
        toc.append("<ul class=\"collapsible_").append(depth < 3 ? "opened" : "closed").append("\">");

        for (AbstractPart part : parent.getChildren()) {
            toc.append("<li>");

            if (part.getStatus() != null)
                toc.append("<img src=\"img/").append(part.getStatus().name().toLowerCase(Locale.ENGLISH) + ".gif\" alt=\"\"> ");

            HtmlArtefact page = (HtmlArtefact) part.getObject(Key.ARTEFACT);
            AbstractPart p = part;
            while (page == null)
                page = (HtmlArtefact) (p = p.getParent()).getObject(Key.ARTEFACT);

            PageSnippets.beginLink(toc, page.getRelativePathName() + "#" + part.getId());
            toc.append(HTMLUtils.escapeText(part.spec().getName()));
            PageSnippets.endLink(toc);

            if (!part.children.isEmpty())
                renderResult(toc, part, depth + 1);

            toc.append("</li>");
        }
        toc.append("</ul>");
    }

    // //////////////////////////////////////////////////////////////
    // zip directory
    // //////////////////////////////////////////////////////////////

    private void zipResult() throws IOException {
        File targetZip = suite.getOutput();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetZip))) {
            zipDir(directory.getPath().length() + 1, directory, zos);
        }

        suite.addResult(targetZip);
    }

    private void zipDir(int commonPath, File zipDir, ZipOutputStream zos) throws IOException {
        String[] dirList = zipDir.list();
        byte[] readBuffer = new byte[2156];
        int bytesIn = 0;

        for (String aDirList : dirList) {
            File f = new File(zipDir, aDirList);
            if (f.isDirectory()) {
                zipDir(commonPath, f, zos);
            } else {
                try (FileInputStream fis = new FileInputStream(f)) {
                    String path = f.getPath().substring(commonPath);
                    ZipEntry anEntry = new ZipEntry(path);
                    zos.putNextEntry(anEntry);
                    while ((bytesIn = fis.read(readBuffer)) != -1) {
                        zos.write(readBuffer, 0, bytesIn);
                    }
                }
            }
        }
    }

}
