# Architecture

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