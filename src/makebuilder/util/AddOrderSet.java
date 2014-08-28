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
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Max Reichardt
 *
 * Set with entries ordered by the time they were added
 */
public class AddOrderSet<T> implements Set<T> {

    /** list based on which functionality is implemented */
    private ArrayList<T> backend = new ArrayList<T>();

    @Override
    public boolean add(T t) {
        if (!backend.contains(t)) {
            backend.add(t);
            return true;
        }
        return false;
    }

    @Override
    public boolean addAll(Collection <? extends T > ts) {
        boolean change = false;
        for (T t : ts) {
            change |= add(t);
        }
        return change;
    }

    @Override
    public Iterator<T> iterator() {
        return backend.iterator();
    }

    @Override
    public void clear() {
        backend.clear();
    }

    @Override
    public boolean contains(Object o) {
        return backend.contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> ts) {
        boolean r = true;
        for (Object t : ts) {
            r &= contains(t);
        }
        return r;
    }

    @Override
    public boolean isEmpty() {
        return backend.isEmpty();
    }

    @Override
    public boolean remove(Object o) {
        return backend.remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return backend.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return backend.retainAll(c);
    }

    @Override
    public int size() {
        return backend.size();
    }

    @Override
    public Object[] toArray() {
        return backend.toArray();
    }

    @Override
    public <X> X[] toArray(X[] a) {
        return backend.toArray(a);
    }
}
