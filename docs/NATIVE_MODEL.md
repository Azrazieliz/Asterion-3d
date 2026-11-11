# Offline model and reconstruction

Use **AI depth relief** for one isolated image. MiDaS predicts relative
inverse depth at 256 × 256. Foundry projects the foreground into a coloured
surface with flat backing and exports the mesh. Transparent and
uniform-background inputs are supported by the foreground mask.

Use **Multi-view reconstruction** for an upright front and right
orthographic image, with an optional back image. Keep the subject pose
and proportions consistent. Foundry intersects the projected masks and
generates the exterior surface, reports silhouette overlap and audits the
resulting mesh. Inspect the result before using it in a production pipeline.

Use **Preserve original GLB** for an existing self-contained GLB when
exact source bytes matter. The job inventories skins, morph targets,
materials and animations without converting them. Source bytes and
export bytes share a SHA-256. Editable mesh revisions are available for
Foundry geometry. The existing asset and all original inputs remain
available as separate files.

The build packages the pinned model and LiteRT 1.4.2 ARM64 runtime into
the APK. Download and hash verification happen on the build machine
through `scripts/prepare_native.py`. The application requires no network
permission. The CPU path records model SHA, thread count and
inference/setup timing. The interpreter closes after each run. Vulkan
and Qualcomm QNN execution still require implementation and device
benchmarks.

Model origin and architecture: [MiDaS Small conversion](https://huggingface.co/litert-community/MiDaS-small/blob/b6f24f361d9e1bce8f33e970aebcb9e5e2d1a12c/README.md).
The APK carries the MiDaS MIT and LiteRT Apache license texts.


## Fully embedded model packaging — 0.5.2 development

The signed build must include the six existing pinned TripoSR, ISNet and SlimSAM ONNX weights as a ZIP_STORED `foundry-3d.foundrymodels` APK asset. The Windows worker supplies the existing verified source archive via `FOUNDRY_MODEL_ARCHIVE`, or the six exact original weights via `FOUNDRY_MODELS_DIR`; the builder checks byte lengths and SHA-256 for every member before packaging. MiDaS and MoveNet are already embedded and remain unchanged.

Android reconstruction jobs extract the embedded archive once into private app data because ONNX external-data weights require local file paths. There is no model download, user import or post-install ZIP selection in the normal creation flow. Model extraction still uses device storage. Source-only CI compilation can omit the large weights, but Gradle APK packaging and the Windows signed-build script refuse an absent bundle.

This is a source-level packaging change pending a signed Windows build and Samsung model extraction/inference test. It does not correct the currently documented visual reconstruction failures.

When an old incomplete or same-size-but-corrupted manually installed model directory is encountered, the reconstruction worker preserves it under private `archive/model-recovery-*`, verifies the exact pinned member checksums, and extracts the APK-owned weights. Verified model paths are cached for the current app process so repeated jobs do not rehash hundreds of megabytes. Model recovery does not delete originals or require phone downloads.
