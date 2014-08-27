/**
 * You received this file as part of an experimental
 * build tool ('makebuilder') - originally developed for MCA2.
 *
 * Copyright (C) 2008-2009 Max Reichardt,
 *   Robotics Research Lab, University of Kaiserslautern
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package makebuilder.util;

import java.util.ArrayList;

/**
 * @author Max Reichardt
 *
 * Utility class for logging when different phases of e.g. makebuilder started and completed.
 * This can be used to e.g. track down performance bottlenecks.
 */
public class ActivityLog {

    /** One element in activity log (tree node) */
    private class Element {

        /** Pparent element */
        private final Element parent;

        /** Name of element/activity */
        private String name;

        /** Time when activity started and ended */
        private final long startTime;
        private long endTime;

        /** Sub-activities */
        private final ArrayList<Element> children = new ArrayList<Element>();

        private Element(Element parent, String name) {
            this.parent = parent;
            this.name = name;
            startTime = System.currentTimeMillis();
            if (this.parent != null) {
                this.parent.children.add(this);
            }
        }

        /**
         * Print element and all subelements
         *
         * @param indentation Current indentation
         */
        private void print(int indentation) {
            for (int i = 0; i < indentation; i++) {
                System.out.print(' ');
            }
            System.out.println("-> " + name + " " + (endTime - startTime) + "ms");

            for (Element child : children) {
                child.print(indentation + 2);
            }
        }


    }

    /** Root activity */
    private final Element root;

    private Element currentGroup;
    private Element currentActivity;

    public ActivityLog(String rootName) {
        root = new Element(null, rootName);
        currentGroup = root;
    }

    /**
     * Add activity group
     *
     * @param name Name of group
     * @param firstActivity Name/description of first activity
     */
    public void addGroup(String name, String firstActivity) {
        currentGroup = new Element(currentGroup, name);
        addActivity(firstActivity);
    }

    /**
     * Should be called whenever a new activity starts
     *
     * @param activityName Name/description of activity
     */
    public void addActivity(String activityName) {
        if (currentActivity != null) {
            currentActivity.endTime = System.currentTimeMillis();
        }
        currentActivity = new Element(currentGroup, activityName);
    }

    /**
     * End activity group
     */
    public void endGroup() {
        if (currentActivity != null) {
            currentActivity.endTime = System.currentTimeMillis();
            currentActivity = null;
        }
        currentGroup.endTime = System.currentTimeMillis();
        currentGroup = currentGroup.parent;
    }

    /**
     * Print activity log
     */
    public void print() {
        if (currentActivity != null) {
            currentActivity.endTime = System.currentTimeMillis();
            currentActivity = null;
        }
        while (currentGroup != null) {
            currentGroup.endTime = System.currentTimeMillis();
            currentGroup = currentGroup.parent;
        }

        root.print(0);
    }
}
