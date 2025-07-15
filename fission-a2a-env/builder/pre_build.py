import asyncio
import hashlib
import json
import logging
import os
import tempfile
import zipfile
from pathlib import Path

import httpx

from builder.types import SourceArchive, HttpZipSource, FilesystemFolderSource, FilesystemZipSource

logger = logging.getLogger(__name__)

async def configure_build_with_http_zip_source(http_zip_source: HttpZipSource):
    src_path = os.getenv("SOURCE_ARCHIVE_PATH")
    assert src_path, "should have SOURCE_ARCHIVE_PATH in env"
    async with httpx.AsyncClient() as client:
        with tempfile.TemporaryFile() as file:
            # download zip file
            logger.info(f"Downloading source archive {http_zip_source.url}")
            resp = await client.get(http_zip_source.url)
            file.write(resp.content)

            # Calculate SHA256 checksum of the downloaded file
            checksum = http_zip_source.sha256Checksum
            file_hash = hashlib.sha256()
            file.seek(0)
            while chunk := file.read(8192):
                file_hash.update(chunk)
            actual_checksum = file_hash.hexdigest()
            if checksum and actual_checksum != checksum:
                raise RuntimeError(f"Checksum verification failed: expected {checksum}, got {actual_checksum}")
            logger.info("Successfully verified SHA256 checksum of the downloaded file")

            # Reset file pointer to beginning after reading
            file.seek(0)

            # extract file to source folder
            logger.info(f"Extracting source archive to {src_path}")
            with zipfile.ZipFile(file, 'r') as zip_ref:
                zip_ref.extractall(src_path)


async def configure_build_with_folder_source(src: FilesystemFolderSource):
    src_path = os.getenv("SOURCE_ARCHIVE_PATH")
    if src_path and Path(src_path) != Path(src.filesystemFolderSource.path):
        raise RuntimeError(
            f"Conflict source archive path: SOURCE_ARCHIVE_PATH={src_path}, source_archive.filesystemFolderSource.path={src.path}")


async def configure_build_with_filesystem_zip_source(src: FilesystemZipSource):
    src_path = os.getenv("SOURCE_ARCHIVE_PATH")
    assert src_path, "should have SOURCE_ARCHIVE_PATH in env"
    assert Path(src.path).exists(), f"zip file should exist: {src.path}"
    with zipfile.ZipFile(src.path) as zip_ref:
        zip_ref.extractall(src_path)


async def configure_build(src: SourceArchive):
    if src.filesystemFolderSource:
        await configure_build_with_folder_source(src.filesystemFolderSource)

    if src.httpZipSource:
        await configure_build_with_http_zip_source(src.httpZipSource)

    if src.filesystemZipSource:
        await configure_build_with_filesystem_zip_source(src.filesystemZipSource)


if __name__ == "main":
    with open(os.getenv("SOURCE_ARCHIVE_MANIFEST_PATH")) as f:
        source_archive = SourceArchive.model_validate_json(json.load(f))
        asyncio.run(configure_build(source_archive))

