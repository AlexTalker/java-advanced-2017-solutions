package ru.ifmo.ctddev.dyadyushkin.implementor;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * An interface implementor
 */
public class Implementor implements Impler {
    /**
     * @see Impler#implement(Class, Path)
     */
    @Override
    public void implement(Class<?> aClass, Path path) throws ImplerException {
        validator(aClass);
        try {
            File f = resultFile(path, aClass);
            try (JavaCodeWriter writer = getWriter(f, aClass)) {
                writer.generate();
                writer.flush();
                writer.close();
            }
        }
        catch (ImplerException e) {
            throw e;
        }
        catch (Exception e) {
            throw new ImplerException(e);
        }
    }

    /**
     * Writer for implementation of an interface
     * @param <T> base interface for implementation
     * @see PrintWriter
     */
    protected class JavaCodeWriter<T> extends PrintWriter {
        /**
         * Class for which code is generated
         * */
        protected Class<T> aClass;
        /**
         * Methods which must be implemented or extended
         */
        protected Collection<Method> methods;
        /**
         * Classes that will be imported in generated code
         * */
        protected Set<Class<?>> imports;
        /**
         * Package to which class is belong
         * @see #aClass
         * */
        protected Package aPackage;

        public JavaCodeWriter(File output, Class<T> aClass) throws Exception {
            super(output);

            this.aClass = aClass;
            this.methods = getMethods(aClass);
            this.aPackage = aClass.getPackage();

            this.imports = new TreeSet<>(CLASS_COMPARATOR);
            // Add our type on top of import
            this.imports.add(aClass);
            methods.stream().forEach(m -> addImportsFrom(m));
        }

        /**
         * This method activated code generation process.
         * Until it called, no code is written to output.
         * */
        public void generate() {
            writeHeader();
            startBlock();
            writeBody();
            endBlock();
        }

        /* Bunch of helpers */

        /**
         * This methods validates and extends
         * collection of classes imported in generated code.
         * @param method Method for type extraction
         * @see Method
         * */
        protected void addImportsFrom(Method method) {
            addImports(method.getReturnType());
            addImportsFrom((Executable) method);
        }

        /**
         * This methods validates and extends
         * collection of classes imported in generated code.
         * @param executable Executable for type extraction
         * @see Executable
         * */
        protected void addImportsFrom(Executable executable) {
            addImports(executable.getParameterTypes());
            addImports(executable.getExceptionTypes());
        }

        /* writers */
        /**
         * This methods writes header of generated
         * class to output.
         * @see #generate()
         * */
        protected void writeHeader() {
            String simpleName = aClass.getSimpleName();
            String packageName = aPackage.getName();

            printf("package %s;\n", packageName);
            for (Class<?> c: imports) {
                printf("import %s;\n", c.getName());
            }

            printf("public class %sImpl", simpleName);
            writeParents();
        }

        /**
         * This method writes parent interface
         * to output.
         * */
        protected void writeParents() {
            if (aClass.isInterface()) {
                printf(" implements %s", aClass.getSimpleName());
            }
        }

        /**
         * This method writes body of a class
         * to output.
         * */
        protected void writeBody() {
            for (Method m: methods) {
                writeMethod(m);
            }
        }

        /* another bunch of helpers */

        /**
         * This method generates arguments string
         * for Executable.
         * @param executable target Executable
         * @return string containing arguments declaration
         * */
        protected String methodParams(Executable executable) {
            Class<?>[] args = executable.getParameterTypes();

            return IntStream.range(0, args.length)
                .mapToObj(idx -> {
                    String s;
                    if (args[idx].isArray()) {
                        s = args[idx].getComponentType().getName() + "[]";
                    }
                    else {
                        s = args[idx].getName();
                    }
                    return s + " arg" + idx;
                }).collect(Collectors.joining(", "));
        }

        /**
         * This method writes start of code block
         * to output.
         * */
        protected void startBlock() {
            println(" {");
        }

        /**
         * This method writes end of code block
         * to output.
         * */
        protected void endBlock() {
            println("}");
        }

        /**
         * @param aClass type to extract parent types
         * @return Set of parent classes and interfaces
         * */
        protected Set<Class<?>> getParents(Class<?> aClass) {
            Set<Class<?>> set = new LinkedHashSet<>();

            set.add(aClass);
            for (Class<?> ii : aClass.getInterfaces()) {
                set.addAll(getParents(ii));
            }
            return set;
        }

        /* Private */


        /**
         * This methods adds types to imports list.
         * @param types array of classes
         * */
        private void addImports(Class<?>... types) {
            this.imports.addAll(Arrays.stream(types)
                .map(t -> extractType(t))
                .filter(t -> validImport(t))
                .collect(Collectors.toList())
            );
        }

        /**
         * This method helps to ignore unnecessary subtypes
         * @param aClass type used to extract data
         * @return desired type or null
         */
        private Class<?> extractType(Class<?> aClass) {
            if (aClass.isArray()) {
                aClass = aClass.getComponentType();
            }
            return aClass.isPrimitive() ? null : aClass;
        }

