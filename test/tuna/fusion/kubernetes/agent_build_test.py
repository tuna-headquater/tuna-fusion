import logging
import os
from time import sleep

import yaml
from kopf._kits.runner import KopfRunner

from tuna.fusion.kubernetes.types import AgentBuildPhase, AgentDeployment, AgentBuild

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
OPERATOR_PATH = os.path.join(PROJECT_ROOT, "src/tuna/fusion/kubernetes/agent_operator.py")


logger = logging.getLogger(__name__)

agent_deployment_1 = """
apiVersion: fusion.tuna.ai/v1
kind: AgentDeployment
metadata:
    name: test-deploy-1
    namespace: default
spec: 
    agentName: agent_1
    agentCatalogueId: 111
    agentRepositoryId: 222
    agentCatalogueName: catalogue_1
    gitRepositoryUrl: https://test.com/project.git 
"""



agent_build_1 = """
apiVersion: fusion.tuna.ai/v1
kind: AgentBuild
metadata:
    name: test-build-1
    namespace: default
    ownerReferences:
        - kind: AgentDeployment
          name: test-deploy-1
          apiVersion: fusion.tuna.ai/v1
          uid: to_be_replaced
spec:
    gitCommitId: xxxxx
    builderImage: busybox:latest
    buildScript: |
        echo "Pretend to be busy..."
        sleep 2
"""

def test_model_validation():
    agent_deployment_obj = yaml.safe_load(agent_deployment_1)
    agent_build_obj = yaml.safe_load(agent_build_1)
    AgentBuild.model_validate(agent_build_obj)
    AgentDeployment.model_validate(agent_deployment_obj)


def test_agent_build_lifecycle(kopf_runner, agent_deployment_crd, agent_build_crd):
    agent_deployment_obj = yaml.safe_load(agent_deployment_1)
    agent_build_obj = yaml.safe_load(agent_build_1)
    created_agent_deployment_instance = agent_deployment_crd.create(agent_deployment_obj)

    # replace uid of agent_deployment with the one created by the operator
    agent_build_obj["metadata"]["ownerReferences"][0]["uid"] = created_agent_deployment_instance.to_dict()["metadata"][
        "uid"]
    assert agent_build_crd.create(agent_build_obj)

    retry_count = 20
    while retry_count:
        retry_count -= 1
        try:
            agent_deployment_obj = agent_deployment_crd.get(name=agent_deployment_obj["metadata"]["name"],
                                                            namespace=agent_deployment_obj["metadata"]["namespace"])
            agent_deployment = AgentDeployment.model_validate(agent_deployment_obj.to_dict())
            logger.info("agent_deployment.status=%s", agent_deployment.status)
            agent_build_obj = agent_build_crd.get(name=agent_build_obj["metadata"]["name"],
                                                  namespace=agent_build_obj["metadata"]["namespace"])
            agent_build = AgentBuild.model_validate(agent_build_obj.to_dict())
            logger.info("agentBuild.status=%s", agent_build.status)
            if agent_build.status and agent_build.status.phase in [AgentBuildPhase.Failed, AgentBuildPhase.Succeeded]:
                break
            logger.info("Sleep 3s for next iteration...")
            sleep(3)
        except Exception as e:
            if retry_count:
                logger.warning("Error during loop: %s", e)
            else:
                raise e
