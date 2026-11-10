# On-device pose evidence

Foundry runs Google MoveNet SinglePose Lightning float16 v4 through its ARM64 LiteRT runtime. The APK bundles the original 4,758,512-byte model and its Apache 2.0 license. The SHA-256 is `0fac2226112d0371903ca86e3853cec24ef603a0b2f96f589b180f0ebdd135ab`. The files served by the TensorFlow Hub and Kaggle release links were byte-identical. [LiteRT release](https://www.kaggle.com/models/google/movenet/tfLite/singlepose-lightning-tflite-float16/1) · [TensorFlow tutorial](https://www.tensorflow.org/hub/tutorials/movenet)

| Boundary | Contract |
| --- | --- |
| Input tensor | `UINT8 [1,192,192,3]`, RGB channel order, values from 0 to 255 |
| Input raster | Bilinear aspect-preserving resize, centered in a black square with transparency composited over black |
| Output tensor | `FLOAT32 [1,1,17,3]`, in model joint order, with normalized `y, x, confidence` |
| Source coordinates | Normalized `x, y` after reversing the letterbox, with origin at the top left and values outside the raster retained |
| Joint support | Confidence at least 0.30 and coordinates inside the raster |
| Torso support | At least one supported shoulder and one supported hip |

MoveNet estimates a single person's 2D joints. A supported point is still a model prediction, including when the joint is occluded. A weak or out-of-frame point cannot establish the position of a cropped limb. The model supplies no depth, surface, clothing, object, or identity evidence. Its documented evaluation focuses on single-person photos and motion imagery. [Google model card](https://storage.googleapis.com/movenet/MoveNet.SinglePose%20Model%20Card.pdf)
