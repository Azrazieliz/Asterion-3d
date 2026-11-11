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

## Android evidence for 0.1.0

Android Settings on SM-S948B confirmed the installed version name
`0.1.0-production-slice`. Control observed package `com.asterion.foundry`
in the foreground and exercised Create, Queue and Projects. Production
job `18234292-fa56-42a6-8ca3-e21f9c0888e1` reported 558 triangles,
23 ms and GLB SHA-256
`099ed0d87b228b66c87e22b8d004e93f1058cfb97e0a0796a641ab3d4ebcae4c`.
Android's document picker accepted Save for `Foundry-18234292.zip`;
the saved ZIP contents were not independently reopened.

Job `377f4dfd-de09-4031-a116-fc8efb4c92ee` was observed paused with a
saved checkpoint and later complete, reporting reuse and the identical
GLB digest at 9 ms. Uncontrolled foreground changes prevented
attributing this to a confirmed force-stop and relaunch. No error dialog
was observed. The available connection could not read Foundry crash logs.

## Native depth and multi-view implementation

The 0.2 source adds bundled MiDaS inference, relative-depth relief,
orthographic visual hull, reference overlap metrics, bounded topology
analysis, immutable GLB intake, revision editing, a searchable asset
library, source-role provenance and private process-exit diagnostics.
Scope and engine admission still follow [SCOPE.md](SCOPE.md).

Build-machine evidence:
- The exact MiDaS model ran with LiteRT on Linux x86-64. Input and output
  were float32 `[1,256,256,3]` and `[1,256,256]`. The tested output was finite.
- Android source compilation used API 35 and the actual LiteRT Java API.
- The pinned ARM64 LiteRT ELF has 16 KiB LOAD alignment.
- The focused suite passed 16 cases and validated ten GLB files, including
  the visual hull, closed depth relief and immutable-parent revision.

The full 0.2 user acceptance suite requires running the APK on the Samsung.
MiDaS does not provide metric depth, unseen surfaces or anatomically
accurate character reconstruction. A hull is constrained by its supplied
views and does not recover occluded concavities. GLB pass-through preserves
the source rig, morph, animation and material bytes with container-level
checks. It does not import that source into Foundry's mesh editor or
admit it to NoWorld.

### Signed 0.2 APK

- Source commit `abc5430f22b370df72b529bf0f54a9fb2ccd4857`.
- Signed APK published in commit `6849d73e9ed259179efbfa2855b197c8536519fc`.
- Package `com.asterion.foundry`, version code 2,
  version name `0.2.0-native-depth`, ABI `arm64-v8a`.
- SHA-256 `b9c098cef7518b636c322699c391a62fd22da4860d5f26485b5e195a718349da`.
- Downloaded APK matched the build digest and original 0.1 signing
  certificate. Signature, ZIP integrity, model checksum and 16 KiB
  native library alignment passed. There is no Android Internet
  permission in the manifest.
- A synthetic shaded-object fixture went through the exact pinned
  model on the host, then through Foundry's depth mesher and GLB
  exporter. The 12,116-triangle GLB passed binary checks. The audit
  counted zero boundary edges, nonmanifold edges, winding conflicts
  and collapsed triangles. This checks the pipeline on one fixture.
- Original GLB inspection read both the generated relief and a skinned
  fixture. The latter reported its embedded skin.
- Device execution of the updated APK and comparative visual quality
  remain unverified. The 0.1 phone checks above apply to 0.1 only.


## 2026-11-11 — Staged quality v0.5 evaluation

Development branch: `work/staged-production-quality-20261111`.
Build source: `232c650198edfff60c4f5e0b5469a5ad71cfd15a`.
APK publishing commit: `fcdb061a167eed85006352ff1cd527bff1f3dd6d`.
APK: `dist/AsterionFoundry-v0.5.0-STAGED-EVALUATION.apk`.
Version code 5, version name `0.5.0-staged-evaluation`.
APK SHA-256: `e492c744943987788db48819dfbaf08fe2b78610b7ce71bbd9efc0d5baa0116f`.

What changed:
- `StageManifest` persists atomic, project-owned geometry/surface/export checkpoint
  evidence with SHA-256 hashes, dependency invalidation and tamper detection.
  Nonidentical upstream stages invalidate downstream evidence without deleting
  prior outputs or the immutable originals. No stage ledger is fabricated for
  old original-GLB-only packages.
