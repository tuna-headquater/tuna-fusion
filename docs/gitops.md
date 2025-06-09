# GitOps Architecture

## Custom resources

| CRD             | State diagram                                             |
|-----------------|-----------------------------------------------------------|
| AgentDeployment | ![Agent Deployment](./state_diagram/agent_deployment.svg) |
| AgentBuildRun   | ![AgentBuildRun](./state_diagram/agent_build_run.svg)     |


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