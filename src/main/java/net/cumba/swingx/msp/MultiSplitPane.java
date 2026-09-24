/*
 * $Id: MultiSplitPane.java,v 1.15 2005/10/26 14:29:54 hansmuller Exp $
 *
 * Copyright 2004 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara, California 95054, U.S.A.
 * All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA
 *
 * MODIFIED FILE (GNU LGPL 2.1, section 2(a)). This file has been changed by P300
 * (https://www.p-300.com) from the SwingLabs SwingX original. When: (1) before 2026-04-24 -- not
 * dated; the first recorded version already carried a new package and Lombok accessors; (2) from
 * 2026-04-24 to 2026-09-24 in P300's non-public repositories -- JSpecify nullness annotations,
 * serialization fixes and static-analysis clean-ups; (3) from 2026-09-24 in this repository, whose
 * git history records each change and its date -- the move to package net.cumba.swingx.msp and
 * fixes to the layout algorithm. The modified file is distributed under the same licence as the
 * original.
 */

package net.cumba.swingx.msp;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JPanel;
import javax.swing.event.MouseInputAdapter;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.cumba.swingx.msp.MultiSplitLayout.Divider;
import net.cumba.swingx.msp.MultiSplitLayout.Node;
import org.jspecify.annotations.Nullable;

/**
 *
 * <p>
 * All properties in this class are bound: when a properties value is changed, all
 * PropertyChangeListeners are fired.
 *
 * @author Hans Muller - SwingX
 */
public class MultiSplitPane extends JPanel
{

    private static final long serialVersionUID = -7739791852705090139L;

    private static final Logger LOGGER = System.getLogger(MultiSplitPane.class.getName());

    /**
     * The accessible context used in {@link #getAccessibleContext()}.
     */
    @SuppressWarnings("HidingField") // matches Swing's JComponent.accessibleContext lazy-init
                                     // pattern
    private transient @Nullable AccessibleContext accessibleContext = null;

    /**
     * The painter used to paint a split divider.
     */
    @Getter
    @Setter
    private transient DividerPainter dividerPainter = new DefaultDividerPainter();

    /**
     * Creates a MultiSplitPane with it's LayoutManager set to to an empty MultiSplitLayout.
     */
    @SuppressWarnings("this-escape")
    public MultiSplitPane()
    {
        super(new MultiSplitLayout());
        InputHandler inputHandler = new InputHandler();
        addMouseListener(inputHandler);
        addMouseMotionListener(inputHandler);
        addKeyListener(inputHandler);
        setFocusable(false);
    }


    @Override
    protected void addImpl(@NonNull Component aComp, @NonNull Object aConstraints, int aIndex)
    {
        // adds special checks if given constraint is a valid string
        // valid means that the layout contains a node with the given string as name.

        if (!(aConstraints instanceof String name))
        {
            // not a string
            throw new IllegalArgumentException("Given constraint is not of type String.");
        }
        if (!getMultiSplitLayout().getLeafNames().contains(name))
        {
            // no leaf for this name
            throw new IllegalArgumentException("No leaf configured for name " + name);
        }
        super.addImpl(aComp, aConstraints, aIndex);
    }


    /**
     * Split the given component (basically the leaf that contains the given component) into two
     * leafs.
     *
     * @param aOldComponent
     *            the component to split.
     * @param aNewLeafName
     *            the name of the new leaf. This is not allowed to be null and it is not allowed to
     *            use a name that is already used by another leaf.
     * @param aSplitRowLayout
     *            if true the split is done in a row layout (1 row with multiple columns) otherwise
     *            it is split in column layout (1 column with multiple rows).
     * @param aNewIsTopLeft
     *            if true the new leaf is placed on top / left of the old component, if false it is
     *            placed below / right of the old component.
     * @return true if the component spaces was successfully split, false otherwise.
     */
    public boolean splitLeaf(@NonNull Component aOldComponent, @NonNull String aNewLeafName,
            boolean aSplitRowLayout, boolean aNewIsTopLeft)
    {
        MultiSplitLayout msl = getMultiSplitLayout();
        String oldName = msl.getLeafNameForComponent(aOldComponent);
        if (oldName == null)
        {
            LOGGER.log(Level.DEBUG, "The given component is not rendered in a valid node.");
            return false;
        }
        boolean res = msl.splitLeaf(oldName, aNewLeafName, aSplitRowLayout, aNewIsTopLeft) != null;
        if (res)
        {
            revalidate();
            repaint();
        }
        return res;
    }


    /**
     * Remove the given component and the leaf, that displays the component. This method will also
     * remove a divider that separates the given component by the next component.
     *
     * @param aComponent
     *            the component to be removed.
     * @return true if the component (and the related leaf) have been removed, false otherwise.
     */
    public boolean removeLeaf(@NonNull Component aComponent)
    {
        MultiSplitLayout msl = getMultiSplitLayout();
        String oldName = msl.getLeafNameForComponent(aComponent);
        if (oldName == null)
        {
            return false;
        }
        boolean res = msl.removeLeaf(oldName);
        if (res)
        {
            remove(aComponent);
            revalidate();
            repaint();
        }
        return res;
    }


