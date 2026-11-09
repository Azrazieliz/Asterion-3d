# Verification — 2026-11-09

This is a **development production slice, not the finished universal studio**.

## Delivered build

- Application: `com.asterion.foundry`, version `0.1.0-production-slice` (1).
- APK: `dist/AsterionFoundry-v0.1.0-DEV.apk`, 41,363 bytes.
- SHA-256: `782c4543ca27eeb847cf48e1806a1a89c83fd2b23122d85838721294807ad08d`.
- Development certificate, Android APK v3 signature verified. No existing application's signer or data changed.
- Android SDK 35, Java 17 compilation, D8 and AAPT2 succeeded on the authorized Windows development worker. No PC dependency during app production operations.
- CPU geometry uses Android ART. OpenGL ES is used for preview rendering. There is no Vulkan compute or QNN implementation in this build.

## Evidence

`docs/BUILD_LOG_20261109.txt` is the actual build log (UTF-16). Twelve core cases passed: six recipes with checkpoint roundtrip/export, silhouette extrusion, unknown-route rejection, invalid-scale rejection, corrupt-checkpoint rejection, nonfinite-data rejection and triangulated OBJ intake. Closed recipes additionally require positive signed volume.

`scripts/validate_glb.py` independently parsed all seven emitted GLBs, checking headers, binary/accessor bounds, finite positions, normalized normals, bounding boxes, inverse-bind/rest-transform consistency, valid joints and normalized weights. This is a focused validator, not the full Khronos glTF validator and not visual/Unreal acceptance.

| Output | Triangles | Bones | GLB bytes |
|---|---:|---:|---:|
| Sword | 558 | 0 | 81,308 |
| Pavilion | 84 | 0 | 13,036 |
| Tree | 1,596 | 0 | 230,772 |
| Terrain | 4,608 | 0 | 664,516 |
| Humanoid template | 5,280 | 12 | 1,143,412 |
| Quadruped template | 5,808 | 13 | 1,257,560 |

## Initial suite status

| Owner acceptance case | Implemented | Build/host verified | Device tested | Visually validated |
|---|---|---|---|---|
| Image-to-3D object | Limited silhouette extrusion only; no depth inference | Yes, synthetic plain-background fixture | No | No |
| Humanoid reconstructed from references | **No**; template is not reconstruction | Template only | No | No |
| Nonhuman creature with appropriate rigging | Quadruped template with rigid weights only | Rest/skin structure only | No | No |
| Architecture/environment | Pavilion, tree and terrain recipes | Yes | No | No |
| Exact existing Koikatsu import | **No**; card preservation only | App compiled | No | No |
| Interrupted/resumed production job | Stage checkpoint/recovery implemented | Mesh save/load roundtrip only | No | Not applicable |
| Repeat job with useful reuse | Hash-keyed geometry reuse implemented | App compiled | No | Not applicable |
| NoWorld-compatible export | Generic GLB and unbound intermediate manifest only | Format checks only | No | No; native admission absent |

## Device inspection

Asterion Control returned paired/reachable SM-S948B on Android 17, Wi-Fi and direct WSS connected. At inspection: approximately 10.8 GiB total RAM, 3.4 GiB available RAM, 14.4 GiB free storage, battery 25%, temperature 29.9 °C, thermal status 0. These are transient measurements, not a sustained benchmark.

The app launch request was refused by Control because its screen was not in the foreground. A successful input acknowledgment did not establish the expected foreground screen. Installation and phone acceptance must not be inferred from connectivity or the build log. The existing development connection was also checked: `adb devices -l` returned an empty device list. No new pairing, reinstallation or device reset was attempted.

## Material limitations

No learned image/text geometry reconstruction, source-accurate Koikatsu extraction, mod resolution, morph editing, animation production/retargeting, multi-participant scene production, full UV unwrap/baking, region repair, reference-view scoring, collision/LOD generation, native Vulkan/QNN compute, measured backend routing, adaptive production learning, LoRA, FBX, verified TeraBox archive, or native Unreal import/cooking.

The template meshes are simple disconnected primitives where applicable; rigid weights are not smooth production skinning. OBJ import accepts triangulated geometry only and does not preserve original materials/UVs/normals. Original source bytes remain intact. UVs on generated meshes are planar coordinates and may overlap; receipt marks this unchecked. Templates do not claim NoWorld painterly anime visual fidelity. Queue storage has stage recovery, not sub-stage streaming for two-hour inference jobs; no long-duration phone test has been run.
