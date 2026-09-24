package net.cumba.swingx.msp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.UIManager;
import net.cumba.swingx.msp.MultiSplitLayout.Divider;
import net.cumba.swingx.msp.MultiSplitLayout.InvalidLayoutException;
import net.cumba.swingx.msp.MultiSplitLayout.Leaf;
import net.cumba.swingx.msp.MultiSplitLayout.Node;
import net.cumba.swingx.msp.MultiSplitLayout.Split;
import org.junit.jupiter.api.Test;

/**
 * Tests of the {@link MultiSplitLayout} tree model ({@link Node}, {@link Split}, {@link Leaf},
 * {@link Divider}) and of the layout's bound properties. No layout pass is run here; see
 * {@link MultiSplitLayoutTest} for that.
 */
class MultiSplitLayoutModelTest
{

    private static final String DIVIDER_SIZE_KEY = "SplitPane.dividerSize";

    // ---------------------------------------------------------------- Node

    @Test
    void nodeBoundsAreDefensivelyCopiedInBothDirections()
    {
        Leaf leaf = new Leaf("a");
        assertEquals(new Rectangle(0, 0, 0, 0), leaf.getBounds());

        Rectangle r = new Rectangle(1, 2, 3, 4);
        leaf.setBounds(r);
        r.x = 99;
        assertEquals(new Rectangle(1, 2, 3, 4), leaf.getBounds(), "setBounds must copy");

        Rectangle got = leaf.getBounds();
        got.width = 99;
        assertEquals(new Rectangle(1, 2, 3, 4), leaf.getBounds(), "getBounds must copy");
        assertNotSame(leaf.getBounds(), leaf.getBounds());
    }


