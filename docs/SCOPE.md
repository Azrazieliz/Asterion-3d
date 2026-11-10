# Asterion 3D Foundry — retained owner scope

Authority: owner implementation assignment of 2026-11-09. The 0.1 production slice does not replace or narrow this scope. Existing NoWorld, Koikatsu, apps, sources, signers and user data must be preserved.

The listed workflows, systems and acceptance examples are minimum coverage. Qualify the product on diverse real inputs outside the examples, including how the systems work together. Record missing capabilities found in production flows and carry them into the implementation and release checks. A passing example or working template alone cannot establish category coverage or visual quality.

## Required finished product

One autonomous Android application on Samsung SM-S948B, Android 17, ARM64. Images, descriptions and existing 3D sources must produce finished, editable 3D assets without manual production. Characters, nonhuman creatures and unusual anatomy, weapons/equipment, architecture/interiors/caves, vegetation/terrain, mechanical objects, animations, expressions and multi-participant interactions are all required. Koikatsu is an optional source when an existing 3D asset needs work that Koikatsu cannot perform. No Koikatsu input is required for ordinary creation or when the asset is already complete in Koikatsu.

### Finished assets and animation

- Animatable assets ship ready to animate, with the appropriate skeleton or controls, binding, skin weights and deformation verified. Editable project files accompany the finished deliverable. When animation is requested, ship finished, playable motion on the intended rig, including expressions or synchronized participant and prop motion where specified. Validate playback, timing, contact and deformation before delivery.
- Support still poses and subtle loops through long, intense, synchronized sequences. Choose and adapt movement from the request and the rig's anatomy, joint constraints and capabilities. Reuse a clip only after checking compatibility or retargeting it. Include anatomy-specific motion for complex creatures such as multi-winged or multi-headed dragons.
- Keep body, clothing, armor and removable accessories independently editable and exportable. Bind separate meshes to the appropriate common skeleton without fusing garment surfaces into the body. Support physically driven cloth and secondary motion, contacts and collisions. Preserve the editable simulation setup and bake what NoWorld requires.
- A cropped or occluded reference is partial evidence. Complete missing geometry coherently using visible landmarks, anatomy or object structure, and additional references or approved source data when available. A cropped arm must have the expected joints and hand unless the character design establishes otherwise. Check part type, attachment, proportions and motion, and attempt targeted repair on failure. Record which parts were inferred and keep failed results as candidates. An intentional design feature or valid unusual anatomy must survive the same checks.

### Staged production, textures and colour

- Foundry may create the requested output through multiple automated stages and refinement passes whenever this improves quality. Build and refine geometry, separate parts, topology, UVs, textures, colour, materials, rigging, deformation, physics and animation in a dependency-aware order appropriate to the asset. Carry the same identity, art direction, part placement and bindings through the stages. Ordinary production remains automatic on the phone.
- Texture and colour fidelity are required parts of finished quality. Preserve or reconstruct the reference palette, colour boundaries, patterns, fine surface detail and material response. Produce usable UVs and the base-colour, normal, roughness, metallic, opacity and emissive data required by the asset and target. Keep editable texture and material sources with the project.
- Review surface appearance in several views under controlled lighting and the target presentation. Check texture seams, stretching, texel density, transparency, colour consistency and detail during deformation and animation. Distinguish surface colour from illumination in the reference. Require visual review of the textured result alongside geometry, anatomy and motion.
- Save intermediate results, dependencies, provenance, quality findings and resource costs. Reuse accepted stages and rerun the affected region or dependent stages when a defect is found. Preserve accepted detail and original sources through refinements. Bound retries by quality improvement, memory, storage, thermal and time budgets, retain the best recoverable candidate, and report any unresolved failure.

### Artistic intelligence and saved transformation rules

- Foundry makes and evaluates artistic decisions about silhouette, proportions, visual hierarchy, materials, detail placement and consistency with the selected style across characters, creatures, motion, objects and environments. Wuthering Waves and Honkai Impact 3rd are quality references for the finished work. Learn from approved references, outputs, corrections and rejected results. Keep reusable rules and any trained changes versioned, scoped, validated and reversible. Preserve approved originals.
- Save reusable transformation profiles such as Alter, Berserker and Grand Convergence. A profile can hold general requirements, approved examples, visual and motion rules, and constraints. Keep character-specific overrides distinct from shared rules. When asked for a named form of a character, apply the saved profile to that character's approved identity and references, preserve defining features, and generate a finished variant that can be animated. Reuse an existing approved form when available. Store a newly generated form as a separate version with provenance and validation results. Keep creative form generation distinct from admission to NoWorld's canonical records and gameplay rules.

Normal operation must be completely on phone, offline, without PC, subscriptions, paid APIs, external GPU or cloud inference. PC may compile the app. Reuse installed components and approved sources. Use measured Vulkan/QNN/ARM64 performance; never label a backend supported merely because the hardware exists. Long jobs need durable checkpoints, thermal and memory budgets, foreground notifications and recovery within Android limits.

## Workstreams retained

| Workstream | Required outcome | 0.1 boundary |
|---|---|---|
| Universal engine | Category routing, AI reconstruction plus procedural geometry, anatomy/skeleton templates, UV/materials, rigging/skinning, LOD/collision | Six deterministic recipes and silhouette extrusion; no learned reconstruction |
| Editable workspace | Geometry/shape, UV/material, rigs/morphs, motion, assembly and conversion | Editable recipe + binary mesh, orbit preview, recipe refinement, GLB export; full editor absent |
| Koikatsu | Optional intake for modifications unavailable in Koikatsu, preserving existing card/mod geometry, body/face/hair/wardrobe/material/morph/rig data | Detect package and preserve card bytes; extraction absent |
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

The humanoid case must include completion of a cropped limb with coherent anatomy and playback on the finished rig. The reuse case must include saving a named transformation profile and applying it to another eligible character while retaining that character's identity. Exercise a finished animation, including a synchronized interaction where requested, on the exported asset. Test Koikatsu intake only with an asset that needs modifications outside Koikatsu. Review the final textures, colours and material response on representative outputs. Exercise a staged refinement that fixes a real defect while retaining accepted geometry and surface detail, then verify the exported result.

## Preservation rules

Never delete original inputs or their only recoverable copy. Preserve licenses/provenance. Never replace exact source geometry with screenshots or templates without reporting the change. No new NoWorld identities, ownership, skeleton aliases, rest poses, missing morph deltas, facial transparency guesses or duplicated shared motion banks. Never claim Unreal acceptance from GLB validity. Never claim GPU/NPU acceleration from a renderer or device feature flag. No protected other-app memory management.
