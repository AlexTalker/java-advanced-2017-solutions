package ru.ifmo.ctddev.dyadyushkin.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WebCrawler implements Crawler {
    private Downloader downloader;
    private ExecutorService downloadExecutor;
    private ExecutorService extractExecutor;

    protected class Cache {
        protected ConcurrentHashMap<String, Document> documentsCache = new ConcurrentHashMap<>();
        protected ConcurrentHashMap<Document, List<String>> urlsCache = new ConcurrentHashMap<>();
        protected ConcurrentHashMap<String, String> urls = new ConcurrentHashMap<>();
        protected ConcurrentHashMap<String, IOException> errors = new ConcurrentHashMap<>();

        public Cache() {}
    }

    protected class DownloadTask implements Callable<Future<List<String>>>, Function<String, Document> {
        private String url;
        private Cache cache;

        protected DownloadTask(String url, Cache cache) {
            this.url = url;
            this.cache = cache;
        }

        @Override
        public Future<List<String>> call() {
            // 'cause ConcurrentHashMap cannot store null
                if (cache.urls.putIfAbsent(url, url) != null)
                    return null;
                Document document = cache.documentsCache.computeIfAbsent(this.url, this);
                return document == null
                    ? null
                    : extractExecutor.submit(new ExtractorTask(document, url, cache));
        }

        @Override
        public Document apply(String s) {
            try {
                return WebCrawler.this.downloader.download(s);
            }
            catch (IOException e) {
                cache.errors.putIfAbsent(s, e);
            }
            return null;
        }
    }

    protected class ExtractorTask implements Callable<List<String>>, Function<Document, List<String>> {
        private Document document;
        private String url;
        private Cache cache;

        protected ExtractorTask(Document document, String url, Cache cache) {
            this.document = document;
            this.url = url;
            this.cache = cache;
        }

        @Override
        public List<String> call() {
            if (cache.errors.containsKey(url))
                return null;

            return cache.urlsCache.computeIfAbsent(document, this);
        }

        @Override
        public List<String> apply(Document document) {
            try {
                return document.extractLinks();
            } catch (IOException e) {
                cache.errors.putIfAbsent(url, e);
            }
            return null;
        }
    }

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader = downloader;
        this.downloadExecutor = Executors.newFixedThreadPool(downloaders);
        this.extractExecutor  = Executors.newFixedThreadPool(extractors);
    }

    protected static <T> T extractFuture(Future<T> future) {
        try {
            return future.get();
        }
        catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Result download(String s, int i) {
        List<String> allUrls = new ArrayList<>(2 << i);
        List<String> currentUrls = Arrays.asList(s);
        Cache cache = new Cache();
        for (int depth = 0; depth < i; depth++) {
            allUrls.addAll(currentUrls);
            List<Callable<Future<List<String>>>> downloads = currentUrls.stream()
                    .map(v -> new DownloadTask(v, cache) )
                    .collect(Collectors.toCollection(LinkedList::new));
            if (downloads.size() == 0)
                break;
            try {
                currentUrls = downloadExecutor.invokeAll(downloads).stream()
                        .filter(Objects::nonNull)
                        .map(WebCrawler::extractFuture)
                        .filter(Objects::nonNull)
                        .map(WebCrawler::extractFuture)
                        .filter(Objects::nonNull)
                        .reduce(new ArrayList<>(i), (all, one) -> { all.addAll(one); return all; });
                currentUrls.removeAll(allUrls);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        allUrls.removeAll(cache.errors.keySet());
        return new Result(allUrls, cache.errors);
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

    public static void test(String url, int deep) throws Exception {
        ReplayDownloader downloader = new ReplayDownloader(url, deep, 10, 10);
        CheckingDownloader checkingDownloader = new CheckingDownloader(downloader, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        WebCrawler crawler = new WebCrawler(checkingDownloader, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        Result r = crawler.download(url, deep);
        Result expected = downloader.expected(deep);

        //Set<String> mine = new HashSet<>(r.getDownloaded());
        //Set<String> theirs = new HashSet<>(expected.getDownloaded());
        //System.out.println(mine.size());
        //System.out.println(theirs.size());
        //Set<String> diff = new TreeSet<>(mine);
        //diff.removeAll(theirs);
        //System.out.println(diff);
//        assert mine.size() == theirs.size();
        r.getErrors().forEach((u, e) -> {
            if (e.getMessage().contains("Duplicate"))
                e.printStackTrace();
        });
        //System.out.println(r.getErrors());
        //System.out.println(expected.getErrors());
        crawler.close();
    }

    public static void main(String... args) throws Exception {
        Queue<Integer> list = new LinkedList<>(Arrays.asList(1));
        while (!list.isEmpty()) {
            Integer i = list.remove();
            list.add(i + 1);
            System.out.println(i);
        }
    }
}
