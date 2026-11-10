# DWPose reference evaluation

**Decision.** Keep DWPose out of the Android app. On the supplied illustration,
the detector found zero people. The estimator produced some plausible visible
arm landmarks in one input variant, but also assigned confident joints to the
wrong anatomy and to cropped regions.

The author-published DWPose and YOLOX graphs ran on ONNX Runtime 1.31.1 CPU
with network sockets denied by the host kernel. The test used the upstream
YOLOX letterbox and person filter, the DWPose 288 × 384 affine crop and
normalization, and the upstream SimCC decoder. A detector miss triggered the
upstream full-image fallback. A second trial used the isolated subject and its
mask bounds from Foundry's saved SlimSAM frame. Both trials were repeated with
the image channels reversed to check input sensitivity. The table counts the
17 COCO body points using the upstream display threshold of 0.30. These are
raw SimCC response scores. [Pinned upstream ONNX implementation](https://github.com/IDEA-Research/DWPose/tree/3dca5db79d9f9ffdd378753ddf6ec66535aace88)

| Input | Points at 0.30 or above | Highest score | Visual check |
|---|---:|---:|---|
| Original, OpenCV BGR fallback | 1 | 0.302 | A predicted knee fell on the visible thigh. Neither shoulder nor hip reached 0.30. |
| SlimSAM cutout, BGR | 9 | 0.473 | Some head landmarks were closer. A predicted wrist landed on a horn and other arm landmarks fell on the torso. Neither hip reached 0.30. |
| Original, RGB fallback | 11 | 0.573 | Some arm landmarks aligned. Both predicted knees fell on the thigh and a predicted ankle fell on the cropped leg. A confidence-only torso gate would have passed this result. |
| SlimSAM cutout, RGB | 3 | 0.395 | Neither shoulder nor hip reached 0.30. |

The author's repository marks the ONNX weights Apache-2.0. Both file sizes
and SHA-256 values matched the repository's LFS metadata at revision
`1a7144101628d69ee7a3768d1ee3a094070dc388`.
[Pinned model repository](https://huggingface.co/yzd-v/DWPose/tree/1a7144101628d69ee7a3768d1ee3a094070dc388)

| File | Bytes | SHA-256 |
|---|---:|---|
| `dw-ll_ucoco_384.onnx` | 134,399,116 | `724f4ff2439ed61afb86fb8a1951ec39c6220682803b4a8bd4f598cd913b1843` |
| `yolox_l.onnx` | 216,746,733 | `7860ae79de6c89a3c1eb72ae9a2756c0ccfbe04b7791bb5880afabd97855a411` |

The weights, reference, landmark overlays and run data stayed local. This
evaluation covers one difficult illustration and host CPU inference. It
provides no phone performance or general pose-accuracy measurement.
