package net.cumba.swingx.msp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Guards this module's third-party attribution.
 * <p>
 * Driven by two checked-in resources: {@code attribution-manifest.tsv}, one row per
 * {@code src/main/java} file, and {@code attribution-artefacts.tsv}, the module's licence files
 * plus the exact tuples its {@code licenses.xml} must declare.
 * <p>
 * Three properties are what stop this guard from quietly ceasing to guard, which is a failure this
 * codebase has hit repeatedly:
 * <ul>
 * <li><b>A missing input FAILS; it never skips.</b> There is no {@code assumeTrue} and no
 * empty-collection short circuit. If the manifest, the source tree or {@code licenses.xml} is not
 * where it is expected, the build goes red.</li>
 * <li><b>Coverage is a set equality in both directions.</b> Every manifest row must name a real
 * file, and every source file must have a row, so a NEW file cannot slip in unattributed.</li>
 * <li><b>Floors.</b> Hard-coded minimum counts, so deleting the sources and their manifest rows
 * together cannot make the equality vacuously true. Lower one only deliberately.</li>
 * </ul>
 * <p>
 * Deliberately NOT Spotless {@code licenseHeader}: that goal <i>applies</i>, so it would stamp a
 * missing notice back in -- a check that repairs what it should report -- and it cannot tell a file
 * ported from upstream from one P300 wrote.
 * <p>
 * This test travels with the module, so it gates in whichever repository the module ends up in and
 * is not bypassable by working in a submodule directly.
 * <p>
 * Moved here from the data browser ({@code net.cumba.dataviewer.licenses.AttributionTest}) together
 * with the two SwingX files, and kept identical to that class except for one extension: a
 * {@code FILE} row may carry a third column naming its in-jar copy (relative to
 * {@code src/main/resources}), held to the same byte-identity as the {@code LICENSE-*.txt} rule.
 * This repo's licence is the conventional root {@code LICENSE}, shipped in the jar as
 * {@code META-INF/LICENSE-LGPL-2.1.txt}.
 */
class AttributionTest
{

    /** Floor on {@code src/main/java} files. Lower only when files are genuinely removed. */
    private static final int MIN_SOURCES = 2;

    /** Floor on the {@code licenses.xml} tuples this module must declare. */
    private static final int MIN_TUPLES = 1;

    /** Floor on module-level attribution files (README, LICENSE-*.txt). */
    private static final int MIN_FILES = 2;

    /**
     * Floor on FILE rows whose in-jar copy is actually compared. Without it, dropping the third
     * column from the LICENSE row would skip the comparison silently (LICENSE does not start with
     * {@code LICENSE-}) while every other assertion still passed.
     */
    private static final int MIN_SHIPPED = 1;

    /** Floor on SHA256 rows, for the same reason. */
    private static final int MIN_HASHES = 1;

    /** Floor on manifest rows that actually carry an upstream notice (kind != NOT_DERIVED). */
    private static final int MIN_ATTRIBUTED = 2;

    private static final String MANIFEST = "attribution-manifest.tsv";

    private static final String ARTEFACTS = "attribution-artefacts.tsv";

    /**
     * Fails the build. Used instead of a JUnit assertion so this class is identical under JUnit 4
     * and JUnit 5; only the {@code @Test} import differs between the modules that carry it.
     */
    private static void require(boolean aCondition, String aMessage)
    {
        if (!aCondition)
        {
            throw new AssertionError(aMessage);
        }
    }


    private static Path moduleDir()
    {
        String base = System.getProperty("projectBasedir");
        Path dir = Paths.get(base == null ? "." : base).toAbsolutePath().normalize();
        require(Files.isDirectory(dir.resolve("src/main/java")), "No src/main/java under " + dir
                + " -- this guard cannot run, and that is a" + " FAILURE, not a reason to skip.");
        return dir;
    }


    /**
     * The subtree the manifest is required to cover COMPLETELY, relative to {@code src/main/java}.
     * Empty means the whole module. A module that carries third-party code in one corner declares
     * that corner here rather than listing every unrelated file; coverage inside the declared scope
     * is still total, so a new file THERE cannot slip in unattributed. The directory must exist --
     * a scope that has been moved away is a failure, not a silent pass.
     */
    private static Path scopeRoot() throws IOException
    {
        Path src = moduleDir().resolve("src/main/java");
        for (String[] r : rows(ARTEFACTS, 2))
        {
            if ("SCOPE".equals(r[0]))
            {
                Path scoped = src.resolve(r[1]);
                require(Files.isDirectory(scoped),
                        ARTEFACTS + " declares SCOPE " + r[1] + " but " + scoped
                                + " is not a directory. The guard's scope has moved,"
                                + " so it is no longer guarding anything.");
                return scoped;
            }
        }
        return src;
    }


