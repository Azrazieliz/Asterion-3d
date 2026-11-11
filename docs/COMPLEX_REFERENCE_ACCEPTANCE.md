# High-complexity reference acceptance and 3D printing

**Status: acceptance pending.** These are the three private references supplied
by the owner, not generated 3D models. The user has specified that production
quality must be maintained for these and even more complicated designs.

| Case | Original image | Dimensions | Exact source SHA-256 |
|---|---|---|---|
| CXR-01 | Cosmic_Dragon_Women_desired_2D_lineup.png | 1536 × 864 | feb44d0bd9bf23fea95a6a4c82c8f485742eb75482c77649fd9d59c4759ff895 |
| CXR-02 | Dragon, Void, and Salt Fusion.png | 1672 × 941 | 731a6fa491fce892812dfb31cf5160e07d36f8de0b818fd3334a781d9ef16d5f |
| CXR-03 | Crystalline Cosmic Dragon Character Sheet.png | 1672 × 941 | beb8a4a77d1695c096616f8676d83abd4e53f502c53cc7e987e383ad7258cd0f |

The actual reference PNGs are intentionally NOT in this public source
repository. The hashes let the owner or a private worker verify exact
uploaded source bytes before real reconstruction runs.

## Distinct content and difficult geometry

CXR-01 is a lineup of **five distinct characters** with separate full-body
presentations and smaller side/back views. Each has its own horn, wing,
proportion, face, armor, hair, palette and complex tail requirements. Five
different bodies must not be collapsed to one generic humanoid template.

CXR-02 is an annotated Dragon / Void / Salt character: large starfield-like
wings, branching horns, long white hair, salt-like crystal erosion,
dark/pale body patterns, precise eyes and face, tail and front/side/back
coherence. The reference mentions about 180 cm height; this is only a
user-supplied design reference, not a measured 3D reconstruction.

CXR-03 is an elaborate crystalline cosmic fusion: **three distinguishable
wing pairs/layers** (light translucent, dark starfield and pale membrane
groups), multiple branching horns, extremely long hair, side/back wing
placement, curled tail with a bright core, thin claws and fine body patterns.
Each actual paired membrane needs independent geometry, attachment, rigging
and alpha/material control. A single textured plane or one dragon wing pair
does not qualify.

These sheets include swatches, textual notes and detail crops. They are NOT
single unambiguous orthographic photos. A future sheet parser must separate
the views, match identity and pose, and preserve detail panels; ambiguity
requires an explicit incomplete-quality result.

## Required evidence per real input

- Source SHA and crop/view provenance, subject bounds, camera/pose confidence,
  front/side/back reference agreement and retained original imagery.
- Exact independent component topology, part IDs and named physical attachments
  for all wings/horns/tail/hair/armor. Missing fingers and hidden anatomy
  must be completed and visually checked, not guessed silently.
- Source-aligned silhouette, body proportions, eyes, face, palette,
  translucency, UV/material response, crystal patterns and hair detail in
  multiple rendered angles at the target NoWorld appearance.
- Meaningful skeleton/skin/wing-tail deformation, facial channels, secondary
  motion and at least one played animation on the exported rig.
- Source-preserving staged repairs that improve a measured defect without
  erasing already accepted detail. Keep unapproved results as candidates.
- Exact canonical NoWorld identities, native asset/skeleton/wardrobe/morph
  bindings, one shared motion bank, native Unreal importer/cook and phone
  result. Do not invent canonical IDs or call a generic GLB "native accepted".
- Actual Samsung ARM64 inference, memory/thermal/long-job recovery,
  GLB export/source round-trip, independently reviewed visual outputs.
- Any **3D print derivative** must pass topology, mm sizing, connected or
  explicitly separately closed shells, and then minimum wall thickness,
  clearances, supports, overhang, collision, hollow/drain/slicer verification
  and a physical test print. Binary STL loses colour, transparency, emission
  and all rig/motion channels; never represent it as a coloured game asset.

## What is implemented and what remains

Host source tests cover 256 possible stable mesh parts, a 128-part + 288
texture-map integration fixture, 63,000-triangle scaled STL output, negative
winding/nonmanifold/inward/isolated-shell cases, an independent Python binary
STL parser, and independent GLB validation. Structural tests are essential
but do not prove likeness to any of these three sheets.

There is not yet a reference-faithful character generated from these source
images. Neither current reconstruction models nor technical GLB tests meet
the artistic gate. No 3MF colour printing, thin-wall/slicer inspection, native
NoWorld admission or physical print of these characters was performed.
Until actual output passes the above tests, status remains NOT ACCEPTED.
