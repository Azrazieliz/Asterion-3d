# Asterion 3D Foundry

Android production application for offline reference reconstruction,
procedural generation and source preservation. The newest signed development evaluation APK is
`0.5.1-part-surfaces-recovery`. The [full product scope](docs/SCOPE.md) remains open.

[Current signed ARM64 v0.5.1 APK](dist/AsterionFoundry-v0.5.1-PRODUCTION-DEV.apk) ·
[Earlier v0.2 APK](dist/AsterionFoundry-v0.2.0-DEV.apk) ·
[Reconstruction and model guide](docs/NATIVE_MODEL.md) ·
[Verification evidence](docs/VERIFICATION.md)

The [signed v0.5.1 development APK](dist/AsterionFoundry-v0.5.1-PRODUCTION-DEV.apk)
adds non-destructive per-part colour revisions and foreground-reentry recovery of
unexpectedly interrupted jobs. Windows signed-build checks, Android Java compilation,
host regressions and exact signer continuity have passed. Samsung upgrade, real
part-edit flow, reference fidelity, visual material acceptance and sustained
offline performance **have not** been established; this is not the full release.
See the [current evidence](docs/VERIFICATION.md).

The [signed v0.5 staged-quality evaluation APK](dist/AsterionFoundry-v0.5.0-STAGED-EVALUATION.apk)
has passed Windows compilation, host checks, APK signature and embedded model verification.
Its signing certificate matches the earlier v0.2 APK. Samsung installation and
visual acceptance remain unverified. The full scope is **not** complete.

The source on this branch adds [reference fidelity changes and verification](docs/REFERENCE_FIDELITY.md)
and the [0.4 production evaluation](docs/PRODUCTION_EVALUATION.md) after that APK.
Volumetric character reconstruction remains a preview and has not passed visual
acceptance.

## Pending 0.5.2 source update — guided UI and APK-bundled models

**No new APK has yet been signed or published.** The currently downloadable
v0.5.1 development binary is unchanged.

The source on `work/staged-production-quality-20261111` adds a simplified
four-destination navigation shell (Create, Projects, Queue, Library), guided
creation entry cards, readable job cards and statuses, expandable editing
operations, compact preview viewpoints and technical reports behind explicit
controls. Specialist Studio, Koikatsu and Performance screens remain accessible
through Tools, and all previous creation methods remain in the app.

The next signed build requires the full pinned TripoSR/ISNet/SlimSAM model archive
embedded in the APK alongside the existing MiDaS/MoveNet weights. The phone no
longer needs a separate model-pack installation; ONNX external data is prepared
privately on first use. Windows builds require `FOUNDRY_MODEL_ARCHIVE` or
`FOUNDRY_MODELS_DIR` pointing at the existing licensed pinned sources.
The Android/Windows builds reject missing weights rather than publishing a
partial APK. Model extraction adds on-device storage cost.

Source compilation does **not** establish the premium UI on Samsung, complete
image-to-3D quality, phone inference or production readiness. See
[UI and bundled model acceptance](docs/UI_AND_BUNDLING.md) and
[verification evidence](docs/VERIFICATION.md).

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
