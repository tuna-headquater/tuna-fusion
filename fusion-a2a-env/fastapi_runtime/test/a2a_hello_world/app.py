from datetime import datetime

from a2a.server.agent_execution import AgentExecutor, RequestContext
from a2a.server.events import EventQueue
from a2a.server.tasks import TaskUpdater
from a2a.types import TaskState
from a2a.utils import new_agent_text_message, new_task


class HelloWorldAgentExecutor(AgentExecutor):
    async def execute(self, context: RequestContext, event_queue: EventQueue) -> None:
        task = context.current_task or new_task(context.message)
        await event_queue.enqueue_event(task)
        msg = new_agent_text_message(text="hello world", task_id=context.task_id)
        updater = TaskUpdater(event_queue, task.id, task.contextId)

        # explicit use timestamps without timezone info so that Java client can correctly parse them
        try:
            await updater.update_status(state=TaskState.working,
                                        message=msg,
                                        timestamp=datetime.now().isoformat()
                                        )
            await updater.update_status(
                TaskState.completed,
                final=True,
                timestamp=datetime.now().isoformat()
            )
        except Exception as e:
            await updater.update_status(TaskState.failed, new_agent_text_message(text=str(e), task_id=task.id, context_id=task.contextId))


    async def cancel(self, context: RequestContext, event_queue: EventQueue) -> None:
        raise Exception('cancel not supported')

