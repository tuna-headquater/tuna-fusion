from enum import Enum
from typing import Optional, List

from pydantic import BaseModel, Field

class AgentBuildTarget(Enum):
    Staging = "staging"
    Production = "production"


class AgentBuildPhase(Enum):
    Pending = "pending"
    Scheduled = "scheduled"
    Running = "running"
    Succeeded = "succeeded"
    Failed = "failed"


class OperatorConfiguration(BaseModel):
    stagingNamespace: str
    productionNamespace: str
    toolchainImage: str


class Metadata(BaseModel):
    class OwnerReference(BaseModel):
        apiVersion: str
        blockOwnerDeletion: str
        controller: bool
        kind: str
        name: str
        uid: str

    name: str
    namespace: str
    uid: str
    labels: dict[str, str]
    ownerReferences: List[OwnerReference]


class AgentDeployment(BaseModel):
    class Status(BaseModel):
        class CurrentBuilds(BaseModel):
            class BuildInfo(BaseModel):
                name: str
                startTimestamp: int
            staging: Optional[BuildInfo] = None
            production: Optional[BuildInfo] = None
        currentBuilds: CurrentBuilds

    class Spec(BaseModel):
        agentName: str
        agentCatalogueId: int
        agentRepositoryId: int
        agentCatalogueName: str
        gitRepositoryUrl: str

    metadata: Metadata
    spec: Spec
    status: Status


class AgentBuild(BaseModel):
    class Spec(BaseModel):
        gitCommitId: str
        buildTarget: AgentBuildTarget
        buildScript: str
        # fission_builder_image: str
        # fission_runtime_image: str
        # fissionFunctionEntrypoint: str
        # fissionEnv: str

    class Status(BaseModel):
        phase: AgentBuildPhase

    metadata: Metadata
    spec: Spec
    status: Status

