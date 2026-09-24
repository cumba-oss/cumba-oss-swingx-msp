package net.cumba.swingx.msp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import net.cumba.swingx.msp.MultiSplitLayout.Divider;
import net.cumba.swingx.msp.MultiSplitLayout.InvalidLayoutException;
import net.cumba.swingx.msp.MultiSplitLayout.Leaf;
import net.cumba.swingx.msp.MultiSplitLayout.Node;
import net.cumba.swingx.msp.MultiSplitLayout.Split;
import org.junit.jupiter.api.Test;

/**
 * Headless tests of the {@link MultiSplitLayout} algorithm: the two layout passes, preferred and
 * minimum sizes, and the tree edits {@code splitLeaf} / {@code removeLeaf} / {@code renameLeaf}.
 * <p>
 * Every expected rectangle below was worked out by hand from the documented contract (dividers keep
 * their size, extra space goes to weighted children in proportion and the remainder to the last
 * child, nothing shrinks below its minimum size), not by re-running the production formula.
 */
class MultiSplitLayoutTest
{

    private static JPanel comp(int prefW, int prefH, int minW, int minH)
    {
        JPanel p = new JPanel();
        p.setPreferredSize(new Dimension(prefW, prefH));
        p.setMinimumSize(new Dimension(minW, minH));
        return p;
    }


    private static JPanel comp()
    {
        return comp(10, 10, 20, 20);
    }


    private static Divider divider(int x, int y, int w, int h)
    {
        Divider d = new Divider();
        d.setBounds(new Rectangle(x, y, w, h));
        return d;
    }


    private static void layout(MultiSplitLayout layout, JPanel host, int w, int h)
    {
        host.setSize(w, h);
        layout.layoutContainer(host);
    }

    /**
     * A layout over a host panel, with components registered under leaf names.
     */
    private static final class Fixture
    {

        final MultiSplitLayout layout;

        final JPanel host;

        Fixture(Node model, int dividerSize)
        {
            layout = new MultiSplitLayout(model);
            layout.setDividerSize(dividerSize);
            host = new JPanel(layout);
        }


        JPanel add(String name)
        {
            JPanel c = comp();
            host.add(c, name);
            return c;
        }


        void layout(int w, int h)
        {
            MultiSplitLayoutTest.layout(layout, host, w, h);
        }
    }

    // ---------------------------------------------------------------- row layouts

    @Test
    void rowWithoutWeightsGivesAllExtraSpaceToTheLastChild()
    {
        Divider d = divider(95, 0, 10, 100);
        Fixture f = new Fixture(new Split(null, true, 0.0, new Leaf("a"), d, new Leaf("b")), 10);
        JPanel a = f.add("a");
        JPanel b = f.add("b");

        f.layout(200, 100);
        assertEquals(new Rectangle(0, 0, 95, 100), a.getBounds());
        assertEquals(new Rectangle(95, 0, 10, 100), d.getBounds());
        assertEquals(new Rectangle(105, 0, 95, 100), b.getBounds());

        f.layout(300, 100);
        assertEquals(new Rectangle(0, 0, 95, 100), a.getBounds(), "unweighted first child keeps");
        assertEquals(new Rectangle(95, 0, 10, 100), d.getBounds());
        assertEquals(new Rectangle(105, 0, 195, 100), b.getBounds());
        assertEquals(new Rectangle(0, 0, 300, 100), f.layout.getModel().getBounds());
    }


    @Test
    void rowHonoursTheContainerInsets()
    {
        Divider d = divider(115, 10, 10, 100);
        Fixture f = new Fixture(new Split(null, true, 0.0, new Leaf("a"), d, new Leaf("b")), 10);
        f.host.setBorder(BorderFactory.createEmptyBorder(10, 20, 0, 0));
        JPanel a = f.add("a");
        JPanel b = f.add("b");

        f.layout(220, 110);
        assertEquals(new Rectangle(20, 10, 95, 100), a.getBounds());
        assertEquals(new Rectangle(125, 10, 95, 100), b.getBounds());
    }


    @Test
    void rowGrowsWeightedChildrenInProportion()
    {
        Divider d = divider(95, 0, 10, 100);
        Split root = new Split(null, true, 0.0, new Leaf("a", 0.25), d, new Leaf("b", 0.75));
        root.setBounds(new Rectangle(0, 0, 200, 100));
        Fixture f = new Fixture(root, 10);
        JPanel a = f.add("a");
        JPanel b = f.add("b");

        f.layout(200, 100);
        assertEquals(new Rectangle(0, 0, 95, 100), a.getBounds(), "no extra space: unchanged");
        assertEquals(new Rectangle(105, 0, 95, 100), b.getBounds());

        f.layout(300, 100);
        // 100 extra pixels: a gets a quarter, the rest ends with b.
        assertEquals(new Rectangle(0, 0, 120, 100), a.getBounds());
        assertEquals(new Rectangle(120, 0, 10, 100), d.getBounds());
        assertEquals(new Rectangle(130, 0, 170, 100), b.getBounds());
    }


