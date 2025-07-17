import json
import logging
import uuid
from uuid import uuid4

import httpx
from a2a.client.client import A2AClient,SendStreamingMessageRequest
from a2a.types import MessageSendParams, Message, Role, Part, TextPart, AgentCard

logger = logging.getLogger(__name__)


async def test_single_turn_chat(query: str):
    """
    Perform a single turn chat with the agent.
    """
    with open('agent_card.json') as f:
        agent_card = AgentCard.model_validate(json.load(f))

    logger.info("AgentCard: %s", agent_card)
    async with httpx.AsyncClient() as httpx_client:
        client = A2AClient(httpx_client=httpx_client, agent_card=agent_card)
        streaming_request = SendStreamingMessageRequest(
            id=str(uuid4()), params=MessageSendParams(message=Message(
                role=Role.agent,
                parts=[Part(root=TextPart(text=query))],
                messageId=str(uuid.uuid4()),
            ))
        )
        stream_response = client.send_message_streaming(streaming_request)
        async for chunk in stream_response:
            logger.info(chunk.model_dump_json(exclude_none=True))