    @Test
    void nodeRejectsNullBounds()
    {
        Leaf leaf = new Leaf("a");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> leaf.setBounds(null));
        assertEquals("null bounds", e.getMessage());
    }


    @Test
    void weightMustStayWithinZeroAndOne()
    {
        Leaf leaf = new Leaf("a");
        assertEquals(0.0, leaf.getWeight());
        leaf.setWeight(0.0);
        leaf.setWeight(1.0);
        assertEquals(1.0, leaf.getWeight());
        assertThrows(IllegalArgumentException.class, () -> leaf.setWeight(-0.01));
        assertThrows(IllegalArgumentException.class, () -> leaf.setWeight(1.01));
        assertEquals(1.0, leaf.getWeight(), "a rejected weight must not be stored");
    }


    @Test
    void addWeightAccumulatesAndIsRangeChecked()
    {
        Leaf leaf = new Leaf("a", 0.25);
        leaf.addWeight(0.5);
        assertEquals(0.75, leaf.getWeight(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> leaf.addWeight(0.5));
        assertEquals(0.75, leaf.getWeight(), 1e-9);
    }


    @Test
    void siblingsFollowTheParentsChildOrder()
    {
        Leaf a = new Leaf("a");
        Divider d = new Divider();
        Leaf b = new Leaf("b");
        new Split(null, true, 0.0, a, d, b);

        assertNull(a.previousSibling());
        assertSame(d, a.nextSibling());
        assertSame(a, d.previousSibling());
        assertSame(b, d.nextSibling());
        assertSame(d, b.previousSibling());
        assertNull(b.nextSibling());
    }


    @Test
    void siblingsAreNullWithoutParentOrWhenNotAChildOfTheParent()
    {
        Leaf orphan = new Leaf("orphan");
        assertNull(orphan.nextSibling());
        assertNull(orphan.previousSibling());

        Split split = new Split(null, true, 0.0, new Leaf("a"), new Divider(), new Leaf("b"));
        Leaf stranger = new Leaf("stranger");
        stranger.setParent(split);
        assertNull(stranger.nextSibling(), "parent set but not listed among its children");
        assertNull(stranger.previousSibling());
    }

    // ---------------------------------------------------------------- Split


    @Test
    void defaultSplitIsAnEmptyRow()
    {
        Split split = new Split();
        assertTrue(split.isRowLayout());
        assertTrue(split.getChildren().isEmpty());
        assertNull(split.getParent());
        assertEquals(0.0, split.getWeight());
    }


    @Test
    void splitConstructorWiresParentOrientationWeightAndChildren()
    {
        Split outer = new Split();
        Leaf a = new Leaf("a");
        Divider d = new Divider();
        Leaf b = new Leaf("b");
        Split inner = new Split(outer, false, 0.4, a, d, b);

        assertSame(outer, inner.getParent());
        assertFalse(inner.isRowLayout());
        assertEquals(0.4, inner.getWeight());
        assertEquals(List.of(a, d, b), inner.getChildren());
        assertSame(inner, a.getParent());
        assertSame(inner, d.getParent());
        assertSame(inner, b.getParent());
    }


    @Test
    void splitConstructorAcceptsANullChildArray()
    {
        Split split = new Split(null, true, 0.0, (Node[]) null);
        assertTrue(split.getChildren().isEmpty());
    }


    @Test
    void setChildrenCopiesTheListAndReparents()
    {
        Leaf a = new Leaf("a");
        Divider d = new Divider();
        Leaf b = new Leaf("b");
        Split split = new Split(null, true, 0.0, a, d, b);

        Leaf c = new Leaf("c");
        List<Node> replacement = new ArrayList<>(List.of(a, d, c));
        split.setChildren(replacement);
        replacement.clear();

        assertEquals(List.of(a, d, c), split.getChildren(), "the incoming list must be copied");
        assertNull(b.getParent(), "a dropped child loses its parent");
        assertSame(split, a.getParent());
        assertSame(split, c.getParent());
        List<Node> children = split.getChildren();
        assertThrows(UnsupportedOperationException.class, () -> children.add(new Leaf("x")));
    }


    @Test
    void setChildrenLeavesAChildAloneThatAlreadyMovedToAnotherParent()
    {
        Leaf a = new Leaf("a");
        Split first = new Split(null, true, 0.0, a, new Divider(), new Leaf("b"));
        Split second = new Split(null, true, 0.0, a, new Divider(), new Leaf("c"));
        assertSame(second, a.getParent());

        first.setChildren(List.of());
        assertSame(second, a.getParent(), "only children still pointing at this split are reset");
    }


    @Test
    void setChildrenRejectsNull()
    {
        Split split = new Split();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> split.setChildren(null));
        assertEquals("children must be a non-null List", e.getMessage());
    }


    @Test
    void splitRemoveLeafDropsTheDividerBeforeIt()
    {
        Leaf a = new Leaf("a");
        Divider d1 = new Divider();
        Leaf b = new Leaf("b");
        Divider d2 = new Divider();
        Leaf c = new Leaf("c");
        Split split = new Split(null, true, 0.0, a, d1, b, d2, c);

        assertTrue(split.removeLeaf(b));
        assertEquals(List.of(a, d2, c), split.getChildren());
        assertNull(b.getParent());

        assertTrue(split.removeLeaf(c));
        assertEquals(List.of(a), split.getChildren());
    }


    @Test
    void splitRemoveLeafOfTheFirstChildDropsTheDividerAfterIt()
    {
        Leaf a = new Leaf("a");
        Divider d1 = new Divider();
        Leaf b = new Leaf("b");
        Split split = new Split(null, true, 0.0, a, d1, b);

        assertTrue(split.removeLeaf(a));
        assertEquals(List.of(b), split.getChildren());
    }


    @Test
    void splitRemoveLeafRejectsNullAndStrangers()
    {
        Leaf a = new Leaf("a");
        Split split = new Split(null, true, 0.0, a, new Divider(), new Leaf("b"));
        assertFalse(split.removeLeaf(null));
        assertFalse(split.removeLeaf(new Leaf("a")), "equal name but not the same node");
        assertEquals(3, split.getChildren().size());
        assertSame(split, a.getParent());
    }


    @Test
    void splitRemoveLeafWithoutNeighbouringDividerKeepsTheRest()
    {
        Leaf a = new Leaf("a");
        Leaf b = new Leaf("b");
        Split split = new Split(null, true, 0.0, a, b);
        assertTrue(split.removeLeaf(b));
        assertEquals(List.of(a), split.getChildren());
    }


    @Test
    void lastWeightedChildIsTheLastWithPositiveWeight()
    {
        Leaf a = new Leaf("a", 0.3);
        Leaf b = new Leaf("b", 0.7);
        Leaf c = new Leaf("c");
        Split split = new Split(null, true, 0.0, a, new Divider(), b, new Divider(), c);
        assertSame(b, split.lastWeightedChild());

        Split unweighted = new Split(null, true, 0.0, new Leaf("x"), new Divider(), new Leaf("y"));
        assertNull(unweighted.lastWeightedChild());
    }


    @Test
    void splitToStringNamesOrientationChildCountAndBounds()
    {
        Split row = new Split(null, true, 0.0, new Leaf("a"), new Divider(), new Leaf("b"));
        row.setBounds(new Rectangle(1, 2, 3, 4));
        assertEquals("MultiSplitLayout.Split ROW [3 children] " + new Rectangle(1, 2, 3, 4),
                row.toString());

        Split column = new Split(null, false, 0.0, new Leaf("a"));
        assertEquals("MultiSplitLayout.Split COLUMN [1 child] " + new Rectangle(),
                column.toString());
    }

    // ---------------------------------------------------------------- Leaf / Divider


    @Test
    void leafConstructorsAndName()
    {
        assertEquals("", new Leaf().getName());
        Leaf leaf = new Leaf("a", 0.5);
        assertEquals("a", leaf.getName());
        assertEquals(0.5, leaf.getWeight());
        leaf.setName("b");
        assertEquals("b", leaf.getName());
        assertThrows(NullPointerException.class, () -> leaf.setName(null));
        assertThrows(NullPointerException.class, () -> new Leaf(null));
        assertThrows(IllegalArgumentException.class, () -> new Leaf("a", 2.0));
    }


    @Test
    void leafToStringCarriesNameWeightAndBounds()
    {
        Leaf leaf = new Leaf("left", 0.25);
        leaf.setBounds(new Rectangle(5, 6, 7, 8));
        assertEquals("MultiSplitLayout.Leaf \"left\" weight=0.25 " + new Rectangle(5, 6, 7, 8),
                leaf.toString());
    }


    @Test
    void dividerCannotCarryAWeight()
    {
        Divider d = new Divider();
        assertThrows(UnsupportedOperationException.class, () -> d.setWeight(0.5));
        assertThrows(UnsupportedOperationException.class, () -> d.addWeight(0.0));
        assertEquals(0.0, d.getWeight());
    }


    @Test
    void dividerOrientationFollowsItsParent()
    {
        Divider orphan = new Divider();
        assertFalse(orphan.isVertical(), "no parent means not vertical");

        Divider inRow = new Divider();
        new Split(null, true, 0.0, new Leaf("a"), inRow, new Leaf("b"));
        assertTrue(inRow.isVertical());

        Divider inColumn = new Divider();
        new Split(null, false, 0.0, new Leaf("a"), inColumn, new Leaf("b"));
        assertFalse(inColumn.isVertical());
    }


    @Test
    void dividerToString()
    {
        Divider d = new Divider();
        d.setBounds(new Rectangle(9, 8, 7, 6));
        assertEquals("MultiSplitLayout.Divider " + new Rectangle(9, 8, 7, 6), d.toString());
    }


    @Test
    void invalidLayoutExceptionCarriesMessageAndNode()
    {
        Leaf leaf = new Leaf("a");
        InvalidLayoutException e = new InvalidLayoutException("broken", leaf);
        assertEquals("broken", e.getMessage());
        assertSame(leaf, e.getNode());
    }

    // ---------------------------------------------------------------- layout properties


    @Test
    void defaultModelIsASingleLeafNamedDefault()
    {
        MultiSplitLayout layout = new MultiSplitLayout();
        Leaf model = (Leaf) layout.getModel();
        assertEquals("default", model.getName());
        assertEquals(List.of("default"), List.copyOf(layout.getLeafNames()));
    }


    @Test
    void dividerSizeComesFromTheLookAndFeel()
    {
        Object saved = UIManager.get(DIVIDER_SIZE_KEY);
        try
        {
            UIManager.put(DIVIDER_SIZE_KEY, 13);
            assertEquals(13, new MultiSplitLayout().getDividerSize());
        }
        finally
        {
            UIManager.put(DIVIDER_SIZE_KEY, saved);
        }
    }


    @Test
    void dividerSizeFallsBackToSevenWhenTheLookAndFeelHasNone()
    {
        Object saved = UIManager.get(DIVIDER_SIZE_KEY);
        try
        {
            UIManager.put(DIVIDER_SIZE_KEY, 0);
            assertEquals(7, new MultiSplitLayout().getDividerSize());
        }
        finally
        {
            UIManager.put(DIVIDER_SIZE_KEY, saved);
        }
    }


    @Test
    void setModelRejectsNullAndDividers()
    {
        MultiSplitLayout layout = new MultiSplitLayout();
        Node before = layout.getModel();
        assertThrows(IllegalArgumentException.class, () -> layout.setModel(null));
        assertThrows(IllegalArgumentException.class, () -> layout.setModel(new Divider()));
        assertSame(before, layout.getModel());
        assertThrows(NullPointerException.class, () -> new MultiSplitLayout(null));

        Split split = new Split(null, true, 0.0, new Leaf("a"), new Divider(), new Leaf("b"));
        layout.setModel(split);
        assertSame(split, layout.getModel());
    }


    @Test
    void setDividerSizeValidatesAndFiresOnlyOnChange()
    {
        MultiSplitLayout layout = new MultiSplitLayout();
        layout.setDividerSize(5);
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;
        layout.addPropertyChangeListener(listener);

        layout.setDividerSize(12);
        assertEquals(12, layout.getDividerSize());
        assertEquals(1, events.size());
        assertEquals("dividerSize", events.get(0).getPropertyName());
        assertEquals(5, events.get(0).getOldValue());
        assertEquals(12, events.get(0).getNewValue());

        layout.setDividerSize(12);
        assertEquals(1, events.size(), "no event when the value does not change");

        layout.setDividerSize(0);
        assertEquals(0, layout.getDividerSize(), "zero is a legal divider size");
        assertThrows(IllegalArgumentException.class, () -> layout.setDividerSize(-1));
        assertEquals(0, layout.getDividerSize());

        layout.removePropertyChangeListener(listener);
        layout.setDividerSize(3);
        assertEquals(2, events.size(), "a removed listener hears nothing");
    }


    @Test
    void nullListenersAreIgnored()
    {
        MultiSplitLayout layout = new MultiSplitLayout();
        PropertyChangeListener listener = evt ->
        {
        };
        layout.addPropertyChangeListener(listener);
        layout.addPropertyChangeListener(null);
        layout.removePropertyChangeListener(null);
        assertEquals(1, layout.getPropertyChangeListeners().length);
        assertSame(listener, layout.getPropertyChangeListeners()[0]);
    }
}