    @Test
    void rowShrinksWeightedChildrenFirst()
    {
        Divider d = divider(95, 0, 10, 100);
        Split root = new Split(null, true, 0.0, new Leaf("a", 0.5), d, new Leaf("b", 0.5));
        root.setBounds(new Rectangle(0, 0, 200, 100));
        Fixture f = new Fixture(root, 10);
        JPanel a = f.add("a");
        JPanel b = f.add("b");
        f.layout(200, 100);

        f.layout(150, 100);
        assertEquals(new Rectangle(0, 0, 70, 100), a.getBounds());
        assertEquals(new Rectangle(70, 0, 10, 100), d.getBounds(), "divider keeps its width");
        assertEquals(new Rectangle(80, 0, 70, 100), b.getBounds());
        assertEquals(new Rectangle(0, 0, 150, 100), root.getBounds());
    }


    @Test
    void rowNeverShrinksBelowTheMinimumSizes()
    {
        Divider d = divider(95, 0, 10, 100);
        Split root = new Split(null, true, 0.0, new Leaf("a", 0.5), d, new Leaf("b", 0.5));
        root.setBounds(new Rectangle(0, 0, 200, 100));
        Fixture f = new Fixture(root, 10);
        JPanel a = f.add("a");
        JPanel b = f.add("b");
        f.layout(200, 100);

        f.layout(30, 100);
        // minimum 20 + divider 10 + minimum 20: the split overflows the 30 px container.
        assertEquals(new Rectangle(0, 0, 20, 100), a.getBounds());
        assertEquals(new Rectangle(20, 0, 10, 100), d.getBounds());
        assertEquals(new Rectangle(30, 0, 20, 100), b.getBounds());
        assertEquals(new Rectangle(0, 0, 50, 100), root.getBounds());
    }

    // ---------------------------------------------------------------- column layouts


    @Test
    void columnWithoutWeightsGivesAllExtraSpaceToTheLastChild()
    {
        Divider d = divider(0, 95, 100, 10);
        Fixture f = new Fixture(new Split(null, false, 0.0, new Leaf("a"), d, new Leaf("b")), 10);
        JPanel a = f.add("a");
        JPanel b = f.add("b");

        f.layout(100, 200);
        assertEquals(new Rectangle(0, 0, 100, 95), a.getBounds());
        assertEquals(new Rectangle(0, 105, 100, 95), b.getBounds());

        f.layout(100, 260);
        assertEquals(new Rectangle(0, 0, 100, 95), a.getBounds());
        assertEquals(new Rectangle(0, 105, 100, 155), b.getBounds());
    }


    @Test
    void columnGrowsWeightedChildrenInProportion()
    {
        Divider d = divider(0, 95, 100, 10);
        Split root = new Split(null, false, 0.0, new Leaf("a", 0.25), d, new Leaf("b", 0.75));
        root.setBounds(new Rectangle(0, 0, 100, 200));
        Fixture f = new Fixture(root, 10);
        JPanel a = f.add("a");
        JPanel b = f.add("b");
        f.layout(100, 200);

        f.layout(100, 300);
        assertEquals(new Rectangle(0, 0, 100, 120), a.getBounds());
        assertEquals(new Rectangle(0, 120, 100, 10), d.getBounds());
        assertEquals(new Rectangle(0, 130, 100, 170), b.getBounds());
    }


    @Test
    void columnShrinksWeightedChildrenFirst()
    {
        Divider d = divider(0, 95, 100, 10);
        Split root = new Split(null, false, 0.0, new Leaf("a", 0.5), d, new Leaf("b", 0.5));
        root.setBounds(new Rectangle(0, 0, 100, 200));
        Fixture f = new Fixture(root, 10);
        JPanel a = f.add("a");
        JPanel b = f.add("b");
        f.layout(100, 200);

        f.layout(100, 150);
        assertEquals(new Rectangle(0, 0, 100, 70), a.getBounds());
        assertEquals(new Rectangle(0, 70, 100, 10), d.getBounds());
        assertEquals(new Rectangle(0, 80, 100, 70), b.getBounds());
    }


