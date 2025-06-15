import os

import yaml
from kopf.testing import KopfRunner
from kubernetes.dynamic import ResourceList

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

def test_agent_deployment_validation_ok(agent_deployment_crd: ResourceList):
    with KopfRunner(["run", "-A", "--verbose", OPERATOR_PATH]) as runner:
        obj = yaml.safe_load(agent_deployment_1)
        AgentDeployment.model_validate(obj)
        agent_deployment_crd.create(obj)


    print(runner.exception)






