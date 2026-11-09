# Asterion 3D Foundry

**0.1 development production slice — full assignment remains incomplete.**

An offline Android application with real procedural mesh generation, limited image-silhouette extrusion, rig templates, editable projects, durable production jobs, geometry reuse, GPU preview and GLB/package export.

[Installable development APK](dist/AsterionFoundry-v0.1.0-DEV.apk) · [Verification report](docs/VERIFICATION.md) · [Full retained scope](docs/SCOPE.md) · [Inspected integration contracts](docs/INTEGRATION.md)

## Use

Install the APK, open Create, select a recipe and choose Create. For image extrusion, choose a transparent/plain-background object image and select Image silhouette. Inspect projects from Projects and export an editable ZIP through Android's document picker. Queue provides resume and pause controls. The explicit interruption option pauses after saving geometry to exercise stage recovery.

Outputs include `project.json`, editable `checkpoint.mesh`, `asset.glb`, `receipt.json`, `import-manifest.json` and unchanged selected sources. Receipts distinguish geometry checks from unvalidated visual quality and absent native Unreal acceptance. No internet permission or runtime PC dependency is used.

## Build

Standard Android Gradle project (AGP 8.7.3, SDK 35, Java 17). The tested Windows build route is `scripts/build-windows.ps1`, using existing Android SDK tools and a development signer. SDK/JDK paths are parameters. No signing secrets are committed.

Core checks: `bash scripts/test-core.sh` on a JDK/Python host. The build script runs the same Java and independent Python GLB checks.

## Not available yet

AI reconstruction, full studio editing, source-accurate Koikatsu geometry/mod import, Vulkan/QNN compute, animation/morph production, visual correction, mobile optimization, adaptive learning and NoWorld-native admission. Humanoid/quadruped templates are not reconstructed characters. Silhouette extrusion is not inferred volumetric reconstruction. See the report for every acceptance distinction.

## 0.2 native depth work

The existing app now includes native offline MiDaS depth relief,
multi-view visual-hull reconstruction, exact self-contained GLB
preservation, topology measurements, non-destructive mesh revisions and a
searchable project library. See [native model and reconstruction](docs/NATIVE_MODEL.md).
The original full scope remains tracked in [SCOPE.md](docs/SCOPE.md).
