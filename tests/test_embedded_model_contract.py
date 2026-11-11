"""Small hermetic tests for the pinned model archive build policy (no large downloads)."""
import hashlib
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import prepare_native


class OfflineBundleContract(unittest.TestCase):
    def setUp(self):
        self.original = prepare_native.RECONSTRUCTION_FILES
        self.temp = tempfile.TemporaryDirectory(prefix="foundry-model-pack-")
        self.root = Path(self.temp.name)
        self.stage = self.root / "stage"
        (self.stage / "assets").mkdir(parents=True)
        self.models = self.root / "models"
        self.models.mkdir()
        first, second = b"onnx", b"external data"
        self.fixture = {"tiny.onnx": (len(first), hashlib.sha256(first).hexdigest()),
                        "tiny.onnx.data": (len(second), hashlib.sha256(second).hexdigest())}
        prepare_native.RECONSTRUCTION_FILES = self.fixture
        (self.models / "tiny.onnx").write_bytes(first)
        (self.models / "tiny.onnx.data").write_bytes(second)

    def tearDown(self):
        prepare_native.RECONSTRUCTION_FILES = self.original
        self.temp.cleanup()

    def test_requires_complete_embedded_models(self):
        with self.assertRaises(FileNotFoundError):
            prepare_native.embed_reconstruction(self.stage, None, None, True)
        self.assertFalse((self.stage / "assets" / prepare_native.EMBEDDED_PACK).exists())

    def test_direct_bundle_is_exact_and_stored(self):
        self.assertTrue(prepare_native.embed_reconstruction(self.stage, self.models, None, True))
        pack = self.stage / "assets" / prepare_native.EMBEDDED_PACK
        prepare_native.verify_reconstruction_archive(pack)
        with zipfile.ZipFile(pack) as archive:
            self.assertEqual(set(archive.namelist()), set(self.fixture))
            self.assertTrue(all(z.compress_type == zipfile.ZIP_STORED
                                for z in archive.infolist()))
        copied = self.root / "copied" / "assets"
        copied.mkdir(parents=True)
        self.assertTrue(prepare_native.embed_reconstruction(
            copied.parent, None, pack, True))
        prepare_native.verify_reconstruction_archive(copied / prepare_native.EMBEDDED_PACK)

    def test_tampered_weights_rejected(self):
        (self.models / "tiny.onnx").write_bytes(b"fail")
        with self.assertRaises(ValueError):
            prepare_native.embed_reconstruction(self.stage, self.models, None, True)

    def test_archive_not_silently_reused_for_source_only(self):
        prepare_native.embed_reconstruction(self.stage, self.models, None, True)
        self.assertFalse(prepare_native.embed_reconstruction(self.stage, None, None, False))
        self.assertFalse((self.stage / "assets" / prepare_native.EMBEDDED_PACK).exists())


if __name__ == "__main__":
    unittest.main()
