# Production evaluation

This source checkpoint implements the flows below. See [the retained acceptance scope](SCOPE.md)
for the requirements a production release must satisfy.

| Flow | How to exercise it | Current boundary |
|---|---|---|
| Named form rules | Save and revise a shared rule in Library. On a completed editable rigged project, save a base-specific rule or use a saved rule to produce a separate project. Prefer a finished variant's settings on its original base to reuse those rules on later requests for that form. Restore older settings from rule history as a new version. The queued project and exported project JSON carry an exact rule revision and its settings. | The implemented rules control proportions and material tint. The head/face joint tint exclusion is optional. Feedback saves a scoped preference. It does not train model weights, generate armor, anatomy, new effects, or approve NoWorld forms. |
| Animation | Select an anatomy-compatible action, or assemble two editable actors and create a coordinated sequence. Inspect supports playback and timeline scrubbing. The track editor supports actor-scoped joint names, still-pose holds, timed stages and baked gravity arcs. | Deterministic animation up to 600 seconds. The evaluator checks sampled deformation and optional joint-origin contact tolerances. Terrain collision, full-body IK and automatically directed complex scenes remain open. |
| Blended skinning | New humanoid and quadruped template jobs bind nearby joint regions automatically. Inspect an older rigid editable rig and select Blend skin weights to create a separate project. The viewport, checkpoint and GLB use the same weights. | Source checkpoint after the 0.4 APK build. The local transition algorithm requires existing joints and an authored rigid bind. Validation samples triangle areas during motion. It does not recover missing geometry, join disconnected shells or prove that the pose looks correct. |
| Separate wardrobe and creature parts | The clothed humanoid keeps a garment mesh independent of the body. The articulated dragon has distinct wing membranes, armor and accessories. Toggle each part in Inspect. The package contains the full scene, individual GLBs with the shared rig and a `parts.json` manifest. | The tunic's rest shape uses gravity, distance constraints and an analytic torso collider. Dynamic cloth, self-collision, material detail reconstruction, and NoWorld native admission are still open. |
| Articulated dragon | Select Articulated dragon, then request dragon flight, dragon landing or dragon perch on that rig. The rig contains 50 joints, with wing digits and tips, jaw, segmented tail and four articulated legs. | Authored procedural asset and motion basis. The supplied illustration's geometry and art direction have not passed reference or Wuthering Waves/Honkai Impact 3rd quality acceptance. |
| Reference reconstruction | Run the local volumetric candidate with the pinned model pack. The receipt separates silhouette overlap and base-colour error in the nominal input camera. | A projected colour improvement does not fix geometry or establish character identity, hidden anatomy, separated parts, or animation readiness. Candidate export remains gated. |
| Calibrated part references | Open a [part evidence bundle](PART_REFERENCE_PIPELINE.md) to reconstruct independently labelled meshes on common view canvases. Stable IDs survive revisions and the GLB carries separate mesh nodes. | The current input requires prepared masks. Synthetic shared-placement and export checks passed. Automatic labelling, finished reference fidelity, rigging and device acceptance remain open. |
| Human pose evidence | Use Character reference candidate in Studio. The bundled [MoveNet](pose-evidence.md) records supported landmark predictions separately from the mesh, with reuse by source and model checksum. | The supplied illustration had no supported torso at the configured threshold. This stage cannot establish hidden joints or complete geometry. |
| NoWorld GLB preflight | Choose Check NoWorld GLB handoff, a self-contained original GLB and its declared JSON profile. Export includes the original bytes, the original profile and the comparison receipt. | One skinned source part. Matching the supplied profile checks source structure, not ownership, canonical membership or native Unreal admission. The upstream NoWorld package gate uses its own supported formats and evidence. |
| Original GLB material variant | Inspect a preserved GLB, select Create a material variant, choose a unique named material and enter its tint. The package contains the original and derived GLBs. | Changes only the selected base-color RGB factors. Alpha, texture data, rig, skin, morph and animation data are preserved. This does not extract Koikatsu cards or resolve their mods. |

The APK build uses the existing Android package ID and development signer. The
evaluation version must stay distinct from the production acceptance result.

The Windows worker built and verified the v0.4 evaluation APK from
`ab80c5fb4780808ffb9febd8e6051f997d4508ef`. The host suite, embedded model/JNI
checks, and APK v3 signature verification passed. The build logged ZIP header
warnings before the successful checks. No phone installation or visual
acceptance was verified for this build. See the
[build receipt](https://github.com/Azrazieliz/Asterion-Control-Bridge/blob/main/results/20261110-foundry-ab80c5fb4780-mwaci0g1-pc_job_status.json).

Run `bash scripts/test-core.sh` for the focused host tests. Set `FOUNDRY_JSON_JAR`
to an Android-compatible host `org.json` JAR to include the NoWorld and original
material fixtures. Read [the TripoSR projection test](REFERENCE_PROJECTION.md)
and [the rejected DWPose comparison](DWPOSE_EVALUATION.md) for the actual
illustration results. The input illustration and generated meshes remain private.

[StdGEN](https://github.com/hyz317/StdGEN) remains a research reference for
separating body, clothing and hair. Its authors limit the released checkpoints
to research purposes. They are not included as a Foundry production backend.
