# GitOps Architecture

## Custom resources

| CRD             | State diagram                                             |
|-----------------|-----------------------------------------------------------|
| AgentDeployment | ![Agent Deployment](./state_diagram/agent_deployment.svg) |
| AgentBuildRun   | ![AgentBuildRun](./state_diagram/agent_build_run.svg)     |


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