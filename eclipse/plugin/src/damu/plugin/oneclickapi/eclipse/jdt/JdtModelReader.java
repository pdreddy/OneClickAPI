package damu.plugin.oneclickapi.eclipse.jdt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

import damu.plugin.oneclickapi.eclipse.RawAnnotation;
import damu.plugin.oneclickapi.eclipse.RawClass;
import damu.plugin.oneclickapi.eclipse.RawField;

/**
 * Reads a class through the JDT model into the plain beans that {@code ModelFactory} understands. This is the
 * Eclipse counterpart of the IntelliJ plugin's PSI reader and the only place that touches JDT types.
 */
public final class JdtModelReader {

    private JdtModelReader() {
    }

    public static RawClass read(IType type, IPath projectRelativeBase) throws JavaModelException {
        RawClass c = new RawClass();
        c.setName(type.getElementName());
        c.setPackageName(type.getPackageFragment().getElementName());
        c.setAnnotations(annotations(type));

        List<IType> chain = new ArrayList<>();
        try {
            ITypeHierarchy h = type.newSupertypeHierarchy(null);
            for (IType t = type; t != null; t = h.getSuperclass(t)) {
                if ("java.lang.Object".equals(t.getFullyQualifiedName())) {
                    break;
                }
                chain.add(0, t); // superclass fields first, like PsiClass.allFields
            }
        } catch (JavaModelException | RuntimeException e) {
            chain.clear();
            chain.add(type);
        }
        List<RawField> fields = new ArrayList<>();
        for (IType t : chain) {
            for (IField f : t.getFields()) {
                if (!Flags.isEnum(f.getFlags())) {
                    fields.add(field(f, t));
                }
            }
        }
        c.setFields(fields);

        if (type.getResource() instanceof IFile file) {
            c.setSourcePath(file.getProjectRelativePath().toString());
        }
        return c;
    }

    private static RawField field(IField f, IType owner) throws JavaModelException {
        RawField r = new RawField();
        r.setName(f.getElementName());
        int flags = f.getFlags();
        r.setStatic(Flags.isStatic(flags));
        r.setTransient(Flags.isTransient(flags));
        r.setAnnotations(annotations(f));

        String sig = f.getTypeSignature();
        r.setType(simpleTypeText(sig));
        r.setPrimitive(Signature.getTypeSignatureKind(sig) == Signature.BASE_TYPE_SIGNATURE && !sig.equals("V"));

        List<String> imports = new ArrayList<>();
        collectImports(owner, sig, imports);
        r.setTypeImports(imports);

        // Relation target and enum: resolve the element type of the field (List<Book> -> Book).
        IType typeRef = resolve(owner, elementSignature(sig));
        boolean toMany = isCollection(sig);
        if (typeRef != null) {
            if (typeRef.isEnum() && !toMany) {
                r.setEnumFqn(typeRef.getFullyQualifiedName('.'));
                List<String> constants = new ArrayList<>();
                for (IField ef : typeRef.getFields()) {
                    if (Flags.isEnum(ef.getFlags())) {
                        constants.add(ef.getElementName());
                    }
                }
                r.setEnumConstants(constants);
            }
            if (hasRelation(r)) {
                r.setRelationTargetName(typeRef.getElementName());
                r.setRelationTargetFqn(typeRef.getFullyQualifiedName('.'));
                IField id = idOf(typeRef);
                if (id != null) {
                    r.setRelationTargetIdName(id.getElementName());
                    r.setRelationTargetIdType(boxed(simpleTypeText(id.getTypeSignature())));
                }
            }
        }
        return r;
    }

    private static boolean hasRelation(RawField r) {
        for (RawAnnotation a : r.getAnnotations()) {
            String q = a.getQualifiedName();
            if (q.endsWith(".ManyToOne") || q.endsWith(".OneToOne") || q.endsWith(".OneToMany") || q.endsWith(".ManyToMany")) {
                return true;
            }
        }
        return false;
    }

    private static IField idOf(IType type) throws JavaModelException {
        IField byName = null;
        for (IField f : type.getFields()) {
            if (f.getAnnotation("Id").exists() || f.getAnnotation("EmbeddedId").exists()
                    || f.getAnnotation("jakarta.persistence.Id").exists() || f.getAnnotation("javax.persistence.Id").exists()) {
                return f;
            }
            if (byName == null && f.getElementName().equalsIgnoreCase("id")) {
                byName = f;
            }
        }
        return byName;
    }

    // ---------------------------------------------------------------------------------- annotations

    private static List<RawAnnotation> annotations(IAnnotatable owner) throws JavaModelException {
        List<RawAnnotation> out = new ArrayList<>();
        for (IAnnotation a : owner.getAnnotations()) {
            RawAnnotation r = new RawAnnotation();
            r.setQualifiedName(qualify(a));
            Map<String, Object> attrs = new LinkedHashMap<>();
            StringBuilder src = new StringBuilder("@").append(a.getElementName());
            IMemberValuePair[] pairs = a.getMemberValuePairs();
            if (pairs.length > 0) {
                src.append('(');
                for (int i = 0; i < pairs.length; i++) {
                    IMemberValuePair p = pairs[i];
                    Object v = literal(p);
                    attrs.put(p.getMemberName(), v);
                    if (i > 0) {
                        src.append(", ");
                    }
                    src.append(p.getMemberName()).append(" = ").append(sourceOf(p.getValue(), p.getValueKind()));
                }
                src.append(')');
            }
            r.setAttributes(attrs);
            r.setSource(src.toString());
            out.add(r);
        }
        return out;
    }

