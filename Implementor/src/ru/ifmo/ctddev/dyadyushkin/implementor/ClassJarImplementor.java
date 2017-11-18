package ru.ifmo.ctddev.dyadyushkin.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A class and interface implementor with generation of resulting jar file.
 */
public class ClassJarImplementor extends ClassImplementor implements JarImpler {
    /**
     * Execute command with {@link ProcessBuilder}.
     * @param workDir working directory of the process
     * @param args arguments of the process
     * @return exit code of process
     * @throws IOException {@link ProcessBuilder#start()}
     * @throws InterruptedException {@link Process#waitFor()}
     */
    private static int executeCommand(Path workDir, String... args) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        processBuilder.directory(workDir.toFile());
        Process process = processBuilder.start();
        return process.waitFor();

    }

    /**
     * @see JarImpler#implement(Class, Path)
     * @param aClass base type
     * @param jarPath output jar file
     * @throws ImplerException error during generation of jar file
     */
    @Override
    public void implementJar(Class<?> aClass, Path jarPath) throws ImplerException {
        Path directory = jarPath.getParent();
        try {
            super.implement(aClass, directory);

            File file = resultFile(directory, aClass);
            Path classPath = directory.toAbsolutePath().relativize(Paths.get(file.toString()));
            String cp = System.getProperty("java.class.path");

            executeCommand(directory,"javac", "-cp", cp, classPath.toString());

            Path compiledClass = Paths.get(classPath.getParent().toString(),
                    calcClassName(aClass) + ".class");
            executeCommand(directory,"jar", "cf",
                    jarPath.getFileName().toString(), compiledClass.toString());
        }
        catch (ImplerException e) { throw e; }
        catch (Exception e) { throw new ImplerException(e); }
    }

    public static void main(String... args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new Exception("Invalid number of arguments.");
        }

        boolean generateJar = args[0].equals("-jar");
        String className, outputFile;

        if (args.length == 3) {
            if (!generateJar) {
                throw new Exception("-jar argument is missing.");
            }
            className = args[1];
            outputFile = args[2];
        }
        else {
            generateJar = false;
            className = args[0];
            outputFile = args[1];
        }

        ClassJarImplementor impler = new ClassJarImplementor();
        Class<?> aClass = Class.forName(className);
        Path file = Paths.get(outputFile);
        if (generateJar) {
            impler.implementJar(aClass, file);
        }
        else {
            impler.implement(aClass, file);
        }
    }
}
