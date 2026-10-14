# Base Finder — Fabric 1.21.1

A client-side scanner for nearby utility blocks in the currently loaded client world. Use only in single-player or on servers where this kind of scanning is permitted. It does not request or force-load remote chunks.

## Controls

- **G** — toggle Base Finder
- **R** — rescan now
- **[** — decrease scan radius
- **]** — increase scan radius

The HUD lists up to 10 nearby detected blocks with coordinates and distance. The scan radius is 4–48 blocks; vertical scan range is 8 blocks above/below the player.

## Build a JAR on your computer

1. Install **Java 21** and **Gradle 8.8**.
2. Open a terminal in this folder (the folder containing `build.gradle`).
3. Run `gradle build`.
4. The mod JAR will be created in `build/libs/`. Copy the regular JAR (not the `-sources.jar`) into your Minecraft 1.21.1 Fabric `mods` folder. Install Fabric API too.

## Build online without installing Gradle

This project includes a GitHub Actions workflow at `.github/workflows/build.yml`.

1. Create a new repository on GitHub.
2. Upload the contents of this project folder to the repository (the `build.gradle` file must be at the repository root).
3. Open the repository's **Actions** tab and choose **Build Base Finder**.
4. Click **Run workflow**. Wait for the build to finish successfully.
5. Open the completed workflow run and download the `base-finder-fabric-1.21.1` artifact. Extract it to get the JAR.

## Notes

This is a source project; the JAR has not been compiled or runtime-tested in the current environment. The GitHub Actions workflow builds it using Java 21 and Gradle 8.8. Scanning only checks block data already available to the client and is not guaranteed to find hidden bases, especially outside loaded chunks.
