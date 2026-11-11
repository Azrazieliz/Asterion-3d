"""Prepare the exact, offline Android model and runtime on a build machine."""
import argparse
import os
import time
import hashlib
from pathlib import Path
import shutil
import tempfile
import urllib.request
import zipfile

from create_tripo_pack import FILES as RECONSTRUCTION_FILES

EMBEDDED_PACK = "foundry-3d.foundrymodels"

# Build-machine-only sources. Every downloaded byte is checked against
# scripts/create_tripo_pack.py before it can enter a signed APK.
# No model network feature is shipped in the Android application.
REMOTE_PINS = {
    "triplane_q8.onnx": "https://huggingface.co/cgb/triposr-onnx-webgpu/resolve/048725e957f485cc6bc082a9d6a239fcf7d8880a/triplane_q8.onnx",
    "triplane_q8.onnx.data": "https://huggingface.co/cgb/triposr-onnx-webgpu/resolve/048725e957f485cc6bc082a9d6a239fcf7d8880a/triplane_q8.onnx.data",
    "decoder.onnx": "https://huggingface.co/cgb/triposr-onnx-webgpu/resolve/048725e957f485cc6bc082a9d6a239fcf7d8880a/decoder.onnx",
    "isnetis.onnx": "https://huggingface.co/skytnt/anime-seg/resolve/a0a563c41338cbe0d23dfb4bfc3e243c518e5768/isnetis.onnx",
    "vision_encoder.onnx": "https://huggingface.co/Xenova/slimsam-77-uniform/resolve/main/onnx/vision_encoder.onnx",
    "prompt_encoder_mask_decoder.onnx": "https://huggingface.co/Xenova/slimsam-77-uniform/resolve/main/onnx/prompt_encoder_mask_decoder.onnx",
}

def obtain_reconstruction_models(cache):
    """Fetch at build time only; resume by retaining verified immutable cache files."""
    cache.mkdir(parents=True, exist_ok=True)
    for name, (size, sha256) in RECONSTRUCTION_FILES.items():
        source = cache / name
        if source.is_file() and source.stat().st_size == size and digest(source) == sha256:
            continue
        if name not in REMOTE_PINS:
            raise ValueError(f"Missing licensed source pin for {name}")
        temporary = cache / (name + ".incoming")
        for attempt in range(3):
            temporary.unlink(missing_ok=True)
            try:
                with urllib.request.urlopen(
                        urllib.request.Request(REMOTE_PINS[name],
                            headers={"User-Agent": "AsterionFoundry-Build/0.5.2"}),
                        timeout=90) as response, temporary.open("wb") as output:
                    size_seen = 0
                    hasher = hashlib.sha256()
                    while chunk := response.read(1024 * 1024):
                        size_seen += len(chunk)
                        if size_seen > size:
                            raise ValueError(f"Oversized reconstruction weight from source: {name}")
                        hasher.update(chunk)
                        output.write(chunk)
                if size_seen != size or hasher.hexdigest() != sha256:
                    raise ValueError(f"Source size or SHA-256 mismatch for {name}")
                temporary.replace(source)
                break
            except Exception:
                temporary.unlink(missing_ok=True)
                if attempt == 2:
                    raise
                time.sleep(attempt + 1)
    return cache

