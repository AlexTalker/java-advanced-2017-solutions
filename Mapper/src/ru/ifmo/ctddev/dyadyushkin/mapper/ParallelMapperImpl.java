package ru.ifmo.ctddev.dyadyushkin.mapper;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

public class ParallelMapperImpl implements ParallelMapper {
    // TODO: Inherit IterativeParalelism & use this class
    private final ExecutorService executorService;

    public ParallelMapperImpl(int threads) {
        executorService = Executors.newFixedThreadPool(threads);
    }

    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> function, List<? extends T> list) throws InterruptedException {
        List<Future<R>> futures = new ArrayList<>(list.size());
        synchronized (executorService) {
            // Submit tasks in ordered to the map() calls
            for (T arg: list) {
                futures.add(executorService.submit(() -> function.apply(arg)));
            }
        }
        boolean done = false;
        List<R> results = new ArrayList<>(list.size());
        try {
            for (Future<R> f: futures) {
                try {
                    results.add(f.get());
                }
                catch (CancellationException ignore) { ignore.printStackTrace(); results.add(null); }
                catch (ExecutionException ignore) { ignore.printStackTrace(); results.add(null); }
            }
            done = true;
            return results;
        }
        finally {
            if (!done) {
                for (Future<R> f: futures)
                    f.cancel(true);
            }
        }
    }

    @Override
    public void close() throws InterruptedException {
        executorService.shutdownNow();
    }

    public static void main(String... args) throws Exception {
        ParallelMapperImpl mapper = new ParallelMapperImpl(4);
        mapper.map((x) -> { System.out.println(x); return 1; }, Arrays.asList(42));
        //mapper.map(null, null);
        mapper.close();
    }
}
