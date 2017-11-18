package ru.ifmo.ctddev.dyadyushkin.arrayset;

import java.util.*;

public class NavigableSet<E> extends SortedSet<E> implements java.util.NavigableSet<E> {
    protected class ReverseOrderIterator extends SortedSetIterator {
        public ReverseOrderIterator(E[] items, int from, int to){
            super(items, from, to);
        }

        @Override
        public boolean hasNext() {
            return to > from;
        }
        @Override
        public E next() {
            return items[--to];
        }
    }

    private boolean fromInclusive = true, toInclusive = false;

    public NavigableSet() {super();}
    public NavigableSet(Collection<E> collection) {
        super(collection);
    }
    public NavigableSet(Collection<E> collection, Comparator<E> comparator) {
        super(collection, comparator);
    }

    protected NavigableSet(NavigableSet<E> parent, E from, E to, boolean fromInclusive, boolean toInclusive) {
        super(parent, from, to);
        //System.out.printf("F %d, T: %d\n", from, to);
        this.fromInclusive = fromInclusive;
        this.toInclusive   = toInclusive;
        //System.out.printf("FROM: %d, TO: %d\n", fromPosition, toPosition);

        if (toInclusive && (toPosition < elements.length)) {
            to = to == null ? elements[toPosition] : to;
            if (compare(elements[toPosition], to) == 0) {
                toPosition++;
            }
        }
        if (!fromInclusive && (fromPosition < elements.length)) {
            from = from == null ? elements[fromPosition] : from;
            if (compare(elements[fromPosition], from) == 0) {
                fromPosition++;
                // Keep upper divide consistent
                if (fromPosition > toPosition) {
                    toPosition = fromPosition;
                }
            }
        }

        assert fromPosition <= toPosition;
        assert fromPosition >= 0 && fromPosition <= elements.length;
        assert toPosition >= 0 && toPosition <= elements.length;
    }
    protected NavigableSet(E[] elements, Comparator<E> cmp) {
        comparator = cmp;
        setElements(elements);
    }


    // TODO: Generalize
    @Override
    public E lower(E e) {
        int pos = castIndex(binarySearch(e)) - 1;
        return outOfRange(pos) ? null : elements[pos];
    }

    @Override
    public E floor(E e) {
        int pos = binarySearch(e);
        pos = pos >= 0 ? pos : castIndex(pos) - 1;
        return outOfRange(pos) ? null : elements[pos];
    }

    @Override
    public E ceiling(E e) {
        int pos = binarySearch(e);
        pos = pos >= 0 ? pos : castIndex(pos);
        return outOfRange(pos) ? null : elements[pos];
    }

    @Override
    public E higher(E e) {
        int pos = binarySearch(e);
        pos = pos >= 0 ? pos + 1 : castIndex(pos);
        return outOfRange(pos) ? null : elements[pos];
    }

    @Override
    public java.util.NavigableSet<E> descendingSet() {
        return new NavigableSet<E>((E[]) toArray(), Collections.reverseOrder(comparator));
    }

    @Override
    public Iterator<E> descendingIterator() {
        return new ReverseOrderIterator(elements, fromPosition, toPosition);
    }

    @Override
    public java.util.NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        return new NavigableSet<>(this, fromElement, toElement, fromInclusive, toInclusive);
    }

    @Override
    public java.util.NavigableSet<E> headSet(E toElement, boolean inclusive) {
        return new NavigableSet<>(this, null, toElement, true, inclusive);
    }

    @Override
    public java.util.NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
        return new NavigableSet<>(this, fromElement, null, inclusive, false);
    }

    protected boolean outOfRange(int i) {
        return !(fromPosition <= i && i < toPosition);
    }

    @Override
    public E pollFirst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public E pollLast() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return Arrays.toString(toArray()).toString();
    }
    public static void main(String... args) {
        java.util.NavigableSet<Integer> s= new NavigableSet<>(Arrays.asList(1,2,3,4));
        java.util.NavigableSet<Integer> ss = new TreeSet<>(Arrays.asList(1,2,3,4));
        for (int i = 0; i < 4; i++) {
            System.out.println(s.subSet(1, i % 2 == 1, 3, i / 2 == 1));
            System.out.println(ss.subSet(1, i % 2 == 1, 3, i / 2 == 1));
        }
    }
}
