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
 * (databrowser-meta {@code PLAN-swingx-msp-extraction}, 2026-09-24) and in review round 1 of that
 * plan. Each test failed before its fix.
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


    private static Divider divider(int x, int y, int w, int h)
    {
        Divider d = new Divider();
        d.setBounds(new Rectangle(x, y, w, h));
        return d;
    }


    /**
     * Growing a column computed the extra height from the bottom EDGE of the available bounds
     * instead of their height, so any column not at y = 0 -- below a top inset, or nested below a
     * sibling -- handed its own y offset out as extra space. The row branch, and upstream SwingX,
     * use the size.
     */
    @Test
    void columnGrowthIgnoresTheTopInsetLikeTheRowIgnoresTheLeftInset()
    {
        // settled model: 200 px of content below a 20 px inset, a 95 | 10 | 95 column
        Divider d = divider(0, 115, 100, 10);
        Split root = new Split(null, false, 0.0, new Leaf("a", 0.5), d, new Leaf("b", 0.5));
        root.setBounds(new Rectangle(0, 20, 100, 200));
        MultiSplitLayout layout = new MultiSplitLayout(root);
        layout.setDividerSize(10);
        JPanel host = new JPanel(layout);
        host.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
        JPanel a = comp();
        JPanel b = comp();
        host.add(a, "a");
        host.add(b, "b");
        host.setSize(100, 220);
        layout.layoutContainer(host);
        assertEquals(new Rectangle(0, 20, 100, 95), a.getBounds(), "settled before growing");

        host.setSize(100, 320); // 100 px more content, shared 50 / 50
        layout.layoutContainer(host);
        assertEquals(new Rectangle(0, 20, 100, 145), a.getBounds());
        assertEquals(new Rectangle(0, 165, 100, 10), d.getBounds());
        assertEquals(new Rectangle(0, 175, 100, 145), b.getBounds());
    }


    /**
     * The same defect without any inset: a column NESTED below a sibling starts at y > 0. This is
     * the shape the data browser's region layout builds (binary splits, a column inside a column),
     * and the one its removeRegion path re-lays out.
     */
    @Test
    void aNestedColumnBelowItsSiblingGrowsByItsOwnShareOnly()
    {
        Leaf mid = new Leaf("mid", 0.5);
        Leaf bot = new Leaf("bot", 0.5);
        Divider innerDiv = divider(0, 145, 100, 10);
        Split inner = new Split(null, false, 0.5, mid, innerDiv, bot);
        inner.setBounds(new Rectangle(0, 105, 100, 95));
        mid.setBounds(new Rectangle(0, 105, 100, 40));
        bot.setBounds(new Rectangle(0, 155, 100, 45));
        Leaf top = new Leaf("top", 0.5);
        top.setBounds(new Rectangle(0, 0, 100, 95));
        Split root = new Split(null, false, 0.0, top, divider(0, 95, 100, 10), inner);
        root.setBounds(new Rectangle(0, 0, 100, 200));
        MultiSplitLayout layout = new MultiSplitLayout(root);
        layout.setDividerSize(10);
        JPanel host = new JPanel(layout);
        JPanel cTop = comp();
        JPanel cMid = comp();
        JPanel cBot = comp();
        host.add(cTop, "top");
        host.add(cMid, "mid");
        host.add(cBot, "bot");
        host.setSize(100, 200);
        layout.layoutContainer(host);
        assertEquals(new Rectangle(0, 105, 100, 40), cMid.getBounds(), "settled before growing");

        // +100 px: top and the inner column take 50 each; inside the inner column (now at
        // y = 155) its 50 px are shared 25 to mid, the rest to the last child.
        host.setSize(100, 300);
        layout.layoutContainer(host);
        assertEquals(new Rectangle(0, 0, 100, 145), cTop.getBounds());
        assertEquals(new Rectangle(0, 155, 100, 65), cMid.getBounds());
        assertEquals(new Rectangle(0, 220, 100, 10), innerDiv.getBounds());
        assertEquals(new Rectangle(0, 230, 100, 70), cBot.getBounds());
    }


    /**
     * A cross-orientation split halved the leaf's weight BEFORE the nested Split copied it, so the
     * parent level lost half of that leaf's share of the extra space. Inside the new Split the two
     * leaves share it 50 / 50 -- weights are relative to their own Split.
     */
    @Test
    void crossOrientationSplitHandsTheLeafsFullWeightToTheNestedSplit()
    {
        Split root = new Split(null, true, 0.0, new Leaf("a", 0.5), new Divider(),
                new Leaf("b", 0.5));
        root.setBounds(new Rectangle(0, 0, 205, 100));
        MultiSplitLayout layout = new MultiSplitLayout(root);
        Leaf a = layout.getLeafForName("a");

        Leaf n = layout.splitLeaf("a", "n", false, false);

        Split nested = assertInstanceOf(Split.class, root.getChildren().get(0));
        assertEquals(0.5, nested.getWeight(), 0.0,
                "the nested split keeps a's parent-level weight");
        assertEquals(1.0, nested.getWeight() + root.getChildren().get(2).getWeight(), 0.0,
                "the parent level's weights still add up");
        assertEquals(0.5, a.getWeight(), 0.0, "inside the nested split: half");
        assertEquals(0.5, n.getWeight(), 0.0, "and half");
    }


    /** Split then remove is a round trip: the leaf gets back exactly the weight it had. */
    @Test
    void crossOrientationSplitThenRemoveRestoresTheLeafsWeight()
    {
        Split root = new Split(null, true, 0.0, new Leaf("a", 0.5), new Divider(),
                new Leaf("b", 0.5));
        root.setBounds(new Rectangle(0, 0, 205, 100));
        MultiSplitLayout layout = new MultiSplitLayout(root);
        Leaf a = layout.getLeafForName("a");

        layout.splitLeaf("a", "n", false, false);
        layout.removeLeaf("n");

        assertSame(a, root.getChildren().get(0), "the nested split collapsed back into a");
        assertEquals(0.5, a.getWeight(), 0.0);
    }


    /**
     * splitLeaf left the Split it created at bounds (0,0,0,0), so the next layout sized the last
     * child from a zero extent: a weighted root leaf split in two came out as a = 202 px and n = -2
     * px.
     */
    @Test
    void aWeightedLeafSplitAndLaidOutGivesBothHalvesTheirSpace()
    {
        MultiSplitLayout layout = new MultiSplitLayout(new Leaf("a", 1.0));
        layout.setDividerSize(5);
        JPanel host = new JPanel(layout);
        JPanel ca = comp();
        host.add(ca, "a");
        host.setSize(205, 105);
        layout.layoutContainer(host);

        layout.splitLeaf("a", "n", true, false);
        JPanel cn = comp();
        host.add(cn, "n");
        layout.layoutContainer(host);

        assertEquals(new Rectangle(0, 0, 100, 105), ca.getBounds());
        assertEquals(new Rectangle(105, 0, 100, 105), cn.getBounds());
        layout.layoutContainer(host);
        assertEquals(new Rectangle(105, 0, 100, 105), cn.getBounds(), "and it is stable");
    }


    /** Splitting the ROOT leaf replaces the model, so it fires "model" like setModel does. */
    @Test
    void splittingTheRootLeafFiresTheModelEvent()
    {
        Leaf a = new Leaf("a");
        MultiSplitLayout layout = new MultiSplitLayout(a);
        List<PropertyChangeEvent> events = new ArrayList<>();
        layout.addPropertyChangeListener(events::add);

        layout.splitLeaf("a", "n", true, false);

        assertEquals(1, events.size());
        assertEquals("model", events.get(0).getPropertyName());
        assertSame(a, events.get(0).getOldValue());
        assertSame(layout.getModel(), events.get(0).getNewValue());
    }
}
