# cumba-oss-swingx-msp

A Swing **multi-split pane**: `MultiSplitLayout` and `MultiSplitPane`, which lay out any number of
components in nested, resizable rows and columns with draggable dividers.

The code derives from Hans Muller's `MultiSplitLayout` / `MultiSplitPane` in **SwingLabs SwingX**
(Copyright 2004 Sun Microsystems, Inc.). This repository is where that code is maintained and
enhanced for the cumba data browser, as a library of its own.

## Licence

**GNU Lesser General Public License, version 2.1, or (at your option) any later version**
(SPDX: `LGPL-2.1-or-later`). That includes LGPL-3.0 and any later version the Free Software
Foundation publishes.

- [`LICENSE`](LICENSE) is the verbatim LGPL-2.1 text. The licence document itself may not be
  changed; the "or later" grant is made here, in each source file's header, and in the pom's
  `<licenses>` entry.
- The upstream files carry that same grant (*"either version 2.1 of the License, or (at your
  option) any later version"*). Our changes to them, and every file added here, are licensed on
  the same terms.
- ⚠ This repository is deliberately **not** under the `AGPL-3.0-only` licence of the rest of the
  cumba OSS suite: code derived from an LGPL library stays LGPL.

## Coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-swingx-msp</artifactId>
    <version>${cumba-oss-swingx-msp.version}</version>
</dependency>
```

## Building

A single-module Maven project with the stack's standard gates (Spotless, PMD, SpotBugs, Error Prone
with NullAway, JaCoCo, Pitest):

```bash
mvn clean verify
```

Java 25.

## Building `main`

On `main` the version is a `-SNAPSHOT`, and so is every sibling `cumba-oss-*`
artifact it depends on. Each sibling publishes its snapshot to the Central
Portal snapshot repository on every push to its `main`. The poms deliberately
name no snapshot repository, so a clone that has not built the siblings itself
needs a settings file that adds it (the same one CI writes):

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <profiles>
    <profile>
      <id>central-portal-snapshots</id>
      <repositories>
        <repository>
          <id>central-portal-snapshots</id>
          <url>https://central.sonatype.com/repository/maven-snapshots/</url>
          <releases><enabled>false</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>central-portal-snapshots</activeProfile>
  </activeProfiles>
</settings>
```

```bash
mvn -gs central-snapshots.xml clean install
```

`-gs` replaces only the Maven installation's global settings; your
`~/.m2/settings.xml` still applies. Alternatively install the siblings locally,
in order: commons → formats → datatable → swingx-msp → corej. A release build
(a tag) never needs this: it depends on released versions from Maven Central
only. Central deletes snapshots after 90 days.

## Origin and modifications

`MultiSplitLayout.java` and `MultiSplitPane.java` (package `net.cumba.swingx.msp`) keep their
intact Sun / SwingLabs headers. They are **modified files** in the sense of LGPL-2.1 §2(a), and
each carries a notice saying so directly under the upstream header. The modifications began before
the first recorded commit (2026-04-24, in the retired `net.cumba.dataviewer` repository): that
import already had a new package and Lombok accessors. They continued there and in the data
browser's `ui/msp/`, and then here on 2026-09-24. This repository's git history is the record from
then on.

Attribution is guarded by `AttributionTest`: every source file must have a row in
`src/test/resources/attribution-manifest.tsv` (P300's own files as `NOT_DERIVED`), the licence
text must ship in the jar byte-identical to `LICENSE`, `LICENSE` itself is pinned by SHA-256 to
the verbatim LGPL-2.1 text, and `src/main/resources/licenses.xml` must declare exactly the
upstream tuple that puts SwingX in the data browser's About dialog.

## Corresponding source

The jar carries `META-INF/SOURCE.txt`, which points at this repository, and `git.properties`,
which records the exact commit (`git.commit.id`) and whether the tree was clean (`git.dirty`).
Use the commit id, not the jar's version: a product distribution rebuilds the library under the
product's version number. A jar with `git.dirty=true` is not a release artefact.

In the data browser's About dialog the library is listed under its own coordinates
(`net.cumba:cumba-oss-swingx-msp`, LGPL-2.1-or-later), next to the upstream SwingX attribution.
