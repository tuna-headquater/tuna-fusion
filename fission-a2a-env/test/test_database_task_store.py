import logging
import os

import pytest
from sqlalchemy import text

from a2a_runtime.database_task_store import DatabaseTaskStore


logger = logging.getLogger(__name__)

@pytest.mark.asyncio
async def test_db_init():
    db_url = os.getenv('DB_URL')
    db = DatabaseTaskStore(db_url=db_url)
    await db._ensure_initialized()
    async with db._engine.connect() as conn:
        result = await conn.execute(text("""SELECT table_name 
    FROM information_schema.tables 
    WHERE table_schema = 'public';
        """))
        rows = result.fetchall()
        logger.info(rows)
        assert len(rows) == 1


@pytest.mark.asyncio
async def test_task_crud():
    pass

