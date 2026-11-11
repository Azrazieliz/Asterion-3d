# Source-safe mesh-part replacement — 2026-11-11

The **Replace a separate mesh part** operation derives a new project from two
existing complete editable projects. It replaces exactly one named target
part with a donor part of the *same role*, keeping the target part ID and
all untouched target part vertices and rig data. Original sources and
parent/donor checkpoints are immutable.

Safety gates:
- Verify both source checkpoint SHA-256 digests against their existing receipts.
- Require same ordered skeleton, names, hierarchy and bit-identical rest transforms.
  Rigless pairs are supported; no implicit retargeting is attempted.
- Require both projects to have the exact same base-colour texture SHA-256
  (or neither has one). Do not reinterpret UVs against another person's atlas.
- Reject absent parts, role mismatches, over-budget geometry and invalid source meshes.
- Preserve the target material PBR layer, part IDs, all unrelated sibling parts and
  unmodified target animation data while keeping the donor as an independently
  referenced immutable provenance source.

Receipts contain the donor project ID, exact donor mesh SHA, source/target
part IDs and method version. All inherited target PBR edits survive because
target part IDs remain stable; the donor material values do not overwrite the
target appearance. Host acceptance checks copy isolation, exact donor geometry,
skeleton match, checkpoint round trips, GLB export and rejection paths.

This is **not** automatic wardrobe fit, retopology, topology stitching, collision
assessment, retargeting, source-fidelity scoring or animation deformation
acceptance. Misaligned but technically compatible assets require a future
spatial fit/visual check. Existing M1–M7 milestones and acceptance are unchanged.
