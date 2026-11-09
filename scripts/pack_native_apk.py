"""Add direct-build Android code and native libs, or verify the bundled contents."""
import hashlib
from pathlib import Path
import sys
import zipfile
from prepare_native import FILES, MODEL

if sys.argv[1] == "--verify":
    with zipfile.ZipFile(sys.argv[2]) as apk:
        assert apk.testzip() is None, "APK ZIP CRC failed"
        model = apk.getinfo("assets/" + MODEL)
        assert model.compress_type == zipfile.ZIP_STORED, "Model needs a mappable APK entry"
        assert hashlib.sha256(apk.read(model)).hexdigest() == FILES[MODEL][1]
        lib = apk.getinfo("lib/arm64-v8a/libtensorflowlite_jni.so")
        assert lib.compress_type == zipfile.ZIP_STORED, "JNI entry must support direct loading"
        assert apk.read(lib)[:4] == b"\x7fELF", "ARM64 library is not ELF"
        assert "classes.dex" in apk.namelist()
    print("Verified embedded model bytes, ARM64 JNI, and APK integrity")
else:
    apk, dex, jni = map(Path, sys.argv[1:])
    with zipfile.ZipFile(apk, "a") as archive:
        for classes in sorted(dex.glob("classes*.dex")):
            archive.write(classes, classes.name, compress_type=zipfile.ZIP_DEFLATED)
        for library in jni.rglob("*.so"):
            archive.write(library, "lib/" + library.relative_to(jni).as_posix(),
                          compress_type=zipfile.ZIP_STORED)