MODEL = "midas_small_256_fp16.tflite"
POSE_MODEL = "movenet_lightning_v4_fp16.tflite"
ORT = "onnxruntime-android-1.31.1"
FILES = {
    "ONNXRUNTIME-THIRD-PARTY": (
        "https://raw.githubusercontent.com/microsoft/onnxruntime/v1.31.1/ThirdPartyNotices.txt",
        "143764b952fdb1a7c69ce653bfba74a7744d6a8a573bfb73e235fba356c83de3",
        1024 * 1024,
    ),
    ORT + ".aar": (
        "https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/"
        "1.31.1/onnxruntime-android-1.31.1.aar",
        "d4d50660300ed2c4f38ffdfd5ab5bfa3a22f995f282dda24f42ea3ea55467b49",
        64 * 1024 * 1024,
    ),
    MODEL: (
        "https://huggingface.co/litert-community/MiDaS-small/resolve/"
        "b6f24f361d9e1bce8f33e970aebcb9e5e2d1a12c/" + MODEL,
        "bec9bce704789e504ec306196fcb0aabe90fd25c2b9d7db382339741950890ca",
        40 * 1024 * 1024,
    ),
    POSE_MODEL: (
        "https://www.kaggle.com/api/v1/models/google/movenet/tfLite/"
        "singlepose-lightning-tflite-float16/1/download/4.tflite",
        "0fac2226112d0371903ca86e3853cec24ef603a0b2f96f589b180f0ebdd135ab",
        6 * 1024 * 1024,
    ),
    "litert-1.4.2.aar": (
        "https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert/1.4.2/litert-1.4.2.aar",
        "9b02cdf9db0a698b341b59e75c54ab8221d6304577cc43830d01024caf1fca74",
        24 * 1024 * 1024,
    ),
    "litert-api-1.4.2.aar": (
        "https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert-api/1.4.2/litert-api-1.4.2.aar",
        "66c2ba8086bd5cbf89bec2bd81c3de6c1a852312829cd55bc30c9dd26ffa357c",
        2 * 1024 * 1024,
    ),
}


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def obtain(cache, name):
    url, expected, limit = FILES[name]
    target = cache / name
    if target.is_file() and digest(target) == expected:
        return target
    with tempfile.NamedTemporaryFile(dir=cache, delete=False) as stream:
        temporary = Path(stream.name)
    try:
        with urllib.request.urlopen(url, timeout=60) as response, temporary.open("wb") as out:
            size = 0
            while data := response.read(1024 * 1024):
                size += len(data)
                if size > limit:
                    raise ValueError("Download exceeded the declared build size limit")
                out.write(data)
        if digest(temporary) != expected:
            raise ValueError(f"Checksum mismatch for {name}")
        temporary.replace(target)
        return target
    finally:
        temporary.unlink(missing_ok=True)


def verify_reconstruction_archive(path):
    """Require the exact six approved models before including them in an APK."""
    with zipfile.ZipFile(path) as archive:
        members = archive.namelist()
        if len(members) != len(RECONSTRUCTION_FILES) or set(members) != set(RECONSTRUCTION_FILES):
            raise ValueError("Reconstruction archive has missing or unexpected model members")
        for name, (expected_size, expected_sha) in RECONSTRUCTION_FILES.items():
            item = archive.getinfo(name)
            if item.file_size != expected_size or item.compress_type != zipfile.ZIP_STORED:
                raise ValueError(f"Unexpected pinned model size or compression: {name}")
            checksum = hashlib.sha256()
            with archive.open(item) as source:
                while chunk := source.read(1024 * 1024):
                    checksum.update(chunk)
            if checksum.hexdigest() != expected_sha:
                raise ValueError(f"Pinned model checksum mismatch: {name}")


