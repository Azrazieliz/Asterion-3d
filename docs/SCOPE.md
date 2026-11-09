# Asterion 3D Foundry — retained owner scope

Authority: owner implementation assignment of 2026-11-09. The 0.1 production slice does not replace or narrow this scope. Existing NoWorld, Koikatsu, apps, sources, signers and user data must be preserved.

## Required finished product

One autonomous Android application on Samsung SM-S948B, Android 17, ARM64. Images, descriptions, existing 3D sources and supported Koikatsu inputs must produce editable high-quality assets without manual production. Characters, nonhuman creatures and unusual anatomy, weapons/equipment, architecture/interiors/caves, vegetation/terrain, mechanical objects, animations, expressions and multi-participant interactions are all required. Koikatsu remains optional and independent.

Normal operation must be completely on phone, offline, without PC, subscriptions, paid APIs, external GPU or cloud inference. PC may compile the app. Reuse installed components and approved sources. Use measured Vulkan/QNN/ARM64 performance; never label a backend supported merely because the hardware exists. Long jobs need durable checkpoints, thermal and memory budgets, foreground notifications and recovery within Android limits.

## Workstreams retained

| Workstream | Required outcome | 0.1 boundary |
|---|---|---|
| Universal engine | Category routing, AI reconstruction plus procedural geometry, anatomy/skeleton templates, UV/materials, rigging/skinning, LOD/collision | Six deterministic recipes and silhouette extrusion; no learned reconstruction |
| Editable workspace | Geometry/shape, UV/material, rigs/morphs, motion, assembly and conversion | Editable recipe + binary mesh, orbit preview, recipe refinement, GLB export; full editor absent |
| Koikatsu | Existing Android installation, exact card/mod source geometry, all body/face/hair/wardrobe/material/morph/rig data | Detect package and preserve card bytes; extraction absent |
| Quality | Multi-view visual reference comparison separated from topology, UV, skin, pose/morph, performance checks; local repair loops | Finite data/normals/rig hierarchy/weight and export format checks only |
| Efficiency | Cheapest reliable route, exact reuse, regional repair, feature caches, dedup, unloading, coarse-to-fine, measured device backend selection | SHA-256 source/geometry cache, single worker and thermal/memory/storage pause thresholds |
| Learning 1 | Persistent outcomes, sources, methods, parameters, timing, resources, checks, repairs and preferences | SQLite successful operation records and receipts; resource history, failures and preferences incomplete |
| Learning 2 | Adaptive measured routing | Not implemented |
| Learning 3 | Searchable reusable validated templates, rigs, mappings, materials and recipes | Hard-coded versioned recipes; no searchable validated component library |
| Learning 4 | Feasible optional versioned/reversible small adapters or training | Not implemented; no training claim |
| NoWorld | Preserve identities/versions/manifestations, five forms, exact rigs/rest/morphs, one motion bank, wardrobe/material contracts, geography and original sources | Contracts inspected; generic GLB intermediates explicitly unbound; no engine admission |
| Storage | Immutable sources, editable projects, candidates/checkpoints, verified finals, mobile derivatives, archives; verified optional TeraBox | Separate roots; source hashes; no destructive cleanup; no TeraBox upload |
| Performance | Resource monitoring, measured processor routing, unloading/concurrency, thermal protection, background recovery | CPU processing, OpenGL ES preview, one worker, budget pauses and stage recovery |
| Interface | Create, Projects, Character/Creature Studio, Objects/Environments, KK, queue, asset library, performance/settings | Create/Projects/Queue/KK/Performance; studio/library specialization incomplete |
| Delivery | Installable APK, sources, accurate build/device/visual report and all eight acceptance cases | See VERIFICATION.md for actual evidence |

## Full acceptance remains mandatory

1. Image-to-3D weapon/object.
2. Reference-reconstructed humanoid (a generic rig template does NOT pass).
3. Appropriate nonhuman creature and rig, with deformation validation.
4. Architectural/environment asset.
5. Exact Koikatsu import from existing Android runtime (card storage does NOT pass).
6. Interrupted and resumed real job, including process termination recovery.
7. Repeat production demonstrating useful reuse.
8. NoWorld-compatible export admitted by genuine contract validation; native Unreal import/cook reported separately.

## Preservation rules

Never delete original inputs or their only recoverable copy. Preserve licenses/provenance. Never replace exact source geometry with screenshots or templates without reporting the change. No new NoWorld identities, ownership, skeleton aliases, rest poses, missing morph deltas, facial transparency guesses or duplicated shared motion banks. Never claim Unreal acceptance from GLB validity. Never claim GPU/NPU acceleration from a renderer or device feature flag. No protected other-app memory management.
