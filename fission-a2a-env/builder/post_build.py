import asyncio
import os
from pathlib import Path

from kubernetes import config, client

from builder.types import DeployArchive, FilesystemFolderSource


async def update_function_build(
        function_build_name: str,
        namespace: str,
        deploy_archive_path: str,
        function_build_crd_group="ai.tuna.fusion",
        function_build_crd_version="v1",
        function_build_kind="PodFunctionBuild",
        function_build_plural="podfunctionbuilds"
):
    assert function_build_name, "should have function_build_name"
    assert Path(deploy_archive_path).exists(), "deploy_archive_path should exist"
    api = client.CustomObjectsApi()

    source = FilesystemFolderSource(path=deploy_archive_path)
    deploy_archive = DeployArchive(filesystemFolderSource=source)

    body = {
        "status": {
            "deployArchive": deploy_archive.model_dump(mode="json")
        }
    }
    return api.patch_namespaced_custom_object_status(
        name=function_build_name,
        namespace=namespace,
        group=function_build_crd_group,
        kind=function_build_kind,
        version=function_build_crd_version,
        plural=function_build_plural,
        body=body
    )


if __name__ == "__main__":
    config.load_kube_config()
    asyncio.run(update_function_build(
        function_build_name=os.getenv("FUNCTION_BUILD_NAME"),
        namespace=os.getenv("NAMESPACE"),
        deploy_archive_path=os.getenv("DEPLOY_ARCHIVE_PATH")
    ))