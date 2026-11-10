# Reference-view color correction

The offline Image to 3D candidate route saves the isolated source frame before
encoding. It compares the extracted surface against that frame at 256 × 256
using TripoSR's nominal front camera, with 40° field of view and a distance of
1.9 model units. The audit uses depth-tested triangles and reports silhouette
intersection over union, precision and recall. Source border contact flags
possible cropping. The source segmentation and camera determine what the
comparison can measure. [TripoSR camera implementation](https://github.com/VAST-AI-Research/TripoSR/blob/main/tsr/utils.py) · [TripoSR render defaults](https://github.com/VAST-AI-Research/TripoSR/blob/main/tsr/system.py)

Color transfer requires IoU of at least 0.75, precision of at least 0.88 and
recall of at least 0.80. The Java path uses 75% source color and 25% decoded
color on vertices supported by the front depth buffer and an opaque interior
source sample. It keeps the original decoder colors if the measured error
increases or stays the same. Error is per-channel mean absolute error in sRGB
over pixels covered by both the model and the source mask, using interpolated
linear base color without scene lighting. These thresholds govern a color
operation. Production acceptance still requires geometry, identity, rig and
deformation review.

## Host evidence

All six pinned graphs matched their expected SHA-256 values. ONNX Runtime
1.31.1 CPU ran on the host in a process with socket creation denied by the
kernel. The real job stopped after encoding, then reused the triplanes and
source frame. The resumed phase produced 60,380 triangles in 2.146 seconds.
The image, renders, model files and checkpoints are private local inputs and
outputs.

| Check | Observed result |
|---|---|
| Supplied image, nominal front silhouette | IoU 0.8922, precision 0.9777, recall 0.9107. Source foreground touches an image border. |
| Supplied image, visible color | 6,090 surface vertices recolored. Base-color sRGB MAE fell from 0.1868 to 0.0913, a 51.1% reduction on overlapping pixels. |
| Geometry preservation | Direct comparison of the original and recolored GLB buffers found identical positions, normals, UVs and vertex alpha for all 181,140 expanded vertices. |
| Character fidelity | The front colors are closer to the reference. Front, side and rear renders still show a distorted face, fused hair and body geometry, and missing or fused tail detail. The candidate remains unrigged and fails character acceptance. |
| Aligned synthetic fixture | IoU 1.0000. MAE fell from 0.3605 to 0.1295, with 225 visible vertices recolored and all hidden-layer colors preserved. |
| Shifted or disjoint synthetic fixtures | IoU 0.3001 and 0.0000 respectively. Both kept the original colors. |

Run `bash scripts/test-core.sh` for the synthetic fixture. To reproduce the
model test, use `tests/VolumetricAcceptance.java` with the pinned model
directory, the private source and a fresh workspace in a network-denied
process. [Model sources and host fixture](REFERENCE_FIDELITY.md)

## Other model routes reviewed

| Model | Published inference requirements | Android status |
|---|---|---|
| Stable Fast 3D | Upstream Python inference reports about 6 GB GPU memory for one image and documents CPU and experimental Mac support. | No verified ARM64 Android package. [Upstream repository](https://github.com/Stability-AI/stable-fast-3d) |
| Stable Point Aware 3D | Gated weights and upstream low-memory mode of roughly 7 GB GPU memory. | No verified ARM64 Android package. [Upstream repository](https://github.com/Stability-AI/stable-point-aware-3d) |
| Hunyuan3D 2 / 2.1 | The 2.0 project reports 6 GB GPU memory for shape and 16 GB for shape with texture. The 2.1 project reports 10 GB and 29 GB respectively. | No verified offline Android build for shape with texture. [2.0 repository](https://github.com/Tencent-Hunyuan/Hunyuan3D-2) · [2.1 repository](https://github.com/Tencent-Hunyuan/Hunyuan3D-2.1) |
| Microsoft TRELLIS | Upstream prerequisites specify Linux and a CUDA-capable NVIDIA GPU with at least 16 GB of memory. | No verified ARM64 Android package. [Upstream repository](https://github.com/microsoft/TRELLIS) |

The active path keeps the tested TripoSR and SlimSAM graphs. None of the
alternative routes was run on the supplied reference. The projection result
has host evidence. Samsung runtime and Vulkan or QNN acceleration still need
device measurements.
