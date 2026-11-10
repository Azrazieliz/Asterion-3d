# Calibrated part references

This is an implementation checkpoint for M1 and M2 in the [completion roadmap](COMPLETION_ROADMAP.md). The broad asset, motion, artistic intelligence, device, NoWorld and Koikatsu requirements continue to follow [the retained scope](SCOPE.md).

Foundry can carve independently labelled parts in shared orthographic coordinates. Each part keeps its scale and placement on the original canvas, its own base-colour contribution to a shared atlas, and a stable ID. The editable checkpoint, extracted mesh, revisions, GLB node metadata, and exported part manifest carry that ID. Older multipart checkpoints load with stable IDs derived from their existing order. Actor assembly namespaces the IDs.

## Input contract

Select **Calibrated part references** in Create or Studio. Supply one ZIP containing `manifest.json` and PNG images at `parts/<id>/front.png`, `parts/<id>/right.png`, and optionally `parts/<id>/back.png`. The metres setting denotes the height of the **full** canvas. This input is intended for an upstream part segmentation stage or previously prepared reference evidence.

```json
{
  "schema": "asterion.foundry.part-references.v1",
  "parts": [
    {"id": "torso-body", "name": "Body", "role": "body"},
    {"id": "coat-layer", "name": "Coat", "role": "garment"}
  ]
}
```

Use one to eight uniquely identified parts. All images of a view use identical uncropped canvases, in the same pose, at the same pixels-per-metre scale and with the same vertical origin. Front and right canvases may have different widths. Back uses the front dimensions and faces the subject from behind. Images must have a transparent background and be at most 512 pixels per axis. Part masks should include intended hidden extent where supported by other evidence. An empty alpha region removes space during carving. Incompatible canvases and masks with insufficient overlap are rejected. ZIP members are read within fixed bounds and never extracted as file paths.

## Evidence and boundary

The receipt reports each part's source-supported overlap, connected components and topology within the audit budget. Fit between surfaces, self-collision, hidden concavities, final textures, and deformation are unverified. The resulting candidate opens in Inspect, where mesh parts can be isolated. Production export remains gated. This stage does not generate part masks automatically, recover a posed character from a single illustration, infer exact occluded anatomy, or produce an animation-ready rig.

`PartHullAcceptance` uses two deliberately separated shapes at distinct heights and depths. It checks shared placement, per-part colour, stable IDs across the mesh lifecycle, legacy checkpoint migration, and rejection of contradictory evidence. The independent GLB checks include the generated multipart file. Synthetic evidence does not constitute character or Samsung acceptance.
