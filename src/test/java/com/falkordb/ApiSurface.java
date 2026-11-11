package com.falkordb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Renders the <em>whole</em> public API surface of JFalkorDB as sorted, line-oriented text, by
 * reflection: the snapshot that {@link ApiSurfaceTest} compares against {@code api/jfalkordb.api}.
 *
 * <p>It exists because the japicmp {@code api-diff} gate only compares the classes inside our jar.
 * {@code GraphPipeline} and {@code GraphTransaction} inherit about a thousand methods from Jedis'
 * pipeline command interfaces, and {@code Driver.getConnection()} returns Jedis' {@code Jedis}, so
 * a Jedis bump can remove or re-type methods our users call without any of our classes changing:
 * the Jedis 8 bump did exactly that with {@code jsonNumIncrBy} and {@code Jedis.aclLogBinary()}.
 *
 * <p>What is rendered:
 *
 * <ul>
 *   <li>Every <b>exported type</b>: a public type (or a protected member type of an extendable
 *       exported class) under {@code com.falkordb}, outside {@code com.falkordb.impl}. For each: its
 *       declaration, its direct generic supertypes, every transitive supertype, its public/protected
 *       constructors, and every public/protected field and method it has, <b>inherited ones
 *       included</b>, with generic signatures and {@code throws} clauses.
 *   <li>Every <b>reachable type</b>: a type outside the JDK and the exported types that a signature
 *       <em>declared by</em> an exported type names (a return, parameter, field, {@code throws} or
 *       type-argument type). Only one level deep, which today means Jedis' {@code Jedis} and {@code
 *       Response}, so the surface callers reach through our own signatures is pinned without pinning
 *       all of Jedis. For these only the public instance methods and fields are listed: callers get
 *       such an object from us, they don't construct or subclass it.
 * </ul>
 *
 * <p>Left out on purpose, so that the text is the same on every JDK and only changes when the API
 * does: synthetic and bridge members (including the ones JaCoCo adds); members declared by JDK types
 * and the supertypes of JDK types (the JDK supertype itself is listed); the type an inherited member
 * is declared in; annotations other than {@code @Deprecated}; constant values. Signatures are printed
 * by {@link #type} rather than {@code Type.toString()}, whose format varies across JDKs. Inherited
 * members are printed as the subtype sees them: type variables of a generic supertype are replaced by
 * the type arguments the subtype supplies, and the erased JVM signature is appended whenever it
 * doesn't follow from the printed generic one.
 *
 * <p>{@link #main} renders any build of the client, for example a published jar run on the classpath
 * of the Jedis version it shipped with, and lists the breaking changes between two snapshots.
 */
final class ApiSurface {

    /** The snapshot file, relative to the project directory. */
    static final String SNAPSHOT = "api/jfalkordb.api";

    /** The command that regenerates the snapshot. */
    static final String UPDATE_COMMAND = "just api-surface-update";

    private static final String ROOT = "com.falkordb.";
    private static final String INTERNAL = "com.falkordb.impl.";
    private static final String DEPRECATED = "@Deprecated ";
    private static final String EXPORTED_SECTION = "## Exported types";

    private static final int DECLARATION = 0;
    private static final int SUPERTYPE = 1;
    private static final int FIELD = 2;
    private static final int CONSTRUCTOR = 3;
    private static final int METHOD = 4;

    private ApiSurface() {}

    /**
     * Prints the API surface of the client classes in a directory or jar, or, with {@code --breaks},
     * the breaking changes between two snapshots (what {@code just api-surface-breaks} runs).
     *
     * @param args either the classes directory or jar to scan, whose classes and dependencies must be
     *     on the classpath (e.g. {@code java -cp target/test-classes:jfalkordb-0.10.1.jar:jedis-7.5.3.jar:...
     *     com.falkordb.ApiSurface jfalkordb-0.10.1.jar}); or {@code --breaks <old snapshot> <new
     *     snapshot> [allow]}, which exits with status 1 on a {@link #breaks breaking change} unless
     *     {@code allow} approves it
     * @throws IOException if the classes or snapshots can't be read
     */
    public static void main(String[] args) throws IOException {
        if (args.length >= 3 && args[0].equals("--breaks")) {
            List<String> breaks = breaks(read(Paths.get(args[1])), read(Paths.get(args[2])));
            boolean approved = args.length > 3 && args[3].equals("allow");
            if (breaks.isEmpty()) {
                System.out.println("No member of " + args[1] + " is removed or changed in " + args[2] + ".");
                return;
            }
            System.out.println("These changes to " + SNAPSHOT + " can break code compiled against the old"
                    + " API (even when the cause is a dependency bump): a removed or changed line breaks"
                    + " callers, a new abstract method breaks implementations");
            breaks.forEach(System.out::println);
            if (approved) {
                System.out.println("Approved as a breaking change (the PR has the `breaking-change` label).");
                return;
            }
            System.out.println("If this is intended, add the `breaking-change` label to the PR and mark its"
                    + " title with `!`, as for any other public-API break.");
            System.exit(1);
        }
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: ApiSurface <classes directory or jar> | --breaks <old snapshot> <new snapshot> [allow]");
        }
        System.out.print(render(Paths.get(args[0]), Thread.currentThread().getContextClassLoader()));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /**
     * Renders the surface of the client classes on this class' classpath.
     *
     * @return the snapshot text
     * @throws IOException if the classes can't be listed or loaded
     */
    static String render() throws IOException {
        Path root;
        try {
            root = Paths.get(FalkorDB.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        return render(root, FalkorDB.class.getClassLoader());
    }

    static String render(Path classes, ClassLoader loader) throws IOException {
        Set<Class<?>> exported = new TreeSet<>(Comparator.comparing(Class::getName));
        for (String name : classNames(classes)) {
            if (!name.startsWith(ROOT) || name.startsWith(INTERNAL)) {
                continue;
            }
            Class<?> type;
            try {
                type = Class.forName(name, false, loader);
            } catch (ClassNotFoundException | LinkageError e) {
                throw new IOException("Can't load " + name + " from " + classes + ": " + e, e);
            }
            if (isExported(type)) {
                exported.add(type);
            }
        }
        Set<Class<?>> reachable = new TreeSet<>(Comparator.comparing(Class::getName));
        for (Class<?> type : exported) {
            reachable.addAll(guarded(type, () -> referencedTypes(type)));
        }
        reachable.removeAll(exported);

        StringBuilder out = new StringBuilder()
                .append("# JFalkorDB public API surface: every public/protected member of every exported\n")
                .append("# type, inherited ones included, and the public instance members of the types our\n")
                .append("# own signatures reach. Checked by ApiSurfaceTest; after a reviewed change, regenerate\n")
                .append("# it with `")
                .append(UPDATE_COMMAND)
                .append("`. A removed or changed line is a change our users see.\n")
                .append("\n")
                .append(EXPORTED_SECTION)
                .append(" (com.falkordb, excluding com.falkordb.impl)\n");
        for (Class<?> type : exported) {
            out.append('\n').append(guarded(type, () -> describe(type, false)));
        }
        out.append("\n## Types reachable from the signatures exported types declare\n");
        for (Class<?> type : reachable) {
            out.append('\n').append(guarded(type, () -> describe(type, true)));
        }
        return out.toString();
    }

    /** Reflection loads every class a signature names; say which one is missing, and where. */
    private static <T> T guarded(Class<?> type, java.util.function.Supplier<T> reflection) {
        try {
            return reflection.get();
        } catch (LinkageError | TypeNotPresentException e) {
            throw new IllegalStateException(
                    "Can't read the API of " + type.getName() + ", a class it names is missing from the classpath: "
                            + e,
                    e);
        }
    }

    private static List<String> classNames(Path classes) throws IOException {
        List<String> names = new ArrayList<>();
        if (Files.isDirectory(classes)) {
            try (Stream<Path> files = Files.walk(classes)) {
                for (Path file : files.collect(Collectors.toList())) {
                    String relative = classes.relativize(file).toString().replace('\\', '/');
                    if (relative.endsWith(".class")) {
                        names.add(toClassName(relative));
                    }
                }
            }
        } else {
            try (JarFile jar = new JarFile(classes.toFile())) {
                for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements(); ) {
                    String entry = e.nextElement().getName();
                    if (entry.endsWith(".class") && !entry.startsWith("META-INF/")) {
                        names.add(toClassName(entry));
                    }
                }
            }
        }
        return names;
    }

    private static String toClassName(String path) {
        return path.substring(0, path.length() - ".class".length()).replace('/', '.');
    }

    private static boolean isExported(Class<?> type) {
        // package-info is a synthetic interface, so it is skipped here too.
        if (type.isSynthetic() || type.isAnonymousClass() || type.isLocalClass()) {
            return false;
        }
        int modifiers = type.getModifiers();
        Class<?> outer = type.getDeclaringClass();
        if (outer == null) {
            return Modifier.isPublic(modifiers);
        }
        return isExported(outer)
                && (Modifier.isPublic(modifiers)
                        || (Modifier.isProtected(modifiers) && !Modifier.isFinal(outer.getModifiers())));
    }

    private static boolean isJdk(Class<?> type) {
        String name = type.getName();
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.");
    }

    private static boolean isVisible(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    // --- the types an exported type's own declarations name ---

    private static Set<Class<?>> referencedTypes(Class<?> type) {
        List<Type> named = new ArrayList<>(Arrays.asList(type.getTypeParameters()));
        for (Type supertype : directSupertypes(type)) {
            if (supertype instanceof ParameterizedType) {
                // The raw supertype's members are already inlined; only its type arguments are named.
                named.addAll(Arrays.asList(((ParameterizedType) supertype).getActualTypeArguments()));
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (!field.isSynthetic() && isVisible(field.getModifiers())) {
                named.add(field.getGenericType());
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (!constructor.isSynthetic() && isVisible(constructor.getModifiers())) {
                named.addAll(Arrays.asList(constructor.getTypeParameters()));
                named.addAll(Arrays.asList(constructor.getGenericParameterTypes()));
                named.addAll(Arrays.asList(constructor.getGenericExceptionTypes()));
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (!method.isSynthetic() && !method.isBridge() && isVisible(method.getModifiers())) {
                named.addAll(Arrays.asList(method.getTypeParameters()));
                named.add(method.getGenericReturnType());
                named.addAll(Arrays.asList(method.getGenericParameterTypes()));
                named.addAll(Arrays.asList(method.getGenericExceptionTypes()));
            }
        }
        Set<Class<?>> classes = new LinkedHashSet<>();
        Set<Type> seen = new HashSet<>();
        for (Type t : named) {
            collectClasses(t, classes, seen);
        }
        Set<Class<?>> reachable = new LinkedHashSet<>();
        for (Class<?> c : classes) {
            if (!c.isPrimitive() && !isJdk(c) && !(c.getName().startsWith(ROOT) && isExported(c))) {
                reachable.add(c);
            }
        }
        return reachable;
    }

    private static void collectClasses(Type type, Set<Class<?>> into, Set<Type> seen) {
        if (!seen.add(type)) {
            return;
        }
        if (type instanceof Class) {
            Class<?> c = (Class<?>) type;
            while (c.isArray()) {
                c = c.getComponentType();
            }
            into.add(c);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType) type;
            collectClasses(p.getRawType(), into, seen);
            if (p.getOwnerType() != null) {
                collectClasses(p.getOwnerType(), into, seen);
            }
            for (Type argument : p.getActualTypeArguments()) {
                collectClasses(argument, into, seen);
            }
        } else if (type instanceof GenericArrayType) {
            collectClasses(((GenericArrayType) type).getGenericComponentType(), into, seen);
        } else if (type instanceof WildcardType) {
            WildcardType w = (WildcardType) type;
            for (Type bound : w.getUpperBounds()) {
                collectClasses(bound, into, seen);
            }
            for (Type bound : w.getLowerBounds()) {
                collectClasses(bound, into, seen);
            }
        } else if (type instanceof TypeVariable) {
            for (Type bound : ((TypeVariable<?>) type).getBounds()) {
                collectClasses(bound, into, seen);
            }
        }
    }

    // --- one type ---

    private static final class Line {
        final int category;
        final String name;
        final String text;

        Line(int category, String name, String text) {
            this.category = category;
            this.name = name;
            this.text = text;
        }
    }

    /**
     * Describes one type: a header line with its name, then one indented line per declaration,
     * supertype and member, sorted.
     *
     * @param type the type
     * @param reachable whether it is a reachable rather than an exported type, which lists only its
     *     public instance methods and fields
     * @return the description, ending with a newline
     */
    static String describe(Class<?> type, boolean reachable) {
        Map<TypeVariable<?>, Type> arguments = typeArguments(type);
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(DECLARATION, "", declaration(type)));
        String extendsWord = type.isInterface() ? "extends " : "implements ";
        Type superclass = type.getGenericSuperclass();
        if (superclass != null && superclass != Object.class) {
            lines.add(new Line(SUPERTYPE, "0", "extends " + type(superclass, arguments)));
        }
        for (Type supertype : type.getGenericInterfaces()) {
            lines.add(new Line(SUPERTYPE, "1", extendsWord + type(supertype, arguments)));
        }
        for (Class<?> supertype : allSupertypes(type)) {
            lines.add(new Line(SUPERTYPE, "2", "supertype " + supertypeAsSeenFrom(supertype, arguments)));
        }
        List<Field> fields = fields(type);
        Map<String, Long> declarations =
                fields.stream().collect(Collectors.groupingBy(Field::getName, Collectors.counting()));
        for (Field field : fields) {
            if (!reachable || isPublicInstance(field)) {
                long count = declarations.get(field.getName());
                lines.add(new Line(
                        FIELD,
                        field.getName(),
                        field(field, arguments) + (count > 1 ? " // ambiguous: " + count + " declarations" : "")));
            }
        }
        if (!reachable) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (!constructor.isSynthetic() && isVisible(constructor.getModifiers())) {
                    lines.add(new Line(CONSTRUCTOR, "", constructor(constructor, arguments)));
                }
            }
        }
        for (Method method : methods(type, arguments)) {
            if (!reachable || isPublicInstance(method)) {
                lines.add(new Line(METHOD, method.getName(), method(method, arguments)));
            }
        }
        lines.sort(Comparator.<Line>comparingInt(l -> l.category)
                .thenComparing(l -> l.name)
                .thenComparing(l -> l.text));
        StringBuilder out = new StringBuilder(type.getName()).append('\n');
        Set<String> seen = new HashSet<>();
        for (Line line : lines) {
            if (seen.add(line.text)) {
                out.append("  ").append(line.text).append('\n');
            }
        }
        return out.toString();
    }

    private static boolean isPublicInstance(Member member) {
        return Modifier.isPublic(member.getModifiers()) && !Modifier.isStatic(member.getModifiers());
    }

    private static String declaration(Class<?> type) {
        StringBuilder s = new StringBuilder("type ");
        if (type.isAnnotationPresent(Deprecated.class)) {
            s.append(DEPRECATED);
        }
        int m = type.getModifiers();
        s.append(Modifier.isPublic(m) ? "public " : "protected ");
        boolean plainClass = !type.isInterface() && !type.isEnum();
        if (plainClass && type.getDeclaringClass() != null && Modifier.isStatic(m)) {
            s.append("static ");
        }
        if (plainClass && Modifier.isAbstract(m)) {
            s.append("abstract ");
        }
        if (plainClass && Modifier.isFinal(m)) {
            s.append("final ");
        }
        if (type.isAnnotation()) {
            s.append("@interface ");
        } else if (type.isInterface()) {
            s.append("interface ");
        } else if (type.isEnum()) {
            s.append("enum ");
        } else {
            s.append("class ");
        }
        return s.append(type.getName())
                .append(typeParameters(type.getTypeParameters(), Collections.emptyMap()))
                .toString();
    }

    /** Every supertype except {@code Object}, sorted; a JDK supertype is listed but not climbed. */
    private static List<Class<?>> allSupertypes(Class<?> type) {
        Set<Class<?>> all = new TreeSet<>(Comparator.comparing(Class::getName));
        List<Class<?>> work = new ArrayList<>(Collections.singletonList(type));
        while (!work.isEmpty()) {
            Class<?> c = work.remove(work.size() - 1);
            List<Class<?>> parents = new ArrayList<>(Arrays.asList(c.getInterfaces()));
            if (c.getSuperclass() != null) {
                parents.add(c.getSuperclass());
            }
            for (Class<?> parent : parents) {
                // What a JDK type extends is the JDK's business and varies by version: for example,
                // java.lang.Enum implements java.lang.constant.Constable since JDK 12.
                if (parent != Object.class && all.add(parent) && !isJdk(parent)) {
                    work.add(parent);
                }
            }
        }
        return new ArrayList<>(all);
    }

    /**
     * A transitive supertype with the type arguments this type supplies to it, e.g. {@code
     * java.lang.Iterable<com.falkordb.Record>}: assignability depends on them, not just on the class.
     */
    private static String supertypeAsSeenFrom(Class<?> supertype, Map<TypeVariable<?>, Type> arguments) {
        String name = supertype.getName();
        Class<?> outer = supertype.getDeclaringClass();
        if (isInner(supertype)) {
            // An inner class sees its outer class's type variables, so those arguments count too.
            String owner = supertypeAsSeenFrom(outer, arguments);
            if (!owner.equals(outer.getName())) {
                name = owner + "$" + supertype.getSimpleName();
            }
        }
        TypeVariable<?>[] variables = supertype.getTypeParameters();
        // Raw is not the same as erased arguments: a raw Marker converts to a Marker<String>, a
        // Marker<Object> doesn't.
        if (variables.length == 0 || Arrays.stream(variables).allMatch(v -> arguments.get(v) instanceof Erased)) {
            return name;
        }
        return name + Arrays.stream(variables).map(v -> type(v, arguments)).collect(Collectors.joining(", ", "<", ">"));
    }

    /** A non-static member class, whose instances belong to an instance of the class around it. */
    private static boolean isInner(Class<?> type) {
        return type.getDeclaringClass() != null && !type.isInterface() && !Modifier.isStatic(type.getModifiers());
    }

    private static List<Type> directSupertypes(Class<?> type) {
        List<Type> supertypes = new ArrayList<>(Arrays.asList(type.getGenericInterfaces()));
        if (type.getGenericSuperclass() != null) {
            supertypes.add(type.getGenericSuperclass());
        }
        return supertypes;
    }

    /** The type arguments {@code type} supplies, directly or indirectly, to each generic supertype. */
    private static Map<TypeVariable<?>, Type> typeArguments(Class<?> type) {
        Map<TypeVariable<?>, Type> arguments = new HashMap<>();
        List<Type> work = new ArrayList<>(directSupertypes(type));
        Set<Class<?>> visited = new HashSet<>();
        while (!work.isEmpty()) {
            Type supertype = work.remove(work.size() - 1);
            Class<?> raw;
            if (supertype instanceof ParameterizedType) {
                ParameterizedType p = (ParameterizedType) supertype;
                raw = (Class<?>) p.getRawType();
                // Outer<String>.Inner supplies Outer's T as well as Inner's own type variables.
                for (Type owner = p;
                        owner instanceof ParameterizedType;
                        owner = ((ParameterizedType) owner).getOwnerType()) {
                    ParameterizedType q = (ParameterizedType) owner;
                    TypeVariable<?>[] variables = ((Class<?>) q.getRawType()).getTypeParameters();
                    Type[] actual = q.getActualTypeArguments();
                    for (int i = 0; i < variables.length; i++) {
                        arguments.putIfAbsent(variables[i], actual[i]);
                    }
                }
            } else {
                raw = (Class<?>) supertype;
                // Inherited through a raw reference, a generic supertype's members are erased (an inner
                // class's outer type variables too): print its type variables as their erasure, so
                // that a change of bound (a change of the JVM signature callers are compiled against)
                // shows.
                for (Class<?> c = raw; c != null; c = isInner(c) ? c.getDeclaringClass() : null) {
                    for (TypeVariable<?> variable : c.getTypeParameters()) {
                        arguments.putIfAbsent(variable, new Erased(erasure(variable, Collections.emptyMap())));
                    }
                }
            }
            if (visited.add(raw) && !isJdk(raw)) {
                work.addAll(directSupertypes(raw));
            }
        }
        return arguments;
    }

    /** The public/protected methods {@code type} has once overriding is resolved. */
    private static List<Method> methods(Class<?> type, Map<TypeVariable<?>, Type> arguments) {
        Map<String, List<Method>> bySignature = new TreeMap<>();
        for (Class<?> owner : hierarchy(type)) {
            for (Method method : owner.getDeclaredMethods()) {
                int m = method.getModifiers();
                if (method.isSynthetic() || method.isBridge() || !isVisible(m)) {
                    continue;
                }
                if (owner != type && owner.isInterface() && Modifier.isStatic(m)) {
                    continue; // static interface methods are not inherited
                }
                // Keyed on the parameters as this type sees them, so that an override of a method
                // of a generic supertype (set(Long) for set(T)) is recognised as one.
                String key = method.getName()
                        + Arrays.stream(method.getGenericParameterTypes())
                                .map(p -> erasure(p, arguments).getName())
                                .collect(Collectors.joining(",", "(", ")"));
                bySignature.computeIfAbsent(key, k -> new ArrayList<>()).add(method);
            }
        }
        List<Method> result = new ArrayList<>();
        for (List<Method> candidates : bySignature.values()) {
            result.addAll(mostSpecific(candidates));
        }
        return result;
    }

    /**
     * The public/protected fields {@code type} has once hiding is resolved, leaving out what JDK types
     * declare (those can't change with a dependency and differ between JDK versions).
     */
    private static List<Field> fields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Field field : fieldMembers(type, new HashMap<>())) {
            if (isVisible(field.getModifiers()) && !isJdk(field.getDeclaringClass())) {
                result.add(field);
            }
        }
        return result;
    }

    /**
     * The fields {@code type} declares plus those it inherits along each path: a declaration hides the
     * same-named fields of its own supertypes only, so in a diamond a field hidden on one path still
     * comes down the other. Every field takes part in hiding, whatever its access: a private field in a
     * superclass hides the same-named fields above it, so callers lose them. Like {@link
     * #allSupertypes}, a JDK type is not climbed.
     */
    private static Set<Field> fieldMembers(Class<?> type, Map<Class<?>, Set<Field>> memo) {
        Set<Field> cached = memo.get(type);
        if (cached != null) {
            return cached;
        }
        Set<Field> members = new LinkedHashSet<>();
        Set<String> declared = new HashSet<>();
        for (Field field : type.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                members.add(field);
                declared.add(field.getName());
            }
        }
        if (!isJdk(type)) {
            List<Class<?>> parents = new ArrayList<>(Arrays.asList(type.getInterfaces()));
            if (type.getSuperclass() != null && type.getSuperclass() != Object.class) {
                parents.add(type.getSuperclass());
            }
            for (Class<?> parent : parents) {
                for (Field field : fieldMembers(parent, memo)) {
                    if (!declared.contains(field.getName())) {
                        members.add(field);
                    }
                }
            }
        }
        memo.put(type, members);
        return members;
    }

    /** {@code type} and every supertype, JDK ones included so that they can override or hide. */
    private static List<Class<?>> hierarchy(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        hierarchy.add(type);
        hierarchy.addAll(allSupertypes(type));
        return hierarchy;
    }

    /**
     * Drops the methods another candidate overrides (by declaring-type assignability, so diamonds
     * resolve), then lets a class declaration win over interface ones, then drops what JDK types
     * declare: those can't change with a dependency and differ between JDK versions.
     */
    private static List<Method> mostSpecific(List<Method> candidates) {
        List<Method> kept = new ArrayList<>();
        for (Method candidate : candidates) {
            Class<?> owner = candidate.getDeclaringClass();
            boolean overridden = candidates.stream()
                    .map(Member::getDeclaringClass)
                    .anyMatch(other -> other != owner && owner.isAssignableFrom(other));
            if (!overridden) {
                kept.add(candidate);
            }
        }
        if (kept.stream().anyMatch(m -> !m.getDeclaringClass().isInterface())) {
            kept.removeIf(m -> m.getDeclaringClass().isInterface());
        }
        kept.removeIf(m -> isJdk(m.getDeclaringClass()));
        return kept;
    }

    // --- members ---

    private static String modifiers(Member member, boolean isDefault) {
        int m = member.getModifiers();
        StringBuilder s = new StringBuilder();
        if (((AnnotatedElement) member).isAnnotationPresent(Deprecated.class)) {
            s.append(DEPRECATED);
        }
        s.append(Modifier.isPublic(m) ? "public " : "protected ");
        if (Modifier.isStatic(m)) {
            s.append("static ");
        }
        if (Modifier.isAbstract(m)) {
            s.append("abstract ");
        }
        if (isDefault) {
            s.append("default ");
        }
        if (Modifier.isFinal(m)) {
            s.append("final ");
        }
        return s.toString();
    }

    private static String field(Field field, Map<TypeVariable<?>, Type> arguments) {
        String generic =
                "field " + modifiers(field, false) + type(field.getGenericType(), arguments) + " " + field.getName();
        return erasure(field.getGenericType(), arguments) == field.getType()
                ? generic
                : generic + " // erased: " + field.getType().getTypeName();
    }

    private static String constructor(Constructor<?> constructor, Map<TypeVariable<?>, Type> arguments) {
        Type[] parameters = constructor.getGenericParameterTypes();
        Class<?>[] erased = constructor.getParameterTypes();
        if (parameters.length == erased.length - 1 && isInner(constructor.getDeclaringClass())) {
            // An inner class constructor's generic signature can leave out the outer instance.
            List<Type> withOuter = new ArrayList<>(Collections.singletonList(erased[0]));
            withOuter.addAll(Arrays.asList(parameters));
            parameters = withOuter.toArray(new Type[0]);
        } else if (parameters.length != erased.length) {
            parameters = erased;
        }
        return "constructor " + modifiers(constructor, false)
                + typeParametersWithSpace(constructor.getTypeParameters(), arguments)
                + constructor.getDeclaringClass().getName()
                + parameters(parameters, constructor.isVarArgs(), arguments)
                + throwsClause(constructor.getGenericExceptionTypes(), arguments);
    }

    private static String method(Method method, Map<TypeVariable<?>, Type> arguments) {
        String generic = "method " + modifiers(method, method.isDefault())
                + typeParametersWithSpace(method.getTypeParameters(), arguments)
                + type(method.getGenericReturnType(), arguments) + " " + method.getName()
                + parameters(method.getGenericParameterTypes(), method.isVarArgs(), arguments)
                + throwsClause(method.getGenericExceptionTypes(), arguments);
        Type[] generics = method.getGenericParameterTypes();
        Class<?>[] erased = method.getParameterTypes();
        boolean erasureShows = erasure(method.getGenericReturnType(), arguments) == method.getReturnType()
                && generics.length == erased.length;
        for (int i = 0; erasureShows && i < generics.length; i++) {
            erasureShows = erasure(generics[i], arguments) == erased[i];
        }
        if (erasureShows) {
            return generic;
        }
        return generic + " // erased: " + method.getReturnType().getTypeName() + " " + method.getName()
                + Arrays.stream(erased).map(Class::getTypeName).collect(Collectors.joining(", ", "(", ")"));
    }

    private static String parameters(Type[] types, boolean varArgs, Map<TypeVariable<?>, Type> arguments) {
        List<String> rendered = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            String t = type(types[i], arguments);
            if (varArgs && i == types.length - 1 && t.endsWith("[]")) {
                t = t.substring(0, t.length() - 2) + "...";
            }
            rendered.add(t);
        }
        return rendered.stream().collect(Collectors.joining(", ", "(", ")"));
    }

    private static String throwsClause(Type[] exceptions, Map<TypeVariable<?>, Type> arguments) {
        if (exceptions.length == 0) {
            return "";
        }
        return Arrays.stream(exceptions)
                .map(e -> type(e, arguments))
                .sorted()
                .collect(Collectors.joining(", ", " throws ", ""));
    }

    private static String typeParametersWithSpace(TypeVariable<?>[] variables, Map<TypeVariable<?>, Type> arguments) {
        String s = typeParameters(variables, arguments);
        return s.isEmpty() ? "" : s + " ";
    }

    private static String typeParameters(TypeVariable<?>[] variables, Map<TypeVariable<?>, Type> arguments) {
        if (variables.length == 0) {
            return "";
        }
        List<String> rendered = new ArrayList<>();
        for (TypeVariable<?> variable : variables) {
            Type[] bounds = variable.getBounds();
            if (bounds.length == 1 && bounds[0] == Object.class) {
                rendered.add(variable.getName());
            } else {
                rendered.add(variable.getName() + " extends "
                        + Arrays.stream(bounds).map(b -> type(b, arguments)).collect(Collectors.joining(" & ")));
            }
        }
        return rendered.stream().collect(Collectors.joining(", ", "<", ">"));
    }

    // --- types ---

    /** Prints a type, replacing a supertype's type variables with what the subtype supplies. */
    private static String type(Type type, Map<TypeVariable<?>, Type> arguments) {
        if (type instanceof Class) {
            Class<?> c = (Class<?>) type;
            return c.isArray() ? type(c.getComponentType(), arguments) + "[]" : c.getName();
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType) type;
            Class<?> raw = (Class<?>) p.getRawType();
            String name = p.getOwnerType() instanceof ParameterizedType
                    ? type(p.getOwnerType(), arguments) + "$" + raw.getSimpleName()
                    : raw.getName();
            return name
                    + Arrays.stream(p.getActualTypeArguments())
                            .map(a -> type(a, arguments))
                            .collect(Collectors.joining(", ", "<", ">"));
        }
        if (type instanceof GenericArrayType) {
            return type(((GenericArrayType) type).getGenericComponentType(), arguments) + "[]";
        }
        if (type instanceof WildcardType) {
            WildcardType w = (WildcardType) type;
            if (w.getLowerBounds().length > 0) {
                return "? super " + type(w.getLowerBounds()[0], arguments);
            }
            Type upper = w.getUpperBounds()[0];
            return upper == Object.class ? "?" : "? extends " + type(upper, arguments);
        }
        if (type instanceof TypeVariable) {
            Type argument = arguments.get(type);
            return argument != null ? type(argument, arguments) : ((TypeVariable<?>) type).getName();
        }
        if (type instanceof Erased) {
            return type(((Erased) type).erasure, arguments);
        }
        throw new IllegalArgumentException("Unknown kind of Type: " + type.getClass());
    }

    private static Class<?> erasure(Type type, Map<TypeVariable<?>, Type> arguments) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        }
        if (type instanceof GenericArrayType) {
            Class<?> component = erasure(((GenericArrayType) type).getGenericComponentType(), arguments);
            return Array.newInstance(component, 0).getClass();
        }
        if (type instanceof TypeVariable) {
            Type argument = arguments.get(type);
            return argument != null
                    ? erasure(argument, arguments)
                    : erasure(((TypeVariable<?>) type).getBounds()[0], arguments);
        }
        if (type instanceof WildcardType) {
            return erasure(((WildcardType) type).getUpperBounds()[0], arguments);
        }
        if (type instanceof Erased) {
            return ((Erased) type).erasure;
        }
        throw new IllegalArgumentException("Unknown kind of Type: " + type.getClass());
    }

    /**
     * What a type variable stands for when its class is inherited raw: members print it as its
     * erasure, and the supertype line stays raw.
     */
    private static final class Erased implements Type {
        final Class<?> erasure;

        Erased(Class<?> erasure) {
            this.erasure = erasure;
        }
    }

    // --- comparing two snapshots ---

    /**
     * Lists what changed between two snapshots, grouped by type: {@code -} for a line only the
     * expected snapshot has, {@code +} for one only the actual has, and {@code ~} for a member whose
     * only change is that it was deprecated or undeprecated. Comment lines are ignored.
     *
     * @param expected the committed snapshot
     * @param actual the snapshot of the code
     * @return one line per change, or an empty list when the two describe the same API
     */
    static List<String> diff(String expected, String actual) {
        Map<String, Set<String>> before = parse(expected);
        Map<String, Set<String>> after = parse(actual);
        Set<String> types = new TreeSet<>(before.keySet());
        types.addAll(after.keySet());
        List<String> changes = new ArrayList<>();
        for (String type : types) {
            Set<String> removed = new TreeSet<>(before.getOrDefault(type, Collections.emptySet()));
            removed.removeAll(after.getOrDefault(type, Collections.emptySet()));
            Set<String> added = new TreeSet<>(after.getOrDefault(type, Collections.emptySet()));
            added.removeAll(before.getOrDefault(type, Collections.emptySet()));
            if (removed.isEmpty() && added.isEmpty()) {
                continue;
            }
            Map<String, String> addedByUndeprecated = new HashMap<>();
            for (String line : added) {
                addedByUndeprecated.put(line.replace(DEPRECATED, ""), line);
            }
            List<String> lines = new ArrayList<>();
            for (String line : removed) {
                String counterpart = addedByUndeprecated.get(line.replace(DEPRECATED, ""));
                if (counterpart != null) {
                    lines.add("  ~ " + counterpart
                            + (counterpart.contains(DEPRECATED) ? "  (now deprecated)" : "  (no longer deprecated)"));
                    added.remove(counterpart);
                } else {
                    lines.add("  - " + line);
                }
            }
            for (String line : added) {
                lines.add("  + " + line);
            }
            changes.add(type
                    + (before.containsKey(type)
                            ? (after.containsKey(type) ? "" : "  (type removed)")
                            : "  (type added)"));
            changes.addAll(lines);
        }
        return changes;
    }

    /** The lines of a text, whether it ends them with LF or CRLF. */
    static List<String> lines(String text) {
        return new BufferedReader(new StringReader(text)).lines().collect(Collectors.toList());
    }

    /**
     * The breaking part of {@link #diff}, under their types: every line of the old snapshot the new
     * one no longer has (a removed or changed member, supertype or declaration), and every abstract
     * method added to an exported interface or abstract class that already existed, which breaks the
     * classes implementing or extending it (japicmp fails on that too). Other additions, and
     * deprecation-only changes, are not breaks.
     *
     * @param expected the old snapshot
     * @param actual the new snapshot
     * @return the type lines and their breaking lines, or an empty list when nothing breaks
     */
    static List<String> breaks(String expected, String actual) {
        Set<String> extendable = extendableExportedTypes(actual);
        List<String> breaks = new ArrayList<>();
        String header = null;
        String type = null;
        boolean existed = false;
        for (String line : diff(expected, actual)) {
            if (!line.startsWith(" ")) {
                header = line;
                type = line.contains("  (") ? line.substring(0, line.indexOf("  (")) : line;
                existed = !line.endsWith("(type added)");
                continue;
            }
            boolean removed = line.startsWith("  - ");
            boolean newAbstractMethod = existed
                    && extendable.contains(type)
                    && line.startsWith("  + method ")
                    && line.contains(" abstract ");
            if (removed || newAbstractMethod) {
                if (header != null) {
                    breaks.add(header);
                    header = null;
                }
                breaks.add(newAbstractMethod ? line + "  (new abstract method: implementations break)" : line);
            }
        }
        return breaks;
    }

    /** The exported interfaces and abstract classes: the types callers may implement or extend. */
    private static Set<String> extendableExportedTypes(String snapshot) {
        Set<String> types = new HashSet<>();
        boolean exported = false;
        String type = null;
        for (String line : lines(snapshot)) {
            if (line.startsWith("## ")) {
                exported = line.startsWith(EXPORTED_SECTION);
            } else if (!line.isEmpty() && !line.startsWith("#") && !line.startsWith(" ")) {
                type = line;
            } else if (exported
                    && type != null
                    && line.startsWith("  type ")
                    && (line.contains(" interface ") || line.contains(" abstract class "))) {
                types.add(type);
            }
        }
        return types;
    }

    private static Map<String, Set<String>> parse(String snapshot) {
        Map<String, Set<String>> types = new LinkedHashMap<>();
        Set<String> current = null;
        for (String line : lines(snapshot)) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith(" ")) {
                if (current == null) {
                    throw new IllegalArgumentException("Member line before any type: " + line);
                }
                current.add(line.trim());
            } else {
                current = types.computeIfAbsent(line, k -> new LinkedHashSet<>());
            }
        }
        return types;
    }
}
