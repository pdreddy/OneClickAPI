package damu.plugin.oneclickapi.eclipse.ui;

/** The "Generate Tests Only" command: the same flow as {@link GenerateApiHandler}, limited to test classes. */
public final class GenerateTestsHandler extends GenerateApiHandler {

    @Override
    protected boolean testsOnly() {
        return true;
    }
}
