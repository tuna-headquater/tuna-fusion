# tuna-fusion-charts

helm chart for tuna-fusion.


## Quick start

For installation with default configurations, which only allows single instance in one Kubernetes cluster, just run: 

```shell
helm upgrade --install -n tuna-fusion-system --create-namespace global-default . 
```

With default configurations, `tuna-fusion` will 
* watch for related CRs in all namespaces
* require `ClusterRole` and `ClusterRoleBinding` granted to `ServiceAccount` in order to handle access to CRs in all namespaces
* interfere with other `tuna-fusion` instances in the same cluster

For namespace scoped installation, run:

```shell
helm upgrade --debug ns-limited-test-1 . \
  --install -n tuna-fusion-system \
  --create-namespace \
  --set global.clusterScoped=false \
  --set "global.additionalWatchedNamespaces={test-ns-1,test-ns-2}"
```

With such configurations, `tuna-fusion` will
* watch for related CRs in `test-ns-1` and `test-ns-2`
* Won't interfere with other `tuna-fusion` instances in the same cluster

You can install another release watching `test-ns-3` only:
```shell
helm upgrade --debug ns-limited-test-2 . \
  --install -n tuna-fusion-system \
  --create-namespace \
  --set global.clusterScoped=false \
  --set "global.additionalWatchedNamespaces={test-ns-3}"
```