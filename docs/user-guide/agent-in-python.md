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

    Let's make some assumptions about how `tuna-fusion` is installed:

    1. `tuna-fusion` is installed in `default` namespace, so you don't need to specify namespace in the `metadata.namespace` field of resources and  `kubectl` commands in later sections.
    2. You can access `tuna-fusion-gitops-server` and `tuna-fusion-exeuctor` using Kuberentes Service domains on port 80, specifically `tuna-fusion-gitops-server.default.svc.cluster.local` and `tuna-fusion-executor.default.svc.cluster.local`.

    If you have `tuna-fusion` with default Helm chart values, you don't need to touch anything to make example code to work.

    If `namespace` or any other chart value is changed, you need to update the code samples accordingly.


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

You can see the details of `AgentDeployment` you just created. 

```shell
kubectl get agentdeployments test-deploy-1
```

### Commit the code changes

There are two ways to update agent deployments:

1. Manually create a `PodFunctionBuild`.
2. Update code changes through `tuna-fusion-gitops-server`.

Both solutions will trigger build pipeline to run automatically. As creating `PodFunctionBuild` for each update is tedious, we recommend using `tuna-fusion-gitops-server` to update code changes.

#### Using virtual Git repository server

To push updates using `git` client, we have to add a git remote first:

```shell
git remote add test-deploy-1 http://tuna-fusion-gitops-server.default.svc.cluster.local/repositories/namespacs/default/agents/test-deploy-1.git
```

!!! warning

    `tuna-fusion-gitops-server` won't persist the code data you commit. In fact, those updates will be discarded right after a git push is finished.
    
    Use virtual repositroies solely for CI pipelines not for traiditonal source code management.



After a `remote` has been set, you can commence your first `push` to the virtual repository:

```shell
git add .
git commit -am 'feat: first version of hello world'
git push test-deploy-1
```

During the push, `tuna-fusion` will:

1. Fetch updated git objects and create a snapshot archive of latest commit.
2. Trigger a single execution of build pipeline. 
3. Livestream the build logs to the user.


#### Alternative way: Create `PodFunctionBuild` manually

Example resource manifest:

```yaml title="pod-function-build-1.yaml" linenums="1"
apiVersion: fusion.tuna.ai/v1
kind: PodFunctionBuild # (1)
metadata:
  name: test-pod-function-build-1 # (2) 
spec:
  podFunctionName: test-deploy-1 # (3)
  sourceArchive:
    httpZipSource:
      url: https://gist.github.com/RobinQu/f8f755f8bb0807ad564662c637175d23/archive/e82defc2b563fbc9f36b49a94cc3b8b80e5be689.zip # (4)
      sha256Checksum: 5aa97e2c44e86a9993ba3f4a450847b8c6912439bb83b1e1037ac9a5408df65f # (5)
```

1. `PodFunctionBuild` is a resource that triggers build process.
2. Resource names should be unique across all builds.
3. Reference the `PodFunction` we created earlier.
4. Source archive is provided via HTTP URL. It should be accessible for `tuna-fusion` to download.
5. SHA256 checksum for this URL. Build will fail if checksum failed.


### Check status of `test-deploy-1` 

After the push is processed, you can check the status of `AgentDeployment` by running:

```shell
kubectl get ad test-deploy-1
```

Here `ad` is abbreviation of `AgentDeployment`, and `test-deploy-1` is the name of the `AgentDeployment` resource. In the `CURRENTBUILD` column you should see latest build in status of success. 

Before continue to next part, let's write down the link in `URL` column of deployed agent, which is the base URL for A2A Client.


### Visiting your first agent

To interact with `test-deploy-1` agent, you can use [a2a-inspector](https://github.com/a2aproject/a2a-inspector) utility. Or just write a simple python script to send messages.

In the following code, we demonstrate how to access `test-deploy-1` using `a2a-python` SDK.

```python title="client.py" linenums="1"
import logging

from typing import Any
from uuid import uuid4

import httpx

from a2a.client import A2ACardResolver, A2AClient
from a2a.types import (
    AgentCard,
    MessageSendParams,
    SendMessageRequest,
    SendStreamingMessageRequest,
)


async def main() -> None:
    PUBLIC_AGENT_CARD_PATH = '/.well-known/agent.json'

    logging.basicConfig(level=logging.INFO) # (1)
    logger = logging.getLogger(__name__)  # Get a logger instance

    base_url = 'http://tuna-fusion-executor.default.svc.cluster.local/a2a/namespaces/default/agents/test-deploy-1' # (2)

    async with httpx.AsyncClient() as httpx_client:
        # Initialize A2ACardResolver
        resolver = A2ACardResolver(
            httpx_client=httpx_client,
            base_url=base_url, # (3)
        )

        try:
            logger.info(
                f'Attempting to fetch public agent card from: {base_url}{PUBLIC_AGENT_CARD_PATH}'
            )
            final_agent_card_to_use = (
                await resolver.get_agent_card()
            )  # Fetches from default public path
            logger.info('Successfully fetched public agent card:')
            logger.info(
                final_agent_card_to_use.model_dump_json(indent=2, exclude_none=True)
            )
            logger.info(
                '\nUsing PUBLIC agent card for client initialization (default).'
            )
        except Exception as e:
            logger.error(
                f'Critical error fetching public agent card: {e}', exc_info=True
            )
            raise RuntimeError(
                'Failed to fetch the public agent card. Cannot continue.'
            ) from e

        client = A2AClient(
            httpx_client=httpx_client, agent_card=final_agent_card_to_use
        )
        logger.info('A2AClient initialized.')

        send_message_payload: dict[str, Any] = {
            'message': {
                'role': 'user',
                'parts': [
                    {'kind': 'text', 'text': 'how much is 10 USD in INR?'}
                ],
                'messageId': uuid4().hex,
            },
        }
        request = SendMessageRequest( # (4)
            id=str(uuid4()), params=MessageSendParams(**send_message_payload)
        )

        response = await client.send_message(request)
        print(response.model_dump(mode='json', exclude_none=True))

        streaming_request = SendStreamingMessageRequest(
            id=str(uuid4()), params=MessageSendParams(**send_message_payload)
        )

        stream_response = client.send_message_streaming(streaming_request)

        async for chunk in stream_response: # (5)
            print(chunk.model_dump(mode='json', exclude_none=True))


if __name__ == '__main__':
    import asyncio
    asyncio.run(main())
```

1. Configure logging to show INFO level messages
2. The URL we take from `test-deploy-1`'s status fields in last step
3. Initialize a client with the base URL of `test-deploy-1`
4. Prepare a streaming request
5. Iterating chunk data of the streaming response using `async for loop`

## Summary

In this tutorial, we have completed the round trip of deploying an application using `tuna-fusion`. Although the `hello-world` agent is a simple one, it demonstrates the basic usage of `tuna-fusion`. Of course, you can discover more complicated samples in [tuna-fusion-agent-samples](https://github.com/tuna-headquater/tuna-fusion-agent-samples) or [a2aproject/a2a-samples](https://github.com/a2aproject/a2a-samples).





