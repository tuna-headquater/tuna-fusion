from typing import Optional

from pydantic import BaseModel

class HttpZipSource(BaseModel):
    url: str
    sha256Checksum: str


class FilesystemZipSource(BaseModel):
    path: str
    sha256Checksum: str


class FilesystemFolderSource(BaseModel):
    path: str


class SourceArchive(BaseModel):
    httpZipSource: Optional[HttpZipSource] = None
    filesystemZipSource: Optional[FilesystemZipSource] = None
    filesystemFolderSource: Optional[FilesystemFolderSource] = None


class DeployArchive(BaseModel):
    filesystemFolderSource: FilesystemFolderSource