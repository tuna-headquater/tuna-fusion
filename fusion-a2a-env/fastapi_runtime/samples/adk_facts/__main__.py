import json
import logging

import click
import uvicorn

from a2a.server.apps import A2AStarletteApplication
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.tasks import InMemoryTaskStore
from a2a.types import (
    AgentCapabilities,
    AgentCard,
    AgentSkill,
)
from agent import root_agent as facts_agent
from agent_executor import ADKAgentExecutor
from dotenv import load_dotenv
from starlette.middleware.cors import CORSMiddleware



load_dotenv()

logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)


class MissingAPIKeyError(Exception):
    """Exception for missing API key."""


@click.command()
@click.option("--host", default="localhost")
@click.option("--port", default=10002)
def main(host, port):

    # Agent card (metadata)
    with open('agent_card.json') as f:
        agent_card = AgentCard.model_validate(json.load(f))

    print(agent_card)

    request_handler = DefaultRequestHandler(
        agent_executor=ADKAgentExecutor(
            agent=facts_agent,
        ),
        task_store=InMemoryTaskStore(),
    )

    server = A2AStarletteApplication(
        agent_card=agent_card, http_handler=request_handler
    )
    app = server.build()
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],  # Allow all origins
        allow_methods=["*"],  # Allow all HTTP methods (GET, POST, etc.)
        allow_headers=["*"],  # Allow all headers
    )

    uvicorn.run(app, host=host, port=port)


if __name__ == "__main__":
    main()
