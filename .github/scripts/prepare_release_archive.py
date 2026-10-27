#!/usr/bin/env python3
"""Normalize unsigned release APKs and package a standard seekable ZIP archive.

Why this script exists:
`actions/upload-artifact` (when `archive: true`, the default) wraps uploaded files
on the fly using Node `archiver` (`compress-commons` streaming mode). Streaming mode
sets General Purpose Bit 3 (`0x0008`, Extended Local Header / trailing Data Descriptor),
writes `crc32 = 0`, `compressed_size = 0`, and `uncompressed_size = 0` into the Local
File Header (`PK\\x03\\x04`), emits ZIP64 `version_made_by = 4.5` (`0x032d`), and wraps
incompressible APK/MP3 bytes in raw DEFLATE Type-0 (`STORED`) blocks that contain literal
inner APK `PK\\x03\\x04` and `PK\\x07\\x08` signatures. Stream/LFH-oriented extractors and
Android/Apktool ZIP readers fail on such archives with `W: invalid stored block lengths`
(from `zlib` `inflate.c`).

This script:
1. Verifies (and if necessary rewrites + `zipalign`s) each unsigned `.apk` so no entry
   inside the APK carries a streaming Data Descriptor (`flags & 0x0008`) or mismatched
   Local File Header vs Central Directory sizes/CRCs.
2. Packages the `.apk` files into a standard seekable ZIP 2.0 archive using `zip -j -X -6`
   (no Data Descriptors, no extra fields, exact CRC32 and sizes in every Local File Header).
3. Strictly validates raw Local File Headers, Central Directory records, raw DEFLATE streams
   (in small streaming chunks), and extracted APK SHA-256 digests so `actions/upload-artifact`
   can upload the verified `.zip` directly with `archive: false`.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile
import zlib
from pathlib import Path

LFH_SIGNATURE = 0x04034B50
CDH_SIGNATURE = 0x02014B50
EOCD_SIGNATURE = 0x06054B50
GPB_DATA_DESCRIPTOR_MASK = 0x0008
STREAM_CHUNK_SIZE = 4096


class ZipValidationError(RuntimeError):
    """Raised when a ZIP or APK fails structural or stream compatibility checks."""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(65536):
            digest.update(chunk)
    return digest.hexdigest()


def parse_and_validate_zip_headers(
    archive_path: Path,
    *,
    forbid_extra_fields: bool = False,
    check_contiguous: bool = True,
) -> list[dict[str, int | str]]:
    """Validate that every entry has complete LFH fields and no bit-3 Data Descriptor."""
    data = archive_path.read_bytes()
    size = len(data)
    if size < 22:
        raise ZipValidationError(f"{archive_path}: file too small to be a valid ZIP ({size} bytes)")

    # Locate End of Central Directory (EOCD) record (allow up to 64 KiB comment)
    search_start = max(0, size - (22 + 65535))
    eocd_pos = data.rfind(b"PK\x05\x06", search_start)
    if eocd_pos < 0:
        raise ZipValidationError(f"{archive_path}: missing End of Central Directory signature")

    (
        eocd_sig,
        disk_num,
        cd_start_disk,
        entries_on_disk,
        total_entries,
        cd_size,
        cd_offset,
        comment_len,
    ) = struct.unpack_from("<IHHHHIIH", data, eocd_pos)

    if eocd_sig != EOCD_SIGNATURE:
        raise ZipValidationError(f"{archive_path}: invalid EOCD signature {eocd_sig:#x}")
    if disk_num != 0 or cd_start_disk != 0 or entries_on_disk != total_entries:
        raise ZipValidationError(f"{archive_path}: multi-disk or ZIP64 EOCD not expected")
    if eocd_pos + 22 + comment_len != size:
        raise ZipValidationError(
            f"{archive_path}: trailing bytes after EOCD ({eocd_pos + 22 + comment_len} != {size})"
        )
    if cd_offset + cd_size != eocd_pos:
        raise ZipValidationError(
            f"{archive_path}: central directory range [{cd_offset}, {cd_offset + cd_size}) "
            f"does not end at EOCD ({eocd_pos}); ZIP64 locator headers are not allowed"
        )
    if total_entries == 0:
        raise ZipValidationError(f"{archive_path}: archive contains zero entries")

    entries: list[dict[str, int | str]] = []
    pos = cd_offset
    for idx in range(total_entries):
        if pos + 46 > eocd_pos:
            raise ZipValidationError(f"{archive_path}: truncated CDH at index {idx}")
        (
            cd_sig,
            ver_made,
            ver_need_cd,
            flags_cd,
            method_cd,
            mtime_cd,
            mdate_cd,
            crc_cd,
            csize_cd,
            usize_cd,
            fn_len_cd,
            ex_len_cd,
            cm_len_cd,
            _disk_start,
            _int_attr,
            _ext_attr,
            lfh_offset,
        ) = struct.unpack_from("<IHHHHHHIIIHHHHHII", data, pos)

        if cd_sig != CDH_SIGNATURE:
            raise ZipValidationError(
                f"{archive_path}: invalid CDH signature {cd_sig:#x} at entry {idx}"
            )
        name_bytes_cd = data[pos + 46 : pos + 46 + fn_len_cd]
        name = name_bytes_cd.decode("utf-8")
        pos += 46 + fn_len_cd + ex_len_cd + cm_len_cd

        if flags_cd & GPB_DATA_DESCRIPTOR_MASK:
            raise ZipValidationError(
                f"{archive_path}: entry {name!r} sets GPB bit 3 (Data Descriptor) in CDH "
                f"(flags=0x{flags_cd:04x})"
            )
        if forbid_extra_fields and ex_len_cd != 0:
            raise ZipValidationError(
                f"{archive_path}: entry {name!r} has unexpected CDH extra field ({ex_len_cd} bytes)"
            )
        if (ver_made & 0xFF) >= 45 or ver_need_cd >= 45:
            raise ZipValidationError(
                f"{archive_path}: entry {name!r} uses ZIP64 version headers "
                f"(ver_made=0x{ver_made:04x}, ver_need={ver_need_cd})"
            )

        if lfh_offset + 30 > cd_offset:
            raise ZipValidationError(
                f"{archive_path}: entry {name!r} LFH offset {lfh_offset} out of bounds"
            )
        (
            lfh_sig,
            ver_need_lfh,
            flags_lfh,
            method_lfh,
            _mtime_lfh,
            _mdate_lfh,
            crc_lfh,
            csize_lfh,
            usize_lfh,
            fn_len_lfh,
            ex_len_lfh,
        ) = struct.unpack_from("<IHHHHHIIIHH", data, lfh_offset)

        if lfh_sig != LFH_SIGNATURE:
            raise ZipValidationError(
                f"{archive_path}: entry {name!r} invalid LFH signature {lfh_sig:#x}"
            )
        if flags_lfh & GPB_DATA_DESCRIPTOR_MASK:
            raise ZipValidationError(
                f"{archive_path}: entry {name!r} sets GPB bit 3 (Data Descriptor) in LFH "
                f"(flags=0x{flags_lfh:04x})"
            )
        if forbid_extra_fields and ex_len_lfh != 0:
            raise ZipValidationError(
                f"{archive_path}: entry {name!r} has unexpected LFH extra field ({ex_len_lfh} bytes)"
            )
        if ver_need_lfh >= 45:
            raise ZipValidationError(
                f"{archive_path}: entry {name!r} requires ZIP64 (ver_need={ver_need_lfh})"
            )

        name_bytes_lfh = data[lfh_offset + 30 : lfh_offset + 30 + fn_len_lfh]
        if name_bytes_lfh != name_bytes_cd:
            raise ZipValidationError(
                f"{archive_path}: entry {name!r} LFH filename {name_bytes_lfh!r} != CDH filename"
            )
        if method_lfh != method_cd:
            raise ZipValidationError(
                f"{archive_path}: entry {name!r} LFH method {method_lfh} != CDH method {method_cd}"
            )
        if (crc_lfh, csize_lfh, usize_lfh) != (crc_cd, csize_cd, usize_cd):
            raise ZipValidationError(
                f"{archive_path}: entry {name!r} LFH (crc={crc_lfh:#x}, csize={csize_lfh}, "
                f"usize={usize_lfh}) != CDH (crc={crc_cd:#x}, csize={csize_cd}, usize={usize_cd})"
            )

        data_start = lfh_offset + 30 + fn_len_lfh + ex_len_lfh
        data_end = data_start + csize_cd
        if data_end > cd_offset:
            raise ZipValidationError(
                f"{archive_path}: entry {name!r} data range [{data_start}, {data_end}) "
                f"overflows central directory offset {cd_offset}"
            )

        # Stream-verify payload directly from the Local File Header offsets
        payload = memoryview(data)[data_start:data_end]
        if method_lfh == zipfile.ZIP_STORED:
            if csize_lfh != usize_lfh:
                raise ZipValidationError(
                    f"{archive_path}: STORED entry {name!r} csize {csize_lfh} != usize {usize_lfh}"
                )
            computed_crc = zlib.crc32(payload) & 0xFFFFFFFF
            actual_usize = len(payload)
        elif method_lfh == zipfile.ZIP_DEFLATED:
            inflater = zlib.decompressobj(-zlib.MAX_WBITS)
            computed_crc = 0
            actual_usize = 0
            try:
                for offset in range(0, len(payload), STREAM_CHUNK_SIZE):
                    out = inflater.decompress(payload[offset : offset + STREAM_CHUNK_SIZE])
                    if out:
                        computed_crc = zlib.crc32(out, computed_crc)
                        actual_usize += len(out)
                tail = inflater.flush()
                if tail:
                    computed_crc = zlib.crc32(tail, computed_crc)
                    actual_usize += len(tail)
            except zlib.error as exc:
                raise ZipValidationError(
                    f"{archive_path}: DEFLATE stream error in {name!r}: {exc}"
                ) from exc
            if not inflater.eof or inflater.unused_data:
                raise ZipValidationError(
                    f"{archive_path}: incomplete or padded DEFLATE stream in {name!r} "
                    f"(eof={inflater.eof}, unused={len(inflater.unused_data)})"
                )
            computed_crc &= 0xFFFFFFFF
        else:
            raise ZipValidationError(
                f"{archive_path}: unsupported compression method {method_lfh} on {name!r}"
            )

        if actual_usize != usize_lfh:
            raise ZipValidationError(
                f"{archive_path}: entry {name!r} inflated size {actual_usize} != {usize_lfh}"
            )
        if computed_crc != crc_lfh:
            raise ZipValidationError(
                f"{archive_path}: entry {name!r} CRC {computed_crc:#010x} != {crc_lfh:#010x}"
            )

        entries.append(
            {
                "name": name,
                "lfh_offset": lfh_offset,
                "data_start": data_start,
                "data_end": data_end,
                "method": method_lfh,
                "csize": csize_lfh,
                "usize": usize_lfh,
                "crc": crc_lfh,
            }
        )

    if check_contiguous:
        # Confirm entries are contiguous up to cd_offset (no hidden trailing Data Descriptors or gaps)
        entries_sorted = sorted(entries, key=lambda item: int(item["lfh_offset"]))
        if int(entries_sorted[0]["lfh_offset"]) != 0:
            raise ZipValidationError(
                f"{archive_path}: first LFH does not start at offset 0 ({entries_sorted[0]['lfh_offset']})"
            )
        for cur, nxt in zip(entries_sorted, entries_sorted[1:]):
            if int(cur["data_end"]) != int(nxt["lfh_offset"]):
                raise ZipValidationError(
                    f"{archive_path}: gap/Data Descriptor of "
                    f"{int(nxt['lfh_offset']) - int(cur['data_end'])} bytes after {cur['name']!r}"
                )
        if int(entries_sorted[-1]["data_end"]) != cd_offset:
            raise ZipValidationError(
                f"{archive_path}: gap/Data Descriptor of "
                f"{cd_offset - int(entries_sorted[-1]['data_end'])} bytes before Central Directory"
            )

    with zipfile.ZipFile(archive_path, "r") as zf:
        bad_member = zf.testzip()
        if bad_member is not None:
            raise ZipValidationError(f"{archive_path}: zipfile.testzip() failed on {bad_member!r}")

    return entries


def rewrite_apk_without_data_descriptors(apk_path: Path) -> None:
    """Rewrite an APK with explicit LFH sizes/CRCs and no GPB bit-3 Data Descriptors."""
    with tempfile.NamedTemporaryFile(
        prefix="normalized-",
        suffix=".apk",
        dir=str(apk_path.parent),
        delete=False,
    ) as tmp_handle:
        tmp_path = Path(tmp_handle.name)

    try:
        with zipfile.ZipFile(apk_path, "r") as src_zf, zipfile.ZipFile(tmp_path, "w") as dst_zf:
            for src_info in src_zf.infolist():
                payload = src_zf.read(src_info.filename)
                dst_info = zipfile.ZipInfo(
                    filename=src_info.filename,
                    date_time=src_info.date_time,
                )
                dst_info.compress_type = src_info.compress_type
                dst_info.create_system = src_info.create_system
                dst_info.external_attr = src_info.external_attr
                dst_info.internal_attr = src_info.internal_attr
                dst_info.flag_bits = src_info.flag_bits & ~GPB_DATA_DESCRIPTOR_MASK
                if dst_info.compress_type == zipfile.ZIP_DEFLATED:
                    dst_zf.writestr(dst_info, payload, compress_type=zipfile.ZIP_DEFLATED, compresslevel=6)
                else:
                    dst_zf.writestr(dst_info, payload, compress_type=zipfile.ZIP_STORED)
        os.replace(tmp_path, apk_path)
    finally:
        if tmp_path.exists():
            tmp_path.unlink()


def validate_apk_store_and_alignment(
    apk_path: Path,
    entries: list[dict[str, int | str]],
    *,
    require_alignment: bool,
) -> None:
    """Confirm resources.arsc and sound assets stay uncompressed (and 4-byte aligned when aligned)."""
    for entry in entries:
        name = str(entry["name"])
        method = int(entry["method"])
        data_start = int(entry["data_start"])
        if name == "resources.arsc" or (name.startswith("assets/sounds/") and name.endswith(".mp3")):
            if method != zipfile.ZIP_STORED:
                raise ZipValidationError(
                    f"{apk_path}: {name!r} must be ZIP_STORED (0), found method={method}"
                )
        if require_alignment and method == zipfile.ZIP_STORED and int(entry["usize"]) > 0:
            align = 4096 if name.endswith(".so") else 4
            if data_start % align != 0:
                raise ZipValidationError(
                    f"{apk_path}: STORED entry {name!r} at offset {data_start} is not {align}-byte aligned"
                )


def ensure_apk_aligned_and_normalized(apk_path: Path, zipalign_bin: str | None) -> None:
    """Ensure the unsigned APK has zero Data Descriptors and passes 4-byte zipalign."""
    needs_rewrite = False
    try:
        parse_and_validate_zip_headers(
            apk_path,
            forbid_extra_fields=False,
            check_contiguous=False,
        )
    except ZipValidationError as exc:
        print(f"Normalizing APK headers for {apk_path.name}: {exc}")
        needs_rewrite = True

    if needs_rewrite:
        rewrite_apk_without_data_descriptors(apk_path)

    if zipalign_bin:
        with tempfile.NamedTemporaryFile(
            prefix="aligned-",
            suffix=".apk",
            dir=str(apk_path.parent),
            delete=False,
        ) as aligned_handle:
            aligned_path = Path(aligned_handle.name)
        try:
            subprocess.run(
                [zipalign_bin, "-f", "-p", "4", str(apk_path), str(aligned_path)],
                check=True,
            )
            os.replace(aligned_path, apk_path)
        finally:
            if aligned_path.exists():
                aligned_path.unlink()
        subprocess.run([zipalign_bin, "-c", "4", str(apk_path)], check=True)

    entries = parse_and_validate_zip_headers(
        apk_path,
        forbid_extra_fields=False,
        check_contiguous=bool(zipalign_bin or needs_rewrite),
    )
    validate_apk_store_and_alignment(
        apk_path,
        entries,
        require_alignment=bool(zipalign_bin),
    )
    print(f"Verified APK {apk_path.name}: {len(entries)} entries, 0 Data Descriptors")


def build_release_zip(apk_paths: list[Path], output_zip: Path, zipalign_bin: str | None) -> None:
    if not apk_paths:
        raise ZipValidationError("No APK files provided to package")

    zip_bin = shutil.which("zip")
    if not zip_bin:
        raise ZipValidationError("'zip' CLI utility is required to build standard seekable archives")

    for apk in apk_paths:
        ensure_apk_aligned_and_normalized(apk, zipalign_bin)

    if output_zip.exists():
        output_zip.unlink()
    output_zip.parent.mkdir(parents=True, exist_ok=True)

    # -j: store flat basenames without directory prefixes
    # -X: strip extra OS file attributes so LFH and CDH extra_field_length == 0
    # -6: standard DEFLATE with seekable LFH rewrite (flags=0x0000, no Data Descriptors)
    cmd = [zip_bin, "-j", "-X", "-6", str(output_zip), *[str(p) for p in apk_paths]]
    subprocess.run(cmd, check=True)

    verify_release_zip(output_zip, expected_apks=apk_paths)


def verify_release_zip(zip_path: Path, expected_apks: list[Path]) -> None:
    if not zip_path.is_file():
        raise ZipValidationError(f"Release ZIP not found: {zip_path}")

    entries = parse_and_validate_zip_headers(zip_path, forbid_extra_fields=True)
    expected_by_name = {apk.name: sha256_file(apk) for apk in expected_apks}
    actual_names = {str(entry["name"]) for entry in entries}

    if expected_by_name and actual_names != set(expected_by_name.keys()):
        raise ZipValidationError(
            f"{zip_path}: archive members {sorted(actual_names)} != expected {sorted(expected_by_name)}"
        )

    with tempfile.TemporaryDirectory(prefix="verify-release-zip-") as tmp_dir:
        extract_dir = Path(tmp_dir)
        with zipfile.ZipFile(zip_path, "r") as zf:
            zf.extractall(extract_dir)

        for name in sorted(actual_names):
            extracted_apk = extract_dir / name
            if not extracted_apk.is_file():
                raise ZipValidationError(f"{zip_path}: extracted member missing: {name}")
            actual_sha = sha256_file(extracted_apk)
            if name in expected_by_name and actual_sha != expected_by_name[name]:
                raise ZipValidationError(
                    f"{zip_path}: SHA-256 mismatch for {name}: "
                    f"{actual_sha} != {expected_by_name[name]}"
                )
            inner_entries = parse_and_validate_zip_headers(extracted_apk, forbid_extra_fields=False)
            print(
                f"Verified extracted {name} ({extracted_apk.stat().st_size} bytes, "
                f"sha256={actual_sha[:16]}..., {len(inner_entries)} inner entries)"
            )

    print(
        f"Release ZIP {zip_path.name} passed all header, LFH/CDH, and streaming DEFLATE checks "
        f"({zip_path.stat().st_size} bytes)."
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    build_parser = subparsers.add_parser("build", help="Normalize APKs and build release ZIP")
    build_parser.add_argument("--output", type=Path, required=True, help="Output .zip file path")
    build_parser.add_argument("--zipalign", type=str, default=None, help="Optional zipalign binary")
    build_parser.add_argument("apks", nargs="+", type=Path, help="Input .apk files")

    verify_parser = subparsers.add_parser("verify", help="Verify release ZIP and inner APKs")
    verify_parser.add_argument("zip_path", type=Path, help="Path to .zip archive to verify")
    verify_parser.add_argument(
        "--expected-apk",
        dest="expected_apks",
        action="append",
        type=Path,
        default=[],
        help="Expected original .apk file (may be specified multiple times)",
    )

    args = parser.parse_args(argv)
    try:
        if args.command == "build":
            build_release_zip(args.apks, args.output, args.zipalign)
        elif args.command == "verify":
            verify_release_zip(args.zip_path, args.expected_apks)
    except (ZipValidationError, subprocess.CalledProcessError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
