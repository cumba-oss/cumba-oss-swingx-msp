package net.cumba.swingx.msp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import net.cumba.swingx.msp.MultiSplitLayout.Divider;
import net.cumba.swingx.msp.MultiSplitLayout.Leaf;
import net.cumba.swingx.msp.MultiSplitLayout.Node;
import net.cumba.swingx.msp.MultiSplitLayout.Split;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the four defects the phase-3 test campaign found in {@link MultiSplitLayout}
 * (databrowser-meta {@code PLAN-swingx-msp-extraction}, 2026-09-24). Each test failed before its
 * fix.
 */
class MultiSplitLayoutRegressionTest
{

    private static JPanel comp()
    {
        JPanel p = new JPanel();
        p.setPreferredSize(new Dimension(10, 10));
        p.setMinimumSize(new Dimension(20, 20));
        return p;
    }


    /** {@code setModel} passed the NEW model as the old value, so no event was ever delivered. */
    @Test
    void setModelFiresTheModelPropertyChangeWithTheRealOldValue()
    {
        Leaf first = new Leaf("first");
        MultiSplitLayout layout = new MultiSplitLayout(first);
        List<PropertyChangeEvent> events = new ArrayList<>();
        layout.addPropertyChangeListener(events::add);

        Leaf second = new Leaf("second");
        layout.setModel(second);

        assertEquals(1, events.size(), "exactly one model event");
        PropertyChangeEvent e = events.get(0);
        assertEquals("model", e.getPropertyName());
        assertSame(first, e.getOldValue());
        assertSame(second, e.getNewValue());
    }


    /**
     * A same-orientation split with the new leaf top/left inserted the divider BEFORE the new leaf
     * ({@code [div, new, old]}), so two dividers became adjacent and the next layout threw
     * {@code InvalidLayoutException}.
     */
    @Test
    void sameOrientationSplitWithTheNewLeafLeftKeepsLeavesAndDividersAlternating()
    {
        MultiSplitLayout layout = new MultiSplitLayout(new Leaf("a"));
        layout.setDividerSize(5);
        JPanel host = new JPanel(layout);
        host.add(comp(), "a");
        host.setSize(305, 100);
        layout.layoutContainer(host);

        layout.splitLeaf("a", "b", true, false);
        host.add(comp(), "b");
        Leaf n = layout.splitLeaf("b", "n", true, true);
        host.add(comp(), "n");

        Split root = assertInstanceOf(Split.class, layout.getModel());
        List<Node> children = root.getChildren();
        assertEquals(5, children.size());
        assertSame(layout.getLeafForName("a"), children.get(0));
        assertInstanceOf(Divider.class, children.get(1));
        assertSame(n, children.get(2), "the new leaf sits LEFT of the leaf it was split from");
        assertInstanceOf(Divider.class, children.get(3));
        assertSame(layout.getLeafForName("b"), children.get(4));

        assertDoesNotThrow(() -> layout.layoutContainer(host));
    }


    /**
     * Growing a column computed the extra height from the bottom EDGE of the available bounds
     * instead of their height, so a top inset was handed out as extra space. The row branch, and
     * upstream SwingX, use the size.
     */
    @Test
    void columnGrowthIgnoresTheTopInsetLikeTheRowIgnoresTheLeftInset()
    {
        Split root = new Split(null, false, 0.0, new Leaf("a", 0.5), new Divider(),
                new Leaf("b", 0.5));
        MultiSplitLayout layout = new MultiSplitLayout(root);
        layout.setDividerSize(10);
        JPanel host = new JPanel(layout);
        host.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
        JPanel a = comp();
        JPanel b = comp();
        host.add(a, "a");
        host.add(b, "b");

        host.setSize(100, 220); // 200 px of content below the 20 px inset
        layout.layoutContainer(host);
        int a0 = a.getHeight();
        int b0 = b.getHeight();

        host.setSize(100, 320); // 100 px more content, to be shared 50 / 50
        layout.layoutContainer(host);
        assertEquals(a0 + 50, a.getHeight(), "a gets half of the 100 extra pixels");
        assertEquals(b0 + 50, b.getHeight(), "b gets the other half");
        assertEquals(20, a.getY(), "content still starts below the inset");
        assertEquals(320, b.getY() + b.getHeight(), "and ends at the bottom edge");
    }


    /**
     * A cross-orientation split halved the leaf's weight BEFORE the nested Split copied it, so the
     * parent level lost half of that leaf's share of the extra space.
     */
    @Test
    void crossOrientationSplitHandsTheLeafsFullWeightToTheNestedSplit()
    {
        Split root = new Split(null, true, 0.0, new Leaf("a", 0.5), new Divider(),
                new Leaf("b", 0.5));
        root.setBounds(new Rectangle(0, 0, 205, 100));
        MultiSplitLayout layout = new MultiSplitLayout(root);

        layout.splitLeaf("a", "n", false, false);

        Split nested = assertInstanceOf(Split.class, root.getChildren().get(0));
        assertEquals(0.5, nested.getWeight(), 0.0,
                "the nested split keeps a's parent-level weight");
        assertEquals(1.0, nested.getWeight() + root.getChildren().get(2).getWeight(), 0.0,
                "the parent level's weights still add up");
    }
}
