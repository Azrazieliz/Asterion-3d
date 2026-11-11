# Part-local PBR finish revisions — 2026-11-11

M1 additive engineering increment; the complete scope in SCOPE.md remains authoritative.

## Implemented

- A selectable **Edit individual part PBR finish** operation creates a separate derived project, changing the metallic and roughness factors of one exact stable part ID.
- A versioned, portable `part-materials.foundry` layer stores only deviations from role defaults. Existing projects without this file retain the original material behavior.
- A material layer is inherited by subsequent derived mesh, skin, colour, form and animation jobs and enters the cache key. Missing/remapped source IDs fail explicitly instead of silently dropping saved material intent.
- Per-part GLB materials and independently exported part GLBs carry the actual numeric factors. Shared geometry, skeleton, weights, UVs, colour attributes and original base-colour texture are untouched by the material operation.
- The receipt records a SHA-256 for the layer. Stage manifest surface/export dependencies bind its hash and ProjectPackage verifies and includes the editable layer.
- `PartMaterialsAcceptance` tests part isolation, save/load, inheritance, GLB factors, sibling preservation, extract-part export, checksum-independent parent immutability and malformed/reassigned data rejection. Both host scripts invoke the fixture.

## Limits and required follow-up

Numeric PBR metallic/roughness factors are **not** texture authoring, normal map creation, emissive/opacity stacks, UV unwrapping, reference-faithful shading or material appearance validation. The phone viewer does not yet establish physically based rendering under calibrated lights. Full PBR maps, independent texture sources, region editing and deformation-aware visual review remain M1–M4 work. This commit does not deliver a signed APK or Samsung acceptance by itself.

Any downstream future component changing part IDs must provide an explicit validated remapping. This layer refuses silent loss of overrides.
