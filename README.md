# tuna-fusion

Opensource implementation of Google ADK runtime and more.

# Highlights

* Runtime for any A2A protocol agents
  * Based on [fission.io](https://fission.io/) `tuna-fusion` guarantees on-demand scalability across cluster.
  * Builtin observability.
* Runtime services for Google ADK:
  * Builtin services including `MemroyService`, `ArtifactService`, `SessionService`.
  * Public agent services via A2A protocol 
* More than a compatible runtime:
    * Composable data ingestion pipeline for memory input.  
    * GitOps CI/CD workflow for agents and tools.
    * Dashboard for centralized managements.


# Roadmap

* 1.0 - A2A support 
  * A2A runtime
    * [ ] A2A fission env
    * service implementation
      * [ ] TaskStore: PgSQL backed task store
      * [ ] PushNotifier: MQ based task notification
      * [ ] QueueManager: Kafka based event queue
    * [ ] Observability: logs and traces
  * Management server
    * [ ] Namespace
    * [ ] Agent catalogue
    * [ ] Git repo server
    * Tekton pipelines
      * [ ] Build and apply
    * [ ] Dashboard UI
* 1.1 - Tool support
  

# User Guide

## Installation

## Deploy an agent

## Deploy a MCP server