- `SurfaceAudit` records geometry UV range/degeneracy, sampled texture colours,
  alpha and vertex palette separately from *unverified* reference appearance.
  This audit does not automatically improve texture fidelity or validate seams,
  morphology, materials under target lighting, rig deformation or visual quality.
- `ProductionService` includes audit data in the receipt and writes stage evidence
  for generated editable assets. `ProjectPackage` verifies and exports the
  stage ledger, preserving the existing project, source and part export paths.
- `StagePipelineAcceptance` exercises checksum binding, stage reuse,
  dependency invalidation, unsafe output rejection, tamper detection and
  sampled surface evidence. The Windows build invokes the fixture when the
  existing host `org.json` JAR is available.

Verified by the existing authorized Windows worker:
- Android Java compilation, D8, native model preparation and integrity, host core
  checks, stage fixture and GLB validation passed at the build source above.
- APK v3 signature and 16 KiB archive/JNI alignment checks passed.
- Original v0.2 and new v0.5 APK certificate SHA-256 matches:
  `9bc439712a0a460cc4703ebf254aaaf9bf3cc6e67b0ced6fc12162ed3ca3b9ad`.
- Build output emitted known `zip WARNING: header mismatch` messages during
  tools processing. The final verification tools returned success; investigate
  these warnings independently rather than asserting they are harmless.

Not accepted:
- The Samsung was paired but `phone_status` reported `reachable:false`;
  native `phone_ui` and bridge checks returned HTTP 503 during this work.
  No device install, live preview, inference benchmark, long-job recovery,
  graphics quality, material or NoWorld native acceptance was established.
- The full owner scope in `docs/SCOPE.md` and all roadmap completion gates
  remain open. This is a bounded engineering increment, not the complete app.

## 2026-11-11 — v0.5.1 part-surface and interruption-recovery increment

Active branch: `work/staged-production-quality-20261111`.
Signed APK source commit: `b3d417c0985259f4404d83bffbc264ff3673f544`.
Signed APK publishing commit: `befe94a075bfa45234dc61eb8f3ef0ff105cb92d`.
Published file: `dist/AsterionFoundry-v0.5.1-PRODUCTION-DEV.apk`.
Version code **6**; version name `0.5.1-part-surfaces-recovery`; package
`com.asterion.foundry`; SHA-256
`30d35b39063453baa034b902b3bcc8bf60aa87f22fdc3cff1b3ec5a0c090703d`;
file size **77,395,437 bytes**.

The Windows Asterion Control worker `LAPTOP-1LI4VRCJ` compiled the precise
source commit with the preserved project environment; host geometry,
part-surface, form, skin, rig, motion, exported GLB, and staged quality
regressions passed. The build script reported APK v3 signature verification,
alignment, embedded model/notice/JNI verification and the SHA above.
A direct `apksigner --print-certs` comparison with the previous signed v0.5
APK found the identical signer certificate SHA-256:
`9bc439712a0a460cc4703ebf254aaaf9bf3cc6e67b0ced6fc12162ed3ca3b9ad`.
The original signed v0.5 file remains published and unchanged.

