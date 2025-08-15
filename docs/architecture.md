# Architecture

On this page, let's dive deeper about the architecture design and implementations.

## Principals

Remember the challenges about agentic applications in production in the landing page? To address these challenges, we need to design a new runtime framework. I took a lot of time thinking about principles and guidelines about how the architecture should be designed. 

Let's list a few questions:

* Should this runtime system be bundled with application frameworks? If not, what experience we would like to offer to developers with existing frameworks like LangChain, Google ADK?
* What programing languages should be used to develop such runtime frameworks?
* What programing languages should be supported by such runtime frameworks for agent development?
* What's the boundary of such runtime frameworks? Should it cover the management of all aspect of an agentic application?
* ...

It's quite difficult to have correct answers to all these questions. As agentic applications are evolving rapidly both in academic research and industry practice. People constantly come up with new ideas about what agents can be done, and how agents can be implemented. For example, before `OpenAI` introduces computer-use tools, we would think about agent tools short-lived computation tasks. And in recent days, agentic applications go beyond desktop and cloud. We can even find many attempts of agentic applications in robotics system using VLM and VLA models.

In the end, I would like carry out some opinionated guidelines that to narrow down problem set of `tuna-fusion` and make the project more agile to run. Primary guidelines are following:

* `tuna-fusion` focus on LLM-driven agentic applications for general purpose on the cloud.
* Agent frameworks agostic: `tuna-fusion` won't care about how developers write the code as long as they can export the implementation using `AgentExecutor` adaptor layer from [A2A Library](https://github.com/a2aproject/a2a-python). By leveraging A2A protocol, we also gain the semantics of sessions and tasks, `tuna-fusion` will take care of these runtime data. 
* Java and Python as first-class languages: `tuna-fusion` should support Java and Python as first-class languages both for developing `tuna-fusion` itself and developing agentic applications deployed to `tuna-fusion`.
* `MCP` for connecting the ecosystem: `tuna-fusion` introduces the adapters to use exiting MCP server packages and the workflows to develop one from scratch. Nowadays, people connect classical agent tools, like web search and data interpreters, as well as memory system, to agent applications using MCP protocol. I think it's an obvious trend to replace vendor-specific integrations with MCP server implementations. 


## The Diagrams

### System context diagram

```plantuml
@startuml
!include <C4/C4_Container>


Person(dev, "Developer")
Person(user, "User")

System(fusion, "tuna-fusion")
System_Ext(external_systems, "External systems", "Exposing exisiting HTTP APIs")
System_Ext(mcp_tools, "Existing MCP tools", "Exposing MCP tools using SSE / StreamableHTTP")
System_Ext(memory, "Agent memory system", "Vector DBs, Graph DBs, Relation DBs, ...")
SystemDb_Ext(prompts, "Prompt datasources", "Configmaps, Relations DBs, ...")

Rel(fusion, external_systems, "Integrate as Agent Tools")
Rel(fusion, mcp_tools, "Integrate as Agent Tools")
Rel(fusion, memory, "Connect")
Rel(fusion, prompts, "Integrate as promtp resources")

Rel(dev, fusion, "Compose agents")
Rel(user, fusion, "Use agents")

@enduml
```


### Container diagram

```plantuml
@startuml
!include <C4/C4_Container>

Person(dev, "Developer")
Person(user, "User")

Container_Ext(external_system, "External system", "HTTP APIs")
Container_Ext(mcp_tool, "Exisiting MCP tools")
Container_Ext(db, "DB Storage", "RDB(PgSQL, MySQL) / AWS DynamoDB / AlibabaCloud TableStore")
ContainerDb_Ext(prompts, "Prompt datasources", "Configmaps, Relations DBs, ...")

System_Boundary(fusion, "tuna-fusion") {
    Container(apiserver, "Kubernetes API Server")
    Container(dashboard, "tuna-fusion-dashboard", "Python")
    Container(gitops, "tuna-fusion-gitops-server", "Java")
    Container(operator, "tuna-fusion-operator", "Java")
    Container(executor, "tuna-fusion-executor", "Java")
    Container(agent_pods, "Pods running A2A runtime", "Python")
    Container(tool_pods, "Pods running MCP tools", "Python")
    Rel(gitops, apiserver, "Publish PodFunctionBuild CR")
    Rel(apiserver, operator, "Notify")
    Rel(operator, agent_pods, "Provision")
    Rel(operator, tool_pods, "Provision")
    Rel(executor, agent_pods, "Use")
    Rel(executor, tool_pods, "Use")
    Rel(dashboard, apiserver, "Manage CRs")
    Rel(executor, prompts, "Use")
    Rel(executor, mcp_tool, "Use with MCP SSE/StreamableHTTP")
    Rel(executor, external_system, "Use with HTTP APIs")
    Rel(agent_pods, db, "Use")
}

Rel(dev, dashboard, "Manage CRs")
Rel(dev, gitops, "Git push")
Rel(user, executor, "Use wtih A2A and MCP protocols")

@enduml
```

