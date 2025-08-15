# Agent development using Java

## Prerequisites

* Python environment
      * uv 0.7.4+
    * Python 3.13+
* Install `tuna-fusion` in Kubernetes cluster
* Understanding about A2A protocol
    * [A2A protocol concept](https://a2a-protocol.org/latest/topics/what-is-a2a/)
    * [A2A protocol specification](https://a2a-protocol.org/latest/specs/a2a-protocol-specification/)
* Understanding about how to develop with [a2a-python]() SDK
    * [Introduction of a2a-python](https://a2a-protocol.org/latest/tutorials/python/1-introduction/)
    * [AgentExecutor](https://a2a-protocol.org/latest/tutorials/python/4-agent-executor/)
    * Code sample to export LangCloud agent using `AgentExecutor`: [a2a-samples/samples/python/agents/langgraph/app
      /agent_executor.py](https://github.com/a2aproject/a2a-samples/blob/main/samples/python/agents/langgraph/app/agent_executor.py#L27)


## A tour of `hello-world` agent

To demonstrate the basic workflow of deploying an agent, we will create a simple agent that returns a greeting message.


### Create project folder

First, we initialize an empty folder and initialize as a Git repository.

```shell
mkdir hello-world
cd hello-world
git init .
```

!!! info

    You can check out the complete code sample from samples repository directly.

    ```shell
    git clone git@github.com:tuna-headquater/tuna-fusion-agent-samples.git
    ```


### Write an AgentExecutor

As we are going to use `AgentExecutor` class, we have to declare `a2a-sdk` as only dependency.

```txt title="requirements.txt"
a2a-sdk
```

And create the source file of `HelloWorldAgentExecutor`.

``` python title="app.py" linenums="1"

from datetime import datetime
from a2a.server.agent_execution import AgentExecutor, RequestContext 
from a2a.server.events import EventQueue
from a2a.server.tasks import TaskUpdater
from a2a.types import TaskState
from a2a.utils import new_agent_text_message, new_task


class HelloWorldAgentExecutor(AgentExecutor): # (1)
    async def execute(self, context: RequestContext, event_queue: EventQueue) -> None:
        task = context.current_task or new_task(context.message)
        await event_queue.enqueue_event(task)
        # (2)
        msg = new_agent_text_message(text="hello world", task_id=context.task_id)
        updater = TaskUpdater(event_queue, task.id, task.contextId)

        try:
            await updater.update_status(state=TaskState.working,
                                        message=msg,
                                        timestamp=datetime.now().isoformat() # (3)
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

```

1. Use official `a2a-python` SDK to create an agent executor.
2. Reply simple "hello world" message to the client once a client connects and send an arbitrary message.
3. Explicitly use timestamps without timezone info so that Java client can correctly parse them. This is [a known issue](https://github.com/a2aproject/a2a-java/issues/232).



### Create `AgentEnvirontment` resource

!!! info

    Let's assume `tuna-fusion` is installed in `default` namespace, so you don't need to specify namespace in the `metadata.namespace` field of resources and  `kubectl` commands in later sections.



`AgentEnvironment` should be created before actual agent deployment. You can create one `AgentEnvironment` resource for a group of logically related agent instances which often share some common configurations.

```yaml title="agent-env-1.yaml" linenums="1"
apiVersion: fusion.tuna.ai/v1  # (1)
kind: AgentEnvironment
metadata:
  name: agent-env-1 # (2)
spec:
  driver:
    type: PodPool # (3)
    podPoolSpec:
      poolSize: 3
```


1. `apiVersion` for `tuna-fusion` CRDs
2. Name of the `AgentEnvironment`: It should be unique in the namespace and it will be used in `AgentDeployment` later.
3. `PodPool` is used as provision driver and detailed properties for `PodPool` can be configured in `podPoolSpec` field. See [CRD Reference](../reference/crds-reference.md) for more details

And let's apply it to the cluster:

```shell
kubectl apply -f agent-env-1.yaml
```

### Create `AgentDeployment` resource

`AgentDeployment` resource should be created for each standalone agent project.


```yaml title="agent-deploy-1.yaml" linenums="1"
apiVersion: fusion.tuna.ai/v1
kind: AgentDeployment # (1)
metadata:
  name: test-deploy-1 # (2)
spec:
  agentCard: # (3)
    name: agent1
    description: a simple http server that speaks hello
    capabilities:
      streaming: true
      pushNotifications: false
      stateTransitionHistory: false
    defaultInputModes:
      - "text/plain"
      - "application/json"
    defaultOutputModes:
      - "application/json"
      - "image/png"
    skills:
      - description: "hello world"
        id: say_hello
        name: say hello
        tags:
          - hello
    provider:
      organization: tuna
      url: https://github.com/tuna-headquarter
    version: "1.0.0"
  environmentName: agent-env-1 # (4)
  git:
    watchedBranchName: "refs/heads/main" # (5)
  a2a: # (6)
    queueManager:
      provider: InMemory
    taskStore:
      provider: InMemory
  entrypoint: app.HelloWorldAgentExecutor # (7)
```


1. Notice here this resource is kind of `AgentDeployment`
2. Name of `AgentDeployment` is treated as the name of the agent.
3. Yes. It's the [agent card](https://a2a-protocol.org/latest/specification/#55-agentcard-object-structure) definition of your agent.
4. It should be the name of a properly created `AgentEnvironment`.
5. The branch name to watch. If not specified, `main` will be used.
6. The A2A runtime configurations. Default in-memory implementations of [TaskStore]() and [QueueManager]() can be used as test purposes. It's advised to use external storage to support sessions in distributed deployments. See [CRDs Reference](../reference/crds-reference.md) for move advanced configurations.
7. The import path `AgentExecutor` class. In this case, it's `app.HelloWorldAgentExecutor` as `HelloWorldAgentExecutor` in `app.py` Python file.

Let's apply it to the cluster:

```shell
kubectl apply -f agent-deploy-1.yaml
```

### Commit the code changes

There are two ways of update agent deployments:

1. Manually create a `PodFunctionBuild`.
2. Update code changes through `tuna-fusion-gitops-server`.

Both solutions will trigger build pipeline to run automatically. As creating `PodFunctionBuild` for each update is tedious, we recommend using `tuna-fusion-gitops-server` to update code changes.

To push updates using `git` client, we have to add a git remote first:

```shell
#git remote add test-deploy-1 http://
```