    @Test
    void columnNeverShrinksBelowTheMinimumSizes()
    {
        Divider d = divider(0, 95, 100, 10);
        Split root = new Split(null, false, 0.0, new Leaf("a", 0.5), d, new Leaf("b", 0.5));
        root.setBounds(new Rectangle(0, 0, 100, 200));
        Fixture f = new Fixture(root, 10);
        JPanel a = f.add("a");
        JPanel b = f.add("b");
        f.layout(100, 200);

        f.layout(100, 30);
        assertEquals(new Rectangle(0, 0, 100, 20), a.getBounds());
        assertEquals(new Rectangle(0, 20, 100, 10), d.getBounds());
        assertEquals(new Rectangle(0, 30, 100, 20), b.getBounds());
        assertEquals(new Rectangle(0, 0, 100, 50), root.getBounds());
    }


    @Test
    void singleLeafModelFillsTheContainer()
    {
        MultiSplitLayout layout = new MultiSplitLayout();
        JPanel host = new JPanel(layout);
        JPanel c = comp();
        host.add(c, "default");
        layout(layout, host, 123, 45);
        assertEquals(new Rectangle(0, 0, 123, 45), c.getBounds());
        assertEquals(new Rectangle(0, 0, 123, 45), layout.getModel().getBounds());
    }

    // ---------------------------------------------------------------- sizes


    private static Fixture nestedForSizes()
    {
        Split column = new Split(null, false, 0.0, new Leaf("b"), new Divider(), new Leaf("c"));
        Split root = new Split(null, true, 0.0, new Leaf("a"), new Divider(), column);
        Fixture f = new Fixture(root, 5);
        f.host.add(comp(50, 30, 10, 15), "a");
        f.host.add(comp(40, 20, 12, 8), "b");
        f.host.add(comp(60, 25, 14, 9), "c");
        f.host.setBorder(BorderFactory.createEmptyBorder(1, 2, 3, 4));
        return f;
    }


    @Test
    void preferredSizeSumsAlongTheAxisAndMaximisesAcrossIt()
    {
        Fixture f = nestedForSizes();
        // column: max(40, 5, 60) x (20 + 5 + 25); row: 50 + 5 + 60 x max(30, 5, 50); + insets
        assertEquals(new Dimension(115 + 6, 50 + 4), f.layout.preferredLayoutSize(f.host));
    }


    @Test
    void minimumSizeSumsAlongTheAxisAndMaximisesAcrossIt()
    {
        Fixture f = nestedForSizes();
        // column: max(12, 5, 14) x (8 + 5 + 9); row: 10 + 5 + 14 x max(15, 5, 22); + insets
        assertEquals(new Dimension(29 + 6, 22 + 4), f.layout.minimumLayoutSize(f.host));
    }


    @Test
    void leavesWithoutComponentsContributeNothingButDividersDo()
    {
        Fixture f = new Fixture(
                new Split(null, true, 0.0, new Leaf("a"), new Divider(), new Leaf("b")), 6);
        assertEquals(new Dimension(6, 6), f.layout.preferredLayoutSize(f.host));
        assertEquals(new Dimension(6, 6), f.layout.minimumLayoutSize(f.host));

        MultiSplitLayout single = new MultiSplitLayout();
        assertEquals(new Dimension(0, 0), single.preferredLayoutSize(new JPanel()));
    }

    // ---------------------------------------------------------------- model validation


    @Test
    void aSplitNeedsMoreThanTwoChildren()
    {
        Split root = new Split(null, true, 0.0, new Leaf("a"), new Divider());
        Fixture f = new Fixture(root, 5);
        InvalidLayoutException e = assertThrows(InvalidLayoutException.class,
                () -> f.layout(100, 100));
        assertEquals("Split must have > 2 children", e.getMessage());
        assertSame(root, e.getNode());
    }


    @Test
    void aSplitMustNotStartWithADivider()
    {
        Divider first = new Divider();
        Fixture f = new Fixture(
                new Split(null, true, 0.0, first, new Leaf("a"), new Divider(), new Leaf("b")), 5);
        InvalidLayoutException e = assertThrows(InvalidLayoutException.class,
                () -> f.layout(100, 100));
        assertEquals("expected a Split or Leaf Node", e.getMessage());
        assertSame(first, e.getNode());
    }