    /**
     * Reads a driving resource from {@code src/test/resources} ON DISK, deliberately not from the
     * classpath.
     * <p>
     * ⚠ Measured 2026-09-09: a classpath read resolves {@code target/test-classes}, which
     * {@code mvn test} copies into but never prunes. Deleting the manifest from the source tree
     * therefore left a stale copy behind, and the guard went on "passing" against a file that no
     * longer existed -- the exact silent-disarming failure this class is written to avoid.
     */
    private static List<String[]> rows(String aResource, int aColumns) throws IOException
    {
        Path f = moduleDir().resolve("src/test/resources").resolve(aResource);
        require(Files.isRegularFile(f), f + " is missing. The guard's input is gone, so the"
                + " guard is not guarding. Restore the resource; do not delete the test.");
        List<String[]> out = new ArrayList<>();
        for (String line : Files.readString(f, StandardCharsets.UTF_8).split("\n", -1))
        {
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#"))
            {
                continue;
            }
            String[] parts = t.split("\t", -1);
            require(parts.length >= aColumns, "Malformed row in " + f + " (expected " + aColumns
                    + " tab-separated columns): " + t);
            out.add(parts);
        }
        require(!out.isEmpty(), f + " has no data rows. An empty manifest would make every other"
                + " assertion here vacuous.");
        return out;
    }


    private static Set<String> minus(Set<String> aLeft, Set<String> aRight)
    {
        TreeSet<String> out = new TreeSet<>(aLeft);
        out.removeAll(aRight);
        return out;
    }


    private static String text(Element aParent, String aTag)
    {
        NodeList n = aParent.getElementsByTagName(aTag);
        return n.getLength() == 0 ? "" : n.item(0).getTextContent();
    }


