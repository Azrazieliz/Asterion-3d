"""Add direct-build Android code and native libs, or verify the bundled contents."""
import hashlib
import shutil
import tempfile
from pathlib import Path
import sys
import zipfile
from prepare_native import FILES, MODEL, POSE_MODEL, EMBEDDED_PACK, RECONSTRUCTION_FILES, verify_reconstruction_archive

if sys.argv[1] == "--verify":
    with zipfile.ZipFile(sys.argv[2]) as apk:
        assert apk.testzip() is None, "APK ZIP CRC failed"
        model_archive = apk.getinfo("assets/" + EMBEDDED_PACK)
        expected_weight_bytes = sum(meta[0] for meta in RECONSTRUCTION_FILES.values())
        assert model_archive.compress_type == zipfile.ZIP_STORED, "Bundled reconstruction model ZIP must remain uncompressed"
        assert expected_weight_bytes <= model_archive.file_size <= expected_weight_bytes + 16384, "Bundled reconstruction weight pack is missing or truncated"
        # Validate the *final signed APK contents*, not only the build-machine
        # input files. Spool to disk, never materialize ~700 MiB in RAM.
        with tempfile.TemporaryFile() as scratch:
            with apk.open(model_archive) as embedded:
                shutil.copyfileobj(embedded, scratch, length=1024 * 1024)
            scratch.seek(0)
            verify_reconstruction_archive(scratch)
        for name in (MODEL, POSE_MODEL):
            model = apk.getinfo("assets/" + name)
            assert model.compress_type == zipfile.ZIP_STORED, "Model needs a mappable APK entry"
            assert hashlib.sha256(apk.read(model)).hexdigest() == FILES[name][1], name
        for name in ("MOVENET-LICENSE", "MOVENET-NOTICES"):
            assert apk.read("assets/licenses/" + name), name
        lib = apk.getinfo("lib/arm64-v8a/libtensorflowlite_jni.so")
        assert lib.compress_type == zipfile.ZIP_STORED, "JNI entry must support direct loading"
        assert apk.read(lib)[:4] == b"\x7fELF", "ARM64 library is not ELF"
        for name in ("libonnxruntime.so", "libonnxruntime4j_jni.so"):
            library = apk.getinfo("lib/arm64-v8a/" + name)
            assert library.compress_type == zipfile.ZIP_STORED and apk.read(library)[:4] == b"\x7fELF"
        assert "classes.dex" in apk.namelist()
    print("Verified all reconstruction weights are embedded, plus MiDaS, MoveNet, notices, ARM64 JNI and APK integrity")
else:
    apk, dex, jni = map(Path, sys.argv[1:])
    with zipfile.ZipFile(apk, "a") as archive:
        for classes in sorted(dex.glob("classes*.dex")):
            archive.write(classes, classes.name, compress_type=zipfile.ZIP_DEFLATED)
        for library in jni.rglob("*.so"):
            archive.write(library, "lib/" + library.relative_to(jni).as_posix(),
                          compress_type=zipfile.ZIP_STORED)
