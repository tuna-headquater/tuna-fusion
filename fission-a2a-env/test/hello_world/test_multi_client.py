import json
import logging
import uuid
from uuid import uuid4

import httpx
import pytest
from a2a.client.client import A2AClient
from a2a.types import MessageSendParams, Message, Role, Part, TextPart, AgentCard, TaskResubscriptionRequest, \
    TaskIdParams, SendMessageRequest, JSONRPCErrorResponse, SendMessageSuccessResponse, Task, TaskStatusUpdateEvent, \
    TaskArtifactUpdateEvent

logger = logging.getLogger(__name__)


@pytest.mark.asyncio
@pytest.mark.parametrize("query", ["What is the capital of France?", "What is the capital of Germany?"])
async def test_multi_client_resubscribe(query: str):
    with open('agent_card.json') as f:
        agent_card = AgentCard.model_validate(json.load(f))

    logger.info("AgentCard: %s", agent_card)
    task_id = str(uuid4())
    async with httpx.AsyncClient() as httpx_client:
        client = A2AClient(httpx_client=httpx_client, agent_card=agent_card)
        request = SendMessageRequest(
            id=str(uuid4()),
            params=MessageSendParams(message=Message(
                role=Role.agent,
                taskId=task_id,
                parts=[Part(root=TextPart(text=query))],
                messageId=str(uuid.uuid4()),
            ))
        )
        response = await client.send_message(request)
        if isinstance(response.root, JSONRPCErrorResponse):
            logger.error("Error: %s", response.root.error)
            raise RuntimeError(str(response.root.error))
        elif isinstance(response.root, SendMessageSuccessResponse):
            event = response.root.result
            if isinstance(event, Task):
                logger.info("Task %s", event)
                task_id = event.id
            elif isinstance(event, Message):
                logger.info("Message: %s", event)
                task_id = event.taskId
            elif isinstance(event, TaskStatusUpdateEvent):
                logger.info("Task status update: %s", event)
                task_id = event.taskId
            elif isinstance(event, TaskArtifactUpdateEvent):
                logger.info("Task artifact update: %s", event)
                task_id = event.taskId
            else:
                logger.warning("Unknown event: %s", event)
                raise RuntimeError("Unknown event")


    assert task_id, "should have valid task id"
    async with httpx.AsyncClient() as httpx_client:
        client = A2AClient(httpx_client=httpx_client, agent_card=agent_card)
        resubscribe_request = TaskResubscriptionRequest(params=TaskIdParams(id=task_id),id=str(uuid4()))
        async for event in client.send_message_streaming(resubscribe_request):
            logger.info("Event: %s", event)
