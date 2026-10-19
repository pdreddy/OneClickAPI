# OneClickAPI for Eclipse

The Eclipse version of OneClickAPI. It shares the generator with the IntelliJ plugin: `eclipse/core` compiles
`src/main/kotlin/.../core` and the `templates` straight from the IntelliJ project, so both IDEs generate identical code
and the IntelliJ project's tests of that code run here too.

| Module | What it is |
|---|---|
| `core` | Kotlin jar: the shared `core` package + `damu.plugin.oneclickapi.eclipse` (`ModelFactory`, `ApiGenerator`), a Java-friendly facade with no IDE types. |
| `plugin` | The OSGi bundle: command + menus, options/preview/report dialogs (SWT/JFace), JDT model reader, workspace file access. Bundles `core`, FreeMarker and the Kotlin stdlib in `lib/`. |
| `feature` | Eclipse feature wrapping the bundle. |
| `site` | p2 update site (`site/target/repository`, and a zip of it). |

## Build

Needs JDK 17+ and Maven 3.9+ (Tycho 4). Targets Eclipse 2024-09 or newer.

```
cd eclipse
mvn clean package
```

The update site is `site/target/repository`; the zip next to it can be installed with
*Help > Install New Software > Add > Archive*.

## Use

Right-click an `@Entity` class (or a plain POJO for MyBatis) in the Package Explorer, Project Explorer or Java editor,
then **Generate API (OneClickAPI)...** (also `Ctrl+Shift+Alt+G`). Pick a preset, adjust the options, preview, write.
`.oneclickapi.yml` presets and `.oneclickapi/templates` overrides in the project root work as in IntelliJ.

## Status

Built and unit-tested headless (`mvn package`). The UI has not been run inside a live Eclipse yet. Not yet ported:
saving presets from the dialog, per-class module detection for multi-module workspaces beyond one project per
selection, and automatic Maven/Gradle project refresh after the build file is edited (use *Maven > Update Project*).
