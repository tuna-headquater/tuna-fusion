# Architecture
To understand how `tuna-fusion` work, some fundamental design patterns are introduced:

* Operator pattern in `Kubernetes`
* Gateway pattern


## Custom resources

| CRD               	| Usage                                          | State diagram                                     |
|-----------------------|------------------------------------------------|---------------------------------------------------|
| AgentDeployment		| Manages lifecycle for agent repository         | ![Agent Deployment](_assets/agent_deployment.svg) |
| AgentBuild			| Manages a single build run of agent repository | ![AgentBuildRun](state_diagramgent_build_run.svg) |
| MCPServerDeployment	| Manages a lifecycle for MCP server             |                                                   |
| MCPServerBuild		| Manages a single build run of MCP server       |                                                   |
| HTTPToolDeplyoment	|                                                |                                                   |
| AgentResourceBinding	| Manages relationships between a single agent and other resources | 											                                       |
| AgentMemory			| Manages agent memory resource					 | 															                                   | 


## Workflow

* Admission control
    * For `AgentDeployment`, check data integrity
    * For `AgentBuild`:
        * check data integrity
        * check `ownerReferences` is properly set, and referenced `AgentDeplyoment` actually exists.
        * check `currentBuilds[buildTarget]` of `AgentDeployment`
* Timer loop for pending `AgentBuild`:
    * Fetch `AgentDeployment` via `ownerRefernecees`
    * Create `Job` as child resource, with time limit.
    * Update `phase` to `scheduled`
* Timer loop for non-terminal `AgentBuild`
    * Fetch related `Job`
    * Check job state:
        * if `Job` is `active` or `ready`, update `phase` of current `AgentBuild` to `running`
        * if `Job` is `failed`, update `phase` of current `AgentBuild` to `failed`
        * if `Job` is `succeeded`, update `phase` of current `AgentBuild` to `succeeded`
* Field event handler of `phase` of `AgentBuild`
    * If `phase` is terminal state, update `currentBuilds[buildTarget]` to `None`


## Pipeline

```plantuml
@startuml
actor "Developer" as dev
participant "tuna-fusion-server" as fusion
participant "tuna-fusion-operator" as operator
participant "k8s-apiserver" as apiserver
participant "k8s-webhook" as webhook
participant "fission" as fission
 
 
 
== Staging ==
activate fusion
dev->fusion: Git push to feature branch
fusion->fusion: Save repo snapshot

group AgentDeployment (PendingStaging->StagingDeployed)
fusion->apiserver: Create AgentDeployment CR
apiserver-->fusion
fusion-->dev: Return AgentDeployment object


activate operator

apiserver->webhook: trigger
webhook -> operator: notify


group AgentBuildRun lifecycle
operator->apiserver: Create AgentBuildRun CR
apiserver->webhook: trigger
webhook->operator: notify

create pod
operator -> pod: watch for job completion
pod -> fission: fn create or update
fission --> pod: return logs
pod -> operator
destroy pod

operator-->webhook
webhook-->apiserver
apiserver-->operator
end


operator --> webhook
webhook --> apiserver
end
deactivate operator

deactivate fusion


== Release ==

dev->fusion: Git push tags

activate fusion

group AgentDeployment (PendingProduction->ProductionDeployed)
fusion -> apiserver: Patch AgentDeployment CR
fusion <-- apiserver
fusion --> dev


apiserver -> webhook: trigger

webhook->operator: notify
activate operator
operator->apiserver: Create AgentBuildRun CR
apiserver-->operator
deactivate operator
webhook-->apiserver

apiserver->webhook: trigger AgentBuildRun CreateEvent
webhook->operator: notify AgentBuildRun CreateEVent

activate operator

group AgentBuildRun lifecycle
create pod
operator->pod: watch for job completion
pod->fission: create or update fn in production namespace
fission-->pod
destroy pod
end

deactivate operator
operator-->webhook
webhook-->apiserver


end


deactivate fusion



@enduml

```

## Component Diagram

```plantuml
@startuml
!include <C4/C4_Container>
!include <C4/C4_Component>


Person(dev, "Developer")
Person(user, "User")

Container_Ext(agent, "Agents by Developers", "Python or Java")
Container_Ext(http_tool, "Http tools declared by Developers", "OAS schema")
Container_Ext(mcp_tool, "MCP tools by Developers", "npx or pip package")
Container_Ext(db, "DB Storage", "RDB(PgSQL, MySQL) / AWS DynamoDB / AlibabaCloud TableStore")
Container_Ext(s3, "S3 Compatible Storage")


System_Boundary(fusion, "tuna-fusion") {
    Container(gitops, "GitOps Server", "Java")
    Container(apiserver, "Kubernetes API Server")
    Container(a2a_runtime, "A2A runtime", "Python")
    Container_Boundary(crd, "CRD operators", "Java") {
        Component(tuna_pool_driver, "Tuna Pooling Driver")  
        Component(k8s_deploy_driver, "K8S Deploy Driver")
        Component(aws_lambda_driver, "AWS Lambda Driver")
    }
    
    Container_Boundary(executor, "Resource Executor") {
        Component(tuna_pool_executor, "Tuna Pooling Executor")
        Component(k8s_deploy_executor, "K8S Deploy Executor")
        Component(aws_lambda_executor, "AWS Lambda Executor")
        Component(http_tool_executor, "OAS HTTP Tool Executor")
    }
    
    Container(gateway, "Gateway server")    
    Rel(gitops, apiserver, "Publish CRDs")
    Rel(apiserver, crd, "Notify")
    Rel(crd, a2a_runtime, "Configure")
}


Rel(tuna_pool_driver, "agent", "Bootstrap")
Rel(k8s_deploy_driver, "agent", "Bootstrap")
Rel(aws_lambda_driver, "agent", "Bootstrap")

Rel(aws_lambda_driver, "mcp_tool", "Bootstrap")
Rel(k8s_deploy_driver, "mcp_tool", "Bootstrap")
Rel(tuna_pool_driver, "mcp_tool", "Bootstrap")

Rel(agent, a2a_runtime, "Consume")
Rel(dev, gitops, "Code push")
Rel(dev, apiserver, "Manage")

Rel(user, gateway, "Use via A2A + MCP Protocol")
Rel(gateway, executor, "Use")

Rel(executor, agent, "Use via A2A Protocol")
Rel(executor, mcp_tool, "Use via MCP Protocol")
Rel(executor, http_tool, "Load")


Rel(gitops, s3, Upload archives)
Rel(executor, s3, Use archives)

Rel(a2a_runtime, db, "Use")

@enduml
```

## Domain models

```plantuml
@startuml
set namespaceSeparator none


package ai.tuna.fusion.kubernetes.crds {
    class AgentCatalogue {
        String uniqueIdentifier
    }
    class AgentDeployment {
        String uniqueIdentifier
        String environmentName
    }
    class AgentBuild {
        String buildScript
        String builderImage
        String serviceAccountName
        ...
    }
    class AgentEnvironment {
        String runtimeImage
        String builderImage
        int poolSize
    }
    AgentDeployment -- AgentEnvironment
    AgentDeployment *-- AgentBuild
}


@enduml
```