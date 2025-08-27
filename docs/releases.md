# Releases

## v0.1.1

* Enhancements:
   * `gitops-server` supports Git submodules
   * Make fields optional for `AgentEnvironment` and `AgentDeployment`
* Samples are updated and migrated to [tuna-fusion-agent-samples](https://github.com/tuna-headquater/tuna-fusion-agent-samples) 



## v0.1.0

First `GA` release. It's targeted as MVP of `tuna-fusion`. Main features are:

1. CRDs and controllers: `AgentDeployment`, `AgentEnvironment`, `PodPool`, `PodFunction` and `PodFunctionBuild`.
2. Basic Gitops pipeline for `PodFunctionBuild`.
3. Basic gateway server for A2A protocol.

