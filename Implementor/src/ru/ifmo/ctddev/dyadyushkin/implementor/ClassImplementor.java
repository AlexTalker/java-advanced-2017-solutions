package ru.ifmo.ctddev.dyadyushkin.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A class and interface implementor
 */
public class ClassImplementor extends Implementor {

    /**
     * Writer for class and interface implementation.
     * @param <T> A class or interface type.
     * @see Implementor.JavaCodeWriter
     */
    protected class JavaClassCodeWriter<T> extends JavaCodeWriter<T> {
        /**
         * Necessary constructors of the class
         */
        protected List<Constructor<?>> constructors;

        /**
         * @param file output file
         * @param aClass base type
         * @throws Exception errors during construction of the writer
         * @see JavaCodeWriter#JavaCodeWriter(File, Class)
         */
        public JavaClassCodeWriter(File file, Class<T> aClass) throws Exception {
            super(file, aClass);
            this.constructors = Arrays.stream(aClass.getDeclaredConstructors())
                .filter(c -> !Modifier.isPrivate(c.getModifiers()))
                .collect(Collectors.toList());

            constructors.stream().forEach(c -> addImportsFrom(c));
            // We cannot extend class if it has no default constructor
            if (!aClass.isInterface() && constructors.size() == 0) {
                throw new ImplerException("No default constructor: " + aClass.toString());
            }
        }

        /**
         * @param aClass A type
         * @return set of subtypes for the type
         */
        @Override
        protected Set<Class<?>> getParents(Class<?> aClass) {
            Set<Class<?>> classes = super.getParents(aClass);
            Class<?> superClass = aClass.getSuperclass();

            if (superClass != null) {
                classes.addAll(getParents(superClass));
            }
            return classes;
        }

        /**
         * @see JavaCodeWriter#writeParents()
         */
        @Override
        protected void writeParents() {
            if (!aClass.isInterface()) {
                printf(" extends %s", aClass.getName());
            }
            super.writeParents();
        }

        /**
         * @see JavaCodeWriter#writeBody()
         */
        @Override
        protected void writeBody() {
            writeConstructors();
            super.writeBody();
        }

        /**
         * Write necessary constructors into generated class body.
         */
        protected void writeConstructors() {
            for (Constructor<?> c: constructors) {
                printf("%sImpl(%s)", aClass.getSimpleName(), methodParams(c));
                Class<?>[] exceptions = c.getExceptionTypes();
                for (int i = 0; i < exceptions.length; i++) {
                    if (i == 0) {
                        printf(" throws ");
                    }
                    else {
                        printf(", ");
                    }
                    print(exceptions[i].getSimpleName());
                }
                startBlock();
                Class<?>[] cc = c.getParameterTypes();
                print("super(");
                for (int i = 0; i < cc.length; i++) {
                    if (i > 0) {
                        print(", ");
                    }
                    printf("arg%d", i);
                }
                println(");");
                endBlock();
            }
        }
    }

    /**
     * @see Implementor#validator(Class)
     */
    @Override
    protected void validator(Class<?> aClass) throws ImplerException {
        if (aClass.isPrimitive()) {
            throw new ImplerException("Type is a primitive.");
        }
        if (!aClass.isInterface()) {
            if (Modifier.isFinal(aClass.getModifiers())) {
                throw new ImplerException("Cannot extend final class: " + aClass.toString());
            }
        }
        if (Enum.class.isAssignableFrom(aClass)) {
            throw new ImplerException("Cannot extend enum type: " + aClass.toString());
        }
    }

    /**
     * @see Implementor#getWriter(File, Class)
     */
    @Override
    protected JavaCodeWriter<?> getWriter(File sourceCode, Class<?> aClass) throws Exception {
        return new JavaClassCodeWriter<>(sourceCode, aClass);
    }

    public static void main(String... args) throws Exception {
        Implementor impl = new ClassImplementor();
        try {
            impl.implement(javax.swing.text.Element.class, Paths.get("tmp", "test"));
        }
        catch (ImplerException e) {
            System.out.println(e);
        }
    }
}
