# gameProject

A [libGDX](https://libgdx.com/) project generated with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff).

This project was generated with a template including simple application launchers and an `ApplicationAdapter` extension that draws libGDX logo.

## Assets

Drop images (`.png`, `.jpg`, `.jpeg`, `.bmp`) into any sub-folder of `assets/`; they are found automatically at
start-up (or with **Rescan assets folder** in the editor). No code changes or lists to update.

- A folder whose name contains `terrain` holds terrain maps. They appear at the top of the editor's list and can
  have collision and shadow shapes like any other asset (no green-screen tools; they have no green screen).
- Everything else is a placeable asset; its folder is its category.
- `name_flipped.jpg` next to `name.jpg` is the same asset seen from the other side; the game uses it to rotate the
  asset. Assets without one are mirrored instead.

**Edit Assets** (title screen) opens the editor. On opening it rescans the assets folder, removes the green
background from every object that still has one (writing its processed image), scans again and runs the asset
check, so new images are ready straight away.

- Zoom with the mouse wheel over the canvas or the -/+/Fit buttons at its top-left; drag empty space to pan.
- Panels are split into collapsible sections; every slider has a box for typing the exact value (Enter applies
  it). The filter box above the asset list narrows it by name or folder.
- *Edit Asset* mode: draw any number of collision and shadow polygons ("New shape"), remove the green-screen
  background, halve the resolution. Saving writes the processed image to `assets/processed/` (the original is never
  changed) and the settings to `assets/data/asset_meta.json`. Commit both so the game uses them.
  **Check all assets** flags (in orange) every asset that still has its green background or has no collision;
  mark an asset "No collision needed" to stop it being flagged for collision.
- *Scene* mode: spawn assets on the terrain, drag them around, resize (-/+), rotate (R) and delete (Del).
  New copies spawn at the asset's default size, set under "Size in scene" in Edit Asset mode; "Reset to default
  size" puts a resized object back to it. Saved to `assets/data/editor_scene.json`.
  An object whose collision bounds overlap another object's shadow is darkened where that shadow falls on it
  (objects without collision use the point they stand on). This is worked out every frame, so it follows objects
  as they move. It uses the stencil buffer, which the desktop launcher enables.

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
