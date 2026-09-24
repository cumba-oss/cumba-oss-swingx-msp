/*
 * $Id: MultiSplitLayout.java,v 1.15 2005/10/26 14:29:54 hansmuller Exp $
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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import javax.swing.UIManager;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * The MultiSplitLayout layout manager recursively arranges its components in row and column groups
 * called "Splits". Elements of the layout are separated by gaps called "Dividers". The overall
 * layout is defined with a simple tree model whose nodes are instances of MultiSplitLayout.Split,
 * MultiSplitLayout.Divider, and MultiSplitLayout.Leaf. Named Leaf nodes represent the space
 * allocated to a component that was added with a constraint that matches the Leaf's name. Extra
 * space is distributed among row/column siblings according to their 0.0 to 1.0 weight. If no
 * weights are specified then the last sibling always gets all of the extra space, or space
 * reduction.
 *
 * <p>
 * Although MultiSplitLayout can be used with any Container, it's the default layout manager for
 * MultiSplitPane. MultiSplitPane supports interactively dragging the Dividers, accessibility, and
 * other features associated with split panes.
 *
 * <p>
 * All properties in this class are bound: when a properties value is changed, all
 * PropertyChangeListeners are fired.
 *
 * @author Hans Muller - SwingX
 * @see MultiSplitPane
 */

@SuppressWarnings("PMD.UselessParentheses")
public class MultiSplitLayout implements LayoutManager
{

    private static final Logger LOGGER = System.getLogger(MultiSplitLayout.class.getName());

    /**
     * The childMap is a map that is used to map form a leaf name to the component.
     */
    private final Map<String, Component> childMap = new HashMap<>();

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /**
     * The root of the tree of Split, Leaf, and Divider nodes that define this layout.
     */
    @Getter
    private Node model;

    /**
     * The width of dividers in Split rows, and the height of dividers in Split columns.
     */
    @Getter
    private int dividerSize;

    /**
     * Create a MultiSplitLayout with a default model with a single Leaf node named "default".
     *
     * #see setModel
     */
    @SuppressWarnings("this-escape")
    public MultiSplitLayout()
    {
        this(new Leaf("default"));
    }


    /**
     * Create a MultiSplitLayout with the specified model.
     *
     * #see setModel
     */
    // NullAway.Init: `model` is initialised by setModel(), which throws on null — never left null.
    @SuppressWarnings(
    {
            "this-escape", "NullAway.Init"
    })
    public MultiSplitLayout(@NonNull Node aModel)
    {
        setModel(aModel);
        dividerSize = UIManager.getInt("SplitPane.dividerSize");
        if (dividerSize <= 0)
        {
            dividerSize = 7;
        }
    }


    public void addPropertyChangeListener(PropertyChangeListener aListener)
    {
        if (aListener != null)
        {
            pcs.addPropertyChangeListener(aListener);
        }
    }


    public void removePropertyChangeListener(PropertyChangeListener aListener)
    {
        if (aListener != null)
        {
            pcs.removePropertyChangeListener(aListener);
        }
    }


    public PropertyChangeListener[] getPropertyChangeListeners()
    {
        return pcs.getPropertyChangeListeners();
    }


    private void firePCS(String aPropertyName, Object aOldValue, Object aNewValue)
    {
        if (!Objects.equals(aOldValue, aNewValue))
        {
            pcs.firePropertyChange(aPropertyName, aOldValue, aNewValue);
        }
    }


    /**
     * Split the leaf with the given name into two leafs (separated y a divider).
     *
     * @param aLeafName
     *            the name of the leaf to split.
     * @param aNewLeafName
     *            the name of the leaf to insert. This name must not be null and no leaf is allowed
     *            to exist for this name.
     * @param aSplitRowLayout
     *            if true the leafs are split in a row layout, if false a column layout.
     * @param aNewIsTopLeft
     *            if true the new one is on top / left, otherwise the new one is bottom / right.
     * @return the new leaf.
     */
    public @Nullable Leaf splitLeaf(@NonNull String aLeafName, @NonNull String aNewLeafName,
            boolean aSplitRowLayout, boolean aNewIsTopLeft)
    {
        if (getLeafForName(aNewLeafName) != null)
        {
            throw new IllegalArgumentException(
                    "A leaf with the name " + aNewLeafName + " does already exist.");
        }

        Leaf oldLeaf = getLeafForName(aLeafName);
        if (oldLeaf == null)
        {
            return null;
        }

        Leaf newLeaf = new Leaf(aNewLeafName);
        Divider newDivider = new Divider();
        Rectangle outerBounds = oldLeaf.getBounds();

        // The weight the leaf held among its siblings. How it is shared depends on the case below:
        // a same-orientation split halves it between the two new siblings; a new Split (cross
        // orientation, or a split of the root leaf) takes it over whole, and the two leaves inside
        // that Split share it 50 / 50 -- weights are relative to the leaf's OWN Split.
        double oldWeight = oldLeaf.getWeight();

        int divSize = getDividerSize();
        if (aSplitRowLayout)
        {
            // split left and right

            int width = (outerBounds.width - divSize) / 2;
            Rectangle bl = new Rectangle(outerBounds.x, outerBounds.y, width, outerBounds.height);
            Rectangle bd = new Rectangle(outerBounds.x + width, outerBounds.y, divSize,
                    outerBounds.height);
            Rectangle br = new Rectangle(outerBounds.x + width + divSize, outerBounds.y, width,
                    outerBounds.height);
            newDivider.setBounds(bd);

            if (aNewIsTopLeft)
            {
                newLeaf.setBounds(bl);
                oldLeaf.setBounds(br);
            }
            else
            {
                oldLeaf.setBounds(bl);
                newLeaf.setBounds(br);
            }

        }
        else
        {
            // split top and bottom

            int height = (outerBounds.height - divSize) / 2;
            Rectangle bt = new Rectangle(outerBounds.x, outerBounds.y, outerBounds.width, height);
            Rectangle bd = new Rectangle(outerBounds.x, outerBounds.y + height, outerBounds.width,
                    divSize);
            Rectangle bb = new Rectangle(outerBounds.x, outerBounds.y + height + divSize,
                    outerBounds.width, height);
            newDivider.setBounds(bd);
            if (aNewIsTopLeft)
            {
                newLeaf.setBounds(bt);
                oldLeaf.setBounds(bb);
            }
            else
            {
                oldLeaf.setBounds(bt);
                newLeaf.setBounds(bb);
            }
        }

        Split parent = oldLeaf.getParent();

        if (parent != null)
        {
            List<Node> childs = new ArrayList<>(parent.getChildren());
            int idx = childs.indexOf(oldLeaf);

            if (parent.isRowLayout() == aSplitRowLayout)
            {
                // this is the simple one: [new, div, old] or [old, div, new]
                if (oldWeight > 0)
                {
                    oldLeaf.setWeight(oldWeight / 2);
                    newLeaf.setWeight(oldWeight / 2);
                }
                if (aNewIsTopLeft)
                {
                    childs.add(idx, newLeaf);
                    childs.add(idx + 1, newDivider);
                }
                else
                {
                    childs.add(idx + 1, newDivider);
                    childs.add(idx + 2, newLeaf);
                }
            }
            else
            {
                childs.set(idx, wrappingSplit(parent, aSplitRowLayout, oldWeight, oldLeaf,
                        newDivider, newLeaf, aNewIsTopLeft, outerBounds));
            }
            parent.setChildren(childs);
            return newLeaf;
        }
        else if (oldLeaf == model)
        {
            // no parent --> we are root. Through setModel, so the "model" event fires.
            setModel(wrappingSplit(null, aSplitRowLayout, oldWeight, oldLeaf, newDivider, newLeaf,
                    aNewIsTopLeft, outerBounds));
            return newLeaf;
        }
        return null;
    }


