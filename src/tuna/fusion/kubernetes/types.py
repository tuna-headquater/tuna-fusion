from enum import Enum
from typing import Optional

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

class AgentDeployment(BaseModel):
    class Status(BaseModel):
        class CurrentBuilds(BaseModel):
            class BuildInfo(BaseModel):
                name: str
                start_timestamp: int
            staging: Optional[BuildInfo] = None
            production: Optional[BuildInfo] = None
        current_builds: CurrentBuilds

    class Spec(BaseModel):
        agent_name: str
        agent_catalogue_id: int
        agent_repository_id: int
        agent_catalogue_name: str
        agent_repository_name: str
        git_repository_url: str

    spec: Spec
    status: Status


class AgentBuild(BaseModel):
    class Spec(BaseModel):
        git_commit_id: str
        builder_image: str
        build_target: AgentBuildTarget
        build_script: str

    class Status(BaseModel):
        phase: AgentBuildPhase

    spec: Spec
    status: Status

