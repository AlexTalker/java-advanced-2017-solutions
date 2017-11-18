package ru.ifmo.ctddev.dyadyushkin.mapper;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Function;

public class IterativeParallelism extends ru.ifmo.ctddev.dyadyushkin.concurrent.IterativeParallelism {
    private ParallelMapper parallelMapper = null;

    @SuppressWarnings("unused")
    public IterativeParallelism() {super();}

    @SuppressWarnings("unused")
    public IterativeParallelism(ParallelMapper parallelMapper) {
        super();
        this.parallelMapper = parallelMapper;
    }

    @Override
    protected  <T, R> List<R> execute(int n, T[] data, Function<Spliterator<T>, ? extends R> function) throws InterruptedException {
        if (Objects.nonNull(parallelMapper)) {
            return parallelMapper.map(function, Arrays.asList(split(data, n)));
        }
        return super.execute(n, data, function);
    }
}
