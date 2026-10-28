import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "prepare_release_archive.py"
spec = importlib.util.spec_from_file_location("release_archive", SCRIPT)
archive = importlib.util.module_from_spec(spec)
spec.loader.exec_module(archive)


class ReleaseArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / "open-fire-unsigned.apk"
        with zipfile.ZipFile(self.apk, "w") as apk:
            apk.writestr("AndroidManifest.xml", b"manifest" * 100, compress_type=zipfile.ZIP_DEFLATED)
            apk.writestr("resources.arsc", os.urandom(1024), compress_type=zipfile.ZIP_STORED)
            apk.writestr("assets/sounds/example.mp3", os.urandom(10000), compress_type=zipfile.ZIP_STORED)
        self.output = self.root / "release.zip"

    def build(self):
        archive.build_release_zip([self.apk], self.output, None)

    def test_archive_stores_exact_apk_and_extracts_with_unzip(self):
        self.build()
        with zipfile.ZipFile(self.output) as zf:
            self.assertEqual(zf.namelist(), [self.apk.name])
            self.assertEqual(zf.infolist()[0].compress_type, zipfile.ZIP_STORED)
            self.assertEqual(zf.read(self.apk.name), self.apk.read_bytes())
        subprocess.run(["unzip", "-tq", str(self.output)], check=True)
        archive.verify_release_zip(self.output, [self.apk])

    def test_corruption_is_rejected(self):
        self.build()
        entries = archive.parse_and_validate_zip_headers(self.output)
        data = bytearray(self.output.read_bytes())
        data[int(entries[0]["data_start"]) + 50] ^= 1
        self.output.write_bytes(data)
        with self.assertRaises(archive.ZipValidationError):
            archive.verify_release_zip(self.output, [self.apk])

    def test_truncated_archive_is_rejected(self):
        self.build()
        self.output.write_bytes(self.output.read_bytes()[:-10])
        with self.assertRaises(archive.ZipValidationError):
            archive.verify_release_zip(self.output, [self.apk])


if __name__ == "__main__":
    unittest.main()