    @Test
    void manifestCoversEverySourceFileExactly() throws IOException
    {
        Path src = moduleDir().resolve("src/main/java");
        Path scope = scopeRoot();
        TreeSet<String> onDisk = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(scope))
        {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> onDisk.add(src.relativize(p).toString().replace('\\', '/')));
        }
        require(onDisk.size() >= MIN_SOURCES,
                "Only " + onDisk.size() + " source files under " + scope + ", below the floor of "
                        + MIN_SOURCES + ". Either the sources moved -- in which case this guard"
                        + " has just stopped guarding -- or the floor needs lowering on purpose.");

        TreeSet<String> declared = new TreeSet<>();
        for (String[] r : rows(MANIFEST, 3))
        {
            declared.add(r[0]);
        }
        require(declared.equals(onDisk),
                MANIFEST + " and " + scope + " disagree. Only on disk: " + minus(onDisk, declared)
                        + "; only in the manifest: " + minus(declared, onDisk)
                        + ". Every source file needs a row -- that is what stops a NEW file"
                        + " slipping in unattributed.");
    }


    @Test
    void everySourceFileCarriesItsNotice() throws IOException
    {
        Path src = moduleDir().resolve("src/main/java");
        List<String[]> manifest = rows(MANIFEST, 3);
        require(manifest.size() >= MIN_SOURCES, MANIFEST + " holds " + manifest.size()
                + " rows, below the floor of " + MIN_SOURCES);
        for (String[] r : manifest)
        {
            Path f = src.resolve(r[0]);
            require(Files.isRegularFile(f), MANIFEST + " names a file that does not exist: " + f);
            if ("NOT_DERIVED".equals(r[1]))
            {
                // Explicitly allow-listed as P300's own work. It still has to be LISTED, so a
                // new file cannot reach the tree without somebody classifying it; it just needs
                // no upstream notice.
                continue;
            }
            String s = Files.readString(f, StandardCharsets.UTF_8);
            require(s.startsWith("/*"),
                    r[0] + " has no leading block comment, so it carries no attribution notice"
                            + " at all.");
            String head = s.substring(0, s.indexOf("*/") + 2);
            require(head.contains(r[2]), r[0] + " (" + r[1] + ") has lost its notice: the header"
                    + " should contain \"" + r[2] + "\" but reads:\n" + head);
        }
    }


    @Test
    void moduleArtefactsExistAndTravelInTheJar() throws IOException
    {
        Path dir = moduleDir();
        int files = 0;
        int shippedChecks = 0;
        for (String[] r : rows(ARTEFACTS, 2))
        {
            if (!"FILE".equals(r[0]))
            {
                continue;
            }
            files++;
            Path f = dir.resolve(r[1]);
            require(Files.isRegularFile(f) && Files.size(f) > 0,
                    "Missing or empty attribution artefact: " + f);
            Path shipped = null;
            if (r.length >= 3 && !r[2].isBlank())
            {
                Path resources = dir.resolve("src/main/resources").normalize();
                shipped = resources.resolve(r[2]).normalize();
                require(shipped.startsWith(resources) && !shipped.equals(f.normalize()),
                        ARTEFACTS + " names in-jar copy " + r[2] + " for " + r[1]
                                + ", which is not a file under src/main/resources distinct from"
                                + " it. A copy compared with itself proves nothing.");
            }
            else if (f.getFileName().toString().startsWith("LICENSE-"))
            {
                shipped = dir.resolve("src/main/resources/META-INF")
                        .resolve(f.getFileName().toString());
            }
            if (shipped != null)
            {
                shippedChecks++;
                require(Files.isRegularFile(shipped), f.getFileName()
                        + " does not travel inside the jar; expected a copy at " + shipped);
                require(Files.mismatch(f, shipped) == -1, f + " and " + shipped
                        + " have drifted apart. The licence text the per-file notices point at"
                        + " must be the one that actually ships.");
            }
        }
        require(files >= MIN_FILES, "Only " + files + " FILE rows in " + ARTEFACTS
                + ", below the floor of " + MIN_FILES);
        require(shippedChecks >= MIN_SHIPPED,
                "Only " + shippedChecks + " FILE rows compared"
                        + " against an in-jar copy, below the floor of " + MIN_SHIPPED
                        + ". The licence text is no longer proven to ship.");
    }


    @Test
    void pinnedFilesStillHaveTheirContent() throws Exception
    {
        Path dir = moduleDir();
        int hashes = 0;
        for (String[] r : rows(ARTEFACTS, 2))
        {
            if (!"SHA256".equals(r[0]))
            {
                continue;
            }
            require(r.length >= 3, "SHA256 row needs a path and a digest: " + String.join("|", r));
            hashes++;
            Path f = dir.resolve(r[1]);
            require(Files.isRegularFile(f), "SHA256 row names a missing file: " + f);
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(f));
            String actual = java.util.HexFormat.of().formatHex(digest);
            require(actual.equals(r[2]), f + " has SHA-256 " + actual + ", expected " + r[2]
                    + ". Its content changed; if that is intended, update the pin on purpose.");
        }
        require(hashes >= MIN_HASHES, "Only " + hashes + " SHA256 rows in " + ARTEFACTS
                + ", below the floor of " + MIN_HASHES);
    }


    @Test
    void theManifestStillAttributesSomething() throws IOException
    {
        // Non-vacuity. Without this, turning every row into NOT_DERIVED would leave a manifest
        // that is complete, a set equality that holds, and a guard that checks nothing.
        long attributed = rows(MANIFEST, 3).stream().filter(r -> !"NOT_DERIVED".equals(r[1]))
                .count();
        require(attributed >= MIN_ATTRIBUTED,
                "Only " + attributed + " of the manifest's rows"
                        + " carry an upstream notice, below the floor of " + MIN_ATTRIBUTED
                        + ". A manifest of nothing but NOT_DERIVED rows is a guard that has stopped"
                        + " guarding.");
    }


    @Test
    void licensesXmlDeclaresExactlyTheExpectedUpstreams() throws Exception
    {
        Path xml = moduleDir().resolve("src/main/resources/licenses.xml");
        require(Files.isRegularFile(xml), xml + " is missing. That file is the only channel that"
                + " puts this module's attribution into the application's About dialog.");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = factory.newDocumentBuilder().parse(xml.toFile());
        NodeList deps = doc.getElementsByTagName("dependency");
        require(deps.getLength() >= MIN_TUPLES, xml + " declares only " + deps.getLength()
                + " entries, below the floor of " + MIN_TUPLES);

        TreeSet<String> actual = new TreeSet<>();
        for (int i = 0; i < deps.getLength(); i++)
        {
            Element d = (Element) deps.item(i);
            String group = text(d, "groupId");
            require(!"net.cumba".equals(group),
                    xml + " declares an entry under groupId"
                            + " net.cumba. AboutPanel drops every such entry, so the row would be"
                            + " invisible while looking correct. Use the UPSTREAM coordinates.");
            Element lic = (Element) d.getElementsByTagName("license").item(0);
            require(lic != null, xml + " entry " + group + " carries no <license> element.");
            actual.add(String.join("\t", group, text(d, "artifactId"), text(d, "version"),
                    text(lic, "name"), text(lic, "url")));
        }

        TreeSet<String> expected = new TreeSet<>();
        for (String[] r : rows(ARTEFACTS, 2))
        {
            if ("LICENSETUPLE".equals(r[0]))
            {
                require(r.length >= 6, "LICENSETUPLE row needs 5 values: " + String.join("|", r));
                expected.add(String.join("\t", r[1], r[2], r[3], r[4], r[5]));
            }
        }
        require(expected.size() >= MIN_TUPLES, "Only " + expected.size() + " LICENSETUPLE rows in "
                + ARTEFACTS + ", below the floor of " + MIN_TUPLES);
        require(actual.equals(expected),
                "licenses.xml does not match " + ARTEFACTS + ". Only in licenses.xml: "
                        + minus(actual, expected) + "; only expected: " + minus(expected, actual)
                        + ". LicenseInfo equality covers all five fields while"
                        + " the dialog renders only three, so a drifted tuple appears as a second,"
                        + " identical-looking row.");
    }
}
