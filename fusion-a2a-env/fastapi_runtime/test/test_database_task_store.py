import logging
import os
import uuid
from typing import Any, AsyncGenerator

import pytest
import pytest_asyncio
from a2a.types import Task
from sqlalchemy import text

from runtime.a2a_runtime.database_task_store import DatabaseTaskStore


logger = logging.getLogger(__name__)


@pytest_asyncio.fixture(scope="function")
async def store() -> AsyncGenerator[DatabaseTaskStore, Any]:
    db_url = os.getenv('DB_URL')
    db = DatabaseTaskStore(db_url=db_url)
    await db.ensure_initialized()
    yield db
    async with db._engine.connect() as conn:
        await conn.run_sync(db._metadata.drop_all)


@pytest.mark.asyncio
async def test_db_init(store):
    async with store._engine.connect() as conn:
        result = await conn.execute(text("""SELECT table_name 
    FROM information_schema.tables 
    WHERE table_schema = 'public';
        """))
        rows = result.fetchall()
        logger.info(rows)
        assert len(rows) == 1


@pytest.mark.asyncio
async def test_task_crud(store):
    """
    1. Create with valid task data
    2. Create with invalid task data, e.g. missing some required field
    """
    t1_id = str(uuid.uuid4())
    t1 = Task.model_validate({
        "id": t1_id,
        "contextId": "test-context-001",
        "status": {"state": "submitted"},  # 合法的状态值
        "artifacts": [
            {
                "id": "artifact-001",
                "name": "test_artifact.txt",
                "content": "This is a test artifact",
                "artifactId": "artifact-001",  # 必填字段
                "parts": []  # 必填字段
            }
        ],
        "history": [
            {
                "timestamp": "2025-07-07T10:00:00Z",
                "event": "task_created",
                "details": "Initial task creation",
                "messageId": "msg-001",  # 必填字段
                "parts": [],  # 必填字段
                "role": "user"  # 修改为允许的枚举值
            }
        ],
        "metadata": {
            "priority": "high",
            "source": "test_case"
        }
    })
    await store.save(t1)

    t2 = await store.get(task_id=t1_id)
    assert t2 == t1

    await store.delete(task_id=t1_id)
    t3 = await store.get(task_id=t1_id)
    assert t3 is None



