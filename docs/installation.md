# Installation `tuna-fusion`


## Prerequisites

First, ensure a working Kubernetes cluster is available. And `kubectl` and `helm` are installed locally.


### Kubernetes cluster

!!! info
    Kubernetes 1.27 or higher is required



If you are developing locally, consider using following tools:


* [orbstack](https://github.com/orbstack/orbstack/), recommended for macOS users.
* [minikube](https://minikube.sigs.k8s.io/docs/start/), recommended for users of other operating systems.


### `kubectl` CLI

`Kubectl` is a command line interface for running commands against Kubernetes clusters, visit [kubectl](https://kubernetes.io/docs/tasks/tools/#kubectl) docs to see how to install it.

Please make sure your `.kubeconfig` file is properly configured. We can quickly verify by checking Kubernetes version:

```shell
kubectl version
```

### `Helm` CLI

`Helm` is the package manager for Kubernetes. It's the default way to install `tuna-fusion` to a Kubernetes cluster. 

If you already use helm, you can skip this chapter.

!!! info
    Helm V3 is required.

Please consult Helm's [official documentations](https://helm.sh/docs/intro/install/) about how to install it.

You can check your installation by running:

```shell
helm version
```

## Installing using `helm`

!!! info

    The Helm chart of `tuna-fusion` is located at `tuna-fusion-charts` folder of [tuna-fusion](https://github.com/tuna-headquater/tuna-fusion) repository. In the following section, we will install the chart locally without using a chart repository. We will publish to a public chart repository in the future.

### Namespaced scope installation 

For installation with default configurations, just run:

```shell
helm upgrade --install -n tuna-fusion-system --create-namespace global-default ./tuna-fusion-charts
```

With default configurations, `tuna-fusion` will: 

* Watch for related CRs in the namespaces `tuna-fusion` is installed. In this case, it's the namespace with name of `tuna-fusion-system`.
* Require only `Role` and `RoleBinding` granted to `ServiceAccount` in order to handle access to CRs in the same namespace.

If related CRs would go to other namespaces, you can install `tuna-fusion` with following configurations: 

```shell
helm upgrade ns-limited-test-1 ./tuna-fusion-charts \
  --install -n tuna-fusion-system \
  --create-namespace \
  --set global.clusterScoped=false \
  --set "global.additionalWatchedNamespaces={test-ns-1,test-ns-2}"
```

With such configurations, `tuna-fusion` will:

* Watch for related CRs in `test-ns-1` and `test-ns-2`
* Won't interfere with other `tuna-fusion` instances in the same cluster
* Require `ClusterRole` and `ClusterRoleBinding` granted to `ServiceAccount` in order to handle access to CRs in multiple namespaces.

!!! info
    It's recommended to deploy Agent resources to a different namespace from the one `tuna-fusion` is installed. Just assign more namespaces to `additionalWatchedNamespaces`. But it's your responsibility to [ensure the permissions](user-guide/rbac-permissions.md) granted to `ServiceAccount` in namespaces you specified. 


Since we are using namespace scoped installations, multiple instance of `tuna-fusion` could co-exist with each other.  You can install another release watching `test-ns-3` only:

```shell
helm upgrade --debug ns-limited-test-2 . \
  --install -n tuna-fusion-system \
  --create-namespace \
  --set global.clusterScoped=false \
  --set "global.additionalWatchedNamespaces={test-ns-3}"
```

### Cluster scoped installation 

In a more aggressive manner, you can install `tuna-fusion` in cluster scoped mode:

```shell
helm upgrade --install -n tuna-fusion-system --create-namespace global-default ./tuna-fusion-charts --set global.clusterScoped=true 
```

With such configurations, `tuna-fusion` will:

* Watch for related CRs in all namespaces
* Require `ClusterRole` and `ClusterRoleBinding` granted to `ServiceAccount` in order to handle access to CRs in multiple namespaces.
* Conflict with other installed `tuna-fusion` instances.


!!! warning

    Cluster scoped installation is not recommended for poduction use. One should always limit the scope of watched resources. 


## Verify installation

Please check all Pods required by `tuna-fusion ` are running. Assuming `tuna-fusion` is installed in namespace `tuna-fusion-system`, you can run:

```shell
% kubectl get pods -n tuna-fusion-system
```

Example output would be:

```text
NAME                                                           READY   STATUS    RESTARTS   AGE
ns-limited-test-1-tuna-fusion-executor-84fb9fff84-9h8fc        1/1     Running   0          20h
ns-limited-test-1-tuna-fusion-gitops-server-545b8ccd9b-zgf96   1/1     Running   0          20h
ns-limited-test-1-tuna-fusion-operator-7c8c974fc-prwhc         1/1     Running   0          20h
ns-limited-test-2-tuna-fusion-executor-596668d48c-j6mwx        1/1     Running   0          20h
ns-limited-test-2-tuna-fusion-gitops-server-7b9cbd4b76-2gg86   1/1     Running   0          20h
ns-limited-test-2-tuna-fusion-operator-57bb98bddd-6gcg7        1/1     Running   0          20h
```

