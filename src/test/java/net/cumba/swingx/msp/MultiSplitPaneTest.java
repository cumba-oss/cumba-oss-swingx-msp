package net.cumba.swingx.msp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JPanel;
import javax.swing.event.MouseInputAdapter;
import net.cumba.swingx.msp.MultiSplitLayout.Divider;
import net.cumba.swingx.msp.MultiSplitLayout.Leaf;
import net.cumba.swingx.msp.MultiSplitLayout.Split;
import org.junit.jupiter.api.Test;

/**
 * Headless tests of {@link MultiSplitPane}: adding and removing components by leaf name, divider
 * painting, the divider drag handled by its input handler, and accessibility. Mouse and key events
 * are synthesised and handed straight to the pane's own listener; no window is ever shown.
 */
class MultiSplitPaneTest
{

    private static final int W = 205;

    private static final int H = 105;

    /** A pane laid out as a row [a | b] at 205 x 105 with a 5 px divider at x=100. */
    private static final class RowPane
    {

        final MultiSplitPane pane = new MultiSplitPane();

        final JPanel a = new JPanel();

        final JPanel b = new JPanel();

        final Divider divider;

        RowPane(boolean row)
        {
            pane.setDividerSize(5);
            pane.setSize(W, H);
            pane.add(a, "default");
            pane.doLayout();
            assertTrue(pane.splitLeaf(a, "b", row, false));
            pane.add(b, "b");
            pane.doLayout();
            divider = (Divider) ((Split) pane.getMultiSplitLayout().getModel()).getChildren()
                    .get(1);
        }
    }

    private static MouseInputAdapter handler(MultiSplitPane pane)
    {
        List<MouseListener> handlers = Arrays.stream(pane.getMouseListeners())
                .filter(l -> l instanceof KeyListener).toList();
        assertEquals(1, handlers.size());
        return (MouseInputAdapter) handlers.get(0);
    }


    private static MouseEvent mouse(Component source, int id, int x, int y)
    {
        return new MouseEvent(source, id, 0L, 0, x, y, 1, false);
    }


    private static KeyEvent key(Component source, int id, int code)
    {
        return new KeyEvent(source, id, 0L, 0, code, KeyEvent.CHAR_UNDEFINED);
    }

    // ---------------------------------------------------------------- construction


    @Test
    void newPaneUsesAMultiSplitLayoutWithOneDefaultLeaf()
    {
        MultiSplitPane pane = new MultiSplitPane();
        MultiSplitLayout layout = assertInstanceOf(MultiSplitLayout.class, pane.getLayout());
        assertSame(layout, pane.getMultiSplitLayout());
        assertEquals("default", ((Leaf) layout.getModel()).getName());
        assertFalse(pane.isFocusable());
        assertNull(pane.activeDivider());
        assertNotNull(pane.getDividerPainter());

        MouseInputAdapter handler = handler(pane);
        assertTrue(Arrays.asList(pane.getMouseMotionListeners()).contains(handler));
        assertTrue(Arrays.asList(pane.getKeyListeners()).contains((KeyListener) handler));
    }


    @Test
    void setModelAndSetDividerSizeDelegateToTheLayout()
    {
        MultiSplitPane pane = new MultiSplitPane();
        Split model = new Split(null, true, 0.0, new Leaf("x"), new Divider(), new Leaf("y"));
        pane.setModel(model);
        pane.setDividerSize(9);
        assertSame(model, pane.getMultiSplitLayout().getModel());
        assertEquals(9, pane.getMultiSplitLayout().getDividerSize());
    }

    // ---------------------------------------------------------------- add / split / remove


    @Test
    void addAcceptsOnlyTheNameOfAnExistingLeaf()
    {
        MultiSplitPane pane = new MultiSplitPane();
        JPanel c = new JPanel();
        // Typed as Object so add(Component, Object) is chosen, not add(Component, int).
        Object notAString = 1;

        IllegalArgumentException notString = assertThrows(IllegalArgumentException.class,
                () -> pane.add(c, notAString));
        assertEquals("Given constraint is not of type String.", notString.getMessage());
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> pane.add(c, "nope"));
        assertEquals("No leaf configured for name nope", unknown.getMessage());
        assertThrows(NullPointerException.class, () -> pane.add(c), "a constraint is required");
        assertEquals(0, pane.getComponentCount());

