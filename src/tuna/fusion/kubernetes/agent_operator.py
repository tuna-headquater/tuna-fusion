import logging
from datetime import datetime

import kopf
from kopf import WebhookAutoServer
from kubernetes import config
from kubernetes.client import ApiClient, BatchV1Api
from kubernetes.dynamic import DynamicClient
from pydantic import ValidationError

from tuna.fusion.kubernetes.types import AgentBuild, AgentBuildPhase, AgentDeployment
from tuna.fusion.kubernetes.utilities import get_agent_deployment_resource, \
    create_builder_job_object, create_job, get_job_status, get_configuration, \
    get_reference_agent_deployment, print_validation_error

logger = logging.getLogger(__name__)


@kopf.on.startup()
def configure(settings: kopf.OperatorSettings, **_):
    settings.admission.managed = 'auto.kopf.dev'
    settings.admission.server = WebhookAutoServer()


@kopf.on.create("agentdeployments")
def validate_agent_deployment(body, **_):
    """
    Validate agent deployment configuration
    :param body:
    :param _:
    :return:
    """
    logger.info("Validating AgentDeployment data: %s", body)
    try:
        agent_deployment = AgentDeployment.model_validate(body)
    except ValidationError as e:
        raise kopf.AdmissionError(message=print_validation_error(e))
    # if agent_deployment.status:
    #     raise kopf.AdmissionError("Status cannot be setup before resource is created.")


@kopf.on.create("agentbuilds")
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
    logger.info("Validating AgentBuild data: %s", body)
    try:
        AgentBuild.model_validate(body)
    except ValidationError as e:
        raise kopf.AdmissionError(message=print_validation_error(e))


@kopf.on.create("agentbuilds")
def on_agent_build_create(body, patch, **kwargs):
    logger.debug("on_agent_build_create: %s", body)
    patch.status["phase"] = AgentBuildPhase.Pending


@kopf.on.create("agentdeployments")
def on_agent_deployment_create(body, patch, **kwargs):
    logger.debug("on_agent_deployment_create: %s", body)
    patch.status["currentBuild"] = None

#
@kopf.timer("agentbuilds", interval=2, when=lambda status, **_: status and status.get("phase") == AgentBuildPhase.Pending)
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

    if not agent_deployment.status.currentBuild:
        logger.info("Start to schedule build for deploy.name=%s, build.name=%s", agent_deployment.metadata.name, agent_build.metadata.name)
        job_obj = create_builder_job_object(
            configuration=configuration,
            agent_deployment=agent_deployment,
            agent_build=agent_build
        )
        batch_api = BatchV1Api(ApiClient())
        create_job_resp = create_job(batch_api, job_obj, namespace)
        logger.info("Build job scheduled for deploy.name=%s, build.name=%s", agent_deployment.metadata.name, agent_build.metadata.name)
        if not create_job_resp.successful():
            raise kopf.TemporaryError("Failed to create job")

        # update agent build
        return {"phase": AgentBuildPhase.Scheduled}
    else:
        logger.warning("Build (%s) is still running for deploy.name=%s", agent_deployment.status.currentBuild.name, agent_deployment.metadata.name)

    # not updating
    return None


# @kopf.timer('fusion.tuna.ai', 'v1', 'AgentBuild', interval=2, when=lambda status, **_: status and status.get("phase") not in [AgentBuildPhase.Succeeded, AgentBuildPhase.Failed])
# def check_active_agent_builds(namespace, meta, **kwargs):
#     """
#     Check job status and update status of agent build
#     :param namespace:
#     :param meta:
#     :param kwargs:
#     :return:
#     """
#     batch_api = BatchV1Api(ApiClient())
#     job_name = meta["name"]
#     status = get_job_status(batch_api, job_name, namespace)
#     if status.active or status.ready:
#         return {"phase": AgentBuildPhase.Running}
#     if status.failed:
#         return {"phase": AgentBuildPhase.Failed}
#     if status.succeeded:
#         return {"phase": AgentBuildPhase.Succeeded}
#     raise kopf.TemporaryError("Illegal Job status: " + status.to_str())
#
#
#
# @kopf.on.field('fusion.tuna.ai', 'v1', 'AgentBuild', field='status.phase')
# def on_agent_build_status_phase_update(body, meta, old, new, **kwargs):
#     """
#     Listen to the changes of `phase` on AgentBuild, and update `currentBuilds` of `AgentDeployment`.
#     :param body:
#     :param meta:
#     :param old:
#     :param new:
#     :param kwargs:
#     :return:
#     """
#     dynamic_client = DynamicClient(ApiClient())
#     agent_deployment_resource = get_agent_deployment_resource(dynamic_client)
#     agent_build = AgentBuild.model_validate(body)
#     agent_deployment = get_reference_agent_deployment(agent_build)
#     build_target = body.get("spec", {}).get("build_target")
#     current_build_update = None
#
#     if new == AgentBuildPhase.Scheduled:
#         current_build_update = {"currentBuild": {
#             "name": meta["name"],
#             "startTimestamp": int(datetime.now().timestamp())}
#         }
#     elif new in [AgentBuildPhase.Succeeded, AgentBuildPhase.Failed]:
#         # Prepare the patch data to set currentBuilds to None for the corresponding target
#         current_build_update = {"currentBuild": None}
#
#     if current_build_update:
#         # Patch the specific agent deployment with the update
#         patch_result = agent_deployment_resource.patch(
#             name=agent_deployment.metadata.name,
#             body=current_build_update,
#             content_type="application/merge-patch+json"
#         )
#         if not patch_result:
#             raise kopf.TemporaryError(f"Failed to patch AgentDeployment {agent_deployment.metadata.name}")
#
#