    /**
     * The Split that replaces a leaf split across its parent's orientation (or the root leaf). It
     * takes over the leaf's weight and bounds -- bounds matter: a Split left at (0,0,0,0) makes the
     * next layout size its last child from a zero extent -- and the two leaves inside share it 50 /
     * 50 when the leaf had a weight at all.
     */
    private static Split wrappingSplit(@Nullable Split aParent, boolean aRowLayout, double aWeight,
            Leaf aOldLeaf, Divider aDivider, Leaf aNewLeaf, boolean aNewIsTopLeft,
            Rectangle aBounds)
    {
        if (aWeight > 0)
        {
            aOldLeaf.setWeight(0.5);
            aNewLeaf.setWeight(0.5);
        }
        Split split = aNewIsTopLeft
                ? new Split(aParent, aRowLayout, aWeight, aNewLeaf, aDivider, aOldLeaf)
                : new Split(aParent, aRowLayout, aWeight, aOldLeaf, aDivider, aNewLeaf);
        split.setBounds(aBounds);
        return split;
    }


    /**
     * Remove the leaf with the given name.
     *
     * @param aLeafName
     *            the name of the leaf to remove.
     * @return true if the leaf for the given name was removed, false otherwise.
     */
    public boolean removeLeaf(@NonNull String aLeafName)
    {
        Leaf oldLeaf = getLeafForName(aLeafName);
        if (oldLeaf == null)
        {
            LOGGER.log(Level.WARNING, "No leaf found for name {0}.", aLeafName);
            return false;
        }
        Split parent = oldLeaf.getParent();
        if (parent == null)
        {
            LOGGER.log(Level.WARNING, "Leaf for name {0} has no parent.", aLeafName);
            return false;
        }
        List<Node> childs = new ArrayList<>(parent.getChildren());
        int idx = childs.indexOf(oldLeaf);
        if (idx < 0)
        {
            LOGGER.log(Level.ERROR, "Can't find index of child in parent!");
            return false;
        }

        if (childs.size() > 3)
        {
            if (oldLeaf.getWeight() > 0)
            {
                // the leaf to remove has a weight --> add weight to other component
                // we only add weight to Nodes that already have a weight.
                Node cBefore = getLastWeightedChildBefore(parent, idx);
                Node cAfter = getNextWeightedChildAfter(parent, idx);
                addWeight(oldLeaf.getWeight(), cBefore, cAfter);
            }

            if (parent.removeLeaf(oldLeaf))
            {
                // we update the layout of the parent
                Rectangle bounds = parent.getBounds();
                layout1(parent, bounds);
                layout2(parent, bounds);
                return true;
            }

            return false;

        }
        else if (childs.size() == 3)
        {
            // we replace the parent by the other non divider child
            Node other = (idx == 0) ? childs.get(2) : childs.get(0);
            Split p2 = parent.getParent();
            if (p2 == null)
            {
                LOGGER.log(Level.WARNING,
                        "Can''t remove leaf {0}: not supported for the last divider.", aLeafName);
                return false;
            }
            List<Node> c2 = new ArrayList<>(p2.getChildren());
            int idx2 = c2.indexOf(parent);
            if (idx2 < 0)
            {
                LOGGER.log(Level.ERROR, "Can't find index of child in parent!");
                return false;
            }

            // we need to replace the weight of other by the weight of parent
            other.setWeight(parent.getWeight());
            c2.set(idx2, other).setParent(null);
            p2.setChildren(c2);
            // we update the layout of the parent
            Rectangle bounds = p2.getBounds();
            layout1(p2, bounds);
            layout2(p2, bounds);
            return true;
        }
        else
        {
            LOGGER.log(Level.WARNING, "Parent of leaf {0} has not enought childs!", aLeafName);
            return false;
        }
    }


    public boolean renameLeaf(@NonNull String aCurrentName, @NonNull String aNewName)
    {
        if (getLeafForName(aNewName) != null)
        {
            LOGGER.log(Level.DEBUG,
                    "Can''t rename \"{0}\" to \"{1}\" because new name is already in use.",
                    aCurrentName, aNewName);
            return false;
        }

        Leaf l = getLeafForName(aCurrentName);
        if (l == null)
        {
            LOGGER.log(Level.DEBUG,
                    "Can''t rename \"{0}\" to \"{1}\" because no leaf is available for old name.",
                    aCurrentName, aNewName);
            return false;
        }

        Component c = childMap.remove(aCurrentName);
        if (c == null)
        {
            LOGGER.log(Level.ERROR,
                    "Can''t rename \"{0}\" to \"{1}\" because no component is available for old name.",
                    aCurrentName, aNewName);
            return false;
        }

        l.setName(aNewName);
        childMap.put(aNewName, c);

        return true;
    }