### Component diagram

#### `tuna-fusion-operator`

```plantuml
@startuml
!include <C4/C4_Component>
LAYOUT_LANDSCAPE()

System_Ext(apiserver, "Kubernetes APIServer")

Person(user, "User", "Representing users of kubectl CLI or API access from tuna-fusion-dashboard or tuna-fusion-gitops-server")

Container_Boundary(operator, "tuna-fusion-operator") {
    Component(agent_env, "AgentEnvironmentReconciler")
    Component(agent_env_podpool_dr, "AgentEnvrinonmentPodPoolDR")
    
    
    Component(agent_deploy, "AgentDeploymentReconciler")
    Component(agent_deploy_pod_function_dr, "AgentDeploymentPodFunctionDR")
    
    Component(podpool, "PodPoolReconciler")
    Component(pod_function, "PodFunctionReconciler")
    Component(podpool_depoly, "PodFunctionDeplyomentDR")
    Component(pod_function_build, "PodFunctionBuildReconciler")
    Component(builder_job, "Builder Jobs for PodFunctionBuild")
    
    Rel(agent_env, agent_env_podpool_dr, "reconcile")
    
    Rel(agent_deploy, agent_deploy_pod_function_dr, "reconcile")
    Rel(podpool, podpool_depoly, "reconcile")
}

Rel(user, apiserver, "Submit CRs")

Rel(agent_env_podpool_dr, apiserver, "Create PodPool CR")
Rel(agent_deploy_pod_function_dr, apiserver, "Create PodFunction CR")
Rel(pod_function_build, apiserver, "Create Job resource")


Rel(apiserver, agent_env, "Notify events")
Rel(apiserver, agent_deploy, "Notify events")
Rel(apiserver, podpool, "Notify events")
Rel(apiserver, pod_function, "Notify events")
Rel(apiserver, pod_function_build, "Notify events") 
Rel(apiserver, builder_job, "Create")

@enduml
```

