import logging
import os

import pytest
import yaml
from kopf.testing import KopfRunner
from kubernetes.dynamic import ResourceList
from assertpy import assert_that

from tuna.fusion.kubernetes.types import AgentDeployment

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
OPERATOR_PATH = os.path.join(PROJECT_ROOT, "src/tuna/fusion/kubernetes/operator.py")

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

agent_deployment_2 = """
apiVersion: fusion.tuna.ai/v1
kind: AgentDeployment
metadata:
    name: invalid_name
    # and no namespace is provided
spec: 
    agentName: agent_1
    agentCatalogueId: 111
    agentRepositoryId: 222
    agentCatalogueName: catalogue_1
    gitRepositoryUrl: https://test.com/project.git 
"""


logger = logging.getLogger(__name__)

def test_agent_deployment_validation_ok(agent_deployment_crd: ResourceList):
    with KopfRunner(["run", "-A", "--verbose", OPERATOR_PATH]) as runner:
        obj = yaml.safe_load(agent_deployment_1)
        AgentDeployment.model_validate(obj)
        agent_deployment_crd.create(obj)
        found_one = agent_deployment_crd.get(name=obj["metadata"]["name"], namespace=obj["metadata"]["namespace"]).to_dict()
        assert_that(obj["metadata"]).is_subset_of(found_one["metadata"])
        assert_that(obj["spec"]).is_subset_of(found_one["spec"])
    logger.error("runner.exception=%s", runner.exception)


def test_agent_deployment_validation_fail(agent_deployment_crd: ResourceList):
    with KopfRunner(["run", "-A", "--verbose", OPERATOR_PATH]) as runner:
        obj = yaml.safe_load(agent_deployment_2)
        obj["spec"]["agentName"] = "agent_1"
        with pytest.raises(Exception) as exec_info:
            agent_deployment_crd.create(obj)
        assert exec_info.type == ValueError
    logger.error("runner.exception=%s", runner.exception)