def embed_reconstruction(stage, model_dir, model_archive, required):
    destination = stage / "assets" / EMBEDDED_PACK
    if model_archive:
        source = Path(model_archive).expanduser().resolve()
        if not source.is_file():
            raise FileNotFoundError(source)
        verify_reconstruction_archive(source)
        if source != destination:
            shutil.copyfile(source, destination)
        return True
    if model_dir:
        directory = Path(model_dir).expanduser().resolve()
        for name, (size, sha256) in RECONSTRUCTION_FILES.items():
            source = directory / name
            if not source.is_file() or source.stat().st_size != size or digest(source) != sha256:
                raise ValueError(f"Required pinned model missing or mismatched: {name}")
        temporary = destination.with_suffix(".tmp")
        try:
            with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_STORED, allowZip64=False) as archive:
                for name in RECONSTRUCTION_FILES:
                    archive.write(directory / name, name)
            temporary.replace(destination)
        finally:
            temporary.unlink(missing_ok=True)
        verify_reconstruction_archive(destination)
        return True
    if required:
        raise FileNotFoundError("Model bundle required: set FOUNDRY_MODEL_ARCHIVE or FOUNDRY_MODELS_DIR on the Windows build worker")
    destination.unlink(missing_ok=True)
    return False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage", type=Path, required=True)
    parser.add_argument("--model-dir", type=Path, default=os.environ.get("FOUNDRY_MODELS_DIR"))
    parser.add_argument("--model-archive", type=Path, default=os.environ.get("FOUNDRY_MODEL_ARCHIVE"))
    parser.add_argument("--require-embedded-models", action="store_true")
    parser.add_argument("--fetch-embedded-models", action="store_true",
                        help="Build-machine only: fetch exact pinned weights when no local source is configured")
    args = parser.parse_args()
    stage = args.stage.resolve()
    cache = stage.parent / "downloads"
    cache.mkdir(parents=True, exist_ok=True)
    for folder in ("assets/licenses", "jni/arm64-v8a", "jars"):
        (stage / folder).mkdir(parents=True, exist_ok=True)
    model_dir = args.model_dir
    if args.require_embedded_models and args.fetch_embedded_models             and not model_dir and not args.model_archive:
        model_dir = obtain_reconstruction_models(cache / "reconstruction-models")
    embedded = embed_reconstruction(stage, model_dir, args.model_archive, args.require_embedded_models)
    for name in (MODEL, POSE_MODEL):
        model = obtain(cache, name)
        with model.open("rb") as stream:
            if stream.read(8)[4:8] != b"TFL3":
                raise ValueError(f"Invalid LiteRT model identifier for {name}")
        shutil.copyfile(model, stage / "assets" / name)
    shutil.copyfile(Path(__file__).resolve().parents[1] / "third-party" / "MIDAS-LICENSE",
                    stage / "assets/licenses/MiDaS.txt")
    for artifact in ("litert-1.4.2", "litert-api-1.4.2"):
        with zipfile.ZipFile(obtain(cache, artifact + ".aar")) as archive:
            (stage / "jars" / (artifact + ".jar")).write_bytes(archive.read("classes.jar"))
            (stage / "assets/licenses" / (artifact + ".txt")).write_bytes(archive.read("LICENSE"))
            if artifact.startswith("litert-1."):
                library = "arm64-v8a/libtensorflowlite_jni.so"
                (stage / "jni" / library).write_bytes(archive.read("jni/" + library))
    with zipfile.ZipFile(obtain(cache, ORT + ".aar")) as archive:
        (stage / "jars" / (ORT + ".jar")).write_bytes(archive.read("classes.jar"))
        for name in ("libonnxruntime.so", "libonnxruntime4j_jni.so"):
            path = "arm64-v8a/" + name
            (stage / "jni" / path).write_bytes(archive.read("jni/" + path))
    shutil.copyfile(obtain(cache, "ONNXRUNTIME-THIRD-PARTY"),
                    stage / "assets/licenses/ONNXRUNTIME-THIRD-PARTY")
    for name in ("ONNXRUNTIME-LICENSE", "TRIPOSR-LICENSE", "TRIPOSR-NOTICES",
                 "MOVENET-LICENSE", "MOVENET-NOTICES"):
        shutil.copyfile(Path(__file__).resolve().parents[1] / "third-party" / name,
                        stage / "assets/licenses" / name)
    print(f"Prepared {MODEL}, {POSE_MODEL}, LiteRT and ONNX Runtime ARM64 with pinned SHA-256 hashes")
    print("Verified full 3D model archive embedded" if embedded else "Source compilation only; APK packaging requires all models")


if __name__ == "__main__":
    main()