        pane.add(c, "default");
        assertEquals(1, pane.getComponentCount());
        assertSame(c, pane.getComponentForLeafName("default"));
        assertEquals("default", pane.getLeafNameForComponent(c));
    }


    @Test
    void splitLeafByComponentLaysOutBothHalves()
    {
        RowPane p = new RowPane(true);
        assertEquals(new Rectangle(0, 0, 100, H), p.a.getBounds());
        assertEquals(new Rectangle(100, 0, 5, H), p.divider.getBounds());
        assertEquals(new Rectangle(105, 0, 100, H), p.b.getBounds());
        assertEquals("b", p.pane.getLeafNameForComponent(p.b));
    }


    @Test
    void splitLeafRefusesComponentsWithoutALeaf()
    {
        MultiSplitPane pane = new MultiSplitPane();
        assertFalse(pane.splitLeaf(new JPanel(), "x", true, false), "not in the pane");

        JPanel c = new JPanel();
        pane.add(c, "default");
        pane.setModel(new Leaf("other"));
        assertFalse(pane.splitLeaf(c, "x", true, false), "its leaf left the model");
        assertInstanceOf(Leaf.class, pane.getMultiSplitLayout().getModel());
    }


    @Test
    void removeLeafRemovesComponentAndLeaf()
    {
        RowPane p = new RowPane(true);
        JPanel c = new JPanel();
        assertTrue(p.pane.splitLeaf(p.b, "c", true, false));
        p.pane.add(c, "c");
        p.pane.doLayout();
        assertEquals(3, p.pane.getComponentCount());

        assertTrue(p.pane.removeLeaf(c));
        assertEquals(2, p.pane.getComponentCount());
        assertNull(c.getParent());
        assertNull(p.pane.getComponentForLeafName("c"));
        assertNull(p.pane.getMultiSplitLayout().getLeafForName("c"));
        assertEquals(new Rectangle(105, 0, 100, H), p.b.getBounds(), "b takes c's space back");
    }


    @Test
    void removeLeafRefusesUnknownComponentsAndTheLastSplit()
    {
        RowPane p = new RowPane(true);
        assertFalse(p.pane.removeLeaf(new JPanel()));
        assertFalse(p.pane.removeLeaf(p.b), "the root split keeps at least two leaves");
        assertSame(p.pane, p.b.getParent());
        assertEquals(2, p.pane.getComponentCount());
    }


    @Test
    void renameLeafDelegatesToTheLayout()
    {
        RowPane p = new RowPane(true);
        assertTrue(p.pane.renameLeaf("b", "right"));
        assertSame(p.b, p.pane.getComponentForLeafName("right"));
        assertEquals("right", p.pane.getLeafNameForComponent(p.b));
        assertFalse(p.pane.renameLeaf("b", "again"));
    }

    // ---------------------------------------------------------------- painting


    @Test
    void thePainterPaintsEveryDividerInsideTheClip()
    {
        RowPane p = new RowPane(true);
        List<Divider> painted = new ArrayList<>();
        p.pane.setDividerPainter(new MultiSplitPane.DividerPainter()
        {

            @Override
            public void paint(Graphics g, Divider divider)
            {
                painted.add(divider);
                Rectangle r = divider.getBounds();
                g.setColor(Color.RED);
                g.fillRect(r.x, r.y, r.width, r.height);
            }
        });

        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setClip(0, 0, 50, 50);
            p.pane.paintChildren(g);
            assertEquals(List.of(), painted, "divider outside the clip");

            g.setClip(0, 0, W, H);
            p.pane.paintChildren(g);
            assertEquals(List.of(p.divider), painted);
        }
        finally
        {
            g.dispose();
        }
        assertEquals(Color.RED.getRGB(), image.getRGB(102, 50));
    }


    @Test
    void theDefaultPainterPaintsNothingAndANullPainterIsSkipped()
    {
        RowPane p = new RowPane(true);
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setClip(0, 0, W, H);
            p.pane.paintChildren(g);
            assertEquals(0, image.getRGB(102, 50), "the divider gap stays untouched");
            assertTrue((image.getRGB(50, 50) >>> 24) != 0, "the children were painted");

            p.pane.setDividerPainter(null);
            assertNull(p.pane.getDividerPainter());
            p.pane.paintChildren(g);
            assertEquals(0, image.getRGB(102, 50));
        }
        finally
        {
            g.dispose();
        }
    }

    // ---------------------------------------------------------------- cursor


    @Test
    void cursorShowsTheResizeDirectionOverADivider()
    {
        RowPane row = new RowPane(true);
        MouseInputAdapter h = handler(row.pane);

        h.mouseMoved(mouse(row.pane, MouseEvent.MOUSE_MOVED, 102, 50));
        assertEquals(Cursor.E_RESIZE_CURSOR, row.pane.getCursor().getType());
        h.mouseMoved(mouse(row.pane, MouseEvent.MOUSE_MOVED, 10, 50));
        assertEquals(Cursor.DEFAULT_CURSOR, row.pane.getCursor().getType());
        h.mouseEntered(mouse(row.pane, MouseEvent.MOUSE_ENTERED, 101, 5));
        assertEquals(Cursor.E_RESIZE_CURSOR, row.pane.getCursor().getType());
        h.mouseExited(mouse(row.pane, MouseEvent.MOUSE_EXITED, 101, 5));
        assertEquals(Cursor.DEFAULT_CURSOR, row.pane.getCursor().getType(),
                "leaving resets the cursor even over a divider");

        RowPane column = new RowPane(false);
        MouseInputAdapter hc = handler(column.pane);
        hc.mouseMoved(mouse(column.pane, MouseEvent.MOUSE_MOVED, 50, 52));
        assertEquals(Cursor.N_RESIZE_CURSOR, column.pane.getCursor().getType());
    }

    // ---------------------------------------------------------------- dragging


    @Test
    void draggingAVerticalDividerMovesItAndRelaysTheNeighbours()
    {
        RowPane p = new RowPane(true);
        MouseInputAdapter h = handler(p.pane);

        h.mousePressed(mouse(p.pane, MouseEvent.MOUSE_PRESSED, 102, 50));
        assertSame(p.divider, p.pane.activeDivider());

        h.mouseDragged(mouse(p.pane, MouseEvent.MOUSE_DRAGGED, 152, 80));
        assertEquals(new Rectangle(150, 0, 5, H), p.divider.getBounds(),
                "moves by the pointer delta, keeping the grab offset; y is ignored");

        h.mouseReleased(mouse(p.pane, MouseEvent.MOUSE_RELEASED, 152, 80));
        assertNull(p.pane.activeDivider());

        p.pane.doLayout();
        assertEquals(new Rectangle(0, 0, 150, H), p.a.getBounds());
        assertEquals(new Rectangle(155, 0, 50, H), p.b.getBounds());
    }


    @Test
    void theCursorIsFrozenWhileDragging()
    {
        RowPane p = new RowPane(true);
        MouseInputAdapter h = handler(p.pane);
        h.mouseMoved(mouse(p.pane, MouseEvent.MOUSE_MOVED, 102, 50));
        h.mousePressed(mouse(p.pane, MouseEvent.MOUSE_PRESSED, 102, 50));
        h.mouseMoved(mouse(p.pane, MouseEvent.MOUSE_MOVED, 10, 10));
        assertEquals(Cursor.E_RESIZE_CURSOR, p.pane.getCursor().getType());
    }


    @Test
    void draggingIsClampedToTheNeighbours()
    {
        RowPane p = new RowPane(true);
        MouseInputAdapter h = handler(p.pane);

        h.mousePressed(mouse(p.pane, MouseEvent.MOUSE_PRESSED, 102, 50));
        h.mouseDragged(mouse(p.pane, MouseEvent.MOUSE_DRAGGED, -40, 50));
        assertEquals(0, p.divider.getBounds().x, "not past a's left edge");
        h.mouseDragged(mouse(p.pane, MouseEvent.MOUSE_DRAGGED, 1000, 50));
        assertEquals(W - 5, p.divider.getBounds().x, "not past b's right edge");
    }


    @Test
    void draggingAHorizontalDividerMovesItVertically()
    {
        RowPane p = new RowPane(false);
        assertEquals(new Rectangle(0, 50, W, 5), p.divider.getBounds());
        MouseInputAdapter h = handler(p.pane);

        h.mousePressed(mouse(p.pane, MouseEvent.MOUSE_PRESSED, 30, 51));
        h.mouseDragged(mouse(p.pane, MouseEvent.MOUSE_DRAGGED, 90, 71));
        assertEquals(new Rectangle(0, 70, W, 5), p.divider.getBounds());
        h.mouseDragged(mouse(p.pane, MouseEvent.MOUSE_DRAGGED, 90, 500));
        assertEquals(H - 5, p.divider.getBounds().y);
        h.mouseDragged(mouse(p.pane, MouseEvent.MOUSE_DRAGGED, 90, -500));
        assertEquals(0, p.divider.getBounds().y);
        h.mouseDragged(mouse(p.pane, MouseEvent.MOUSE_DRAGGED, 90, 71));
        h.mouseReleased(mouse(p.pane, MouseEvent.MOUSE_RELEASED, 90, 71));

        p.pane.doLayout();
        assertEquals(new Rectangle(0, 0, W, 70), p.a.getBounds());
        assertEquals(new Rectangle(0, 75, W, 30), p.b.getBounds());
    }


    @Test
    void escapeCancelsADragAndRestoresTheDivider()
    {
        RowPane p = new RowPane(true);
        MouseInputAdapter h = handler(p.pane);
        KeyListener k = (KeyListener) h;

        h.mousePressed(mouse(p.pane, MouseEvent.MOUSE_PRESSED, 102, 50));
        h.mouseDragged(mouse(p.pane, MouseEvent.MOUSE_DRAGGED, 150, 50));
        k.keyPressed(key(p.pane, KeyEvent.KEY_PRESSED, KeyEvent.VK_A));
        assertSame(p.divider, p.pane.activeDivider(), "other keys do not cancel");

        k.keyPressed(key(p.pane, KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE));
        assertNull(p.pane.activeDivider());
        assertEquals(new Rectangle(100, 0, 5, H), p.divider.getBounds());
        assertEquals(Cursor.DEFAULT_CURSOR, p.pane.getCursor().getType());

        h.mouseDragged(mouse(p.pane, MouseEvent.MOUSE_DRAGGED, 170, 50));
        assertEquals(new Rectangle(100, 0, 5, H), p.divider.getBounds(),
                "after a cancel, dragging does nothing until the next press");
    }


    @Test
    void pressingAwayFromADividerStartsNoDrag()
    {
        RowPane p = new RowPane(true);
        MouseInputAdapter h = handler(p.pane);
        KeyListener k = (KeyListener) h;

        h.mousePressed(mouse(p.pane, MouseEvent.MOUSE_PRESSED, 10, 10));
        assertNull(p.pane.activeDivider());
        h.mouseDragged(mouse(p.pane, MouseEvent.MOUSE_DRAGGED, 150, 10));
        assertEquals(new Rectangle(100, 0, 5, H), p.divider.getBounds());
        h.mouseReleased(mouse(p.pane, MouseEvent.MOUSE_RELEASED, 150, 10));
        k.keyPressed(key(p.pane, KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE));
        k.keyReleased(key(p.pane, KeyEvent.KEY_RELEASED, KeyEvent.VK_ESCAPE));
        k.keyTyped(new KeyEvent(p.pane, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, 'x'));
        assertNull(p.pane.activeDivider());
        assertEquals(new Rectangle(100, 0, 5, H), p.divider.getBounds());
    }


    @Test
    void aDividerWithoutTwoNeighboursCannotBeDragged()
    {
        MultiSplitPane pane = new MultiSplitPane();
        Divider lonely = new Divider();
        lonely.setBounds(new Rectangle(0, 0, 5, 50));
        Leaf leaf = new Leaf("a");
        leaf.setBounds(new Rectangle(5, 0, 50, 50));
        pane.setModel(new Split(null, true, 0.0, lonely, leaf));
        MouseInputAdapter h = handler(pane);

        h.mousePressed(mouse(pane, MouseEvent.MOUSE_PRESSED, 2, 10));
        assertNull(pane.activeDivider());
        h.mouseDragged(mouse(pane, MouseEvent.MOUSE_DRAGGED, 30, 10));
        assertEquals(new Rectangle(0, 0, 5, 50), lonely.getBounds());
    }

    // ---------------------------------------------------------------- accessibility


    @Test
    void accessibleContextIsASplitPaneAndCached()
    {
        MultiSplitPane pane = new MultiSplitPane();
        AccessibleContext context = pane.getAccessibleContext();
        assertInstanceOf(MultiSplitPane.AccessibleMultiSplitPane.class, context);
        assertEquals(AccessibleRole.SPLIT_PANE, context.getAccessibleRole());
        assertSame(context, pane.getAccessibleContext());
    }


    @Test
    void theInputHandlerIsAlsoTheMotionListener()
    {
        MultiSplitPane pane = new MultiSplitPane();
        MouseMotionListener[] motion = pane.getMouseMotionListeners();
        assertEquals(1, motion.length);
        assertSame(handler(pane), motion[0]);
    }
}
