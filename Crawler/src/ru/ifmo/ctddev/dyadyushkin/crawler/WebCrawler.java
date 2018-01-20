package ru.ifmo.ctddev.dyadyushkin.crawler;

import info.kgeorgiy.java.advanced.crawler.CachingDownloader;
import info.kgeorgiy.java.advanced.crawler.Crawler;
import info.kgeorgiy.java.advanced.crawler.Document;
import info.kgeorgiy.java.advanced.crawler.Downloader;
import info.kgeorgiy.java.advanced.crawler.Result;
import info.kgeorgiy.java.advanced.crawler.URLUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;

public class WebCrawler implements Crawler {
    private final Downloader downloader;
    private final ExecutorService downloadExecutor, extractExecutor;
    private final ConcurrentHashMap<String, Semaphore> hosts = new ConcurrentHashMap<>();
    private final int perHost;

    @SuppressWarnings("WeakerAccess")
    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader       = downloader;
        this.downloadExecutor = Executors.newFixedThreadPool(downloaders);
        this.extractExecutor  = Executors.newFixedThreadPool(extractors);
        this.perHost          = perHost;
    }

    protected class Cache {
        protected ConcurrentHashMap<String, String> urls = new ConcurrentHashMap<>();
        protected ConcurrentHashMap<String, IOException> errors = new ConcurrentHashMap<>();
        protected ConcurrentLinkedQueue<Future<?>> queue = new ConcurrentLinkedQueue<>();
        protected int maxDepth;

        Cache(int depth) { this.maxDepth = depth; }
    }

    protected class ExtractTask implements Runnable {
        Cache cache;
        int depth;
        Document document;
        String url;

        ExtractTask(Cache cache, int depth, String url, Document document) {
            this.document = document;
            this.cache    = cache;
            this.depth    = depth;
            this.url      = url;
        }

        @Override
        public void run() {
            try {
                List<String> result = document.extractLinks();
                ++depth;

                if (depth >= cache.maxDepth)
                    return;

                for (String s: result) {
                    if (cache.urls.put(s,s) == null)
                        cache.queue.add(downloadExecutor.submit(new DownloadTask(cache, depth, s)));
                }
            }
            catch (IOException e) {
                cache.errors.putIfAbsent(url, e);
            }
        }
    }

    protected class DownloadTask implements Runnable {
        private String url;
        private Cache cache;
        private int depth;

        DownloadTask(Cache cache, int depth, String url) {
            this.url   = url;
            this.cache = cache;
            this.depth = depth;
        }

        @Override
        public void run() {
            Document document = null;
            String host;

            try {
                host = URLUtils.getHost(url);
            }
            catch (MalformedURLException e) {
                e.printStackTrace();
                return;
            }

            Semaphore semaphore = hosts.compute(host, this::retreive);
            semaphore.acquireUninterruptibly();
            try {
                document = downloader.download(url);
            }
            catch (IOException e) {
                cache.errors.putIfAbsent(url, e);
            }
            finally {
                semaphore.release();
            }
            if (Objects.nonNull(document))
                cache.queue.add(extractExecutor.submit(new ExtractTask(cache, depth, url, document)));
        }

        @SuppressWarnings("unused")
        private Semaphore retreive(String url, Semaphore semaphore) {
            return semaphore == null
                ? new Semaphore(perHost)
                : semaphore;
        }
    }

    @Override
    public Result download(String s, int i) {
        Cache cache = new Cache(i);
        cache.urls.put(s,s);
        cache.queue.add(downloadExecutor.submit(new DownloadTask(cache, 0, s)));

        Future<?> future;
        while (Objects.nonNull(future = cache.queue.poll())) {
            try {
                future.get();
            }
            catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }

        }
        List<String> result = new ArrayList<>(cache.urls.keySet());
        result.removeAll(cache.errors.keySet());

        return new Result(result, cache.errors);
    }

    @Override
    public void close() {
        try {
            downloadExecutor.shutdownNow();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        try {
            extractExecutor.shutdownNow();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void main(String... args) throws Exception {
        int depth = 1, downloadors = Integer.MAX_VALUE,
            extractors = Integer.MAX_VALUE, perHost = Integer.MAX_VALUE;
        if (args.length < 1) {
            throw new RuntimeException("Url is missing in command-line arguments.");
        }
        String url = args[0];
        for (int i = 1; i < args.length && i < 5; i++) {
            int n = Integer.valueOf(args[i]);
            switch (i) {
                case 1: depth       = n; break;
                case 2: downloadors = n; break;
                case 3: extractors  = n; break;
                case 4: perHost     = n; break;
            }
        }
        Result result;
        try (Crawler crawler = new WebCrawler(new CachingDownloader(), downloadors, extractors, perHost)) {
            result = crawler.download(url, depth);
        }
        if (Objects.nonNull(result)) {
            result.getDownloaded().forEach(System.out::println);
            result.getErrors().forEach((k, v) -> System.err.printf("Error on url %s: %s\n", k, String.valueOf(v)));
        }
    }
}
