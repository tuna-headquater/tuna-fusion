from time import sleep

import pytest
from kubernetes.dynamic import DynamicClient, ResourceList
import uuid
from kubernetes import client, config, utils
import os
import yaml
from pathlib import Path


PROJECT_ROOT = Path(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))


@pytest.fixture(scope="module")
def test_namespace():
    """
    Create a namespace with uuid as its name, using k8s client
    :return: generated namespace name
    """
    # Load kubeconfig
    config.load_kube_config()

    v1 = client.CoreV1Api()
    namespace_name = str(uuid.uuid4())

    namespace = client.V1Namespace(
        metadata=client.V1ObjectMeta(name=namespace_name)
    )

    v1.create_namespace(namespace)
    yield namespace_name

    # Clean up
    v1.delete_namespace(name=namespace_name, body=client.V1DeleteOptions())


def _create_and_get_crd(crd_file_path: str, namespace: str = "default") -> tuple[ResourceList, str]:
    """
    Internal method to create CRD and return its DynamicClient resource instance.
    :param crd_file_path: absolute path to the CRD YAML file
    :param namespace: namespace to deploy CRD in (defaults to "default")
    :return: DynamicClient resource instance for the CRD
    """
    # Create CRD using extension_api
    extension_api = client.ApiextensionsV1Api()

    # Read CRD file to extract apiVersion and kind
    with open(crd_file_path, 'r') as f:
        crd_content = yaml.safe_load(f)

        # Extract apiVersion and kind from CRD
        crd_spec = crd_content.get('spec', {})
        api_version = f"{crd_spec.get('group')}/{crd_spec.get('versions')[0].get('name')}"
        kind = crd_content.get('spec', {}).get("names", {}).get("kind")
        name = crd_content.get('metadata', {}).get("name")
        response = extension_api.create_custom_resource_definition(crd_content)
        assert response is not None, "Failed to create CustomResourceDefinition"
        # wait for k8s to register CRD
        sleep(2)

    # Create DynamicClient instance
    dyn_client = DynamicClient(client.api_client.ApiClient())

    # Get CRD object to return
    api_instance = dyn_client.resources.get(api_version=api_version, kind=kind)

    return api_instance, name


def _delete_crd(name: str):
    """
    Internal method to delete CRD by kind.
    :param name: name of the CRD
    """
    extension_api = client.ApiextensionsV1Api()
    extension_api.delete_custom_resource_definition(
        name=name,
        body=client.V1DeleteOptions(),  # 删除选项，可以指定删除策略
    )

@pytest.fixture(scope="module")
def agent_deployment_crd(test_namespace: str):
    """
    Apply AgentDeployment CRD in given test_namespace.
    CRD is located in `/charts/tuna-fusion/tuna-fusion-operator/crds/agent_deployment.yaml`
    :return: resource instance of the CRD created by DynamicClient
    """

    # Load kubeconfig
    config.load_kube_config()

    # Get the path of CRD file relative to current file (conftest.py)
    crd_file_path = PROJECT_ROOT / "charts/tuna-fusion/charts/tuna-fusion-operator/crds/agent_deployment.yaml"
    crd_file_path = os.path.abspath(crd_file_path)

    api_instance, kind = _create_and_get_crd(crd_file_path, namespace=test_namespace)
    yield api_instance
    _delete_crd(kind)


@pytest.fixture(scope="module")
def agent_build_crd(test_namespace: str):
    """
    Apply AgentBuild CRD in given test_namespace.
    CRD is located in `/charts/tuna-fusion/tuna-fusion-operator/crds/agent_build.yaml`
    :return: resource instance of the CRD created by DynamicClient
    """

    # Load kubeconfig
    config.load_kube_config()

    # Get the path of CRD file relative to current file (conftest.py)
    crd_file_path = PROJECT_ROOT / "charts/tuna-fusion/charts/tuna-fusion-operator/crds/agent_build.yaml"
    crd_file_path = os.path.abspath(crd_file_path)

    api_instance, kind = _create_and_get_crd(crd_file_path, namespace=test_namespace)
    yield api_instance
    _delete_crd(kind)
