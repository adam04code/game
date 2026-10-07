# gameProject

A [libGDX](https://libgdx.com/) project generated with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff).

This project was generated with a template including simple application launchers and an `ApplicationAdapter` extension that draws libGDX logo.

## Assets

Drop images (`.png`, `.jpg`, `.jpeg`, `.bmp`) into any sub-folder of `assets/`; they are found automatically at
start-up (or with **Rescan assets folder** in the editor). No code changes or lists to update.

- A folder whose name contains `terrain` holds terrain maps.
- Everything else is a placeable asset; its folder is its category.
- `name_flipped.jpg` next to `name.jpg` is the same asset seen from the other side; the game uses it to rotate the
  asset. Assets without one are mirrored instead.

**Edit Assets** (title screen) opens the editor:

- *Edit Asset* mode: draw collision and shadow polygons, remove the green-screen background, halve the resolution.
  Saving writes the processed image to `assets/processed/` (the original is never changed) and the settings to
  `assets/data/asset_meta.json`. Commit both so the game uses them.
- *Scene* mode: spawn assets on the terrain, drag them around, rotate (R) and delete (Del). Saved to
  `assets/data/editor_scene.json`.

## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3; was called 'desktop' in older docs.
- `server`: A separate application without access to the `core` module.
- `shared`: A common module shared by `core` and `server` platforms.

## Gradle

This project uses [Gradle](https://gradle.org/) to manage dependencies.
The Gradle wrapper was included, so you can run Gradle tasks using `gradlew.bat` or `./gradlew` commands.
Useful Gradle tasks and flags:

- `--continue`: when using this flag, errors will not stop the tasks from running.
- `--daemon`: thanks to this flag, Gradle daemon will be used to run chosen tasks.
- `--offline`: when using this flag, cached dependency archives will be used.
- `--refresh-dependencies`: this flag forces validation of all dependencies. Useful for snapshot versions.
- `build`: builds sources and archives of every project.
- `cleanEclipse`: removes Eclipse project data.
- `cleanIdea`: removes IntelliJ project data.
- `clean`: removes `build` folders, which store compiled classes and built archives.
- `eclipse`: generates Eclipse project data.
- `idea`: generates IntelliJ project data.
- `lwjgl3:jar`: builds application's runnable jar, which can be found at `lwjgl3/build/libs`.
- `lwjgl3:run`: starts the application.
- `server:run`: runs the server application.
- `test`: runs unit tests (if any).

Note that most tasks that are not specific to a single project can be run with `name:` prefix, where the `name` should be replaced with the ID of a specific project.
For example, `core:clean` removes `build` folder only from the `core` project.
