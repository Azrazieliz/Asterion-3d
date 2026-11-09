# Inspected integration boundaries

## NoWorld

Read-only inspection at `77fd363eb9d5f94d82a2f334f12b91d122da594b` on `work/gate12-whole-character-shared-motion-20261031`. No NoWorld files modified.

Relevant preserved programs/contracts:
- `docs/production/CHARACTER_APPEARANCE_CONTRACT.md`: exact Identity/Version, wardrobe parts, native Skeleton paths, complete ordered bone names/parents, authored rest transforms, complete morph inventory and material bindings. Same-count/compatible-rig substitutions are insufficient.
- `Scripts/production/character_package_import.py` and `schemas/character_asset_package.schema.json`: existing source-hash, provenance, material, skeleton and morph gate. Reuse for future admission, do not implement a permissive replacement.
- `Scripts/production/noworld_unreal_content_import.py`: real Unreal worker accepts authenticated source-backed factory jobs, not stand-in meshes. Uses existing canonical content registries.
- `Scripts/production/gate12_grand_original_unity_source_to_glb.py`: source-faithful Unity transform, skin and morph conversion; currently depends on Blender, so cannot be advertised as an Android-native implementation.
- `Scripts/production/bind_native_motion.py`: existing single shared animation path, preserved. No synthesized contact or gameplay acceptance.

Foundry 0.1 exports generic right-handed Y-up metre GLBs. `import-manifest.json` is a Foundry intermediate descriptor, NOT NoWorld's admission schema. Identity/Version/Manifestation are null and native acceptance is false. Character template rigs must never replace NoWorld's exact source rigs. NoWorld import/cooking and visual identity remain unimplemented.

## Koikatsu

Phone package query returned `com.asterion.kk.test`. Existing installed app was not replaced or modified. Repository README is an old native Unity port checkpoint and does not establish the current phone runtime.

The current Windows development directory contains `AsterionKKRuntimeTools/compat/winlator/AsterionKKPackImporter.java` and `AsterionKKStartupDiagnostics.java`, indicating a compatibility-runtime integration path exists. The inspected launcher declares GAME_FOLDER = `Koikatsu Party`, CONTAINER_NAME = `Asterion KK` and offers Wine startup diagnostics. The pack importer root is `new File(getExternalFilesDir(null) or getFilesDir(), "AsterionKK")`. This establishes the tooling layout, not the actual current contents of the phone. Private Android runtime files are not automatically accessible from Foundry; no storage permission bypass, new port or second game installation was attempted.

Exact installed runtime version, imported game directory contents, mod dependency resolution and successful original-mesh extraction remain unverified. Foundry preserves selected card bytes and hashes only. It does not execute Windows plugins, parse full card metadata, resolve zipmods, or extract geometry.

## Models and libraries

Current APK has no third-party geometry or inference dependency. It uses Android/Java APIs and original deterministic geometry code, avoiding an untested native dependency bundle.

Research candidates (not integrated/validated):
- ncnn: BSD-3-Clause runtime with documented Android Vulkan support. A portable runtime is not proof any 3D reconstruction model is converted or supported. https://github.com/Tencent/ncnn
- Hunyuan3D-2: upstream documents 6 GB VRAM for shape, 16 GB for shape+texture. Desktop requirements, license and operator/runtime conversion must be audited before deployment. Current phone available RAM was ~3.4 GiB, so this is NOT a verified suitable Android model. https://github.com/Tencent-Hunyuan/Hunyuan3D-2
- Android service limits: dataSync/mediaProcessing foreground services have time limits; checkpoints and explicit resume are required. https://developer.android.com/develop/background-work/services/fgs/timeout

Vulkan compute, QNN integration, native ARM64 geometry libraries, model adaptation, FBX export and automated visual repair all remain open engineering work, not available features.
