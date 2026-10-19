package damu.plugin.oneclickapi.eclipse.jdt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

/** Collects what {@code ApiGenerator} needs to know about a project: build files, libraries, existing sources. */
public final class ProjectScanner {

    private static final String[] BUILD_FILES = { "pom.xml", "build.gradle.kts", "build.gradle" };
    private static final Pattern GRADLE_CACHE = Pattern.compile("files-2\\.1/([^/]+)/([^/]+)/([^/]+)/[^/]+/[^/]+\\.jar$");
    private static final Pattern MAVEN_REPO = Pattern.compile("repository/(.+)/([^/]+)/([^/]+)/\\2-\\3[^/]*\\.jar$");

    private ProjectScanner() {
    }

    public static Map<String, String> buildFiles(IProject project) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : BUILD_FILES) {
            IFile f = project.getFile(new Path(name));
            if (f.exists()) {
                try (InputStream in = f.getContents(true)) {
                    out.put(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                } catch (CoreException | IOException e) {
                    // unreadable build file: treat as absent
                }
            }
        }
        return out;
    }

    /** {@code group:artifact:version} for every library jar that sits in a Maven repository or the Gradle cache. */
    public static List<String> libraryCoordinates(IJavaProject project) {
        List<String> out = new ArrayList<>();
        try {
            for (IClasspathEntry e : project.getResolvedClasspath(true)) {
                if (e.getEntryKind() != IClasspathEntry.CPE_LIBRARY) {
                    continue;
                }
                String path = e.getPath().toString();
                Matcher g = GRADLE_CACHE.matcher(path);
                if (g.find()) {
                    out.add(g.group(1) + ":" + g.group(2) + ":" + g.group(3));
                    continue;
                }
                Matcher m = MAVEN_REPO.matcher(path);
                if (m.find()) {
                    out.add(m.group(1).replace('/', '.') + ":" + m.group(2) + ":" + m.group(3));
                }
            }
        } catch (JavaModelException e) {
            // no classpath yet: the build file text still tells most of the story
        }
        return out;
    }

    /** The project's {@code @SpringBootApplication} class (smallest name wins when there are several). */
    public static String applicationClass(IJavaProject project) {
        String best = null;
        try {
            for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) {
                if (root.getKind() != IPackageFragmentRoot.K_SOURCE) {
                    continue;
                }
                for (var child : root.getChildren()) {
                    for (ICompilationUnit cu : ((IPackageFragment) child).getCompilationUnits()) {
                        for (IType t : cu.getTypes()) {
                            if (t.getAnnotation("SpringBootApplication").exists()
                                    || t.getAnnotation("org.springframework.boot.autoconfigure.SpringBootApplication").exists()) {
                                String fqn = t.getFullyQualifiedName('.');
                                if (best == null || fqn.compareTo(best) < 0) {
                                    best = fqn;
                                }
                            }
                        }
                    }
                }
            }
        } catch (JavaModelException e) {
            // best effort
        }
        return best;
    }

    /** Project-relative paths of the {@code .java} files under src/main/java and src/test/java. */
    public static List<String> existingJavaFiles(IProject project) {
        List<String> out = new ArrayList<>();
        for (String root : new String[] { "src/main/java", "src/test/java" }) {
            var folder = project.getFolder(new Path(root));
            if (!folder.exists()) {
                continue;
            }
            try {
                folder.accept(r -> {
                    if (r.getType() == IResource.FILE && r.getName().endsWith(".java")) {
                        out.add(r.getProjectRelativePath().toString());
                    }
                    return true;
                });
            } catch (CoreException e) {
                // best effort
            }
        }
        return out;
    }
}