    public boolean renameLeaf(@NonNull String aCurrentName, @NonNull String aNewName)
    {
        return getMultiSplitLayout().renameLeaf(aCurrentName, aNewName);
    }


    public @Nullable String getLeafNameForComponent(@NonNull Component aComponent)
    {
        return getMultiSplitLayout().getLeafNameForComponent(aComponent);
    }


    /**
     * Retrieve the component that is displayed for the given leaf name.
     *
     * @param aNodeName
     *            the leaf name to retrieve the component for.
     * @return the component for the given leaf name or null if the given leaf name has no component
     *         assigned.
     */
    public @Nullable Component getComponentForLeafName(String aNodeName)
    {
        return getMultiSplitLayout().getComponentForLeafName(aNodeName);
    }


    /**
     * A convenience method that returns the layout manager cast to MutliSplitLayout.
     *
     * @return this MultiSplitPane's layout manager
     * @see java.awt.Container#getLayout
     * @see #setModel
     */
    public final MultiSplitLayout getMultiSplitLayout()
    {
        return (MultiSplitLayout) getLayout();
    }


    /**
     * A convenience method that sets the MultiSplitLayout model. Equivalent to
     * <code>getMultiSplitLayout.setModel(model)</code>
     *
     * @param model
     *            the root of the MultiSplitLayout model
     * @see #getMultiSplitLayout
     * @see MultiSplitLayout#setModel
     */
    public final void setModel(Node model)
    {
        getMultiSplitLayout().setModel(model);
    }


    /**
     * A convenience method that sets the MultiSplitLayout dividerSize property. Equivalent to
     * <code>getMultiSplitLayout().setDividerSize(newDividerSize)</code>.
     *
     * @param dividerSize
     *            the value of the dividerSize property
     * @see #getMultiSplitLayout
     * @see MultiSplitLayout#setDividerSize
     */
    public final void setDividerSize(int dividerSize)
    {
        getMultiSplitLayout().setDividerSize(dividerSize);
    }


    /**
     * Returns the Divider that's currently being moved, typically because the user is dragging it,
     * or null.
     *
     * @return the Divider that's being moved or null.
     */
    public @Nullable Divider activeDivider()
    {
        return dragDivider;
    }

    /**
     * Draws a single Divider. Typically used to specialize the way the active Divider is painted.
     *
     */
    public abstract static class DividerPainter
    {

        /**
         * Paint a single Divider.
         *
         * @param g
         *            the Graphics object to paint with
         * @param divider
         *            the Divider to paint
         */
        public abstract void paint(Graphics g, Divider divider);

    }


    private static class DefaultDividerPainter extends DividerPainter
    {

        @Override
        public void paint(Graphics g, Divider divider)
        {
        }
    }

    /**
     * Uses the DividerPainter (if any) to paint each Divider that overlaps the clip Rectangle. This
     * is done after the call to <code>super.paintChildren()</code> so that Dividers can be rendered
     * "on top of" the children.
     * <p>
     * {@inheritDoc}
     */
    @Override
    protected void paintChildren(Graphics g)
    {
        super.paintChildren(g);
        DividerPainter dp = getDividerPainter();
        Rectangle clipR = g.getClipBounds();
        if ((dp != null) && (clipR != null))
        {
            Graphics dpg = g.create();
            try
            {
                MultiSplitLayout msl = getMultiSplitLayout();
                for (Divider divider : msl.dividersThatOverlap(clipR))
                {
                    dp.paint(dpg, divider);
                }
            }
            finally
            {
                dpg.dispose();
            }
        }
    }

    private boolean dragUnderway = false;

    private transient MultiSplitLayout.@Nullable Divider dragDivider = null;

    private @Nullable Rectangle initialDividerBounds = null;

    private int dragOffsetX = 0;

    private int dragOffsetY = 0;

    private int dragMin = -1;

    private int dragMax = -1;

    private class InputHandler extends MouseInputAdapter implements KeyListener
    {

        private void repaintDragLimits()
        {
            // Only called from updateDrag while a drag is underway, which keeps dragDivider set.
            MultiSplitLayout.Divider divider = java.util.Objects.requireNonNull(dragDivider);
            Rectangle damageR = divider.getBounds();
            if (divider.isVertical())
            {
                damageR.x = dragMin;
                damageR.width = dragMax - dragMin;
            }
            else
            {
                damageR.y = dragMin;
                damageR.height = dragMax - dragMin;
            }
            repaint(damageR);
        }


        private void clearDragState()
        {
            dragDivider = null;
            initialDividerBounds = null;
            dragOffsetX = dragOffsetY = 0;
            dragMin = dragMax = -1;
            dragUnderway = false;
        }


