from enum import Enum, StrEnum
from typing import Optional, List

from pydantic import BaseModel


class AgentBuildPhase(StrEnum):
    Pending = "Pending"
    Scheduled = "Scheduled"
    Running = "Running"
    Succeeded = "Succeeded"
    Failed = "Failed"
    Terminating = "Terminating"


class OperatorConfiguration(BaseModel):
    stagingNamespace: str
    productionNamespace: str
    toolchainImage: str


class Metadata(BaseModel):
    class OwnerReference(BaseModel):
        apiVersion: str = None
        blockOwnerDeletion: bool = True
        controller: Optional[str] = None
        kind: str
        name: str
        uid: str = None

    name: str
    namespace: str
    uid: Optional[str] = None
    labels: Optional[dict[str, str]] = None
    ownerReferences: Optional[List[OwnerReference]] = None

class AgentDeployment(BaseModel):
    class Status(BaseModel):
        class BuildInfo(BaseModel):
            name: str
            startTimestamp: int
        currentBuild: Optional[BuildInfo] = None

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
        buildScript: str

    class Status(BaseModel):
        phase: AgentBuildPhase

    metadata: Optional[Metadata] = None
    spec: Spec
    status: Optional[Status] = None

