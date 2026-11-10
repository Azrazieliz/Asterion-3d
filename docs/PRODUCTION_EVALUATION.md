# Production evaluation

This source checkpoint implements the flows below. See [the retained acceptance scope](SCOPE.md)
for the requirements a production release must satisfy.

| Flow | How to exercise it | Current boundary |
|---|---|---|
| Named form rules | Save and revise a shared rule in Library. On a completed editable rigged project, save a base-specific rule or use a saved rule to produce a separate project. The queued project and exported project JSON carry an exact rule revision and its settings. | The implemented rules control proportions and material tint. The head/face joint tint exclusion is optional. These rules do not generate armor, anatomy, new effects, or approved NoWorld forms. |
| Animation | On a completed editable rigged project, select Create animation and enter `idle`, `walk in place`, or `tail wag`, depending on the rig. Play the result in Inspect and export the editable package. Edit animation tracks to create a separate clip revision. | Deterministic joint tracks and rigid Foundry skinning. The clip evaluator checks finite deformation and optional joint-origin contact tolerances. It does not evaluate perceived motion quality, foot-ground contact, or soft skinning. |
| Reference reconstruction | Run the local volumetric candidate with the pinned model pack. The receipt separates silhouette overlap and base-colour error in the nominal input camera. | A projected colour improvement does not fix geometry or establish character identity, hidden anatomy, separated parts, or animation readiness. Candidate export remains gated. |
| NoWorld GLB preflight | Choose Check NoWorld GLB handoff, a self-contained original GLB and its declared JSON profile. Export includes the original bytes, the original profile and the comparison receipt. | One skinned source part. Matching the supplied profile checks source structure, not ownership, canonical membership or native Unreal admission. The upstream NoWorld package gate uses its own supported formats and evidence. |

The APK build uses the existing Android package ID and development signer. The
evaluation version must stay distinct from the production acceptance result.
