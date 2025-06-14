import os

import yaml
from kopf.testing import KopfRunner
from kubernetes.dynamic import ResourceList

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
OPERATOR_PATH = os.path.join(PROJECT_ROOT, "src/tuna/fusion/kubernetes/operator.py")

agent_deployment_1 = """
apiVersion: fusion.tuna.ai
kind: AgentDeployment
metadata:
    name: test_deploy_1
spec: 
    agentName: agent_1
    agentCatalogueId: 111
    agentRepositoryId: 222
    agentCatalogueName: catalogue_1
    gitRepositoryUrl: https://test.com/project.git 
"""

def test_agent_deployment_validation_ok(agent_deployment_crd: ResourceList):
    with KopfRunner(["run", "-A", "--verbose", OPERATOR_PATH]) as runner:
        agent_deployment_crd.create(yaml.safe_load(agent_deployment_1))






