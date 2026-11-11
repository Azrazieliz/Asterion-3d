# Independent part surface revisions — 2026-11-11

The Foundry **Inspect asset → Edit individual part colour** operation creates a new,
queued, editable child project. It acts on one exact stable part ID in a saved
multi-part or single-part mesh and applies a linear-RGB blend at the requested
strength. The original and sibling parts are not overwritten.

## Boundaries and provenance

- Exact parent mesh SHA-256 and retained texture SHA-256 are inputs to the
  child production/cache key. Existing parent provenance and immutable sources
  remain in their original storage paths.
- Source geometry, triangulation, UV coordinates, vertex normals, vertex alpha,
  skeleton joint order, rest transforms and blend weights are copied intact.
  The selected part's RGB vertex attributes alone may change.
- **Base-colour PNG bytes remain unchanged.** In glTF the texture is multiplied
  by the chosen vertex colour; this is a colour revision, not texture inpainting,
  UV correction, PBR map reconstruction or visual reference-fidelity acceptance.
- Every child has a separate mesh checkpoint, surface audit, staged checksum
  ledger and exported GLB/package. The receipt identifies the target part,
  colour, strength, revision method and unverified visual quality.
- No source view is invented, no rig/morph data is inferred, and native NoWorld
  or Unreal admission is not claimed.

## Checks

`PartSurfaceAcceptance` verifies part IDs, non-target part floats, normals,
UVs, alpha, geometry, skin weights, bone/rest transforms, parent immutability,
save/load, GLB generation and invalid-request rejection. The Windows build
script and Linux host suite both include this fixture.

### Remaining milestone work

M1 still needs fully editable per-part PBR texture stacks, UV editing,
morphs, collision/LOD and complete source-grade round trips. M2–M7 and
Samsung reference/visual/thermal acceptance remain open. Host checks do not
establish on-device behavior.
