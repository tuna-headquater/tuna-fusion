import logging
import os
import time

import kopf
from kubernetes import client, config
from kubernetes.client import ApiClient
from kubernetes.dynamic import DynamicClient
from pydantic.v1 import ValidationError

from tuna.fusion.kubernetes.types import AgentBuild, AgentBuildTarget
from tuna.fusion.kubernetes.types import AgentDeployment, OperatorConfiguration

logger = logging.getLogger(__name__)


def print_validation_error(e: ValidationError) -> str:
    error_messages = []
    for error in e.errors():
        field = ".".join(error["loc"])
        message = error["msg"]
        error_messages.append(f"Field '{field}': {message}")
    return "\n".join(error_messages)



def get_configuration(ns: str = os.getenv("TUNA_SYSTEM_NS", "tuna-system"), cm: str = "fusion-server-operator") -> OperatorConfiguration:
    core_v1_api = client.CoreV1Api()
    config_map = core_v1_api.read_namespaced_config_map(name=cm, namespace=ns)
    return OperatorConfiguration(**config_map.data)

def create_builder_job_object(
        configuration: OperatorConfiguration,
        agent_deployment: AgentDeployment,
        agent_build: AgentBuild) -> client.V1Job:
    """

    :param configuration:
    :param agent_deployment:
    :param agent_build:
    :return:
    """
    ns = configuration.stagingNamespace if agent_build.spec.buildTarget == AgentBuildTarget.Staging else configuration.productionNamespace

    # Configure Pod template container
    container = client.V1Container(
        name="tuna-builder",
        image=configuration.toolchainImage,
        resources=client.V1ResourceRequirements(
            limits={"memory": "512Mi", "cpu": "500m"},
            requests={"memory": "256Mi", "cpu": "250m"}),
        volume_mounts=[client.V1VolumeMount(mount_path="/build.sh", name="builder-script-volume", sub_path="build.sh")],
        env=[
            client.V1EnvVar(name="AGENT_BUILD_TARGET", value=agent_build.spec.buildTarget),
            client.V1EnvVar(name="GIT_COMMIT_ID", value=agent_build.spec.gitCommitId),
            client.V1EnvVar(name="FUNCTION_NAME", value=agent_deployment.spec.agentName),
            # client.V1EnvVar(name="FUNCTION_ENV", value=agent_build.spec.fissionEnv),
            # client.V1EnvVar(name="FUNCTION_ENTRYPOINT", value=agent_build.spec.fissionFunctionEntrypoint),
            client.V1EnvVar(name="STAGING_NAMESPACE", value=configuration.stagingNamespace),
            client.V1EnvVar(name="PRODUCTION_NAMESPACE", value=configuration.productionNamespace),
        ],
    )

    init_container = client.V1Container(
        name="init-build-script",
        image="tuna-builder:latest",
        command=["sh", "-c", f"echo $'{agent_build.spec.buildScript}' > /workspace/build.sh && chmod +x /workspace/build.sh"],
        volume_mounts=[client.V1VolumeMount(mount_path="/workspace", name="workspace")],
    )

    # Create and configure a spec section
    template = client.V1PodTemplateSpec(
        metadata=client.V1ObjectMeta(namespace=ns),
        spec=client.V1PodSpec(
            restart_policy="Never",
            containers=[container],
            init_containers=[init_container],
            volumes=[
                client.V1Volume(name="builder-script-volume", empty_dir=client.V1EmptyDirVolumeSource())
            ]
        )
    )

    # Create the specification of deployment
    spec = client.V1JobSpec(
        template=template,
        # retry 4 times
        backoff_limit=4,
        # keep job data for 30 mins
        ttl_seconds_after_finished=60*30
    )

    # Instantiate the job object
    job = client.V1Job(
        api_version="batch/v1",
        kind="Job",
        metadata=client.V1ObjectMeta(
            name=f"{agent_build.spec.buildTarget}_{agent_deployment.spec.agentName}_{int(time.time())}",
            owner_references=[
                client.V1OwnerReference(
                    api_version="fusion.tuna.ai",
                    block_owner_deletion=False,
                    controller=True,
                    kind="AgentDeployment",
                    name=agent_deployment.metadata.name,
                    uid=agent_deployment.metadata.uid,
                )
            ]
        ),
        spec=spec)

    return job


def create_job(api_instance: client.BatchV1Api, job: client.V1Job, ns: str):
    api_response = api_instance.create_namespaced_job(
        body=job,
        namespace=ns)
    logger.info(f"Job created. status='{str(api_response.status)}'")
    return api_response


def get_job_status(api_instance, job_name: str, ns: str) -> client.V1JobStatus:
    api_response: client.V1Job = api_instance.read_namespaced_job_status(
        name=job_name,
        namespace=ns
    )
    return api_response.status


def wait_for_job_completion(api_instance, job_name:str):
    job_completed = False
    while not job_completed:
        api_response = api_instance.read_namespaced_job_status(
            name=job_name,
            namespace="default")
        if api_response.status.succeeded is not None or \
                api_response.status.failed is not None:
            job_completed = True
        logger.debug(f"Job status='{str(api_response.status)}'")


def get_agent_deployment_resource(dyn_client: DynamicClient):
    try:
        return dyn_client.resources.get(api_version="fusion.tuna.ai/v1", kind="AgentDeployment")
    except:
        raise kopf.PermanentError("AgentDeployment resource not found")


def get_reference_agent_deployment(agent_build: AgentBuild):
    ref_names = [ref.name for ref in agent_build.metadata.ownerReferences if ref.kind == "AgentBuild"]
    assert ref_names, "should have at least one AgentDeployment"
    agent_deployment_name = ref_names[0]
    config.load_kube_config()

    agent_deployment_resource = get_agent_deployment_resource(DynamicClient(ApiClient()))
    agent_deployment_instance = agent_deployment_resource.get(name=agent_deployment_name, namespace=agent_build.metadata.namespace)
    return AgentDeployment.model_validate(agent_deployment_instance.to_dict())


