"""Build the optional offline model pack from checksum-pinned files already on disk."""
import argparse
import hashlib
import os
import tempfile
from pathlib import Path
import zipfile

FILES = {
    "triplane_q8.onnx": (885712, "7721f5a45e79e02de8c6e909c21fb8e922fd0b594038a8fc1cfefa42c79ad6d5"),
    "triplane_q8.onnx.data": (483785728, "50bf9b1c1218dc2c7fb6051d0d2cb7a48a1c5f75cc86e67a9d0e09a931298e35"),
    "decoder.onnx": (173796, "6ff43581f1d2b46aa61764c2729a8d33985b51cb6c8088cf36ffee3fd70355eb"),
    "isnetis.onnx": (176069933, "f15622d853e8260172812b657053460e20806f04b9e05147d49af7bed31a6e99"),
    "vision_encoder.onnx": (23276014, "9f8433273a6750b587779baa0cf5508111001bf7e7acfcf585d370139fd366d0"),
    "prompt_encoder_mask_decoder.onnx": (16557892, "f4514391764fbd56e08e119060d874ecd7d52994bfb1968af159e12d4943b5bb"),
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("models", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    for name, (size, expected) in FILES.items():
        path = args.models / name
        with path.open("rb") as stream:
            if path.stat().st_size != size or hashlib.file_digest(stream, "sha256").hexdigest() != expected:
                raise ValueError(f"Checksum or size mismatch: {name}")
    with tempfile.NamedTemporaryFile(dir=args.output.parent, delete=False) as stream:
        temporary = Path(stream.name)
    try:
        with zipfile.ZipFile(temporary, "w", zipfile.ZIP_STORED) as archive:
            for name in FILES:
                archive.write(args.models / name, name)
        os.link(temporary, args.output)
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
