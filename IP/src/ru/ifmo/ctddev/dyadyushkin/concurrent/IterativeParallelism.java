package ru.ifmo.ctddev.dyadyushkin.concurrent;

import info.kgeorgiy.java.advanced.concurrent.ListIP;
import info.kgeorgiy.java.advanced.concurrent.ScalarIP;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IterativeParallelism implements ScalarIP, ListIP {

    private static <T> T[] extractElements(List<? extends T> list) {
        @SuppressWarnings("unchecked")
        T[] elements = (T[]) new Object[list.size()];
        return list.toArray(elements);
    }

    protected  <T, R> List<R> execute(int n, T[] data, Function<Spliterator<T>, ? extends R> function) throws InterruptedException {
        ThreadedWorker<T, R> tw = new ThreadedWorker<>(n, data, function);
        return tw.execute();
    }

    @Override
    public <T> T maximum(int i, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        T[] elements = extractElements(list);
        Function<Spliterator<T>, Accumulator<T>> f = (s) -> {
            Accumulator<T> acc = new Accumulator<>(null);

            if (s.tryAdvance(x -> { acc.value = x; acc.counter++; })) {
                s.forEachRemaining(x -> {
                    acc.value = comparator.compare(acc.value, x) >= 0 ? acc.value : x;
                    acc.counter++;
                });
            }

            return acc;
        };
        List<T> remaining = execute(i, elements, f).stream()
                .filter(v -> v.counter != 0)
                .map(v -> v.value)
                .collect(Collectors.toList());

        return remaining.size() > 0
                ? f.apply(Spliterators.spliterator(remaining, Spliterator.ORDERED)).value
                : null;
    }

    @Override
    public <T> T minimum(int i, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        T[] elements = extractElements(list);
        Function<Spliterator<T>, Accumulator<T>> f = (s) -> {
            Accumulator<T> acc = new Accumulator<>(null);

            if (s.tryAdvance(x -> { acc.value = x; acc.counter++; })) {
                s.forEachRemaining(x -> {
                    acc.value = comparator.compare(acc.value, x) <= 0 ? acc.value : x;
                    acc.counter++;
                });
            }

            return acc;
        };

        List<T> remaining = execute(i, elements, f).stream()
                .filter(v -> v.counter != 0)
                .map(v -> v.value)
                .collect(Collectors.toList());

        return remaining.size() > 0
                ? f.apply(Spliterators.spliterator(remaining, Spliterator.ORDERED)).value
                : null;
    }

    @Override
    public <T> boolean all(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return execute(i, extractElements(list), (s) -> {
                Boolean[] result = new Boolean[1];
                if (s.tryAdvance(x -> result[0] = predicate.test(x))) {
                    s.forEachRemaining(x -> result[0] = result[0] && predicate.test(x));
                }
                return result[0];
            }).stream()
            .filter(Objects::nonNull)
            .reduce(true, (s, c) -> s && c);
    }

    @Override
    public <T> boolean any(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return execute(i, extractElements(list), (s) -> {
                Boolean[] result = new Boolean[1];
                if (s.tryAdvance(x -> result[0] = predicate.test(x))) {
                    s.forEachRemaining(x -> result[0] = result[0] || predicate.test(x));
                }
                return result[0];
            }).stream()
            .filter(Objects::nonNull)
            .reduce(false, (s, c) -> s || c);
    }

    @Override
    public String join(int i, List<?> list) throws InterruptedException {
        List<String> result = execute(i, extractElements(list), (s) -> {
            StringBuilder sb = new StringBuilder();

            s.forEachRemaining(sb::append);

            return sb.toString();
        });

        return String.join("", result);
    }

    @Override
    public <T> List<T> filter(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        return execute(i, extractElements(list), (s) -> {
                List<T> results = new LinkedList<>();

                s.forEachRemaining(v -> { if (predicate.test(v)) results.add(v); });

                return results;
            }).stream()
            .reduce(new ArrayList<>(list.size()), (res, v) -> {
                res.addAll(v);
                return res;
            });
    }

    @Override
    public <T, U> List<U> map(int i, List<? extends T> list, Function<? super T, ? extends U> function) throws InterruptedException {
        return execute(i, extractElements(list), (s) -> {
                List<U> results = new LinkedList<>();

                s.forEachRemaining(v -> results.add(function.apply(v)));

                return results;
            }).stream()
            .reduce(new ArrayList<>(list.size()), (result, v) -> {
                result.addAll(v);
                return result;
            });
    }

    public static class Accumulator<T> {
        T value;
        int counter = 0;

        Accumulator(T value) {
            this.value = value;
        }
    }

    protected static <T> Spliterator<T>[] split(T[] data, int n) {
        @SuppressWarnings("unchecked")
        Spliterator<T>[] spliterators = new Spliterator[n];
        int chunk = data.length / n;
        int base = 0;
        int left = data.length % n;
        for (int i  = 0; i < n; i++){
            int fence = Math.min(data.length, base + chunk + (i < left ? 1 : 0));
            if (base < fence) {
                spliterators[i] = Spliterators.spliterator(data, base, fence, Spliterator.ORDERED);
            }
            else {
                spliterators[i] = Spliterators.emptySpliterator();
            }
            base = fence;
        }
        return spliterators;
    }

    public static class ThreadedWorker<T, R> {
        private List<R> results;
        private Thread[] threads;
        private Spliterator<T>[] spliterators;
        private Function<Spliterator<T>, ? extends R> function;

        protected class Worker implements Runnable {
            private int i;
            private ThreadedWorker<T,R> worker;

            Worker(ThreadedWorker<T, R> worker, int i) {
                this.worker = worker;
                this.i = i;
            }

            @Override
            public void run() {
                results.set(i, this.worker.run(i));
            }
        }

        ThreadedWorker(int n, T[] data, Function<Spliterator<T>, ? extends R> function) {
            threads = new Thread[n];
            this.function = function;
            this.results = new ArrayList<>(n);
            IntStream.range(0, n).forEach(i -> this.results.add(null));
            spliterators = split(data, n);
            for (int i = 0; i < n; i++) {
                threads[i] = new Thread(new Worker(this, i));
            }
        }

        R run(int i) {
            return this.function.apply(spliterators[i]);
        }

        List<R> execute() throws InterruptedException {
            for (Thread t: threads) {
                t.start();
            }
            for (Thread t: threads) {
                t.join();
            }
            return results;
        }
    }


    public static void main(String... args) {
    }
}