    /**
     * Add the weight to the given nodes. If both node exist the weight is split to both otherwise
     * the weight is added to the one node that exists. If no node exists, nothing is done.
     *
     * @param aWeight
     *            the weight to add.
     * @param aChild1
     *            the first child to add weight to.
     * @param aChild2
     *            the other child to add weight to.
     */
    private void addWeight(double aWeight, @Nullable Node aChild1, @Nullable Node aChild2)
    {
        if (aChild1 != null)
        {
            if (aChild2 != null)
            {
                double halfWeight = aWeight / 2;
                aChild1.addWeight(halfWeight);
                aChild2.addWeight(halfWeight);
            }
            else
            {
                aChild1.addWeight(aWeight);
            }
        }
        else if (aChild2 != null)
        {
            aChild2.addWeight(aWeight);
        }
    }


    private @Nullable Node getLastWeightedChildBefore(Split aSplit, int aIndex)
    {
        List<Node> childs = aSplit.getChildren();
        for (int i = aIndex - 1; i >= 0; i--)
        {
            Node c = childs.get(i);
            if (c.getWeight() > 0)
            {
                return c;
            }
        }
        return null;
    }


    private @Nullable Node getNextWeightedChildAfter(Split aSplit, int aIndex)
    {
        List<Node> childs = aSplit.getChildren();
        for (int i = aIndex + 1; i < childs.size(); i++)
        {
            Node c = childs.get(i);
            if (c.getWeight() > 0)
            {
                return c;
            }
        }
        return null;
    }


    /**
     * Set the root of the tree of Split, Leaf, and Divider nodes that define this layout. The model
     * can be a Split node (the typical case) or a Leaf. The default value of this property is a
     * Leaf named "default".
     *
     * @param aModel
     *            the root of the tree of Split, Leaf, and Divider node
     * @throws IllegalArgumentException
     *             if model is a Divider or null
     */
    public void setModel(@Nullable Node aModel)
    {
        if ((aModel == null) || (aModel instanceof Divider))
        {
            throw new IllegalArgumentException("invalid model");
        }
        Node oldModel = model;
        model = aModel;
        firePCS("model", oldModel, aModel);
    }


    /**
     * Sets the width of Dividers in Split rows, and the height of Dividers in Split columns. The
     * default value of this property is the same as for JSplitPane Dividers.
     *
     * @param aDividerSize
     *            the size of dividers (pixels)
     * @throws IllegalArgumentException
     *             if dividerSize &lt; 0
     */
    public void setDividerSize(int aDividerSize)
    {
        if (aDividerSize < 0)
        {
            throw new IllegalArgumentException("invalid dividerSize");
        }
        int oldDividerSize = dividerSize;
        dividerSize = aDividerSize;
        firePCS("dividerSize", oldDividerSize, aDividerSize);
    }


    /**
     * Add a component to this MultiSplitLayout. The <code>name</code> should match the name
     * property of the Leaf node that represents the bounds of <code>child</code>. After
     * layoutContainer() recomputes the bounds of all of the nodes in the model, it will set this
     * child's bounds to the bounds of the Leaf node with <code>name</code>. Note: if a component
     * was already added with the same name, this method does not remove it from its parent.
     *
     * @param aName
     *            identifies the Leaf node that defines the child's bounds
     * @param aChild
     *            the component to be added
     * @see #removeLayoutComponent
     */
    @Override
    public void addLayoutComponent(String aName, Component aChild)
    {
        if (aName == null)
        {
            throw new IllegalArgumentException("name not specified");
        }
        childMap.put(aName, aChild);
    }


    /**
     * Removes the specified component from the layout.
     *
     * @param aComponent
     *            the component to be removed
     * @see #addLayoutComponent
     */
    @Override
    public void removeLayoutComponent(Component aComponent)
    {
        String name = getLeafNameForComponent(aComponent);
        if (name == null)
        {
            name = aComponent.getName();
        }
        if (name != null && childMap.get(name) == aComponent)
        {
            childMap.remove(name);
        }
    }


    /**
     * Retrieve the child component for the given node. Components can only be retrieved for
     * {@link Leaf}'s.
     *
     * @param aNode
     *            the node to retrieve the child component for.
     * @return the component or null.
     */
    private @Nullable Component getChildForNode(Node aNode)
    {
        if (aNode instanceof Leaf leaf)
        {
            String name = leaf.getName();
            return childMap.get(name);
        }
        return null;
    }


    /**
     * Retrieve the leaf for the given name.
     *
     * @param aName
     *            the name of the leaf to retrieve.
     * @return the leaf for the given name or null.
     */
    public @Nullable Leaf getLeafForName(String aName)
    {
        if (model instanceof Leaf l)
        {
            return aName.equals(l.getName()) ? l : null;
        }
        if (model instanceof Split s)
        {
            return getLeafForName(s, aName);
        }
        return null;
    }


    /**
     * Retrieve the leaf for the given name that is contained in the given split.
     *
     * @param aSplit
     *            the split to search in (and below).
     * @param aName
     *            the name of the leaf to retrieve.
     * @return the leaf for the given name or null.
     * @see #getLeafForName(String)
     */
    private @Nullable Leaf getLeafForName(Split aSplit, String aName)
    {
        // loop over all children
        for (Node n : aSplit.getChildren())
        {
            if (n instanceof Leaf l)
            {
                // check if the leaf has the requested name.
                if (aName.equals(l.getName()))
                {
                    return l;
                }
            }
            else if (n instanceof Split s)
            {
                // check all childs of this Split.
                Leaf l = getLeafForName(s, aName);
                if (l != null)
                {
                    return l;
                }
            }
        }
        return null;
    }


    /**
     * Retrieve a collection of all leaf names contained in the model of this layout.
     *
     * @return a collection with all leaf names contained in the model.
     */
    public Collection<String> getLeafNames()
    {
        Collection<String> res = new HashSet<>();
        if (model instanceof Split split)
        {
            addLeafNames(split, res);
        }
        else if (model instanceof Leaf leaf)
        {
            res.add(leaf.getName());
        }
        return res;
    }


    /**
     * Add all leaf names below the given split to the given list.
     *
     * @param aSplit
     *            the split to add leaf names from.
     * @param aNames
     *            the collection of names to add to.
     */
    private void addLeafNames(Split aSplit, Collection<String> aNames)
    {
        for (Node c : aSplit.getChildren())
        {
            if (c instanceof Split split)
            {
                addLeafNames(split, aNames);
            }
            else if (c instanceof Leaf leaf)
            {
                aNames.add(leaf.getName());
            }
        }
    }