        /**
         * This methods writes
         * code for method to output.
         * @param method A method that will be implemented
         */
        private void writeMethod(Method method) {
            printf("public %s %s(%s)",
                    methodReturnType(method.getReturnType()),
                    method.getName(),
                    methodParams(method)
            );
            startBlock();
            Class<?> r = method.getReturnType();
            if (!r.equals(Void.TYPE)) {
                if (r.isPrimitive()) {
                    if (r.equals(Boolean.TYPE)) {
                        println("return false;");
                    }
                    else if (r.equals(Character.TYPE)) {
                        println("return '\\0';");
                    }
                    else {
                        println("return 0;");
                    }
                }
                else {
                    println("return null;");
                }
            }
            endBlock();
        }

        /**
         * This method extracs necessary methods from class instance.
         * These methods will later be implemented by #writeMethod().
         * @param aClass class from which methods will be extracted, including parents
         * @return A collection of methods for implementation
         */
        private Collection<Method> getMethods(Class<?> aClass) {
            Set<Method> methods = new TreeSet<>(METHOD_COMPARATOR);

            getParents(aClass).stream().forEachOrdered(
                ii -> methods.addAll(Arrays.asList(ii.getDeclaredMethods()))
            );
            return methods.stream().filter(m -> {
                int mods = m.getModifiers();

                if (Modifier.isFinal(mods) || Modifier.isStatic(mods) || Modifier.isPrivate(mods)) {
                    return false;
                }
                /* Filter out duplicating clone methods */
                if (m.getName().equals("clone")) {
                    if (Cloneable.class.isAssignableFrom(this.aClass)) {
                        if (m.getParameterTypes().length == 0) {
                            Class<?> c = m.getReturnType();
                            // Check if return type is most high level type
                            long superTypes = methods.stream()
                                    .filter(t -> t.getName().equals(m.getName())
                                            && t.getParameterTypes().length == 0
                                            && c != t.getReturnType()
                                            && c.isAssignableFrom(t.getReturnType()))
                                    .count();
                            if (superTypes > 0) {
                                return false;
                            }
                        }
                    }
                    else {
                        return false;
                    }
                }

                return true;
            }).collect(Collectors.toList());
        }

        /**
         * @param aClass A type.
         * @return A literal that describe the same type in Java code.
         */
        private String methodReturnType(Class<?> aClass) {
            if (aClass.isArray()) {
                return aClass.getComponentType().getName() + "[]";
            }
            return aClass.getName();
        }

        /**
         * This method validates if class needs to be imported/
         * @param aClass Validated class
         * @return Whether it is necessary or not to import the class
         */
        private boolean validImport(Class<?> aClass) {
            if (aClass == null) {
                return false;
            }
            if (aClass.isLocalClass() || aClass.isMemberClass()) {
                return false;
            }

            Package p = aClass.getPackage();
            if (p.getName().equals("java.lang") || p.equals(aPackage)) {
                return false;
            }
            return true;
        }

        /* Comparators */

        /**
         * This class implements comparator which
         * allows us to avoid importing so-called classes twice.
         * Code that contains import of classes with the same name
         * (even from different packages) cannot be compiled successfully.
         */
        private final Comparator<Class<?>> CLASS_COMPARATOR = Comparator.comparing(Class::getSimpleName);

        /**
         * This class allows us to left only uppermost
         * methods in hierarchy, avoiding duplication
         * or loosing final methods.
         */
        private final Comparator<Method> METHOD_COMPARATOR = Comparator.comparing(Method::getName)
                .thenComparing(t -> t.getReturnType().getName(), String::compareTo)
                .thenComparing(t -> t.getParameterTypes().length, Comparator.comparingInt(l -> l))
                .thenComparing(t -> t.getParameterTypes(), (types1, types2) -> {
                    Comparator<Class<?>> comparator = Comparator.comparing(Class::getName);
                    return IntStream.range(0, types1.length)
                            .map(i -> comparator.compare(types1[i], types2[i]))
                            .reduce(0, (c, v) -> c != 0 ? c : v);
                });
    }

    /**
     * This method validate class for {@link #implement(Class, Path)} method
     * @param aClass class which's beign validated
     * @throws ImplerException if the class is forbidden for code generator
     */
    protected void validator(Class<?> aClass) throws ImplerException {
        if (!aClass.isInterface()) {
            throw new ImplerException("Not an interface");
        }
    }

    /**
     * @param sourceCode target file
     * @param aClass target class
     * @return instance of JavaCodeWrite or its subclass
     * @throws Exception errors during construction of the writer
     */
    protected JavaCodeWriter<?> getWriter(File sourceCode, Class<?> aClass) throws Exception {
        return new JavaCodeWriter<>(sourceCode, aClass);
    }

    /* Static */

    /**
     * @param aClass original class
     * @return generated class name
     */
    protected static String calcClassName(Class<?> aClass) {
        return aClass.getSimpleName() + "Impl";
    }

    /**
     * @param directory Where generated code will be
     * @param aClass Class from which code is generated
     * @return File object for source code file
     * @throws IOException errors during creation of output file
     */
    protected static File resultFile(Path directory, Class<?> aClass) throws IOException {
        String[] packages = aClass.getPackage().getName().split("\\.");
        Path file = Paths.get(directory.toAbsolutePath().toString(), packages);

        Files.createDirectories(file);
        file = Paths.get(file.toString(), calcClassName(aClass) + ".java");
        return file.toFile();
    }


//    public static void main(String... args) {
//    }
}
