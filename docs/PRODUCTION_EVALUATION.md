# Production evaluation

This source checkpoint implements the flows below. See [the retained acceptance scope](SCOPE.md)
for the requirements a production release must satisfy.

| Flow | How to exercise it | Current boundary |
|---|---|---|
| Named form rules | Save and revise a shared rule in Library. On a completed editable rigged project, save a base-specific rule or use a saved rule to produce a separate project. Prefer a finished variant's settings on its original base to reuse those rules on later requests for that form. Restore older settings from rule history as a new version. The queued project and exported project JSON carry an exact rule revision and its settings. | The implemented rules control proportions and material tint. The head/face joint tint exclusion is optional. Feedback saves a scoped preference. It does not train model weights, generate armor, anatomy, new effects, or approve NoWorld forms. |
| Animation | On a completed editable rigged project, select Create animation and enter `idle`, `walk in place`, or `tail wag`, depending on the rig. Play the result in Inspect and export the editable package. Edit animation tracks to create a separate clip revision. | Deterministic joint tracks and rigid Foundry skinning. The clip evaluator checks finite deformation and optional joint-origin contact tolerances. It does not evaluate perceived motion quality, foot-ground contact, or soft skinning. |
| Reference reconstruction | Run the local volumetric candidate with the pinned model pack. The receipt separates silhouette overlap and base-colour error in the nominal input camera. | A projected colour improvement does not fix geometry or establish character identity, hidden anatomy, separated parts, or animation readiness. Candidate export remains gated. |
| Human pose evidence | Use Character reference candidate in Studio. The bundled [MoveNet](pose-evidence.md) records supported landmark predictions separately from the mesh, with reuse by source and model checksum. | The supplied illustration had no supported torso at the configured threshold. This stage cannot establish hidden joints or complete geometry. |
| NoWorld GLB preflight | Choose Check NoWorld GLB handoff, a self-contained original GLB and its declared JSON profile. Export includes the original bytes, the original profile and the comparison receipt. | One skinned source part. Matching the supplied profile checks source structure, not ownership, canonical membership or native Unreal admission. The upstream NoWorld package gate uses its own supported formats and evidence. |
| Original GLB material variant | Inspect a preserved GLB, select Create a material variant, choose a unique named material and enter its tint. The package contains the original and derived GLBs. | Changes only the selected base-color RGB factors. Alpha, texture data, rig, skin, morph and animation data are preserved. This does not extract Koikatsu cards or resolve their mods. |

The APK build uses the existing Android package ID and development signer. The
evaluation version must stay distinct from the production acceptance result.

Run `bash scripts/test-core.sh` for the focused host tests. Set `FOUNDRY_JSON_JAR`
to an Android-compatible host `org.json` JAR to include the NoWorld and original
material fixtures. Read [the TripoSR projection test](REFERENCE_PROJECTION.md)
and [the rejected DWPose comparison](DWPOSE_EVALUATION.md) for the actual
illustration results. The input illustration and generated meshes remain private.