    /**
     * Retrieve the component for the given leaf name.
     *
     * @param aName
     *            the leaf name to retrieve the component for.
     * @return the component for the given leaf name or null if no component is available for the
     *         given leaf name.
     */
    public @Nullable Component getComponentForLeafName(String aName)
    {
        return childMap.get(aName);
    }


    /**
     * Retrieve the name of the leaf, the given component is assigned to.
     *
     * @param aComponent
     *            the component to retrieve the leaf name for.
     * @return the name of the leaf, that has the given component assigned, or null if no leaf can
     *         be found for the given component.
     */
    public @Nullable String getLeafNameForComponent(Component aComponent)
    {
        for (Entry<String, Component> e : childMap.entrySet())
        {
            if (e.getValue() == aComponent)
            {
                return e.getKey();
            }
        }
        return null;
    }


    private Dimension preferredComponentSize(Node node)
    {
        Component child = getChildForNode(node);
        return (child != null) ? child.getPreferredSize() : new Dimension(0, 0);
    }


    @SuppressWarnings("unused")
    private Dimension minimumComponentSize(Node node)
    {
        Component child = getChildForNode(node);
        return (child != null) ? child.getMinimumSize() : new Dimension(0, 0);
    }


    private Dimension preferredNodeSize(Node root)
    {
        if (root instanceof Leaf)
        {
            return preferredComponentSize(root);
        }
        else if (root instanceof Divider)
        {
            int divSize = getDividerSize();
            return new Dimension(divSize, divSize);
        }
        else
        {
            Split split = (Split) root;
            List<Node> splitChildren = split.getChildren();
            int width = 0;
            int height = 0;
            if (split.isRowLayout())
            {
                for (Node splitChild : splitChildren)
                {
                    Dimension size = preferredNodeSize(splitChild);
                    width += size.width;
                    height = Math.max(height, size.height);
                }
            }
            else
            {
                for (Node splitChild : splitChildren)
                {
                    Dimension size = preferredNodeSize(splitChild);
                    width = Math.max(width, size.width);
                    height += size.height;
                }
            }
            return new Dimension(width, height);
        }
    }


    private Dimension minimumNodeSize(Node root)
    {
        if (root instanceof Leaf)
        {
            Component child = getChildForNode(root);
            return (child != null) ? child.getMinimumSize() : new Dimension(0, 0);
        }
        else if (root instanceof Divider)
        {
            int divSize = getDividerSize();
            return new Dimension(divSize, divSize);
        }
        else
        {
            Split split = (Split) root;
            List<Node> splitChildren = split.getChildren();
            int width = 0;
            int height = 0;
            if (split.isRowLayout())
            {
                for (Node splitChild : splitChildren)
                {
                    Dimension size = minimumNodeSize(splitChild);
                    width += size.width;
                    height = Math.max(height, size.height);
                }
            }
            else
            {
                for (Node splitChild : splitChildren)
                {
                    Dimension size = minimumNodeSize(splitChild);
                    width = Math.max(width, size.width);
                    height += size.height;
                }
            }
            return new Dimension(width, height);
        }
    }


    private Dimension sizeWithInsets(Container parent, Dimension size)
    {
        Insets insets = parent.getInsets();
        int width = size.width + insets.left + insets.right;
        int height = size.height + insets.top + insets.bottom;
        return new Dimension(width, height);
    }


    @Override
    public Dimension preferredLayoutSize(Container parent)
    {
        Dimension size = preferredNodeSize(getModel());
        return sizeWithInsets(parent, size);
    }


    @Override
    public Dimension minimumLayoutSize(Container parent)
    {
        Dimension size = minimumNodeSize(getModel());
        return sizeWithInsets(parent, size);
    }


    private Rectangle boundsWithYandHeight(Rectangle bounds, double y, double height)
    {
        Rectangle r = new Rectangle();
        r.setBounds((int) bounds.getX(), (int) y, (int) bounds.getWidth(), (int) height);
        return r;
    }


    private Rectangle boundsWithXandWidth(Rectangle bounds, double x, double width)
    {
        Rectangle r = new Rectangle();
        r.setBounds((int) x, (int) bounds.getY(), (int) width, (int) bounds.getHeight());
        return r;
    }


    private void minimizeSplitBounds(Split split, Rectangle bounds)
    {
        Rectangle splitBounds = new Rectangle(bounds.x, bounds.y, 0, 0);
        List<Node> splitChildren = split.getChildren();
        Node lastChild = splitChildren.get(splitChildren.size() - 1);
        Rectangle lastChildBounds = lastChild.getBounds();
        if (split.isRowLayout())
        {
            int lastChildMaxX = lastChildBounds.x + lastChildBounds.width;
            splitBounds.add(lastChildMaxX, bounds.y + bounds.height);
        }
        else
        {
            int lastChildMaxY = lastChildBounds.y + lastChildBounds.height;
            splitBounds.add(bounds.x + bounds.width, lastChildMaxY);
        }
        split.setBounds(splitBounds);
    }