    @Test
    void siblingsMustBeSeparatedByDividers()
    {
        Leaf b = new Leaf("b");
        Fixture f = new Fixture(new Split(null, true, 0.0, new Leaf("a"), b, new Leaf("c")), 5);
        InvalidLayoutException e = assertThrows(InvalidLayoutException.class,
                () -> f.layout(100, 100));
        assertEquals("expected a Divider Node", e.getMessage());
        assertSame(b, e.getNode());
    }


    @Test
    void siblingWeightsMustNotExceedOne()
    {
        Split root = new Split(null, true, 0.0, new Leaf("a", 0.6), new Divider(),
                new Leaf("b", 0.6));
        Fixture f = new Fixture(root, 5);
        InvalidLayoutException e = assertThrows(InvalidLayoutException.class,
                () -> f.layout(100, 100));
        assertEquals("Split children's total weight > 1.0", e.getMessage());
        assertSame(root, e.getNode());
    }


    @Test
    void nestedSplitsAreValidatedToo()
    {
        Split inner = new Split(null, false, 0.0, new Leaf("b"), new Divider());
        Fixture f = new Fixture(new Split(null, true, 0.0, new Leaf("a"), new Divider(), inner), 5);
        InvalidLayoutException e = assertThrows(InvalidLayoutException.class,
                () -> f.layout(100, 100));
        assertSame(inner, e.getNode());
    }

    // ---------------------------------------------------------------- splitLeaf


    private static Fixture singleLeaf(String name)
    {
        Fixture f = new Fixture(new Leaf(name), 5);
        f.add(name);
        f.layout(205, 105);
        return f;
    }


    @Test
    void splitRootLeafIntoARowWithTheNewLeafRight()
    {
        Fixture f = singleLeaf("a");
        Leaf a = f.layout.getLeafForName("a");

        Leaf b = f.layout.splitLeaf("a", "b", true, false);
        Split root = assertInstanceOf(Split.class, f.layout.getModel());
        assertTrue(root.isRowLayout());
        assertEquals(3, root.getChildren().size());
        assertSame(a, root.getChildren().get(0));
        Divider d = assertInstanceOf(Divider.class, root.getChildren().get(1));
        assertSame(b, root.getChildren().get(2));
        assertEquals("b", b.getName());
        assertSame(root, a.getParent());

        assertEquals(new Rectangle(0, 0, 100, 105), a.getBounds());
        assertEquals(new Rectangle(100, 0, 5, 105), d.getBounds());
        assertEquals(new Rectangle(105, 0, 100, 105), b.getBounds());

        JPanel cb = f.add("b");
        f.layout(205, 105);
        assertEquals(new Rectangle(0, 0, 100, 105),
                f.layout.getComponentForLeafName("a").getBounds());
        assertEquals(new Rectangle(105, 0, 100, 105), cb.getBounds());
    }


    @Test
    void splitRootLeafIntoAColumnWithTheNewLeafOnTop()
    {
        Fixture f = singleLeaf("a");
        Leaf a = f.layout.getLeafForName("a");

        Leaf n = f.layout.splitLeaf("a", "n", false, true);
        Split root = assertInstanceOf(Split.class, f.layout.getModel());
        assertFalse(root.isRowLayout());
        assertSame(n, root.getChildren().get(0));
        Divider d = assertInstanceOf(Divider.class, root.getChildren().get(1));
        assertSame(a, root.getChildren().get(2));
        assertFalse(d.isVertical());

        assertEquals(new Rectangle(0, 0, 205, 50), n.getBounds());
        assertEquals(new Rectangle(0, 50, 205, 5), d.getBounds());
        assertEquals(new Rectangle(0, 55, 205, 50), a.getBounds());
    }


    @Test
    void splitRootLeafIntoARowWithTheNewLeafLeft()
    {
        Fixture f = singleLeaf("a");
        Leaf a = f.layout.getLeafForName("a");

        Leaf n = f.layout.splitLeaf("a", "n", true, true);
        Split root = (Split) f.layout.getModel();
        assertEquals(List.of(n, root.getChildren().get(1), a), root.getChildren());
        assertEquals(new Rectangle(0, 0, 100, 105), n.getBounds());
        assertEquals(new Rectangle(105, 0, 100, 105), a.getBounds());
    }


    @Test
    void splitRootLeafIntoAColumnWithTheNewLeafBelow()
    {
        Fixture f = singleLeaf("a");
        Leaf a = f.layout.getLeafForName("a");

        Leaf n = f.layout.splitLeaf("a", "n", false, false);
        Split root = (Split) f.layout.getModel();
        assertEquals(List.of(a, root.getChildren().get(1), n), root.getChildren());
        assertEquals(new Rectangle(0, 0, 205, 50), a.getBounds());
        assertEquals(new Rectangle(0, 55, 205, 50), n.getBounds());
    }