    /** Resolves {@code @Entity} to {@code jakarta.persistence.Entity} using the imports of the declaring type. */
    private static String qualify(IAnnotation a) {
        String name = a.getElementName();
        if (name.indexOf('.') >= 0) {
            return name;
        }
        try {
            IType declaring = (IType) (a.getAncestor(org.eclipse.jdt.core.IJavaElement.TYPE));
            if (declaring != null) {
                String[][] res = declaring.resolveType(name);
                if (res != null && res.length > 0) {
                    return res[0][0].isEmpty() ? res[0][1] : res[0][0] + "." + res[0][1];
                }
            }
        } catch (JavaModelException | RuntimeException e) {
            // unresolved: fall through to the simple name
        }
        return name;
    }

    private static Object literal(IMemberValuePair p) {
        Object v = p.getValue();
        switch (p.getValueKind()) {
            case IMemberValuePair.K_STRING:
            case IMemberValuePair.K_BOOLEAN:
            case IMemberValuePair.K_INT:
                return v;
            case IMemberValuePair.K_LONG:
                return v instanceof Number n ? Integer.valueOf(n.intValue()) : null;
            default:
                return null;
        }
    }

    private static String sourceOf(Object value, int kind) {
        if (value instanceof String s && kind == IMemberValuePair.K_STRING) {
            return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
        if (value instanceof Object[] arr) {
            StringBuilder b = new StringBuilder("{");
            for (int i = 0; i < arr.length; i++) {
                b.append(i > 0 ? ", " : "").append(sourceOf(arr[i], kind));
            }
            return b.append('}').toString();
        }
        return String.valueOf(value);
    }

    // ------------------------------------------------------------------------------------ type text

    /** {@code Ljava.util.List<Lcom.acme.Book;>;} -> {@code List<Book>} */
    static String simpleTypeText(String signature) {
        String s = Signature.toString(signature);
        return s.replaceAll("[\\w$]+(\\.[\\w$]+)*\\.([A-Z][\\w$]*)", "$2").replace(" ", "");
    }

    private static boolean isCollection(String signature) {
        String raw = Signature.toString(Signature.getTypeErasure(signature));
        String simple = raw.substring(raw.lastIndexOf('.') + 1);
        return simple.equals("List") || simple.equals("Set") || simple.equals("Collection") || simple.equals("Iterable");
    }

    private static String elementSignature(String signature) {
        String[] args = Signature.getTypeArguments(signature);
        if (args.length == 1) {
            String a = args[0];
            return a.startsWith("+") || a.startsWith("-") ? a.substring(1) : a;
        }
        if (Signature.getArrayCount(signature) > 0) {
            return Signature.getElementType(signature);
        }
        return signature;
    }

    private static void collectImports(IType owner, String signature, List<String> out) {
        for (String arg : Signature.getTypeArguments(signature)) {
            collectImports(owner, arg.startsWith("+") || arg.startsWith("-") ? arg.substring(1) : arg, out);
        }
        String element = Signature.getArrayCount(signature) > 0 ? Signature.getElementType(signature) : signature;
        String erased = Signature.getTypeErasure(element);
        if (Signature.getTypeSignatureKind(erased) != Signature.CLASS_TYPE_SIGNATURE) {
            return;
        }
        IType t = resolve(owner, erased);
        String q = t != null ? t.getFullyQualifiedName('.') : qualifiedFromSignature(erased);
        if (q == null || q.isEmpty()) {
            return;
        }
        boolean javaLang = q.startsWith("java.lang.") && q.indexOf('.', "java.lang.".length()) < 0;
        if (!javaLang && q.indexOf('.') > 0 && !out.contains(q)) {
            out.add(q);
        }
    }

    private static String qualifiedFromSignature(String sig) {
        String s = Signature.toString(sig);
        return s.indexOf('.') > 0 ? s : null;
    }

    private static IType resolve(IType owner, String signature) {
        try {
            if (Signature.getTypeSignatureKind(signature) != Signature.CLASS_TYPE_SIGNATURE) {
                return null;
            }
            String name = Signature.toString(Signature.getTypeErasure(signature));
            String[][] res = owner.resolveType(name);
            IJavaProject project = owner.getJavaProject();
            if (res != null && res.length > 0) {
                String fqn = res[0][0].isEmpty() ? res[0][1] : res[0][0] + "." + res[0][1];
                return project.findType(fqn.replace('$', '.'));
            }
            // a fully qualified signature (Ljava.util.List;) resolves directly
            return project.findType(name);
        } catch (JavaModelException | RuntimeException e) {
            return null;
        }
    }

    private static String boxed(String type) {
        switch (type) {
            case "int": return "Integer";
            case "long": return "Long";
            case "boolean": return "Boolean";
            case "double": return "Double";
            case "float": return "Float";
            case "short": return "Short";
            case "byte": return "Byte";
            case "char": return "Character";
            default: return type;
        }
    }
}
