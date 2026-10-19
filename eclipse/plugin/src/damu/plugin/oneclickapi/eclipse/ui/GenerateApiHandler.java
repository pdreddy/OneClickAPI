package damu.plugin.oneclickapi.eclipse.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;

import damu.plugin.oneclickapi.core.model.EntityModel;
import damu.plugin.oneclickapi.core.options.GenerationOptions;
import damu.plugin.oneclickapi.core.preset.PresetStore;
import damu.plugin.oneclickapi.core.write.GenerationReport;
import damu.plugin.oneclickapi.eclipse.ApiGenerator;
import damu.plugin.oneclickapi.eclipse.ModelFactory;
import damu.plugin.oneclickapi.eclipse.Prepared;
import damu.plugin.oneclickapi.eclipse.RawClass;
import damu.plugin.oneclickapi.eclipse.jdt.EclipseProjectFiles;
import damu.plugin.oneclickapi.eclipse.jdt.JdtModelReader;
import damu.plugin.oneclickapi.eclipse.jdt.ProjectScanner;

/** Entry point of the "Generate API (OneClickAPI)..." command: collect classes, ask, preview, write, report. */
public class GenerateApiHandler extends AbstractHandler {

    /** {@code true} in the "tests only" command: generates the test classes of the selected classes and nothing else. */
    protected boolean testsOnly() {
        return false;
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        List<IType> types = selectedTypes(event);
        if (types.isEmpty()) {
            MessageDialog.openInformation(shell, "OneClickAPI", "Select a Java class (an @Entity or a plain POJO) first.");
            return null;
        }

        // One generation per project: each project is a module with its own build file and src/main/java.
        Map<IJavaProject, List<IType>> byProject = new LinkedHashMap<>();
        for (IType t : types) {
            byProject.computeIfAbsent(t.getJavaProject(), k -> new ArrayList<>()).add(t);
        }
        for (Map.Entry<IJavaProject, List<IType>> e : byProject.entrySet()) {
            try {
                generate(shell, e.getKey(), e.getValue());
            } catch (JavaModelException ex) {
                MessageDialog.openError(shell, "OneClickAPI", "Cannot read " + e.getKey().getElementName() + ": " + ex.getMessage());
            }
        }
        return null;
    }

    private void generate(Shell shell, IJavaProject javaProject, List<IType> types) throws JavaModelException {
        IProject project = javaProject.getProject();
        if (!project.getFolder("src/main/java").exists()) {
            MessageDialog.openWarning(shell, "OneClickAPI",
                "Cannot find src/main/java in project " + project.getName() + "; skipped.");
            return;
        }

        EclipseProjectFiles files = new EclipseProjectFiles(project);
        File templates = project.getLocation() == null ? null
            : new File(project.getLocation().toFile(), PresetStore.TEMPLATE_DIR);
        ApiGenerator generator = new ApiGenerator(
            files,
            ProjectScanner.buildFiles(project),
            ProjectScanner.libraryCoordinates(javaProject),
            ProjectScanner.applicationClass(javaProject),
            ProjectScanner.existingJavaFiles(project),
            templates);

        List<EntityModel> models = new ArrayList<>();
        List<String> classNames = new ArrayList<>();
        for (IType t : types) {
            RawClass raw = JdtModelReader.read(t, project.getFullPath());
            models.add(ModelFactory.build(raw));
            classNames.add(t.getElementName());
        }
        String names = String.join(", ", classNames);
        String defaultPackage = commonPackage(types);

        GenerationOptions options;
        boolean preview;
        if (testsOnly()) {
            TestsOnlyDialog dialog = new TestsOnlyDialog(shell, names, defaultPackage);
            if (dialog.open() != TestsOnlyDialog.OK) {
                return;
            }
            options = generator.testsOnlyOptions(dialog.basePackage(), dialog.service(), dialog.controller(),
                dialog.mapper(), dialog.integration(), dialog.http());
            preview = dialog.wantsPreview();
        } else {
            GenerateApiDialog dialog = new GenerateApiDialog(shell, generator, classNames, defaultPackage);
            if (dialog.open() != GenerateApiDialog.OK) {
                return;
            }
            options = dialog.options();
            preview = dialog.wantsPreview();
        }

        Prepared prepared;
        try {
            prepared = testsOnly() ? generator.prepareTests(models, options) : generator.prepare(models, options);
        } catch (RuntimeException ex) {
            MessageDialog.openError(shell, "OneClickAPI", "Could not prepare the files: " + ex.getMessage());
            return;
        }

        Set<String> excluded = Set.of();
        if (preview) {
            PreviewDialog previewDialog = new PreviewDialog(shell, generator.resolve(prepared, options), prepared.getWarnings());
            if (previewDialog.open() != PreviewDialog.OK) {
                return;
            }
            excluded = previewDialog.excludedPaths();
        }

        Set<String> skip = excluded;
        GenerationOptions chosen = options;
        Job job = new Job("OneClickAPI: generating " + names) {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                GenerationReport report = generator.execute(prepared, chosen, skip);
                try {
                    project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
                } catch (Exception ignored) {
                    // the files are written either way
                }
                Display.getDefault().asyncExec(() -> new ReportDialog(shell, report).open());
                return Status.OK_STATUS;
            }
        };
        job.setRule(project);
        job.schedule();
    }

    private static String commonPackage(List<IType> types) {
        String pkg = types.get(0).getPackageFragment().getElementName();
        // entities usually live in <base>.entity / .model / .domain: offer <base> as the default
        for (String tail : new String[] { ".entity", ".entities", ".model", ".models", ".domain" }) {
            if (pkg.endsWith(tail)) {
                return pkg.substring(0, pkg.length() - tail.length());
            }
        }
        return pkg;
    }

    // -------------------------------------------------------------------------------- selection

    private static List<IType> selectedTypes(ExecutionEvent event) {
        List<IType> out = new ArrayList<>();
        ISelection selection = HandlerUtil.getCurrentSelection(event);
        if (selection instanceof IStructuredSelection structured && !structured.isEmpty()) {
            for (Object o : structured.toList()) {
                addType(o, out);
            }
        }
        if (out.isEmpty()) {
            IEditorPart editor = HandlerUtil.getActiveEditor(event);
            if (editor != null) {
                IEditorInput input = editor.getEditorInput();
                addType(JavaUI.getEditorInputJavaElement(input), out);
            }
        }
        return out;
    }

    private static void addType(Object o, List<IType> out) {
        try {
            if (o instanceof IType t) {
                if (t.getDeclaringType() == null && !t.isInterface() && !t.isEnum() && !out.contains(t)) {
                    out.add(t);
                }
            } else if (o instanceof ICompilationUnit cu) {
                for (IType t : cu.getTypes()) {
                    addType(t, out);
                }
            } else if (o instanceof IAdaptable a) {
                IJavaElement el = a.getAdapter(IJavaElement.class);
                if (el != null && el != o) {
                    addType(el, out);
                } else if (el == null) {
                    IResource res = a.getAdapter(IResource.class);
                    if (res != null) {
                        IJavaElement je = JavaCore.create(res);
                        if (je != null) {
                            addType(je, out);
                        }
                    }
                }
            }
        } catch (JavaModelException e) {
            // skip this element
        }
    }
}
