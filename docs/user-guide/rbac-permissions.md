# RBAC permissions

Extra permissions are needed for `tuna-fusion` to work.

* `tuna-fusion-operator` itself requires permissions to access resources in managed namespaces.
* `tuna-fusion-executor` requires permissions to readable access to resources in Kubernetes core API, and RW access to Pods in managed namespaces.
* Builder jobs spawned by `tuna-fusion-operator` require RW access to `PodFunctionBuild` to update build progress.
* `tuna-fusion-gitops-server` requires permissions to read access to Pods and Pod logs, and RW access to `PodFunctionBuild` to trigger build process.

!!! info

    If you are installing using official Helm chart with default values, RBAC permissions should be automatically handled. This document is for better understanding of RBAC permissions used by `tuna-fusion`.

## Permissions manifest

For `tuna-fusion` to work, RBAC permissions are created in Helm chart by default. Assuming installed namespace is `tuna-fusion-system`, reference definitions are following: 

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: tuna-fusion-cluster-role
rules:
  # Framework: runtime observation of namespaces & CRDs (addition/deletion).
  - apiGroups: [ apiextensions.k8s.io ]
    resources: [ customresourcedefinitions ]
    verbs: [ list, watch ]
  - apiGroups: [ "" ]
    resources: [ namespaces ]
    verbs: [ list, watch ]

  # Framework: admission webhook configuration management.
  - apiGroups: [ admissionregistration.k8s.io/v1, admissionregistration.k8s.io/v1beta1 ]
    resources: [ validatingwebhookconfigurations, mutatingwebhookconfigurations ]
    verbs: [ create, patch ]

  # Application: read-only access for watching cluster-wide.
  - apiGroups:
      - fusion.tuna.ai
    resources:
      - podpools
      - podpools/status
      - podfunctions
      - podfunctions/status
      - podfunctionbuilds
      - podfunctionbuilds/status
      - agentenvironments
      - agentenvironments/status
      - agentdeployments
      - agentdeployments/status
    verbs:
      - "*"

  # Framework: posting the events about the handlers progress/errors.
  - apiGroups: [ "" ]
    resources: [ events ]
    verbs: [ create ]

  - apiGroups: [ "*" ]
    resources: [ pods, deployments, services, jobs, configmaps ]
    verbs: [ "*" ]

--- 
apiVersion: v1
kind: ServiceAccount
metadata:
  name: tuna-fusion-sa
  namespace: tuna-fusion-system
automountServiceAccountToken: true

---

kind: ClusterRoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: $ns-fusion-builder-binding
subjects:
  - kind: ServiceAccount
    name: tuna-fusion-sa
    namespace: tuna-fusion-system
roleRef:
  kind: ClusterRole
  name: $cluster_role_name
  apiGroup: rbac.authorization.k8s.io
```

!!! warning

    If `additionalNamespaces` are configured during installation, additional `ClusterRoleBinding`s should be created for service account in target namespaces.


## Overriding service account for builder and runtime Pods 

If you deployment requires extra accesses that are not configured by Helm chart, you can override the service account name for builder and runtime Pods to gain more privileges.

There are multiple ways to configure service account for builder and runtime Pods.

First of all, the default service account names are determined by application properties for `tuna-fusion-operator`. You can find more about this in [Application configuration reference](../reference/application-configuration-reference.md).

And in `AgentEnvironment` resource, you can assign the service account for runtime Pods and builder Pods.


```yaml
apiVersion: fusion.tuna.ai/v1
kind: AgentEnvironment
metadata:
  name: agent-env-1
spec:
  driver:
    type: PodPool
    podPoolSpec:
      archivePvcName: shared-archive-pvc
      poolSize: 3
      builderImage: ghcr.io/tuna-headquater/fusion-a2a-env-builder:1754752208
      runtimeImage: ghcr.io/tuna-headquater/fusion-a2a-fastapi-runtime:1754752208
      builderPodServiceAccountName: fusion-builder-sa
      runtimePodServiceAccountName: fusion-runtime-sa
      runPerPod: 10
      ttlPerPod: 86400

  executor:
    baseUrl: http://localhost:8802
```
