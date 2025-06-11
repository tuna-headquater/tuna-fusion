import logging

import kopf
from kubernetes import config
from kubernetes.client import ApiClient, BatchV1Api
from kubernetes.dynamic import DynamicClient
from pydantic import ValidationError

from tuna.fusion.kubernetes.types import AgentBuildTarget, AgentBuild, AgentBuildPhase, AgentDeployment
from tuna.fusion.kubernetes.utilities import get_agent_deployment, get_agent_deployment_resource, \
    create_builder_job_object, create_job, get_job_status, get_configuration

logger = logging.getLogger(__name__)



@kopf.on.create('fusion.tuna.ai', 'v1', 'AgentBuild')
def on_agent_build_create(body, meta, namespace,  **kwargs):
    # 1. check agent deployment
    agent_deployment_name = None
    try:
        agent_deployment_name = meta["ownerReferences"]["name"]
    except KeyError:
        raise kopf.PermanentError("AgentBuild should have ownerReference to a AgentDeployment")


    config.load_kube_config()

    configuration = get_configuration()

    agent_deployment = get_agent_deployment(
        get_agent_deployment_resource(DynamicClient(ApiClient())),
        agent_deployment_name,
        namespace
    )

    agent_build = AgentBuild.model_validate(body)
    if agent_build.spec.build_target == AgentBuildTarget.Staging and agent_deployment.status.current_builds.staging:
        raise kopf.TemporaryError("An existing staging build is still running.")
    if agent_build.spec.build_target == AgentBuildTarget.Production and agent_deployment.status.current_builds.production:
        raise kopf.TemporaryError("An existing production build is still running.")

    # 2. Create job
    job_obj = create_builder_job_object(configuration=configuration, agent_deployment=agent_deployment, agent_build=agent_build)
    batch_api = BatchV1Api(ApiClient())
    create_job_resp = create_job(batch_api, job_obj, namespace)
    if not create_job_resp.successful():
        raise kopf.TemporaryError("Failed to create job")

    # 3. Update status
    return {"phase": AgentBuildPhase.Scheduled}


@kopf.timer('fusion.tuna.ai', 'v1', 'AgentBuild', interval=2, when=lambda status, **_: status["phase"] not in ["failed", "succeeded"])
def check_active_agent_builds(namespace, meta, **kwargs):
    """
    Check job status and update status of agent build
    :param namespace:
    :param meta:
    :param kwargs:
    :return:
    """
    # agent_build = AgentBuild.model_validate(body)
    batch_api = BatchV1Api(ApiClient())
    job_name = meta["name"]
    status = get_job_status(batch_api, job_name, namespace)
    if status.active or status.ready:
        return {"phase": AgentBuildPhase.Running}
    if status.failed:
        return {"phase": AgentBuildPhase.Failed}
    if status.succeeded:
        return {"phase": AgentBuildPhase.Succeeded}
    raise kopf.TemporaryError("Illegal Job status: " + status.to_str())



@kopf.on.field('fusion.tuna.ai', 'v1', 'AgentBuild', field='status.phase',  when=lambda old, new, **_: new in [AgentBuildPhase.Succeeded, AgentBuildPhase.Failed])
def on_agent_build_status_phase_update(body, meta, old, new, **kwargs):
    try:
        agent_deployment_name = meta["ownerReferences"]["name"]
    except KeyError:
        raise kopf.PermanentError("AgentBuild should have ownerReference to a AgentDeployment")

    dynamic_client = DynamicClient(ApiClient())
    agent_deployment_resource = get_agent_deployment_resource(dynamic_client)
    build_target = body.get("spec", {}).get("build_target")

    # Prepare the patch data to set currentBuilds to None for the corresponding target
    current_build_update = {"currentBuilds": {build_target: None}}

    # Patch the specific agent deployment with the update
    patch_result = agent_deployment_resource.patch(
        name=agent_deployment_name,
        body=current_build_update,
        content_type="application/merge-patch+json"
    )
    if not patch_result:
        raise kopf.TemporaryError(f"Failed to patch AgentDeployment {agent_deployment_name}")


@kopf.on.validate("fusion.tuna.ai", "v1", "AgentDeployment")
def validate_agent_deployment(body, **_):
    try:
        agent_deployment = AgentDeployment.model_validate(body)
    except ValidationError as e:
        raise kopf.AdmissionError(message=str(e))
    if agent_deployment.status.current_builds.staging or agent_deployment.status.current_builds.production:
        raise kopf.AdmissionError("AgentDeployment cannot have have current builds on creation")


@kopf.on.create("fusion.tuna.ai", "v1", "AgentBuild")
def validate_agent_build(body, **_):
    try:
        AgentBuild.model_validate(body)
    except ValidationError as e:
        raise kopf.AdmissionError(message=str(e))
