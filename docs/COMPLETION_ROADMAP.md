# Foundry completion roadmap

Baseline `ac9fb96c4280eb1409acf49165e523e402bfa6d8` on `work/reference-fidelity-correction`. The [owner scope](SCOPE.md) governs release acceptance. This roadmap orders the remaining work and adds measurable gates. It does not change the scope or promote any evaluation output.

## Starting point

| Area | Evidence available | Gap to production |
|---|---|---|
| Offline reconstruction | Pinned CPU inference, subject masking, multi-view hull, source-colour reprojection and host checkpoint reuse. The earlier character failed visible geometry and pose evidence checks. | Faithful editable geometry, independent parts, anatomy completion, rig and visual acceptance. |
| Rigs and motion | Editable blended skin, an articulated 50-joint dragon, independent mesh parts, two-actor assembly, authored action stages, contact checks and baked gravity arcs. Host checks exercised a 151-second authored scene. | Automatic scene direction, robust rigging and retargeting, expressions, continuous collision and dynamic cloth, rich animation quality. |
| Art and forms | Versioned proportions and tint rules, per-base preferences, registered original reuse. | Shared art direction across asset categories and motion, feedback-driven routing, full identity-preserving form synthesis. |
| Sources and handoff | Immutable GLB intake and material variants, multipart GLB export, one-part NoWorld source-profile preflight, Koikatsu card-byte preservation. | Editable source-grade conversions, optional exact Koikatsu extraction, admission through the real NoWorld contracts and Unreal import. |
| Runtime and delivery | CPU path, one queued worker, resource pauses, checkpoint/cache foundations. The signed 0.4 APK predates the latest rigging and animation source. | Current phone build, sustained offline performance, process-death recovery, measured Vulkan/QNN, complete release evidence. |

## Delivery sequence

### M0 — Lock the acceptance cases and reference evidence

- Record a separate private case for each supplied illustration, including the dragon. Keep other approved views, owned source assets and reference masks attached to the correct case. Record pose, camera, scale, part visibility, provenance and approvals where known.
- Define review panels for visible identity, inferred anatomy, materials, silhouettes, secondary motion and mobile limits. Calibrate technical thresholds against approved examples. Use Wuthering Waves and Honkai Impact 3rd as quality references across artwork and motion. Record style and reference fidelity separately.
- Pin the current NoWorld contract revision and prepare representative original rigs, wardrobes, morphs and motion from the existing canonical project. Record what the running Samsung, existing signer and Koikatsu runtime actually expose when reachable.

**Exit gate.** Every case has a reference, target deliverable, measurable checks, and a named pass or fail outcome. Visual acceptance includes a review of turntables, close-ups and relevant motion. Raw private references stay outside Git.

### M1 — Build the editable production representation

- Extend the checkpoint format into a versioned scene and component graph with stable part IDs. Represent authored and inferred geometry separately, and retain source views, masks, cameras, provenance, transforms, revisions, and solver settings.
- Support independent body, face components, hair, garments, armor, equipment and creature appendages as appropriate. Bind parts to shared skeletons without fusing their surfaces. Represent rigs, constraints, rest and bind transforms, blend weights, morphs, expressions, materials, UVs, textures, collision and LODs.
- Preserve imported rig, material, animation and morph data through editable revisions and round trips. Give assembly, geometry, materials, rigging and motion usable inspection and editing paths. Maintain compatibility with older projects and separate exact originals from editable derivatives.

**Exit gate.** An asset opens, changes and exports while unchanged parts retain their identity and bindings. Clothing can be replaced independently, and an old project still opens and round-trips.

### M2 — Qualify real offline models and production routing

- Implement the Create router for descriptions, one or more images, existing 3D data, and supported optional Koikatsu intake. Prefer verified exact sources where available, procedural geometry where it satisfies the brief, calibrated reconstruction where views support it, and learned inference for missing or irregular structure. Keep fallbacks scoped to the evidence they can preserve.
- Select and integrate deployable components for subject and part segmentation, landmarks and camera, part-aware geometry, texture/material estimation, and visual evaluation. Qualify each model's rights, weights, preprocessing, output contract, conversion parity, peak RAM, storage, thermal cost and phone runtime. Keep models and recipes pinned and independently replaceable.
- Evaluate semantic decomposition for stylized characters against the private cases. StdGEN is a research comparison for separation of body, clothing and hair. Its published research checkpoints cannot be treated as cleared production weights. Qualify another legally usable backend or train/adapt a permitted component if the phone budget and data support it.
- Establish a bounded coarse-to-fine path with per-stage caches and regional retries. Stop promoting a result when a needed model fails the quality gate. Record the precise missing capability and try an eligible alternative automatically.

