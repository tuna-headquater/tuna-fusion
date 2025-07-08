import json
import json
import logging

from a2a.server.tasks.task_store import TaskStore
from a2a.types import TaskStatus, Task, Artifact, Message
from sqlalchemy import Column, String, JSON, DateTime, func, Table, MetaData
from sqlalchemy.exc import NoResultFound
from sqlalchemy.ext.asyncio import create_async_engine
from sqlalchemy.future import select

logger = logging.getLogger(__name__)


class DatabaseTaskStore(TaskStore):
    def __init__(self, db_url: str,
                 pool_size: int = 5,
                 create_table_if_not_exists: bool = True,
                 table_name: str = "a2a_tasks"):
        super().__init__()
        self._db_url = db_url
        self._table_name = table_name
        self._create_table_if_not_exists = create_table_if_not_exists
        self._engine = create_async_engine(
            db_url,
            pool_size=pool_size,
            pool_pre_ping=True
        )
        self._metadata = MetaData()
        self._tasks_table = Table(
            table_name, self._metadata,
            Column('id', String(36), primary_key=True),
            Column('context_id', String(36), nullable=False),
            Column('status', JSON, nullable=False),
            Column('artifacts', JSON, nullable=True),
            Column('history', JSON, nullable=True),
            Column('metadata', JSON, nullable=True),
            Column('created_at', DateTime, server_default=func.now()),
            Column('updated_at', DateTime, onupdate=func.now())
        )
        self._initialized = False

    async def ensure_initialized(self):
        logger.debug("ensure_initialized: _initialized: %s, _create_table_if_not_exists: %s", self._initialized,
                     self._create_table_if_not_exists)
        if self._create_table_if_not_exists and not self._initialized:
            async with self._engine.connect() as conn:
                await conn.run_sync(self._metadata.create_all)
                await conn.commit()
                self._initialized = True

    async def save(self, task: Task) -> None:
        await self.ensure_initialized()
        async with self._engine.connect() as session:
            task_dict = task.model_dump(mode='json')
            task_id = task.id  # 添加：使用task.id作为task_id
            select_stmt = select(self._tasks_table).where(self._tasks_table.c.id == task_id)
            select_result = await session.execute(select_stmt)
            upsert = True  # 修改：默认设为True，如果找不到结果再改为False
            try:
                select_result.one()
            except NoResultFound:
                upsert = False

            if upsert:
                stmt = self._tasks_table.update().where(self._tasks_table.c.id == task_id).values(
                    context_id=task.contextId,
                    status=json.dumps(task_dict['status']) if task.status else None,
                    artifacts=json.dumps(task_dict.get('artifacts')) if task.artifacts else  None,
                    history=json.dumps(task_dict.get('history')) if task.history else None,
                    metadata=json.dumps(task_dict.get('metadata')) if task.metadata else None,
                    updated_at=func.now()  # 可选添加：显式更新时间戳
                )
            else:
                stmt = self._tasks_table.insert().values(
                    id=task_id,
                    context_id=task.contextId,
                    status=json.dumps(task_dict['status']) if task.status else None,
                    artifacts=json.dumps(task_dict.get('artifacts')) if task.artifacts else None,
                    history=json.dumps(task_dict.get('history')) if task.history else None,
                    metadata=json.dumps(task_dict.get('metadata')) if task.metadata else None,
                )

            await session.execute(stmt)
            await session.commit()

    async def get(self, task_id: str) -> Task | None:
        await self.ensure_initialized()
        async with self._engine.connect() as session:
            stmt = select(self._tasks_table).where(self._tasks_table.c.id == task_id)
            result = await session.execute(stmt)
            try:
                row = result.one()
                return Task(
                    id=row.id,
                    contextId=row.context_id,
                    status=TaskStatus.model_validate(json.loads(row.status)),
                    artifacts=[Artifact.model_validate(a) for a in json.loads(row.artifacts)] if row.artifacts else None,
                    history=[Message.model_validate(m) for m in json.loads(row.history)] if row.history else None,
                    metadata=json.loads(row.metadata) if row.metadata else None
                )
            except NoResultFound:
                return None

    async def delete(self, task_id: str) -> None:
        await self.ensure_initialized()
        async with self._engine.connect() as session:
            async with session.begin():
                stmt = self._tasks_table.delete().where(self._tasks_table.c.id == task_id)
                await session.execute(stmt)
                await session.commit()

