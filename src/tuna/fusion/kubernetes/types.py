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
        apiVersion: Optional[str] = None
        blockOwnerDeletion: bool = True
        controller: Optional[str] = None
        kind: str
        name: str
        uid: Optional[str] = None

    name: str
    namespace: str
    uid: Optional[str] = None
    labels: Optional[dict[str, str]] = None
    ownerReferences: Optional[List[OwnerReference]] = None

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

    metadata: Optional[Metadata] = None
    spec: Spec
    status: Optional[Status] = None


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

    metadata: Optional[Metadata] = None
    spec: Spec
    status: Optional[Status] = None