**Exit gate.** A real candidate path runs offline on the target phone, with retained results and measured memory and timing. At least one decomposed reference passes visible-part checks. Unsupported graphs or anatomy have explicit receipts.

### M3 — Finish the asset families

| Family | Remaining implementation | Acceptance evidence |
|---|---|---|
| Humanoids | Fit silhouette, face, proportions and distinctive details. Reconstruct hair, garments and accessories independently. Infer cropped limbs from anatomical constraints and other evidence, then retopologize, unwrap, texture and auto-rig. Produce facial morphs and expression compatibility. | Reference likeness in several views, believable missing arm and hand, separate wardrobe, validated deformation and a played clip on the finished rig. |
| Animals, monsters and complex dragons | Detect body plan and appendages. Support custom numbers of heads, legs, wings, tails, jaws and digits. Reconstruct attachment geometry and membranes, skin by anatomy and preserve distinctive armor and materials. | A source-matched articulated creature and anatomy-specific movement, with attachment, membrane, joint and deformation checks. |
| Equipment, weapons, props and mechanical assets | Infer components and dimensions, recover sharp features and materials, use procedural construction for regular structure, bind movable pieces and generate sockets and collision where required. | A reference-based 3D game object with faithful detail, editable components and validated material, scale and collision. |
| Architecture, interiors, caves and dungeons | Reconstruct structures, spaces and topology, build modular pieces, openings and interiors, unwrap materials and produce navigation/collision data as required by the handoff. | A coherent reference-driven environment module with usable openings, scale and collision, beyond a stand-alone primitive. |
| Vegetation, terrain and landscapes | Generate fitted vegetation structures, terrain height and surface layers, modular placement, variation and mobile LODs. Preserve authored regional geography and world-state inputs when binding to NoWorld. | A reference-driven terrain or vegetation scene with validated scale, seams, LODs, collision and source provenance. |

Every family needs usable materials, original textures when available, UVs, normals, appropriate topology, LODs and mobile budgets. Validate visible reference fidelity separately from finite geometry, weights, UVs and runtime cost. Record inferred areas and uncertainty. The application repairs defective regions and reruns the affected checks. A rejected candidate stays inspectable with its failure reason.

### M4 — Complete autonomous animation and physics

- Translate a requested pose or action into timing, actor roles, motion beats, contact points, staging, props and environment constraints. Select or retarget compatible motion by anatomy, degrees of freedom, scale, rest pose and authored semantics. Reuse existing NoWorld shared motion when compatible. Author missing movement instead of assigning unrelated clips.
- Add body and limb IK, plant and grip constraints, rotation limits, root motion, transitions, facial expressions and synchronized actor and prop interactions. Add collision, self-collision and secondary motion for separate clothing, hair, tails and wings. Apply physics at the appropriate stage and bake reproducible clips and cloth results to formats the target can import.
- Expand editing and inspection with multi-track scrubbing, contact and constraint diagnostics, recovery during long scene generation, and scoped corrections. Optimize storage and sampling for long scenes.

**Exit gate.** Deliver a still pose, a subtle loop, creature locomotion/flight with meaningful contacts, and a long intense multi-actor sequence with prop synchronization, facial and secondary motion as required. Verify playback, timing, contact, deformation, cloth/body separation and export on the actual target assets.

### M5 — Apply artistic intelligence everywhere

- Create reusable, versioned style profiles for character and creature anatomy, wardrobe, equipment, architecture, terrain, materials, lighting response and motion. Guide silhouette, hierarchy, shape, palette, detail placement, staging and movement with approved examples. Evaluate the resulting assets against the selected style and reference.
- Finish local production memory for successes, failures, assets, parameters, elapsed time, resource use, quality checks, repairs, approvals and corrections. Use measured outcomes to route and refine future work. Make approved components and recipes searchable, reusable and scoped to appropriate asset families.
- Extend Alter, Berserker and Grand Convergence rules to generate complete geometry, apparel, materials, rig, motion and effects changes. Preserve identity and reuse an approved version if it exists. Keep shared rules distinct from per-character overrides and creative outputs distinct from canonical admission.
- Support reversible improvement through rules and feedback from the first release. Qualify optional on-device adapters or other training only with permitted examples, bounded resource use, a held-out quality check and rollback to known-good behavior.

