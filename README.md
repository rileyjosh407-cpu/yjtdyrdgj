# Base Finder — Fabric 1.21.1

Client-side nearby utility-block scanner for single-player or servers where scanning is permitted.

## Controls
- G: Toggle scanner
- R: Rescan
- [: Decrease radius
- ]: Increase radius

The HUD lists up to 10 nearby matching blocks with coordinates and distance. Radius is 4–48 blocks, with a vertical scan range of 8 blocks above/below the player.

This only checks block data currently available to the client. It does not request or force-load chunks, inspect server files, or bypass server protections. It is not a guaranteed remote-base finder.

## Build
The GitHub Actions workflow builds with Java 21 and Gradle 8.8. Open Actions and run "Build Base Finder"; download the base-finder-fabric-1.21.1 artifact from a successful run. Install Fabric Loader and Fabric API for Minecraft 1.21.1, then place the regular mod JAR in the instance's mods folder.
