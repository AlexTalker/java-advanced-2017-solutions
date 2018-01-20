package ru.ifmo.ctddev.dyadyushkin.arrayset;

import java.util.*;

public class SortedSet<E> implements java.util.SortedSet<E> {
    protected E[] elements;
    protected Comparator<E> comparator;
    protected E from, to;
    protected int fromPosition = 0, toPosition = 0;

    protected class SortedSetIterator implements Iterator<E> {
        protected int from, to;
        protected E[] items;

        public SortedSetIterator(E[] items, int from, int to){
            this.items = items;
            this.from = from;
            this.to = to;
        }
        @Override
        public boolean hasNext() {
            return from < to;
        }

        @Override
        public E next() {
            return items[from++];
        }
    }


    @SuppressWarnings("unchecked")
    public SortedSet(){
        elements = (E[]) new Object[0];
    }
    public SortedSet(Collection<E> c) {
        this(c, null);
    }

    @SuppressWarnings("unchecked")
    public SortedSet(Collection<E> c, Comparator<E> cmp) {
        this();
        comparator = cmp;
        E[] newElements = (E[]) new Object[c.size()];
        int i = 0;
        int newCount = 0;
        for (E e: c) {
            if(e == null) {
                throw new NullPointerException();
            }
            if (Arrays.binarySearch(newElements,0, i, e, comparator) < 0) {
                newElements[i++] = e;
                newCount++;
            }
        }
        if (newCount > 0) {
            setElements(Arrays.copyOf(newElements, newCount));
            assert elements.length == newCount;
        }
    }

    protected SortedSet(SortedSet<E> parent, E from, E to) {
        this.elements = parent.elements;
        this.comparator = parent.comparator;
        this.from    = from == null ? parent.from : from;
        this.to      = to   == null ? parent.to   : to;
        toPosition   = to   == null ? parent.toPosition   : castIndex(binarySearch(to));
        fromPosition = Math.min(toPosition, from == null ? parent.fromPosition : castIndex(binarySearch(from)));
    }

    protected void setElements(E[] elements) {
        assert elements != null;

        if (elements.length > 0) {
            Arrays.sort(elements, comparator);
        }
        this.elements = elements;
        toPosition = elements.length;
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }

    @Override
    public java.util.SortedSet<E> subSet(E fromElement, E toElement) {
        return new SortedSet<>(this, fromElement, toElement);
    }

    @Override
    public java.util.SortedSet<E> headSet(E toElement) {
        return new SortedSet<>(this, null, toElement);
    }

    @Override
    public java.util.SortedSet<E> tailSet(E fromElement) {
        // TODO: separate checking of set range and indexing
        // Cast new sets basing on elements position in it
        return new SortedSet<>(this, fromElement, null);
    }

    @Override
    public E first() throws NoSuchElementException {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return elements[fromPosition];
    }

    @Override
    public E last() throws NoSuchElementException {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return elements[toPosition - 1];
    }

    @Override
    public int size() {
        return Math.max(toPosition - fromPosition, 0);
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(Object o) {
        E e = (E) o;
        return checkRange(e) ? Arrays.binarySearch(elements, e, comparator) >= 0 : false;
    }

    @Override
    public Iterator<E> iterator() {
        return new SortedSetIterator(elements, fromPosition, toPosition);
    }

    protected static int castIndex(int i) {
        return i < 0 ? (-i -1) : i;
    }

    @Override
    public Object[] toArray() {
        return Arrays.copyOfRange(elements, fromPosition, toPosition);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(T[] a) {
        return (T[]) toArray();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object e: c) {
            if (!contains(e)) {
                return false;
            }
        }
        return true;
    }

    // Helpers

    protected int binarySearch(E e) {
        return Arrays.binarySearch(elements, e, comparator);
    }

    @SuppressWarnings("unchecked")
    protected int compare(E a, E b) {
        Comparator<? super E> c = comparator();
        if (c != null) {
            return c.compare(a, b);
        }
        Comparable<E> comparable = (Comparable<E>) a;
        return comparable.compareTo(b);
    }

    protected boolean checkRange(E e) {
        boolean result = true;
        if (from != null) {
            result &= compare(from, e) >= 0;
        }
        if (to != null) {
            result &= compare(e, to) < 0;
        }
        return result;
    }

    @Override
    public boolean add(E e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    public static void main(String... args) {
        java.util.SortedSet<Integer> s = new SortedSet<>(Arrays.asList(1,2,3));
        assert s.first() == 1;
        assert s.last() == 3;
        java.util.SortedSet<Integer> ss = s.tailSet(2);
        assert s.first() == 2;
        assert s.last() == 3;
        assert s.toArray().length == 2;
        ss = s.tailSet(3);
        assert s.first() == 3;
        assert s.last() == 3;
        assert s.toArray().length == 1;
    }
}