    private void layoutShrink(Split split, Rectangle bounds)
    {
        Rectangle splitBounds = split.getBounds();
        ListIterator<Node> splitChildren = split.getChildren().listIterator();

        if (split.isRowLayout())
        {
            int totalWidth = 0; // sum of the children's widths
            int minWeightedWidth = 0; // sum of the weighted childrens' min
            // widths
            int totalWeightedWidth = 0; // sum of the weighted childrens' widths
            for (Node splitChild : split.getChildren())
            {
                int nodeWidth = splitChild.getBounds().width;
                int nodeMinWidth = Math.min(nodeWidth, minimumNodeSize(splitChild).width);
                totalWidth += nodeWidth;
                if (splitChild.getWeight() > 0.0)
                {
                    minWeightedWidth += nodeMinWidth;
                    totalWeightedWidth += nodeWidth;
                }
            }

            double x = bounds.getX();
            double extraWidth = splitBounds.getWidth() - bounds.getWidth();
            double availableWidth = extraWidth;
            boolean onlyShrinkWeightedComponents = (totalWeightedWidth
                    - minWeightedWidth) > extraWidth;

            while (splitChildren.hasNext())
            {
                Node splitChild = splitChildren.next();
                Rectangle splitChildBounds = splitChild.getBounds();
                double minSplitChildWidth = minimumNodeSize(splitChild).getWidth();
                double splitChildWeight = onlyShrinkWeightedComponents ? splitChild.getWeight()
                        : (splitChildBounds.getWidth() / totalWidth);

                if (!splitChildren.hasNext())
                {
                    double newWidth = Math.max(minSplitChildWidth, bounds.getMaxX() - x);
                    Rectangle newSplitChildBounds = boundsWithXandWidth(bounds, x, newWidth);
                    layout2(splitChild, newSplitChildBounds);
                }
                else if ((availableWidth > 0.0) && (splitChildWeight > 0.0))
                {
                    double allocatedWidth = Math.rint(splitChildWeight * extraWidth);
                    double oldWidth = splitChildBounds.getWidth();
                    double newWidth = Math.max(minSplitChildWidth, oldWidth - allocatedWidth);
                    Rectangle newSplitChildBounds = boundsWithXandWidth(bounds, x, newWidth);
                    layout2(splitChild, newSplitChildBounds);
                    availableWidth -= (oldWidth - splitChild.getBounds().getWidth());
                }
                else
                {
                    double existingWidth = splitChildBounds.getWidth();
                    Rectangle newSplitChildBounds = boundsWithXandWidth(bounds, x, existingWidth);
                    layout2(splitChild, newSplitChildBounds);
                }
                x = splitChild.getBounds().getMaxX();
            }
        }

        else
        {
            int totalHeight = 0; // sum of the children's heights
            int minWeightedHeight = 0; // sum of the weighted childrens' min
            // heights
            int totalWeightedHeight = 0; // sum of the weighted childrens'
            // heights
            for (Node splitChild : split.getChildren())
            {
                int nodeHeight = splitChild.getBounds().height;
                int nodeMinHeight = Math.min(nodeHeight, minimumNodeSize(splitChild).height);
                totalHeight += nodeHeight;
                if (splitChild.getWeight() > 0.0)
                {
                    minWeightedHeight += nodeMinHeight;
                    totalWeightedHeight += nodeHeight;
                }
            }

            double y = bounds.getY();
            double extraHeight = splitBounds.getHeight() - bounds.getHeight();
            double availableHeight = extraHeight;
            boolean onlyShrinkWeightedComponents = (totalWeightedHeight
                    - minWeightedHeight) > extraHeight;

            while (splitChildren.hasNext())
            {
                Node splitChild = splitChildren.next();
                Rectangle splitChildBounds = splitChild.getBounds();
                double minSplitChildHeight = minimumNodeSize(splitChild).getHeight();
                double splitChildWeight = onlyShrinkWeightedComponents ? splitChild.getWeight()
                        : (splitChildBounds.getHeight() / totalHeight);

                if (!splitChildren.hasNext())
                {
                    double oldHeight = splitChildBounds.getHeight();
                    double newHeight = Math.max(minSplitChildHeight, bounds.getMaxY() - y);
                    Rectangle newSplitChildBounds = boundsWithYandHeight(bounds, y, newHeight);
                    layout2(splitChild, newSplitChildBounds);
                    availableHeight -= (oldHeight - splitChild.getBounds().getHeight());
                }
                else if ((availableHeight > 0.0) && (splitChildWeight > 0.0))
                {
                    double allocatedHeight = Math.rint(splitChildWeight * extraHeight);
                    double oldHeight = splitChildBounds.getHeight();
                    double newHeight = Math.max(minSplitChildHeight, oldHeight - allocatedHeight);
                    Rectangle newSplitChildBounds = boundsWithYandHeight(bounds, y, newHeight);
                    layout2(splitChild, newSplitChildBounds);
                    availableHeight -= (oldHeight - splitChild.getBounds().getHeight());
                }
                else
                {
                    double existingHeight = splitChildBounds.getHeight();
                    Rectangle newSplitChildBounds = boundsWithYandHeight(bounds, y, existingHeight);
                    layout2(splitChild, newSplitChildBounds);
                }
                y = splitChild.getBounds().getMaxY();
            }
        }

        /* The bounds of the Split node root are set to be
         * big enough to contain all of its children. Since
         * Leaf children can't be reduced below their
         * (corresponding java.awt.Component) minimum sizes,
         * the size of the Split's bounds maybe be larger than
         * the bounds we were asked to fit within.
         */
        minimizeSplitBounds(split, bounds);
    }


