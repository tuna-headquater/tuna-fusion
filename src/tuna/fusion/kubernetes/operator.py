import logging
from datetime import datetime

import kopf
from kubernetes import config
from kubernetes.client import ApiClient, BatchV1Api
from kubernetes.dynamic import DynamicClient
from pydantic import ValidationError

from tuna.fusion.kubernetes.types import AgentBuildTarget, AgentBuild, AgentBuildPhase, AgentDeployment
from tuna.fusion.kubernetes.utilities import get_agent_deployment_resource, \
    create_builder_job_object, create_job, get_job_status, get_configuration, \
    get_reference_agent_deployment

logger = logging.getLogger(__name__)


@kopf.on.validate("fusion.tuna.ai", "v1", "AgentDeployment")
def validate_agent_deployment(body, **_):
    """
    Validate agent deployment configuration
    :param body:
    :param _:
    :return:
    """
    try:
        agent_deployment = AgentDeployment.model_validate(body)
    except ValidationError as e:
        raise kopf.AdmissionError(message=str(e))
    if agent_deployment.status.current_builds.staging or agent_deployment.status.current_builds.production:
        raise kopf.AdmissionError("AgentDeployment cannot have have current builds on creation")


@kopf.on.create("fusion.tuna.ai", "v1", "AgentBuild")
def validate_agent_build(body, meta, namespace, **kwargs_):
    """
    Validate agent build:
    1. data fields should be valid
    2. parent (aka, AgentDeployment) should exist
    3. parent (aka, AgentDeployment) should have no current build
    :param namespace:
    :param meta:
    :param body:
    :param _:
    :return:
    """
    try:
        agent_build = AgentBuild.model_validate(body)
    except ValidationError as e:
        raise kopf.AdmissionError(message=str(e))

    try:
        agent_deployment = get_reference_agent_deployment(agent_build)
    except AssertionError as e:
        raise kopf.AdmissionError(message=str(e))

    if agent_build.spec.buildTarget == AgentBuildTarget.Staging and agent_deployment.status.current_builds.staging:
        raise kopf.AdmissionError("An existing staging build is still running.")
    if agent_build.spec.buildTarget == AgentBuildTarget.Production and agent_deployment.status.current_builds.production:
        raise kopf.AdmissionError("An existing production build is still running.")


@kopf.timer('fusion.tuna.ai', 'v1', 'AgentBuild', interval=2, when=lambda status, **_: status["phase"] not in ["failed", "succeeded"])
def check_pending_agent_builds(body, namespace, **kwargs):
    """
    Find pending agent builds and create jobs
    :param namespace:
    :param body:
    :param kwargs:
    :return:
    """

    configuration = get_configuration()
    config.load_kube_config()

    try:
        agent_build = AgentBuild.model_validate(body)
    except ValidationError as e:
        raise kopf.PermanentError(str(e))

    try:
        agent_deployment = get_reference_agent_deployment(agent_build)
    except AssertionError as e:
        raise kopf.PermanentError(str(e))

    job_obj = create_builder_job_object(
        configuration=configuration,
        agent_deployment=agent_deployment,
        agent_build=agent_build
    )
    batch_api = BatchV1Api(ApiClient())
    create_job_resp = create_job(batch_api, job_obj, namespace)
    if not create_job_resp.successful():
        raise kopf.TemporaryError("Failed to create job")
    #
    # # update agent deployment
    # agent_deployment_resource = get_agent_deployment_resource(DynamicClient(ApiClient()))
    # agent_deployment_resource.patch(name=agent_deployment.metadata.name,
    #     body={"currentBuilds": {
    #         agent_build.spec.buildTarget: {
    #             "name": agent_build.metadata.name,
    #             "startTimestamp":  int(datetime.now().timestamp())
    #         }
    #     }},
    #     content_type="application/merge-patch+json")

    # update agent build
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



@kopf.on.field('fusion.tuna.ai', 'v1', 'AgentBuild', field='status.phase')
def on_agent_build_status_phase_update(body, meta, old, new, **kwargs):
    dynamic_client = DynamicClient(ApiClient())
    agent_deployment_resource = get_agent_deployment_resource(dynamic_client)
    agent_build = AgentBuild.model_validate(body)
    agent_deployment = get_reference_agent_deployment(agent_build)
    build_target = body.get("spec", {}).get("build_target")
    current_build_update = None

    if new == AgentBuildPhase.Scheduled:
        current_build_update = {"currentBuilds": {build_target: {
            "name": meta["name"],
            "startTimestamp": int(datetime.now().timestamp())}}
        }
    elif new in [AgentBuildPhase.Succeeded, AgentBuildPhase.Failed]:
        # Prepare the patch data to set currentBuilds to None for the corresponding target
        current_build_update = {"currentBuilds": {build_target: None}}

    if current_build_update:
        # Patch the specific agent deployment with the update
        patch_result = agent_deployment_resource.patch(
            name=agent_deployment.metadata.name,
            body=current_build_update,
            content_type="application/merge-patch+json"
        )
        if not patch_result:
            raise kopf.TemporaryError(f"Failed to patch AgentDeployment {agent_deployment.metadata.name}")



