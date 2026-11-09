"""Prepare the exact, offline Android model and runtime on a build machine."""
import argparse
import hashlib
from pathlib import Path
import shutil
import tempfile
import urllib.request
import zipfile

MODEL = "midas_small_256_fp16.tflite"
FILES = {
    MODEL: (
        "https://huggingface.co/litert-community/MiDaS-small/resolve/"
        "b6f24f361d9e1bce8f33e970aebcb9e5e2d1a12c/" + MODEL,
        "bec9bce704789e504ec306196fcb0aabe90fd25c2b9d7db382339741950890ca",
        40 * 1024 * 1024,
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage", type=Path, required=True)
    args = parser.parse_args()
    stage = args.stage.resolve()
    cache = stage.parent / "downloads"
    cache.mkdir(parents=True, exist_ok=True)
    for folder in ("assets/licenses", "jni/arm64-v8a", "jars"):
        (stage / folder).mkdir(parents=True, exist_ok=True)
    model = obtain(cache, MODEL)
    with model.open("rb") as stream:
        if stream.read(8)[4:8] != b"TFL3":
            raise ValueError("Invalid LiteRT model identifier")
    shutil.copyfile(model, stage / "assets" / MODEL)
    shutil.copyfile(Path(__file__).resolve().parents[1] / "third-party" / "MIDAS-LICENSE",
                    stage / "assets/licenses/MiDaS.txt")
    for artifact in ("litert-1.4.2", "litert-api-1.4.2"):
        with zipfile.ZipFile(obtain(cache, artifact + ".aar")) as archive:
            (stage / "jars" / (artifact + ".jar")).write_bytes(archive.read("classes.jar"))
            (stage / "assets/licenses" / (artifact + ".txt")).write_bytes(archive.read("LICENSE"))
            if artifact.startswith("litert-1."):
                library = "arm64-v8a/libtensorflowlite_jni.so"
                (stage / "jni" / library).write_bytes(archive.read("jni/" + library))
    print(f"Prepared {MODEL} and LiteRT ARM64 with pinned SHA-256 hashes")


if __name__ == "__main__":
    main()
