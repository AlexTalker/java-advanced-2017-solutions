package ru.ifmo.ctddev.dyadyushkin.walk;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class RecursiveWalk extends Walk implements FileVisitor<Path> {
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        return FileVisitResult.CONTINUE;
    }

    public static void main(String... args) {
        RecursiveWalk w = new RecursiveWalk();
        handler(args, w);
    }
}
