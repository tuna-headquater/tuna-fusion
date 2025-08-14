# Concepts

For `tuna-fusion` users, only a few concepts are needed to understand how `tuna-fusion` works.

``` mermaid

flowchart TB
    id1[Code push by developers]
    id2[Agent access by users]
    id3[Fusion executor]
    id4[AgentDeployment]
    
    subgraph "AgentEnvrinoment"
    id4
    end
    
    id1--"Update source code and trigger build"-->id4
    id4--"Update deployment archive"-->id3
    id2 --"HTTP access"--> id3
```

## `AgentEvnrinoment`


`AgentEnvinoment` is a resource that defines the environment for an agent. It contains language-specific parts of `tuna-fusion` runtime so that the system knows how to build agent source code and how to bootstrap the agent.

Currently `tuna-fusion` only support agents written with Python A2A SDK. Java support is coming soon.

`AgentEnvirnoment` resource will be translated to a group Kubernetes Pods running built-in services which are waiting for RPC calls using A2A protocol.


## `AgentDeployment`

`AgentDeployment` is a resource that defines a single agent repository. Developers are instructed to provide information about the agent in `AgentCard` format.

A virtual Git repository is created for each `AgentDeployment` resource, so that developers could update source code and trigger a build. The URL for the repository has such pattern:

```text
http://{gitops-server-url}/repositories/namespaces/{namespace}/agents/{agentDeploymentName}.git
```

Many other aspects of agent are configured as well. For examples: 

* configurations to initialize A2A executor, including `TaskStore` and `QueueManagerProvider`.
* entrypoint of deployed source code.
* the branch in which Git server is listening update
* ...


## `Fusion executor`

`executor` is a built-in gateway services to access deployed workloads like agents and tools. In terms of agents, the `executor` service talk in standard A2A protocol. MCP protocol is also supported for deployed tools. What's more, you can opt to expose your agents using MCP protocol as well.

So `exectuor` is the unified entrance for agent hosts like `CherryStudio` or any other the downstream applications.


## Other concepts

* `MCPServer` resource:
  * Run existing MCP server provided by a `npm` or `pypi` package.
  * Define source code of `FastMCP` server components and `tuna-fusion` would provision necessary resources to run as a MCP server.
  * Define resources, prompts and tools statically and `tuna-fusion` would create a `FastMCP` server dynamic which will then bootstrapped with necessary resource.
* Underlying resources:
    * `SourceArchive`: Original source code provided by developers.  It could be a Zip archive, or a folder on shared filesystem.
    * `DeploymentArchive`: The resulting archive of provided source code after build process. It could only be a Zip archive in deployments folder.
    * `PodPool`: It defines how to maintain Pod resources serve workload defined by `PodFunction` and how to build deployment archive for a `PodFunction`.
    * `PodFunction`: It contains runnable deployment build using runtime image specified in `PodPool`.
    * `PodFunctionBuild`: It triggers a CI build for target resources like `AgentDeployment` and `MCPServer`. So it has to contains information about how to get the source code and the build recipes. 