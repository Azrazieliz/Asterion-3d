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
