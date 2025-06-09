from abc import ABC

from src.tuna.fusion.types.management import CreateAgentRepository, UpdateAgentRepository, DeleteAgentRepository, \
    CreateAgentCatalogueRequest, UpdateAgentCatalogueRequest, DeleteAgentCatalogueRequest, FindAgentRepositories


class AgentCatalogueService(ABC):
    async def create_agent_repo(self, req: CreateAgentRepository):
        pass

    async def update_agent_repo(self, req: UpdateAgentRepository):
        pass

    async def get_agent_repo(self, agent_repo_id: int):
        pass

    async def delete_agent_repo(self, req: DeleteAgentRepository):
        pass

    async def find_agent_repos(self, req: FindAgentRepositories):
        pass

    async def get_agent_catalogue(self, agent_catalogue_id: int):
        pass

    async def create_agent_catalogue(self, req: CreateAgentCatalogueRequest):
        pass

    async def update_agent_catalogue(self, req: UpdateAgentCatalogueRequest):
        pass

    async def delete_agent_catalogue(self, req: DeleteAgentCatalogueRequest):
        pass