    /**
     * Row [a | b] split again at b, column-wise: b's slot becomes a nested column [b / c].
     */
    private static Fixture nestedThreeLeaves()
    {
        Fixture f = singleLeaf("a");
        f.layout.splitLeaf("a", "b", true, false);
        f.add("b");
        f.layout(205, 105);
        f.layout.splitLeaf("b", "c", false, false);
        f.add("c");
        f.layout(205, 105);
        return f;
    }


    @Test
    void splittingAcrossTheParentsOrientationNestsANewSplit()
    {
        Fixture f = nestedThreeLeaves();
        Split root = (Split) f.layout.getModel();
        assertEquals(3, root.getChildren().size());
        Split column = assertInstanceOf(Split.class, root.getChildren().get(2));
        assertFalse(column.isRowLayout());
        assertSame(root, column.getParent());
        assertSame(f.layout.getLeafForName("b"), column.getChildren().get(0));
        assertSame(f.layout.getLeafForName("c"), column.getChildren().get(2));

        assertEquals(new Rectangle(0, 0, 100, 105),
                f.layout.getComponentForLeafName("a").getBounds());
        assertEquals(new Rectangle(105, 0, 100, 50),
                f.layout.getComponentForLeafName("b").getBounds());
        assertEquals(new Rectangle(105, 55, 100, 50),
                f.layout.getComponentForLeafName("c").getBounds());
        assertEquals(new Rectangle(105, 0, 100, 105), column.getBounds());
        assertEquals(Set.of("a", "b", "c"), Set.copyOf(f.layout.getLeafNames()));
    }


    @Test
    void splittingAcrossTheParentsOrientationWithTheNewLeafFirst()
    {
        Fixture f = singleLeaf("a");
        f.layout.splitLeaf("a", "b", true, false);
        Leaf b = f.layout.getLeafForName("b");

        Leaf n = f.layout.splitLeaf("b", "n", false, true);
        Split root = (Split) f.layout.getModel();
        Split column = assertInstanceOf(Split.class, root.getChildren().get(2));
        assertEquals(List.of(n, column.getChildren().get(1), b), column.getChildren());
        assertEquals(new Rectangle(105, 0, 100, 50), n.getBounds());
        assertEquals(new Rectangle(105, 55, 100, 50), b.getBounds());
    }


    @Test
    void splittingAlongTheParentsOrientationExtendsTheParent()
    {
        Fixture f = singleLeaf("a");
        f.layout.splitLeaf("a", "b", true, false);
        f.add("b");
        f.layout(205, 105);

        Leaf c = f.layout.splitLeaf("b", "c", true, false);
        Split root = (Split) f.layout.getModel();
        assertEquals(5, root.getChildren().size());
        assertSame(f.layout.getLeafForName("b"), root.getChildren().get(2));
        assertInstanceOf(Divider.class, root.getChildren().get(3));
        assertSame(c, root.getChildren().get(4));
        assertSame(root, c.getParent());

        JPanel cc = f.add("c");
        f.layout(205, 105);
        assertEquals(new Rectangle(105, 0, 47, 105),
                f.layout.getComponentForLeafName("b").getBounds());
        // the odd pixel of (100 - 5) / 2 goes to the last child
        assertEquals(new Rectangle(157, 0, 48, 105), cc.getBounds());
    }


    @Test
    void splittingAWeightedLeafAlongTheParentHalvesTheWeight()
    {
        Split root = new Split(null, true, 0.0, new Leaf("a", 0.4), new Divider(),
                new Leaf("b", 0.6));
        MultiSplitLayout layout = new MultiSplitLayout(root);

        Leaf c = layout.splitLeaf("a", "c", true, false);
        assertEquals(0.2, layout.getLeafForName("a").getWeight(), 1e-9);
        assertEquals(0.2, c.getWeight(), 1e-9);
        assertEquals(0.6, layout.getLeafForName("b").getWeight(), 1e-9);
    }


    /**
     * Splitting the unweighted ROOT leaf creates a new Split, and inside it the two halves are
     * peers: 0.5 / 0.5. (This test pinned 0 / 0 until review round 2 of PLAN-swingx-msp-extraction,
     * where all growth then went to the second half.)
     */
    @Test
    void splittingTheUnweightedRootLeafMakesTwoEqualPeers()
    {
        MultiSplitLayout layout = new MultiSplitLayout(new Leaf("a"));
        Leaf b = layout.splitLeaf("a", "b", true, false);
        assertEquals(0.5, layout.getLeafForName("a").getWeight());
        assertEquals(0.5, b.getWeight());
    }


