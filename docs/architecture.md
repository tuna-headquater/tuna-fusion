# Architecture

## Component Diagram

```plantuml
@startuml
!include <C4/C4_Container>
!include <C4/C4_Component>


Person(dev, "Developer")
Person(user, "User")

System_Boundary(fusion, "tuna-fusion") {
    Container(gitops, "GitOps Server", "Java")
    Container(apiserver, "Kubernetes API Server")
    Container(a2a_runtime, "A2A runtime", "Python")
    Container_Boundary(crd, "CRD operators", "Java") {
        Component(tuna_pool_driver, "Tuna Pooling Driver")  
        Component(k8s_deploy_driver, "K8S Deploy Driver")
        Component(aws_lambda_driver, "AWS Lambda Driver")
    }
    Component(gateway, "Gateway Server")    
    Rel(gitops, apiserver, "Publish CRDs")
    Rel(apiserver, crd, "Notify")
    Rel(crd, a2a_runtime, "Configure")
       
}

Container_Ext(agent, "Agents by Developers", "Python or Java")
Container_Ext(http_tool, "Http tools declared by Developers", "OAS schema")
Container_Ext(mcp_tool, "MCP tools by Developers", "npx or pip package")



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
Rel(gateway, agent, "Use via A2A Protocol")
Rel(gateway, mcp_tool, "Use via MCP Protocol")
Rel(gateway, http_tool, "Load")
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