    private void layoutGrow(Split split, Rectangle bounds)
    {
        Rectangle splitBounds = split.getBounds();
        ListIterator<Node> splitChildren = split.getChildren().listIterator();
        Node lastWeightedChild = split.lastWeightedChild();

        /* Layout the Split's child Nodes' along the X axis.  The bounds
         * of each child will have the same y coordinate and height as the
         * layoutGrow() bounds argument.  Extra width is allocated to the
         * to each child with a non-zero weight:
         *     newWidth = currentWidth + (extraWidth * splitChild.getWeight())
         * Any extraWidth "left over" (that's availableWidth in the loop
         * below) is given to the last child.  Note that Dividers always
         * have a weight of zero, and they're never the last child.
         */
        if (split.isRowLayout())
        {
            double x = bounds.getX();
            double extraWidth = bounds.getWidth() - splitBounds.getWidth();
            double availableWidth = extraWidth;

            while (splitChildren.hasNext())
            {
                Node splitChild = splitChildren.next();
                Rectangle splitChildBounds = splitChild.getBounds();
                double splitChildWeight = splitChild.getWeight();

                if (!splitChildren.hasNext())
                {
                    double newWidth = bounds.getMaxX() - x;
                    Rectangle newSplitChildBounds = boundsWithXandWidth(bounds, x, newWidth);
                    layout2(splitChild, newSplitChildBounds);
                }
                else if ((availableWidth > 0.0) && (splitChildWeight > 0.0))
                {
                    double allocatedWidth = splitChild.equals(lastWeightedChild) ? availableWidth
                            : Math.rint(splitChildWeight * extraWidth);
                    double newWidth = splitChildBounds.getWidth() + allocatedWidth;
                    Rectangle newSplitChildBounds = boundsWithXandWidth(bounds, x, newWidth);
                    layout2(splitChild, newSplitChildBounds);
                    availableWidth -= allocatedWidth;
                }
                else
                {
                    double existingWidth = splitChildBounds.getWidth();
                    Rectangle newSplitChildBounds = boundsWithXandWidth(bounds, x, existingWidth);
                    layout2(splitChild, newSplitChildBounds);
                }
                x = splitChild.getBounds().getMaxX();
            }
        }

        /* Layout the Split's child Nodes' along the Y axis.  The bounds
         * of each child will have the same x coordinate and width as the
         * layoutGrow() bounds argument.  Extra height is allocated to the
         * to each child with a non-zero weight:
         *     newHeight = currentHeight + (extraHeight * splitChild.getWeight())
         * Any extraHeight "left over" (that's availableHeight in the loop
         * below) is given to the last child.  Note that Dividers always
         * have a weight of zero, and they're never the last child.
         */
        else
        {
            double y = bounds.getY();
            double extraHeight = bounds.getHeight() - splitBounds.getHeight();
            double availableHeight = extraHeight;

            while (splitChildren.hasNext())
            {
                Node splitChild = splitChildren.next();
                Rectangle splitChildBounds = splitChild.getBounds();
                double splitChildWeight = splitChild.getWeight();

                if (!splitChildren.hasNext())
                {
                    double newHeight = bounds.getMaxY() - y;
                    Rectangle newSplitChildBounds = boundsWithYandHeight(bounds, y, newHeight);
                    layout2(splitChild, newSplitChildBounds);
                }
                else if ((availableHeight > 0.0) && (splitChildWeight > 0.0))
                {
                    double allocatedHeight = splitChild.equals(lastWeightedChild) ? availableHeight
                            : Math.rint(splitChildWeight * extraHeight);
                    double newHeight = splitChildBounds.getHeight() + allocatedHeight;
                    Rectangle newSplitChildBounds = boundsWithYandHeight(bounds, y, newHeight);
                    layout2(splitChild, newSplitChildBounds);
                    availableHeight -= allocatedHeight;
                }
                else
                {
                    double existingHeight = splitChildBounds.getHeight();
                    Rectangle newSplitChildBounds = boundsWithYandHeight(bounds, y, existingHeight);
                    layout2(splitChild, newSplitChildBounds);
                }
                y = splitChild.getBounds().getMaxY();
            }
        }
    }


    /* Second pass of the layout algorithm: branch to layoutGrow/Shrink
     * as needed.
     */
    private void layout2(Node root, Rectangle bounds)
    {
        if (root instanceof Leaf)
        {
            Component child = getChildForNode(root);
            if (child != null)
            {
                child.setBounds(bounds);
            }
            root.setBounds(bounds);
        }
        else if (root instanceof Divider)
        {
            root.setBounds(bounds);
        }
        else if (root instanceof Split split)
        {
            boolean grow = split.isRowLayout() ? (split.getBounds().width <= bounds.width)
                    : (split.getBounds().height <= bounds.height);
            if (grow)
            {
                layoutGrow(split, bounds);
                root.setBounds(bounds);
            }
            else
            {
                layoutShrink(split, bounds);
                // split.setBounds() called in layoutShrink()
            }
        }
    }


    /* First pass of the layout algorithm.
     *
     * If the Dividers are "floating" then set the bounds of each
     * node to accomodate the preferred size of all of the
     * Leaf's java.awt.Components.  Otherwise, just set the bounds
     * of each Leaf/Split node so that it's to the left of (for
     * Split.isRowLayout() Split children) or directly above
     * the Divider that follows.
     *
     * This pass sets the bounds of each Node in the layout model.  It
     * does not resize any of the parent Container's
     * (java.awt.Component) children.  That's done in the second pass,
     * see layoutGrow() and layoutShrink().
     */
    private void layout1(Node root, Rectangle bounds)
    {
        if (root instanceof Leaf)
        {
            root.setBounds(bounds);
        }
        else if (root instanceof Split split)
        {
            Iterator<Node> splitChildren = split.getChildren().iterator();
            /* Layout the Split's child Nodes' along the X axis.  The bounds
             * of each child will have the same y coordinate and height as the
             * layout1() bounds argument.
             *
             * Note: the column layout code - that's the "else" clause below
             * this if, is identical to the X axis (rowLayout) code below.
             */
            if (split.isRowLayout())
            {
                double x = bounds.getX();
                while (splitChildren.hasNext())
                {
                    Node splitChild = splitChildren.next();
                    Divider dividerChild = splitChildren.hasNext() ? (Divider) splitChildren.next()
                            : null;
                    double childWidth;

                    if (dividerChild != null)
                    {
                        childWidth = dividerChild.getBounds().getX() - x;
                    }
                    else
                    {
                        childWidth = split.getBounds().getMaxX() - x;
                    }

                    Rectangle childBounds = boundsWithXandWidth(bounds, x, childWidth);
                    layout1(splitChild, childBounds);

                    if (dividerChild != null)
                    {
                        x = dividerChild.getBounds().getMaxX();
                    }
                }
            }

            /* Layout the Split's child Nodes' along the Y axis.  The bounds
             * of each child will have the same x coordinate and width as the
             * layout1() bounds argument.  The algorithm is identical to what's
             * explained above, for the X axis case.
             */
            else
            {
                double y = bounds.getY();
                while (splitChildren.hasNext())
                {
                    Node splitChild = splitChildren.next();
                    Divider dividerChild = splitChildren.hasNext() ? (Divider) splitChildren.next()
                            : null;

                    double childHeight;

                    if (dividerChild != null)
                    {
                        childHeight = dividerChild.getBounds().getY() - y;
                    }
                    else
                    {
                        childHeight = split.getBounds().getMaxY() - y;
                    }

                    Rectangle childBounds = boundsWithYandHeight(bounds, y, childHeight);
                    layout1(splitChild, childBounds);

                    if (dividerChild != null)
                    {
                        y = dividerChild.getBounds().getMaxY();
                    }
                }
            }
            /* The bounds of the Split node root are set to be just
             * big enough to contain all of its children, but only
             * along the axis it's allocating space on.  That's
             * X for rows, Y for columns.  The second pass of the
             * layout algorithm - see layoutShrink()/layoutGrow()
             * allocates extra space.
             */
            minimizeSplitBounds(split, bounds);
        }
    }

    /**
     * The specified Node is either the wrong type or was configured incorrectly.
     */
    public static class InvalidLayoutException extends RuntimeException
    {

        private static final long serialVersionUID = 1L;

        private final transient Node node;