GitHub Actions run
[43017024386](https://github.com/Azrazieliz/Asterion-3d/actions/runs/43017024386)
passed its host regression and `compileDebugJavaWithJavac` jobs for the
identical source tree. This Android-aware compilation uncovered and then
resolved 13 Java API incompatibilities and one checked JSON exception
introduced by the new UI. The Android CI job does not sign, install or test
an APK.

Implemented production improvements:
- `PartSurface`: exact named-part vertex-colour editing with bounded strength,
  separate editable child project and retained base texture and skin/geometry.
  See [part-surface details](PART_SURFACE_REVISIONS.md).
- `Store` and `ProductionService`: unexpected-process-interruption marker,
  selective foreground-reentry requeue, and saved stage checksum verification
  before accepting resumed evidence. See
  [process-interruption limits](PROCESS_INTERRUPTION_RECOVERY.md).
- Existing M0–M7 scope and acceptance are unchanged.

Remaining evidence:
- Samsung SM-S948B was paired and reachable via the authorized native Control
  device connection, but **v0.5.1 installation, version upgrade, interactive
  part-editing and forced-process-restart validation have not been observed**.
  The retained Windows `adb devices -l` returned no attached devices. The
  native Android foreground UI was unrelated to Foundry, so connection and
  native input acknowledgments must not be used as app acceptance.
- Build tools continue to emit `zip WARNING: header mismatch` messages
  despite successful final integrity/signature checks. Warning root cause is
  still open.
- Texture painting, separate PBR stacks, identity/pose and reference fidelity,
  complete rig deformation, autonomous animation, source-exact Koikatsu
  conversion, NoWorld native/Unreal admission, sustained phone thermal/runtime
  and release-grade visual checks remain **unverified or incomplete**.


## 2026-11-11 — Source-only M1 PBR and independent part replacement increments

Development branch: `work/staged-production-quality-20261111`. These are **source commits after the signed v0.5.1 APK**. Do not attribute their features to the previously signed APK.

1. Commit `8f05499073ea5d48ccf34f1d49b98dff06520879` adds durable versioned per-part metallic/roughness factors, independent PBR finish revisions, inheritance across derived projects, part-aware GLB and exported part GLBs, SHA-256-bound project/stage material records, and host regression coverage. Original mesh, texture and sibling part data are preserved. It does not add physically-based texture stacks or visual appearance validation. [GitHub Actions run 43021042181](https://github.com/Azrazieliz/Asterion-3d/actions/runs/43021042181): host-regressions **passed**, android-source-compile **passed**.
2. Commit `1a11e010282a7a7d15fc6733c01467885ac1bfc2` adds one-part geometry transfer between completed editable projects with immutable donor provenance, matching part roles, exact joint hierarchy/rest pose, matching atlas SHA-256, and unchanged target part IDs and PBR layer. Incompatible donors fail rather than silently retargeting or reinterpreting UVs. [GitHub Actions run 43021825482](https://github.com/Azrazieliz/Asterion-3d/actions/runs/43021825482): host-regressions **passed**, android-source-compile **passed**. No automatic garment fit, deformation, collision, clipping or target appearance acceptance is asserted.

**Limits / next gate:** GitHub Actions compiled Android Java sources and ran host fixtures. These CI jobs do **not** sign/install the APK, run inference on Samsung, verify visual outputs, benchmark thermals, perform true NoWorld/Unreal admission or establish full M1–M7 completion. The latest actually published and previously Windows-signed binary remains `dist/AsterionFoundry-v0.5.1-PRODUCTION-DEV.apk`.

The current ChatGPT tool connection could update the canonical GitHub repository and observe GitHub CI, but it did not expose direct Asterion Control Windows keyboard/mouse/phone-worker operations. Therefore no Windows signing job, PC browser input, correct-conversation verification or automatic next-message Send event was executed or asserted. Never represent this GitHub write path as an Asterion Control desktop automation cycle.

**Next real production milestone:** Build the signed next APK with the authorized existing Windows worker (preserve the existing certificate and all published APKs), install and exercise the actual new part-finish/part-replacement flows on Samsung, inspect independent material response under real lighting, then continue M1 with separate per-part PBR texture maps (base colour, normal, metallic/roughness, opacity and emissive) and checkpoint-aware source-preserving texture edits. Measure actual visual fidelity separately from technical GLB checks. Coordinate worker focus/resources with the other project; do not silently fall back to shared PC input.


## 2026-11-11 — Native UI simplification and strict model bundling (source only)

Starting from the previous M1 checkpoint, the branch now includes:

- `d53b25b2`: APK-owned first-use model extraction in the production service, replacing the visible manual ZIP install requirement.
- `af4b26f0`: pinned six-member ONNX archive packaged by Windows/Gradle with an explicit full-model release gate; version code 7 is **source configuration only**.
- `b8f6103d`, `cda0e071`, `23c6a1ac`, `e033bacd`: four-tab navigation, guided creation, readable job states, expandable editor, hidden developer evidence and removal of the old import callback.
- `f23cd36d`: final signed-APK contents verification now checks the large embedded reconstruction archive in addition to existing MiDaS, MoveNet and JNI checks.
- `cb361699`: hermetic build-policy tests cover missing, copied and tampered pinned model archives without downloading the 700 MB source pack in CI.

**What was verified:** code was committed to the canonical branch; prior GitHub Actions source compilation and host regression jobs passed. The new model-pack contract tests have a GitHub CI run, but their result must be inspected separately. **What was not verified:** a new Windows signed APK, actual package size, model pack availability on the Windows worker, Android installation/first-use extraction, Samsung screenshot usability, offline inference quality, visual fidelity, and NoWorld native/Unreal import. The existing published v0.5.1 APK is unchanged. See [UI_AND_BUNDLING.md](UI_AND_BUNDLING.md) for the non-negotiable phone acceptance gate.
