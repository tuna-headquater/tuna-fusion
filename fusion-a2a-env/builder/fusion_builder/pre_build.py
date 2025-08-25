import asyncio
import hashlib
import json
import logging
import os
import tempfile
import zipfile
from pathlib import Path

import httpx

from fusion_builder.models import SourceArchive, HttpZipSource, FilesystemFolderSource, FilesystemZipSource

logger = logging.getLogger(__name__)

def unzip(zip_file, extract_to: str):
    with zipfile.ZipFile(zip_file, 'r') as zip_ref:
        # 获取所有成员的最短公共前缀（第一层目录）
        members = zip_ref.namelist()
        if not members:
            return  # 空zip文件

        # 找出最长的公共前缀（第一层目录）
        prefix = os.path.commonprefix(members)
        if not prefix.endswith('/'):
            # 如果不是以/结尾，说明可能不是目录，回退到第一个成员的目录部分
            prefix = os.path.dirname(members[0])
            if prefix:
                prefix += '/'

        # 遍历zip文件中的所有条目
        for file_info in zip_ref.infolist():
            # 移除公共前缀（第一层目录）
            if file_info.filename.startswith(prefix):
                relative_path = file_info.filename[len(prefix):]
            else:
                relative_path = file_info.filename

            if not relative_path:
                continue  # 跳过原始目录本身

            # 构建目标路径
            target_path = os.path.join(extract_to, relative_path)

            # 确保目标目录存在
            if file_info.filename.endswith('/'):
                os.makedirs(target_path, exist_ok=True)
            else:
                os.makedirs(os.path.dirname(target_path), exist_ok=True)
                # 提取文件内容
                with zip_ref.open(file_info) as source, open(target_path, 'wb') as target:
                    target.write(source.read())


async def configure_build_with_http_zip_source(http_zip_source: HttpZipSource):
    src_path = os.getenv("SOURCE_ARCHIVE_PATH")
    assert src_path, "should have SOURCE_ARCHIVE_PATH in env"
    logger.info("Configure with http zip source: %s", http_zip_source)
    async with httpx.AsyncClient() as client:
        with tempfile.NamedTemporaryFile() as file:
            # download zip file
            logger.info(f"Downloading source archive {http_zip_source.url} to {file.name}")
            resp = await client.get(http_zip_source.url, follow_redirects=True)
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
            # unzip(file, src_path)

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


if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG)
    source_archive_json_path = os.getenv("SOURCE_ARCHIVE_JSON_PATH")
    assert source_archive_json_path and os.path.exists(source_archive_json_path), "SOURCE_ARCHIVE_JSON_PATH is not set"
    with open(source_archive_json_path) as f:
        source_archive = SourceArchive.model_validate(json.load(f))
        asyncio.run(configure_build(source_archive))