        public InvalidLayoutException(String msg, Node node)
        {
            super(msg);
            this.node = node;
        }


        /**
         * Returns the invalid Node.
         *
         * @return the invalid Node.
         */
        public Node getNode()
        {
            return node;
        }
    }

    private void throwInvalidLayout(String msg, Node node)
    {
        throw new InvalidLayoutException(msg, node);
    }


    private void checkLayout(Node root)
    {
        if (root instanceof Split split)
        {
            if (split.getChildren().size() <= 2)
            {
                throwInvalidLayout("Split must have > 2 children", root);
            }
            Iterator<Node> splitChildren = split.getChildren().iterator();
            double weight = 0.0;
            while (splitChildren.hasNext())
            {
                Node splitChild = splitChildren.next();
                if (splitChild instanceof Divider)
                {
                    throwInvalidLayout("expected a Split or Leaf Node", splitChild);
                }
                if (splitChildren.hasNext())
                {
                    Node dividerChild = splitChildren.next();
                    if (!(dividerChild instanceof Divider))
                    {
                        throwInvalidLayout("expected a Divider Node", dividerChild);
                    }
                }
                weight += splitChild.getWeight();
                checkLayout(splitChild);
            }
            if (weight > 1.0)
            {
                throwInvalidLayout("Split children's total weight > 1.0", root);
            }
        }
    }


    /**
     * Compute the bounds of all of the Split/Divider/Leaf Nodes in the layout model, and then set
     * the bounds of each child component with a matching Leaf Node.
     */
    @Override
    public void layoutContainer(Container parent)
    {
        checkLayout(getModel());
        Insets insets = parent.getInsets();
        Dimension size = parent.getSize();
        int width = size.width - (insets.left + insets.right);
        int height = size.height - (insets.top + insets.bottom);
        Rectangle bounds = new Rectangle(insets.left, insets.top, width, height);
        layout1(getModel(), bounds);
        layout2(getModel(), bounds);
    }


    private @Nullable Divider dividerAt(Node root, int x, int y)
    {
        if (root instanceof Divider divider)
        {
            return divider.getBounds().contains(x, y) ? divider : null;
        }
        else if (root instanceof Split split)
        {
            for (Node child : split.getChildren())
            {
                if (child.getBounds().contains(x, y))
                {
                    return dividerAt(child, x, y);
                }
            }
        }
        return null;
    }


    /**
     * Return the Divider whose bounds contain the specified point, or null if there isn't one.
     *
     * @param x
     *            x coordinate
     * @param y
     *            y coordinate
     * @return the Divider at x,y
     */
    public @Nullable Divider dividerAt(int x, int y)
    {
        return dividerAt(getModel(), x, y);
    }


    private boolean nodeOverlapsRectangle(Node node, Rectangle r2)
    {
        Rectangle r1 = node.getBounds();
        return (r1.x <= (r2.x + r2.width)) && ((r1.x + r1.width) >= r2.x)
                && (r1.y <= (r2.y + r2.height)) && ((r1.y + r1.height) >= r2.y);
    }


    private List<Divider> dividersThatOverlap(Node root, Rectangle r)
    {
        if (nodeOverlapsRectangle(root, r) && (root instanceof Split split))
        {
            List<Divider> dividers = new ArrayList<>();
            for (Node child : split.getChildren())
            {
                if (child instanceof Divider divider)
                {
                    if (nodeOverlapsRectangle(child, r))
                    {
                        dividers.add(divider);
                    }
                }
                else if (child instanceof Split)
                {
                    dividers.addAll(dividersThatOverlap(child, r));
                }
            }
            return List.copyOf(dividers);
        }
        else
        {
            return List.of();
        }
    }


    /**
     * Return the Dividers whose bounds overlap the specified Rectangle.
     *
     * @param r
     *            target Rectangle
     * @return the Dividers that overlap r
     * @throws IllegalArgumentException
     *             if the Rectangle is null
     */
    public List<Divider> dividersThatOverlap(Rectangle r)
    {
        if (r == null)
        {
            throw new IllegalArgumentException("null Rectangle");
        }
        return dividersThatOverlap(getModel(), r);
    }

    /**
     * Base class for the nodes that model a MultiSplitLayout.
     */
    public abstract static class Node
    {

        /**
         * The Split parent of this Node, or null.
         */
        @Getter
        @Setter
        private @Nullable Split parent = null;

        private Rectangle bounds = new Rectangle();

        /**
         * Value between 0.0 and 1.0 used to compute how much space to add to this sibling when the
         * layout grows or how much to reduce when the layout shrinks.
         */
        @Getter
        private double weight = 0.0;

        /**
         * Returns the bounding Rectangle for this Node.
         *
         * @return the value of the bounds property.
         * @see #setBounds
         */
        public Rectangle getBounds()
        {
            return new Rectangle(this.bounds);
        }


        /**
         * Set the bounding Rectangle for this node. The value of bounds may not be null. The
         * default value of bounds is equal to <code>new Rectangle(0,0,0,0)</code>.
         *
         * @param bounds
         *            the new value of the bounds property
         * @throws IllegalArgumentException
         *             if bounds is null
         * @see #getBounds
         */
        public void setBounds(@Nullable Rectangle bounds)
        {
            if (bounds == null)
            {
                throw new IllegalArgumentException("null bounds");
            }
            this.bounds = new Rectangle(bounds);
        }


        /**
         * The weight property is a between 0.0 and 1.0 used to compute how much space to add to
         * this sibling when the layout grows or how much to reduce when the layout shrinks. If
         * rowLayout is true then this node's width grows or shrinks by (extraSpace * weight). If
         * rowLayout is false, then the node's height is changed. The default value of weight is
         * 0.0.
         *
         * @param aWeight
         *            a double between 0.0 and 1.0
         * @see MultiSplitLayout#layoutContainer
         * @throws IllegalArgumentException
         *             if weight is not between 0.0 and 1.0
         */
        public void setWeight(double aWeight)
        {
            if ((aWeight < 0.0) || (aWeight > 1.0))
            {
                throw new IllegalArgumentException("invalid weight");
            }
            weight = aWeight;
        }


        /**
         * Add the given weight to the current one.
         *
         * @param aWeight
         *            the weight to add to the current weight.
         */
        public void addWeight(double aWeight)
        {
            setWeight(weight + aWeight);
        }


