# Reference fidelity correction

This branch continues the volumetric evaluation. The full [Foundry scope](SCOPE.md)
remains open. The released APK and the Samsung installation were not changed.

| Capability | Evidence | Result |
|---|---|---|
| Automatic foreground extraction | ISNet prior and pinned SlimSAM 77 ONNX encoder and mask decoder ran locally on the supplied illustration. The selected mask excluded the large mirror section that leaked into the earlier run. | Improved on this input |
| Volumetric reconstruction and interruption recovery | The Java host fixture ran the Android pipeline's model interfaces in a network-denied process. It stopped after encoding and resumed from the saved triplanes. The resumed phase took 2.16 seconds on the host and generated 60,380 triangles. | Host verified |
| Exported surface topology | Independent exact-position welding counted zero boundary edges and zero edges with more than two incident faces. The app's bounded topology audit skipped this mesh because it exceeds 180,000 expanded vertices. | Host verified, app audit skipped |
| Character fidelity | Front, side and back renders showed a distorted face and fused hair, scales and tail. The model generated one mesh without a skeleton or independently editable character parts. | **Failed acceptance** |
| Registered original reuse | A local SHA-256 association resolves the original self-contained GLB before model inference. Host tests verified persistence, byte equality, conflicting registration rejection and checksum failure on tampering. The association comes from the user's registration. | Host verified |
| Textured multi-view export | Synthetic calibrated front, right and back references passed per-face texture mapping, coverage, embedded PNG, UV bounds and GLB buffer checks. The atlas accompanies the editable project and follows scale revisions. Unseen surfaces receive an estimated vertex colour. | Host verified |
| Android compilation | Java compilation against Android API 35 and D8 compilation with the native model runtime dependencies. | Compile verified |
| Samsung launch, runtime and acceleration | No device install or benchmark of this branch. The tested model path uses ONNX Runtime CPU. Vulkan compute and QNN have no acceptance evidence. | Unverified |
| Koikatsu and NoWorld production acceptance | No source-accurate Koikatsu import or Unreal admission exercised by this work. | Open |

The supplied illustration and its reconstruction are private test inputs and
outputs. They are excluded from Git. The volumetric result stays in the
preview-only state, so it cannot be exported as a production package. Without
the original 3D asset or additional views, the illustration cannot verify
cropped or occluded geometry. Its visible identity details also failed this run.

## Model and verification inputs

The local model archive keeps the existing checksum-pinned TripoSR and ISNet
graphs and adds `vision_encoder.onnx` and `prompt_encoder_mask_decoder.onnx`
from the [pinned SlimSAM 77 conversion](https://huggingface.co/Xenova/slimsam-77-uniform/tree/5850ab45f587c112167512ffef949107115e26a0).
Build it with `python3 scripts/create_tripo_pack.py MODEL_DIRECTORY local-models.zip`.
The host check built the archive and ran the same importer used by Android,
verifying and installing all six members into a clean workspace.
See [notices](../third-party/TRIPOSR-NOTICES) and the prior
[volumetric evaluation](VOLUMETRIC_EVALUATION.md) for the other pinned files.

Run `bash scripts/test-core.sh` for the geometry and GLB checks. For the
registry fixture, compile `FilesUtil`, `OriginalGlb`, `ReferenceRegistry` and
`tests/ReferenceRegistryAcceptance.java` with `org.json` on the host classpath.
Pass a local image and two distinct self-contained GLBs, such as the two rig
templates produced by the core suite. For native inference, use
`tests/VolumetricAcceptance.java` with ONNX Runtime 1.31.1, a local reference,
the six model files and a fresh checkpoint directory.
