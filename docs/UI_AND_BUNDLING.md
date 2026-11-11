# Foundry premium UI and embedded models — implementation contract

This is an additive owner-directed development workstream within the full
existing `docs/SCOPE.md`. It does not replace geometry, rigs, materials,
learning, animation, integration, or other M0–M7 obligations.

## Current native UI source

- The four persistent bottom destinations are **Create**, **Projects**,
  **Queue** and **Library**; the Tools control exposes **Studio**,
  **Koikatsu** and **Performance** without deleting their functionality.
- Create starts with clear paths: image-to-3D candidate, illustrated
  character, supported text recipe, original GLB, multiple aligned views and
  specialist studio. More precise methods remain selectable from the creation
  form, which has a single primary action and an optional checkpoint setting.
- Projects and Queue show asset names, clear state descriptions and legitimate
  actions. A complete job still reports **visual quality unverified**; a
  candidate remains **preview only**. Inspect presents orbit/view controls
  and collapsible editing/animation actions. Technical JSON evidence is
  accessible on demand, not dumped into the main interface.
- The existing source workflows, parts, forms, output packages, motion and
  NoWorld/Koikatsu contracts are retained. No technique is promoted to a
  higher-quality status by a visual redesign.

## Inference packaging

- MiDaS and MoveNet remain checksum-pinned APK assets.
- The six existing TripoSR/ISNet/SlimSAM ONNX files must be packaged into
  `assets/foundry-3d.foundrymodels`. The build validates all exact names,
  sizes, SHA-256 digests and ZIP_STORED member compression against
  `scripts/create_tripo_pack.py`.
- Release builders supply the existing verified local model archive through
  `FOUNDRY_MODEL_ARCHIVE`, or the original pinned source files through
  `FOUNDRY_MODELS_DIR`. The authorized PC remains a build/signing worker;
  the Android app never fetches weights, asks for ZIP model import or
  connects to a remote inference endpoint.
- The APK contains the archive even though ONNX Runtime needs its external
  data unpacked to private internal storage once. The first job may take
  longer and needs free storage. Verified existing model installations are
  reused. Android manifest intentionally retains no Internet permission.
- Source-only CI may compile without ~700 MB weight files. A distributable
  Gradle package or Windows signed build may not omit them. Final APK
  integrity checks require the bundled model archive.

## Gates before calling the UI premium or the app production-ready

1. Preserve the previously installed app's signing certificate and upgrades;
   sign a **new** APK. Never overwrite the published v0.5.1.
2. Open all four destinations and the Tools menu on Samsung SM-S948B; verify
   touch targets, contrast, layout, system insets, rotation, back navigation
   and state persistence. Compare actual screenshots and fix crowded sections.
3. Run image, character, text-recipe, existing-GLB, aligned-view, Studio,
   Koikatsu and project editing flows; confirm queue status and export.
4. Inspect a real candidate in the viewport, edit its components and verify
   result/receipt. UI prettiness does not establish reference or material
   quality.
5. Verify that the signed APK **actually contains** every pinned model and
   that a new install runs a first-use ONNX reconstruction entirely offline.
   Measure APK size, extraction storage/time, model load, memory and thermal
   behavior. Confirm there is no model-install prompt.
6. Repeat with the existing immutable originals and report exact source
   compatibility, part persistence, stage recovery and NoWorld handoff
   separately. Fix defects before claiming user acceptance.

## Unverified on this development connection

The source and CI are accessible, but there is no direct Asterion Control
Windows/phone input action exposed in this chat. No new signed APK or phone
screenshots were produced here. The current reconstruction model has documented
reference-fidelity failures; embedding it does not fix those.