        private @Nullable Node siblingAtOffset(int offset)
        {
            Split parentNode = getParent();
            if (parentNode == null)
            {
                return null;
            }
            List<Node> siblings = parentNode.getChildren();
            int index = siblings.indexOf(this);
            if (index == -1)
            {
                return null;
            }
            index += offset;
            return ((index > -1) && (index < siblings.size())) ? siblings.get(index) : null;
        }


        /**
         * Return the Node that comes after this one in the parent's list of children, or null. If
         * this node's parent is null, or if it's the last child, then return null.
         *
         * @return the Node that comes after this one in the parent's list of children.
         * @see #previousSibling
         */
        public @Nullable Node nextSibling()
        {
            return siblingAtOffset(+1);
        }


        /**
         * Return the Node that comes before this one in the parent's list of children, or null. If
         * this node's parent is null, or if it's the last child, then return null.
         *
         * @return the Node that comes before this one in the parent's list of children.
         * @see #nextSibling
         */
        public @Nullable Node previousSibling()
        {
            return siblingAtOffset(-1);
        }
    }


    /**
     * Defines a vertical or horizontal subdivision into two or more tiles.
     */
    public static class Split extends Node
    {

        /**
         * This Split node's children. The returned value is not a reference to the Split's internal
         * list of children. The children attribute is an unmodifiable list.
         */
        @Getter
        private List<Node> children = Collections.emptyList();

        /**
         * True if the this Split's children are to be laid out in a row: all the same height, left
         * edge equal to the previous Node's right edge. If false, children are laid on in a column.
         */
        @Getter
        @Setter
        private boolean rowLayout = true;

        public Split()
        {

        }


        @SuppressWarnings("this-escape")
        public Split(@Nullable Split aParent, boolean aRowLayout, double aWeight,
                Node @Nullable... aChilds)
        {
            setParent(aParent);
            setRowLayout(aRowLayout);
            setWeight(aWeight);
            if (aChilds != null)
            {
                setChildren(Arrays.asList(aChilds));
            }
        }


        /**
         * Set's the children property of this Split node. The parent of each new child is set to
         * this Split node, and the parent of each old child (if any) is set to null. This method
         * defensively copies the incoming List. Default value is an empty List.
         *
         * @param aChildren
         *            List of children
         * @throws IllegalArgumentException
         *             if children is null
         */
        public void setChildren(@Nullable List<Node> aChildren)
        {
            if (aChildren == null)
            {
                throw new IllegalArgumentException("children must be a non-null List");
            }

            for (Node child : children)
            {
                if (child.getParent() == this)
                {
                    child.setParent(null);
                }
            }
            children = Collections.unmodifiableList(new ArrayList<>(aChildren));
            for (Node child : children)
            {
                child.setParent(this);
            }
        }


        /**
         * Remove a single leaf from this Split. This method removes 1 divider that is next to the
         * leaf as well.
         *
         * @param aChild
         *            the leaf to be removed.
         * @return true if the leaf was removed, false otherwise.
         */
        public boolean removeLeaf(@Nullable Leaf aChild)
        {
            if (aChild == null)
            {
                // no child given
                return false;
            }
            int idx = children.indexOf(aChild);
            if (idx < 0)
            {
                // not a child of this parent
                return false;
            }

            List<Node> childs = new ArrayList<>(children);
            childs.remove(idx);
            aChild.setParent(null);

            // remove the divider left of the child (except for first child)
            int dividerIdx = idx > 0 ? idx - 1 : 0;
            if (childs.get(dividerIdx) instanceof Divider)
            {
                childs.remove(dividerIdx);
            }
            children = Collections.unmodifiableList(childs);
            return true;
        }


        /**
         * Convenience method that returns the last child whose weight is > 0.0.
         *
         * @return the last child whose weight is > 0.0.
         */
        public final @Nullable Node lastWeightedChild()
        {
            List<Node> childNodes = getChildren();
            Node weightedChild = null;
            for (Node child : childNodes)
            {
                if (child.getWeight() > 0.0)
                {
                    weightedChild = child;
                }
            }
            return weightedChild;
        }


        @Override
        public String toString()
        {
            int nChildren = getChildren().size();
            StringBuilder sb = new StringBuilder("MultiSplitLayout.Split");
            sb.append(isRowLayout() ? " ROW [" : " COLUMN [");
            sb.append(nChildren).append(nChildren == 1 ? " child" : " children");
            sb.append("] ");
            sb.append(getBounds());
            return sb.toString();
        }
    }


    /**
     * Models a java.awt Component child.
     */
    public static class Leaf extends Node
    {

        /**
         * The Leaf's name.
         */
        @Getter
        @NonNull
        @Setter
        private String name = "";

        /**
         * Create a Leaf node. The default value of name is "".
         */
        public Leaf()
        {
        }


        /**
         * Create a Leaf node with the specified name. Name can not be null.
         *
         * @param aName
         *            value of the Leaf's name property
         * @throws IllegalArgumentException
         *             if name is null
         */
        public Leaf(@NonNull String aName)
        {
            name = aName;
        }


        @SuppressWarnings("this-escape")
        public Leaf(@NonNull String aName, double aWeight)
        {
            name = aName;
            setWeight(aWeight);
        }


        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder("MultiSplitLayout.Leaf");
            sb.append(" \"");
            sb.append(getName());
            sb.append("\"");
            sb.append(" weight=");
            sb.append(getWeight());
            sb.append(" ");
            sb.append(getBounds());
            return sb.toString();
        }
    }


    /**
     * Models a single vertical/horiztonal divider.
     */
    public static class Divider extends Node
    {

        /**
         * Convenience method, returns true if the Divider's parent is a Split row (a Split with
         * isRowLayout() true), false otherwise. In other words if this Divider's major axis is
         * vertical, return true.
         *
         * @return true if this Divider is part of a Split row.
         */
        public final boolean isVertical()
        {
            Split parent = getParent();
            return parent != null && parent.isRowLayout();
        }


        /**
         * Dividers can't have a weight, they don't grow or shrink.
         *
         * @throws UnsupportedOperationException
         *             always, because dividers cannot have a weight.
         */
        @Override
        public void setWeight(double weight)
        {
            throw new UnsupportedOperationException();
        }


        @Override
        public String toString()
        {
            return "MultiSplitLayout.Divider " + getBounds().toString();
        }
    }

}