**Exit gate.** Apply a shared style across unlike assets, apply one saved form profile to another eligible identity, and show a measurable benefit from reuse or feedback without damaging an approved original. Rollback restores the earlier behavior.

### M6 — Prove source fidelity and direct NoWorld handoff

- **NoWorld.** Produce the existing canonical admission schema and exact assets it requires. Retain identity, version, manifestation, wardrobe, native skeleton paths, ordered bones and rest transforms, all morph deltas, facial transparency, materials, source provenance and shared motion. Include original textures, editable source, GLB and other formats such as FBX where required and supported. Extend handoff across creature, environment and interactive assets under their actual contracts.
- Run the established NoWorld validators and native Unreal importer/cooker on a representative package. Add receipts that distinguish source-profile agreement, canonical validation, native import, and cooked Android content. Windows may serve the existing Unreal worker or compile Foundry. Ordinary creation and preparation stay on Android.
- **Koikatsu.** Inspect the existing installed runtime and authorized storage/export route. Parse supported cards and mod dependencies, recover original geometry and bind data, and preserve body, face, wardrobe, hair, textures, rig and morphs. Report unresolved dependencies, and leave the installation and originals intact. Use this only for requested edits outside Koikatsu.

**Exit gate.** An asset moves from Foundry through the real NoWorld gate with zero manual reconstruction or contract repair. The native import/cook result is separate and evidenced. A real Koikatsu asset needing an outside edit survives the source extraction and edit with its original data intact.

### M7 — Finish Android operations and delivery

- Measure ARM64 CPU, Vulkan and QNN per operation on the actual Samsung. Add native backends where compatible and beneficial, with real execution-provider evidence and CPU fallback. Schedule by quality, time, memory and thermal cost. Manage concurrent work, model loading, storage and cache reuse.
- Persist stage and sub-stage progress through app death, interruption and device restart where Android permits. Resume multi-hour work from verified checkpoints. Expose meaningful progress, budgets, recovery and actionable failures in Create, Queue, Projects, Library, the studios, Koikatsu, and Performance.
- Separate immutable sources, editable revisions, candidates, verified outputs, mobile derivatives and archives. Finish checksum-verified backup/restore, deduplication and safe cleanup. Keep optional TeraBox archival gated on verified transfer and recovery.
- Build and sign the current source using the existing application identity and signer. Verify upgrade and data migration, install and exercise normal flows on the Samsung offline, inspect crash logs, and publish the exact APK, source and evidence report after the release gates pass.

**Exit gate.** Target-device tests demonstrate offline use, restart recovery, useful reuse, thermal and memory stability, and a verified signed upgrade. The application shows which backends ran and which outputs passed each gate.

## Priority and dependencies

1. Start M0, the necessary M1 work and M2 together. The immediate critical path is one faithful, editable, separated humanoid from a real reference on the Samsung, with coherent cropped anatomy and a played clip.
2. In parallel, build the M4 motion and physics pipeline on the current rigs, and qualify the existing NoWorld and Koikatsu interfaces from M6. Use these to catch incompatibilities before multiplying asset categories.
3. Expand M3 to the reference dragon, object and environment cases. Apply M5 from the first approved outputs and extend it to all categories as they mature. Run a genuine NoWorld round trip early and repeat at release.
4. Use M7 constraints throughout. Complete device and release acceptance once the representative outputs work, and keep that work separate from repeated testing of already rejected candidates. Capture exact source commits and receipts at each meaningful checkpoint.

## Release evidence checklist

| Original acceptance case | Required proof on the release candidate |
|---|---|
| Image-to-3D object | Real image input, finished editable object, technical and visual checks, usable export. |
| Reference humanoid | Visible identity, coherent inferred cropped limb, independent wardrobe and working animation. |
| Rigged nonhuman | Reference-faithful complex anatomy, separate parts, validated motion and deformation. |
| Architecture/environment | Finished reference-driven environment with usable geometry, materials, scale and collision. |
| Exact Koikatsu intake | Real source extraction for a necessary outside edit, verified data preservation and dependency report. |
| Interruption and recovery | Real job interrupted through process termination, resumed without losing approved work, completed output compared against uninterrupted run. |
| Useful reuse | Measured cache or component reuse and a saved named form reused across eligible characters with identity retained. |
| NoWorld handoff | Existing canonical validation receipt, native Unreal import/cook receipt where required, and an asset ready for the existing integration path. |

Each result records the source revision, inputs and permissions, model and backend versions, APK version, device result, quality outcome, and any unresolved defect. Report engineering, device, visual and native-engine results separately.