    /** A same-orientation split halves the leaf's weight between siblings: 0 stays 0 / 0. */
    @Test
    void splittingAnUnweightedLeafInItsOwnOrientationKeepsBothUnweighted()
    {
        Split root = new Split(null, true, 0.0, new Leaf("a", 0.0), new Divider(),
                new Leaf("c", 1.0));
        MultiSplitLayout layout = new MultiSplitLayout(root);
        Leaf b = layout.splitLeaf("a", "b", true, false);
        assertEquals(0.0, layout.getLeafForName("a").getWeight());
        assertEquals(0.0, b.getWeight());
        assertEquals(1.0, layout.getLeafForName("c").getWeight());
    }


    @Test
    void splitLeafRejectsATakenNameAndIgnoresAnUnknownLeaf()
    {
        MultiSplitLayout layout = new MultiSplitLayout(new Leaf("a"));
        Node model = layout.getModel();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> layout.splitLeaf("a", "a", true, false));
        assertEquals("A leaf with the name a does already exist.", e.getMessage());
        assertNull(layout.splitLeaf("missing", "b", true, false));
        assertSame(model, layout.getModel());
    }

    // ---------------------------------------------------------------- removeLeaf


    /**
     * Row [a(.2) | b(.3) | c(.5)] at 200 x 100 with dividers at x=60 and x=130.
     */
    private static Fixture threeInARow(Divider d1, Divider d2)
    {
        Split root = new Split(null, true, 0.0, new Leaf("a", 0.2), d1, new Leaf("b", 0.3), d2,
                new Leaf("c", 0.5));
        root.setBounds(new Rectangle(0, 0, 200, 100));
        Fixture f = new Fixture(root, 5);
        f.add("a");
        f.add("b");
        f.add("c");
        f.layout(200, 100);
        return f;
    }


    @Test
    void threeInARowLaysOutBetweenTheDividers()
    {
        Fixture f = threeInARow(divider(60, 0, 5, 100), divider(130, 0, 5, 100));
        assertEquals(new Rectangle(0, 0, 60, 100),
                f.layout.getComponentForLeafName("a").getBounds());
        assertEquals(new Rectangle(65, 0, 65, 100),
                f.layout.getComponentForLeafName("b").getBounds());
        assertEquals(new Rectangle(135, 0, 65, 100),
                f.layout.getComponentForLeafName("c").getBounds());
    }


    @Test
    void removingAMiddleLeafSplitsItsWeightAndLetsTheLeftNeighbourTakeTheSpace()
    {
        Divider d1 = divider(60, 0, 5, 100);
        Divider d2 = divider(130, 0, 5, 100);
        Fixture f = threeInARow(d1, d2);
        Leaf a = f.layout.getLeafForName("a");
        Leaf b = f.layout.getLeafForName("b");
        Leaf c = f.layout.getLeafForName("c");

        assertTrue(f.layout.removeLeaf("b"));
        Split root = (Split) f.layout.getModel();
        assertEquals(List.of(a, d2, c), root.getChildren());
        assertNull(b.getParent());
        assertNull(f.layout.getLeafForName("b"));
        assertEquals(0.35, a.getWeight(), 1e-9);
        assertEquals(0.65, c.getWeight(), 1e-9);
        // removeLeaf re-lays the parent itself
        assertEquals(new Rectangle(0, 0, 130, 100),
                f.layout.getComponentForLeafName("a").getBounds());
        assertEquals(new Rectangle(135, 0, 65, 100),
                f.layout.getComponentForLeafName("c").getBounds());
    }


    @Test
    void removingTheFirstLeafGivesItsWeightAndSpaceToTheNext()
    {
        Divider d1 = divider(60, 0, 5, 100);
        Divider d2 = divider(130, 0, 5, 100);
        Fixture f = threeInARow(d1, d2);

        assertTrue(f.layout.removeLeaf("a"));
        Split root = (Split) f.layout.getModel();
        assertEquals(List.of(f.layout.getLeafForName("b"), d2, f.layout.getLeafForName("c")),
                root.getChildren());
        assertEquals(0.5, f.layout.getLeafForName("b").getWeight(), 1e-9);
        assertEquals(new Rectangle(0, 0, 130, 100),
                f.layout.getComponentForLeafName("b").getBounds());
    }


    @Test
    void removingTheLastLeafGivesItsWeightAndSpaceToThePrevious()
    {
        Divider d1 = divider(60, 0, 5, 100);
        Divider d2 = divider(130, 0, 5, 100);
        Fixture f = threeInARow(d1, d2);

        assertTrue(f.layout.removeLeaf("c"));
        Split root = (Split) f.layout.getModel();
        assertEquals(List.of(f.layout.getLeafForName("a"), d1, f.layout.getLeafForName("b")),
                root.getChildren());
        assertEquals(0.8, f.layout.getLeafForName("b").getWeight(), 1e-9);
        assertEquals(0.2, f.layout.getLeafForName("a").getWeight(), 1e-9);
        assertEquals(new Rectangle(65, 0, 135, 100),
                f.layout.getComponentForLeafName("b").getBounds());
    }


    @Test
    void removingAWeightedLeafBetweenUnweightedNeighboursDropsTheWeight()
    {
        Split root = new Split(null, true, 0.0, new Leaf("a"), divider(60, 0, 5, 100),
                new Leaf("b", 0.5), divider(130, 0, 5, 100), new Leaf("c"));
        root.setBounds(new Rectangle(0, 0, 200, 100));
        MultiSplitLayout layout = new MultiSplitLayout(root);

        assertTrue(layout.removeLeaf("b"));
        assertEquals(0.0, layout.getLeafForName("a").getWeight());
        assertEquals(0.0, layout.getLeafForName("c").getWeight());
    }


    @Test
    void removingOneOfTwoNestedLeavesCollapsesTheNestedSplit()
    {
        Fixture f = nestedThreeLeaves();
        Split root = (Split) f.layout.getModel();
        Split column = (Split) root.getChildren().get(2);
        root.getChildren().get(0).setWeight(0.4);
        column.setWeight(0.6);
        Leaf b = f.layout.getLeafForName("b");

        assertTrue(f.layout.removeLeaf("c"));
        assertSame(b, root.getChildren().get(2), "b replaces the column in the root");
        assertSame(root, b.getParent());
        assertNull(column.getParent());
        assertEquals(0.6, b.getWeight(), 1e-9, "b inherits the column's weight");
        assertEquals(new Rectangle(105, 0, 100, 105),
                f.layout.getComponentForLeafName("b").getBounds());
    }


    @Test
    void theLastDividerOfTheRootCannotBeRemoved()
    {
        Fixture f = singleLeaf("a");
        f.layout.splitLeaf("a", "b", true, false);
        Node model = f.layout.getModel();

        assertFalse(f.layout.removeLeaf("b"));
        assertSame(model, f.layout.getModel());
        assertEquals(3, ((Split) model).getChildren().size());
    }


    @Test
    void removeLeafRefusesUnknownRootAndUnderpopulatedSplits()
    {
        MultiSplitLayout single = new MultiSplitLayout(new Leaf("a"));
        assertFalse(single.removeLeaf("missing"));
        assertFalse(single.removeLeaf("a"), "the root leaf has no parent");

        Split twoChildren = new Split(null, true, 0.0, new Leaf("a"), new Leaf("b"));
        MultiSplitLayout broken = new MultiSplitLayout(twoChildren);
        assertFalse(broken.removeLeaf("a"));
        assertEquals(2, twoChildren.getChildren().size());
    }

    // ---------------------------------------------------------------- names and components


    @Test
    void leafLookupSearchesNestedSplits()
    {
        Fixture f = nestedThreeLeaves();
        Leaf c = f.layout.getLeafForName("c");
        assertEquals("c", c.getName());
        assertInstanceOf(Split.class, c.getParent());
        assertNull(f.layout.getLeafForName("zzz"));

        MultiSplitLayout single = new MultiSplitLayout(new Leaf("x"));
        assertSame(single.getModel(), single.getLeafForName("x"));
        assertNull(single.getLeafForName("y"));
    }


    @Test
    void aForeignNodeTypeAsModelHasNoLeaves()
    {
        Node custom = new Node()
        {
        };
        MultiSplitLayout layout = new MultiSplitLayout(custom);
        assertNull(layout.getLeafForName("default"));
        assertTrue(layout.getLeafNames().isEmpty());
    }


    @Test
    void componentsAreFoundByLeafNameAndBack()
    {
        MultiSplitLayout layout = new MultiSplitLayout();
        Component c = comp();
        layout.addLayoutComponent("default", c);
        assertSame(c, layout.getComponentForLeafName("default"));
        assertEquals("default", layout.getLeafNameForComponent(c));
        assertNull(layout.getLeafNameForComponent(comp()));
        assertNull(layout.getComponentForLeafName("other"));
        assertThrows(IllegalArgumentException.class, () -> layout.addLayoutComponent(null, c));
    }


    @Test
    void removeLayoutComponentForgetsOnlyThatComponent()
    {
        MultiSplitLayout layout = new MultiSplitLayout();
        Component c = comp();
        layout.addLayoutComponent("default", c);

        Component namedLikeIt = comp();
        namedLikeIt.setName("default");
        layout.removeLayoutComponent(namedLikeIt);
        assertSame(c, layout.getComponentForLeafName("default"),
                "a different component with a matching name must not evict the real one");

        layout.removeLayoutComponent(comp());
        assertSame(c, layout.getComponentForLeafName("default"));

        layout.removeLayoutComponent(c);
        assertNull(layout.getComponentForLeafName("default"));
    }


    @Test
    void removeLayoutComponentFallsBackToTheComponentName()
    {
        MultiSplitLayout layout = new MultiSplitLayout();
        Component c = comp();
        c.setName("x");
        layout.addLayoutComponent("x", c);
        layout.removeLayoutComponent(c);
        assertNull(layout.getComponentForLeafName("x"));
    }


    @Test
    void renameLeafMovesTheNameAndTheComponent()
    {
        Fixture f = singleLeaf("a");
        Leaf leaf = f.layout.getLeafForName("a");
        Component c = f.layout.getComponentForLeafName("a");

        assertTrue(f.layout.renameLeaf("a", "z"));
        assertSame(leaf, f.layout.getLeafForName("z"));
        assertEquals("z", leaf.getName());
        assertNull(f.layout.getLeafForName("a"));
        assertSame(c, f.layout.getComponentForLeafName("z"));
        assertNull(f.layout.getComponentForLeafName("a"));
    }


    @Test
    void renameLeafRefusesTakenUnknownAndComponentlessLeaves()
    {
        Fixture f = singleLeaf("a");
        f.layout.splitLeaf("a", "b", true, false);

        assertFalse(f.layout.renameLeaf("a", "b"), "target name in use");
        assertFalse(f.layout.renameLeaf("missing", "c"), "no such leaf");
        assertFalse(f.layout.renameLeaf("b", "c"), "leaf b has no component");
        assertEquals("b", f.layout.getLeafForName("b").getName());
        assertNull(f.layout.getLeafForName("c"));
    }

    // ---------------------------------------------------------------- hit testing


    @Test
    void dividerAtFindsNestedDividersAndNothingElse()
    {
        Fixture f = nestedThreeLeaves();
        Split root = (Split) f.layout.getModel();
        Divider vertical = (Divider) root.getChildren().get(1);
        Divider horizontal = (Divider) ((Split) root.getChildren().get(2)).getChildren().get(1);

        assertSame(vertical, f.layout.dividerAt(102, 10));
        assertTrue(vertical.isVertical());
        assertSame(horizontal, f.layout.dividerAt(150, 52));
        assertFalse(horizontal.isVertical());
        assertNull(f.layout.dividerAt(10, 10), "inside leaf a");
        assertNull(f.layout.dividerAt(150, 10), "inside leaf b");
        assertNull(f.layout.dividerAt(500, 500), "outside the model");
    }


    @Test
    void dividersThatOverlapCollectsNestedDividersInTreeOrder()
    {
        Fixture f = nestedThreeLeaves();
        Split root = (Split) f.layout.getModel();
        Divider vertical = (Divider) root.getChildren().get(1);
        Divider horizontal = (Divider) ((Split) root.getChildren().get(2)).getChildren().get(1);

        assertEquals(List.of(vertical, horizontal),
                f.layout.dividersThatOverlap(new Rectangle(0, 0, 205, 105)));
        assertEquals(List.of(horizontal),
                f.layout.dividersThatOverlap(new Rectangle(150, 52, 1, 1)));
        assertEquals(List.of(vertical), f.layout.dividersThatOverlap(new Rectangle(101, 5, 1, 1)));
        assertEquals(List.of(), f.layout.dividersThatOverlap(new Rectangle(0, 0, 10, 10)));
        assertEquals(List.of(), f.layout.dividersThatOverlap(new Rectangle(500, 500, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> f.layout.dividersThatOverlap(null));
    }


    @Test
    void aLeafModelHasNoDividers()
    {
        Fixture f = singleLeaf("a");
        assertEquals(List.of(), f.layout.dividersThatOverlap(new Rectangle(0, 0, 205, 105)));
        assertNull(f.layout.dividerAt(1, 1));
    }
}
