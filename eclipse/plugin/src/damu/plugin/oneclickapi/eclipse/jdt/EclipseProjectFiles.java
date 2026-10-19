package damu.plugin.oneclickapi.eclipse.jdt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;

import damu.plugin.oneclickapi.core.write.ProjectFiles;

/** {@link ProjectFiles} on top of the Eclipse workspace, so that editors and the Java model see the change at once. */
public final class EclipseProjectFiles implements ProjectFiles {

    private final IProject project;

    public EclipseProjectFiles(IProject project) {
        this.project = project;
    }

    @Override
    public String read(String path) {
        IFile file = project.getFile(new Path(path));
        if (!file.exists()) {
            return null;
        }
        try (InputStream in = file.getContents(true)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (CoreException | IOException e) {
            return null;
        }
    }

    @Override
    public boolean exists(String path) {
        return project.getFile(new Path(path)).exists();
    }

    @Override
    public void write(String path, String content) {
        try {
            IFile file = project.getFile(new Path(path));
            mkdirs(file.getParent());
            ByteArrayInputStream data = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            if (file.exists()) {
                file.setContents(data, IResource.FORCE | IResource.KEEP_HISTORY, new NullProgressMonitor());
            } else {
                file.create(data, IResource.FORCE, new NullProgressMonitor());
                file.setCharset("UTF-8", new NullProgressMonitor());
            }
        } catch (CoreException e) {
            throw new IllegalStateException("Cannot write " + path + ": " + e.getMessage(), e);
        }
    }

    private void mkdirs(IContainer container) throws CoreException {
        if (container.exists() || !(container instanceof IFolder folder)) {
            return;
        }
        mkdirs(folder.getParent());
        folder.create(IResource.FORCE, true, new NullProgressMonitor());
    }
}