In `tuna-fusion-operator`, we employ the [operator pattern](https://kubernetes.io/docs/concepts/extend-kubernetes/operator/) to manage lifecycle of all kinds of resources. This brings two kinds of important components: `Reconciler` and `DepedentResource`. These two actors come from [Operator frameworks SDK](https://github.com/operator-framework) which is common choice to implement real world operators.

You can learn more about these concepts from here:

* [JOSDK - DependentResource](https://javaoperatorsdk.io/docs/documentation/dependent-resource-and-workflows/dependent-resources/)
* [Tutorial from GO Operator SDK](https://sdk.operatorframework.io/docs/building-operators/golang/tutorial/)


The goals of these components are to:

1. Create, update or delete (if necessary) Kubernetes core resources like Pods, Services, ConfigMaps, Secrets, etc using events from informer APIs.
2. Provide simplified API interface to users. That's user won't care about how the high-level resources like `AgentDeployment`s are handled. They just submit and wait for reconciliation to finish.  


#### `tuna-fusion-executor`

```plantuml
@startuml
!include <C4/C4_Component>
LAYOUT_LANDSCAPE()

System_Ext(apiserver, "Kubernetes APIServer")

Person(user, "User", "Representing users of executor HTTP APIs or A2A clients")


Container_Boundary(executor, "tuna-fusion-executor") {
    Component(a2a, "A2AExectorController", "Webflux Controller", "A2A protocol gateway") 
    Component(mcp, "MCPExectorController", "Webflux Controller", "MCP protocol gateway")
    Component(pod_manager, "PodManager", "K8S Controller", "Provide Pod access table and cleanup orphaned Pods")
    Rel(a2a, pod_manager, "Acquire Pod access")
    Rel(mcp, pod_manager, "Acquire Pod access")
}

Rel(pod_manager, apiserver, "Watch for Pod events")
Rel(user, a2a, "Use")
Rel(user, mcp, "Use")

@enduml
```

`tuna-fusion-executor` is the access layer of workload components like `AgentDeploymnet` and `MCPServer`. And it's the gateway server that exposes both MCP and A2A protocols. The gateway pattern allows us to dynamic choose destination Pods based on resource availability and translate bi-directionally between protocols.

Behind the gateway server, it connects to a selected Pod using appropriate protocol. For example, if you are accessing the A2A endpoint, it will forward A2A JSON-RPC requests.

In the Pods for agents and tools, some frameworks code bridges the user provided logics (in commited source code) and incoming JSON-RPC requests. These frameworks code will be published as a standalone package, but at present it's built into runtime image directly. See source codes at `fission-a2a-env/fastapi-runtime` for more details.

#### `tuna-fusion-gitops-server`

```plantuml
@startuml
!include <C4/C4_Component>
LAYOUT_LANDSCAPE()

System_Ext(apiserver, "Kubernetes APIServer")

Person(user, "User", "Representing agent develpers")

Container_Boundary(gitops, "tuna-fusion-gitops-server") {
    Component(git_server, "Git Repository Server", "Servlet", "Git server to accept Git commands.")
    Component(recieve_hook, "Git Recieve Hook", "", "handle source code update")
    Rel(git_server, recieve_hook, "Trigger")
}

Container_Ext(operator, "tuna-fusion-operator")
Rel(apiserver, operator, "Notify")

Rel(user, git_server, "Code push")
Rel(recieve_hook, apiserver, "Create PodFunctionBuild CR")

@enduml
```

`tuna-fusion-gitops-server` is a virtual Git server for source code deployment. The internal CI pipeline will be triggered to build source code and deploy to a Kubernetes cluster. This saves effort to create complex resources for each build. 

!!! info

    Technically speaking, `tuna-fusion-gitops-server` is an optional sub-system. You can always submit your `PodFunctionBuild` CR to trigger CI pipeline. 


## The Implementations

### Custom resources

| CRD               	        | Description                                                             | State diagram                                             |
|----------------------------|-------------------------------------------------------------------------|-----------------------------------------------------------|
| AgentEnvironment	          | Define how agents should be built and deployed                          | N/A                                                       |                       |                                                                  |                                                         |
| AgentDeployment		          | Define `AgentCard` and other properties of a single agent               | N/A                                                       |
| PodPool	                   | Defines how a group of Pods should be pooled                            | N/A                                                       |
| PodFunction		              | Defines the actual implementation of business units called `Function`s. | N/A                                                       |
| PodFunctionBuild			        | Define the properties of single build                                   | ![PodFunctionBuild](_assets/pod_function_build_state.svg) |
| MCPServer (WIP)            | Defines how should the given MCP server implementations be deployed     | 											                                               |

### Reconciliation


#### AgentEnvironment reconciliation

```mermaid
flowchart
    start((Start))
    finish((Stop))
    subgraph AgentEnvironmentReconcilation
        s8{{Check deletion timestamp}}
        s9["Remove finializer"]
        s10["GC by apiserver"]
        s8 --YES-->s9
        s9 --> s10
        s10 --> finish

        subgraph PodPoolDependentResorce
            s1{{"Check existence of PodPool resource"}}
            s2["Create dependent resoruce: PodPool"]
            s12["Update depentent resource: PodPool"]
            subgraph PodPoolReconcilation
                s4{{Check existence of Deployment resource}}
                s5["Create dependent resource: Deplyoment"]
                s11["Update depentente resource: Deplyoment"]
                s7["Deployment reconcilation"]
                
                s4 --"No"--> s5
                s4 --"Yes"-->s11
                s5 --> s7
                s11 --> s7
                
            end
            
            s1 --"No"--> s2
            s1 --"Yes"-->s12
            s12 --> PodPoolReconcilation
            s2 --> PodPoolReconcilation
        end

        s3["Wait for next reconcilation"]
        PodPoolReconcilation --> s3
        s7 --> s3
        s3 --> s1
        
        s8 --NO--> PodPoolDependentResorce
        PodPoolDependentResorce --> s3
        s3 --> s8
            
        
    end
    start --> s8
    
    
```

#### AgentDeployment reconciliation

```mermaid
flowchart
    start((Start))
    finish((Stop))
    subgraph AgentDeploymentReconcilation
        
        s0{{Check deletion timestamp}}
        s4[Remove finalizer]
        s5[GC by apiserver]
        s0--YES-->s4
        s4-->s5
        
        subgraph PodFunctionDepenenteResource
            s1{{Check exisiting of PodFunction resource}}
            s2["Create dependent resource: PodFunction"]
            s3["Update dependent resource: PodFunction"]
            s1 --Yes--> s3
            s1 --NO--> s2
            s2 --> PodFunctionReconcilation
            s3 --> PodFunctionReconcilation
        end
        s0 --NO--> PodFunctionDepenenteResource
        
        s10["Wait for next reconcilation"]
        PodFunctionDepenenteResource --> s10
        s10 --> s0
        
        
    end
    start --> s0
    s5 --> finish
```


#### PodFunctionBuild reconciliation


```mermaid

flowchart
    start((Start))
    finish((Stop))
    subgraph PodFunctionBuildReconcilation 
        
        s0{{Check expiration of PodFunctionBuild resource}}
        
        s9{{Check deletionTimestamp}}
        s10[Remove finalizer]
        s11[GC by apiserver]
        s9 --YES-->s10
        s10-->s11
        s9 --NO--> s0
        
        subgraph BuilderJobDependentResource
            s1{{Check existing of builder Job}}
            s2["Create dependent resource: Job"]
            s6["Update dependent resource: Job"]
            s12["Job reosurce reconilation"]
            s1 --YES--> s6
            s6 --> s12
            s2 --> s12
            s1 --NO--> s2
        end
        
        subgraph ConfigmapDependentResource
            s3["Check existing of Configmap containing file assets"]
            s4["Create dependent resource: Configmap"]
            s7["Update depenent resource: Configmap"]
            s13["Configmap reconcilation"]
            s3 --NO--> s4
            s3 --YES--> s7
            s4 --> s13
            s7 --> s13
        end

        BuilderJobDependentResource --> ConfigmapDependentResource
        
        
        s5["Wait for next reconcilation"]
        ConfigmapDependentResource --> s5
        
        s8["Delete PodFunctionBuild resource"]
        
        s0 --NO--> BuilderJobDependentResource
        s0 --YES--> s8
        
        s5 --> s9
    end
    
    start --> s9
    s8 --> s9
    s11-->finish
    
```

### Builder Job Pod design

Builder job is created during PodFunctionBuild reconciliation. It's responsible to run builder image where source code archive (`SourceArchive`) submitted by user in `PodFunctionBuild` resource is transformed into consumable artifacts by runtime image, aka `DeployArchive`.


```mermaid
flowchart
    subgraph "Builder POD"
        subgraph "Volumes"
            configmap1["Configmap for workspace file assets mounted at /workspace"]
            configmap2["Configmap for deploy-archive file assets mounted at /patch"]
            configmap3["Configmaps referenced in PodFunction mounted at /configmaps/{namespace}/{configMapName}"]
            secret1["Secrets referenced in PodFunction mounted at /secrets/{namespace}/{configMapName}"]
            pvc1["Shared Archive PVC mouted at /archive"]
        end
        
        builder-container --> configmap1
        builder-container --> configmap2
        builder-container --> configmap3
        builder-container --> secret1
        builder-container --> pvc1

        builder-container --> envs["Injected env variables"]
    end
    
```


### GitOps workflow for CI build

`tuna-fusion` has different workflows for different resources. The following sequence chart depicts how a `PodFunctionBuild` resource is handled in the system. Recipes of other resources are similar.  

```plantuml
@startuml
actor "Developer" as dev
participant "tuna-git-ops-server" as gitops
participant "k8s-apiserver" as apiserver
participant "tuna-fusion-operator" as operator
 

dev -> gitops: Code push

activate gitops

gitops -> gitops: trigger git-receive hook
activate gitops


gitops -> apiserver: create PodFunctionBuild
activate apiserver
apiserver --> gitops
deactivate apiserver

apiserver -> operator: Notify
activate operator
operator -> operator: reconcil PodFunctionBuild
operator --> apiserver
deactivate operator


gitops -> apiserver: watch for PodFunctionBuild
gitops --> dev: Update logs
apiserver --> gitops: watch is finished


deactivate gitops

gitops --> dev: Push is finished
deactivate gitops

@enduml
```

