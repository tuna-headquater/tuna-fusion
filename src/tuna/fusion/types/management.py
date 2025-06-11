from datetime import datetime
from enum import Enum
from typing import List

from a2a.types import AgentCard
from pydantic import BaseModel, Field


class AgentSnapshot(BaseModel):
    id: int
    agent_card: AgentCard
    git_commit: str
    agent_repository_id: int
    created_at: datetime
    updated_at: datetime

#
# class AgentDeployment(BaseModel):
#     class State(Enum):
#         PENDING="pending"
#         RUNNING="running"
#         DEPLOYED="deployed"
#         FAILED="failed"
#
#     agent_repository_id: int
#     state: State
#     snapshot_id: int
#     created_at: datetime
#     updated_at: datetime


class AgentRepository(BaseModel):
    id: int
    agent_card: AgentCard
    git_repository_path: str
    created_at: datetime
    updated_at: datetime
    tags: List[str] = Field(default_factory=list)


class FindAgentRepositories(BaseModel):
    keyword: str
    limit: int = 20
    offset: int = 0
    date_after: datetime
    date_before: datetime


class CreateAgentRepository(BaseModel):
    agent_card: AgentCard



class UpdateAgentRepository(BaseModel):
    agent_card: AgentCard


class DeleteAgentRepository(BaseModel):
    id: int


class AgentCatalogue(BaseModel):
    """
    AgentCatalogue is a class that represents a catalogue of agents.
    """
    id: int
    name: str
    description: str = Field(default="")
    created_at: datetime
    updated_at: datetime



class CreateAgentCatalogueRequest(BaseModel):
    name: str
    description: str = Field(default="")
    tags: List[str] = Field(default_factory=list)



class DeleteAgentCatalogueRequest(BaseModel):
    id: int


class UpdateAgentCatalogueRequest(AgentCatalogue):
    id: int
    description: str = Field(default="")
    tags: List[str] = Field(default_factory=list)
