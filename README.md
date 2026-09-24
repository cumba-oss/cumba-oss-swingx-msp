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
text must ship in the jar byte-identical to `LICENSE`, and `src/main/resources/licenses.xml` must
declare exactly the upstream tuple that puts SwingX in the data browser's About dialog.
