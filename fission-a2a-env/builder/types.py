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
    httpZipSource: HttpZipSource
    filesystemZipSource: FilesystemZipSource
    filesystemFolderSource: FilesystemFolderSource


class DeployArchive(BaseModel):
    filesystemFolderSource: FilesystemFolderSource