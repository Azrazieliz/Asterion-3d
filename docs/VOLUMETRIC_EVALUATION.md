# Volumetric reconstruction evaluation

**Release decision — failed character fidelity.** The source on this evaluation branch
must not replace the published build or count as completion of the
[retained scope](SCOPE.md).

The candidate engine uses a pinned TripoSR encoder and decoder with an ISNet Anime
foreground mask for opaque inputs. It saves triplanes and sampled density before
extracting an editable, vertex-coloured mesh. The Android build compiles against
the ARM64 ONNX Runtime API. The runtime disables telemetry and the manifest has
no Internet permission. The models are installed once from a local, checksum
verified archive. No original image or model weights are stored in Git.

## Acceptance evidence

| Check | Observed result |
|---|---|
| Separate the supplied character from the background | **Fail.** The foreground mask included part of the large mirror. |
| Preserve identity through rotation | **Fail.** Front, side and rear renders from the exported mesh showed distorted facial features and merged or lost hair, horns, scale details and tail geometry. |
| Produce a 3D surface and export | Host Java generated 70,044 triangles after the extraction correction. GLB layout passed. Exact position welding found zero boundary and overused edges. |
| Resume a real model job | Host Java deliberately stopped after encoding and resumed from the saved triplanes. This is a checkpoint exercise on the host. |
| Separate editable parts and validate deformation | **Fail.** The candidate is a single vertex-coloured, unrigged mesh. |
| Run and benchmark on the Samsung | Not performed. Android source and DEX compilation do not establish device execution. |
| Accept into NoWorld | Not performed. Candidate projects cannot use production package export. |

The model was rerun with socket creation and network system calls blocked by
the host kernel after an earlier benchmark check was blocked over a runtime
telemetry request. The isolated model run used the supplied illustration locally.
Its images, geometry and intermediate data remain outside the repository.

There is no validated source model or additional authenticated view for the hidden
areas of this illustration. An inferred back, cropped limbs or occluded parts
cannot be certified as exact from the illustration. Reusing a verified original
asset can preserve geometry and materials where one exists.

## Reproduce the engineering check

The [pinned conversion](https://huggingface.co/cgb/triposr-onnx-webgpu/tree/048725e957f485cc6bc082a9d6a239fcf7d8880a)
supplies `triplane_q8.onnx`, `triplane_q8.onnx.data` and `decoder.onnx`.
The [pinned ISNet repository](https://huggingface.co/skytnt/anime-seg/tree/493cb60893f47441b26ec4fb9a306bce9e342982)
supplies `isnetis.onnx`. Build the local archive with
`python3 scripts/create_tripo_pack.py MODEL_DIRECTORY local-models.zip`.
The archive builder and Android importer both validate exact sizes and SHA-256.

The Java host fixture in `tests/VolumetricAcceptance.java` takes a model directory,
a local image and a fresh workspace. Compile it with the shared mesh sources and
the ONNX Runtime 1.31.1 desktop JAR. Run the fixture in a network-denied process.
Passing this engineering check is insufficient for production acceptance.
