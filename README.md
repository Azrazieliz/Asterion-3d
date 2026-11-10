# Asterion 3D Foundry

Android production application for offline reference reconstruction,
procedural generation and source preservation. The latest published APK is
`0.2.0-native-depth`. The [full product scope](docs/SCOPE.md) remains open.

[Installable ARM64 APK](dist/AsterionFoundry-v0.2.0-DEV.apk) ·
[Reconstruction and model guide](docs/NATIVE_MODEL.md) ·
[Verification evidence](docs/VERIFICATION.md)

The source on this branch adds [reference fidelity changes and verification](docs/REFERENCE_FIDELITY.md)
and the [0.4 production evaluation](docs/PRODUCTION_EVALUATION.md) after that APK.
Volumetric character reconstruction remains a preview and has not passed visual
acceptance.

## Create and export

- Select **AI depth relief** to estimate the visible surface from one
  isolated image. The APK contains the model and inference runtime.
- Select **Multi-view reconstruction** to derive a volume from aligned
  front and right views, with an optional back view.
- Select **Preserve original GLB** to inventory and package a
  self-contained GLB while retaining its exact source bytes.
- Use Studio for procedural object, environment and rig template
  recipes. Use Library to find completed work. Inspect generated meshes
  to create a separate scale or colour revision.
- Export an editable package from Projects. It contains the GLB,
  validation receipt and checksum-addressed originals. Generated
  projects include Foundry's editable mesh checkpoint.

Projects record the method, quality checks and provenance. The
intermediate format does not confer NoWorld admission.

## Build and validate

Android API 35 and Java 17. Gradle and the direct Windows build both
prepare checksum-pinned offline dependencies at build time. Use
`scripts/build-windows.ps1` on the existing signing worker for an update
compatible with the previously published APK. Run
`bash scripts/test-core.sh` for the focused host geometry and GLB suite.