        private void startDrag(int mx, int my)
        {
            requestFocusInWindow();
            MultiSplitLayout msl = getMultiSplitLayout();
            MultiSplitLayout.Divider divider = msl.dividerAt(mx, my);
            if (divider == null)
            {
                // not at divider position
                dragUnderway = false;
                return;
            }

            MultiSplitLayout.Node prevNode = divider.previousSibling();
            MultiSplitLayout.Node nextNode = divider.nextSibling();
            if ((prevNode == null) || (nextNode == null))
            {
                // we do not have two nodes
                dragUnderway = false;
                return;
            }

            // we have a divider and two nodes --> we can drag
            initialDividerBounds = divider.getBounds();
            dragOffsetX = mx - initialDividerBounds.x;
            dragOffsetY = my - initialDividerBounds.y;
            dragDivider = divider;
            Rectangle prevNodeBounds = prevNode.getBounds();
            Rectangle nextNodeBounds = nextNode.getBounds();
            if (dragDivider.isVertical())
            {
                dragMin = prevNodeBounds.x;
                dragMax = nextNodeBounds.x + nextNodeBounds.width;
                dragMax -= dragDivider.getBounds().width;
            }
            else
            {
                dragMin = prevNodeBounds.y;
                dragMax = nextNodeBounds.y + nextNodeBounds.height;
                dragMax -= dragDivider.getBounds().height;
            }
            dragUnderway = true;

        }


        private void updateDrag(int mx, int my)
        {
            if (!dragUnderway)
            {
                return;
            }

            // dragUnderway implies dragDivider was set in startDrag (cleared together).
            MultiSplitLayout.Divider divider = java.util.Objects.requireNonNull(dragDivider);
            Rectangle oldBounds = divider.getBounds();
            Rectangle bounds = new Rectangle(oldBounds);
            if (divider.isVertical())
            {
                bounds.x = mx - dragOffsetX;
                bounds.x = Math.max(bounds.x, dragMin);
                bounds.x = Math.min(bounds.x, dragMax);
            }
            else
            {
                bounds.y = my - dragOffsetY;
                bounds.y = Math.max(bounds.y, dragMin);
                bounds.y = Math.min(bounds.y, dragMax);
            }
            divider.setBounds(bounds);
            revalidate();
            repaintDragLimits();
        }


        private void finishDrag()
        {
            if (dragUnderway)
            {
                clearDragState();
            }
        }


        private void cancelDrag()
        {
            if (dragUnderway)
            {
                // dragUnderway implies dragDivider and initialDividerBounds were set together.
                java.util.Objects.requireNonNull(dragDivider)
                        .setBounds(java.util.Objects.requireNonNull(initialDividerBounds));
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                repaint();
                revalidate();
                clearDragState();
            }
        }


        private void updateCursor(int x, int y, boolean show)
        {
            if (dragUnderway)
            {
                return;
            }
            int cursorID = Cursor.DEFAULT_CURSOR;
            if (show)
            {
                MultiSplitLayout.Divider divider = getMultiSplitLayout().dividerAt(x, y);
                if (divider != null)
                {
                    cursorID = divider.isVertical() ? Cursor.E_RESIZE_CURSOR
                            : Cursor.N_RESIZE_CURSOR;
                }
            }
            setCursor(Cursor.getPredefinedCursor(cursorID));
        }


        @Override
        public void mouseEntered(MouseEvent e)
        {
            updateCursor(e.getX(), e.getY(), true);
        }


        @Override
        public void mouseMoved(MouseEvent e)
        {
            updateCursor(e.getX(), e.getY(), true);
        }


        @Override
        public void mouseExited(MouseEvent e)
        {
            updateCursor(e.getX(), e.getY(), false);
        }


        @Override
        public void mousePressed(MouseEvent e)
        {
            startDrag(e.getX(), e.getY());
        }


        @Override
        public void mouseReleased(MouseEvent e)
        {
            finishDrag();
        }


        @Override
        public void mouseDragged(MouseEvent e)
        {
            updateDrag(e.getX(), e.getY());
        }


        @Override
        public void keyPressed(KeyEvent e)
        {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
            {
                cancelDrag();
            }
        }


        @Override
        public void keyReleased(KeyEvent e)
        {
        }


        @Override
        public void keyTyped(KeyEvent e)
        {
        }
    }

    @Override
    public AccessibleContext getAccessibleContext()
    {
        if (accessibleContext == null)
        {
            accessibleContext = new AccessibleMultiSplitPane();
        }
        return accessibleContext;
    }

    protected class AccessibleMultiSplitPane extends AccessibleJPanel
    {

        private static final long serialVersionUID = 4177114112369591280L;

        @Override
        public AccessibleRole getAccessibleRole()
        {
            return AccessibleRole.SPLIT_PANE;
        }
    }
}
