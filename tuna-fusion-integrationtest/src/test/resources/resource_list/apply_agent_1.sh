#!/usr/bin/env bash
set -ex

ns=${1}
release_name=${2}
cluster_role_name="$ns"-fusion-full-access-role
timestamp=$(date +%s)
podfunctionbuild_name="test-deploy-1-function-build-${timestamp}"

echo "=========================================="
echo "Namespace: $ns"
echo "Cluster Role Name: $cluster_role_name"
echo "Timestamp: $timestamp"
echo "PodFunctionBuild Name: $podfunctionbuild_name"
echo "Release name for tuna-fusion: $release_name"
echo "=========================================="


cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Namespace
metadata:
  name: $ns
EOF


cat <<EOF | kubectl apply -n "$ns" -f -
apiVersion: fusion.tuna.ai/v1
kind: AgentEnvironment
metadata:
  name: env-1
spec:
  driver:
    type: PodPool
    podPoolSpec:
      archivePvcName: shared-archive-pvc
      poolSize: 3
      builderImage: ghcr.io/tuna-headquater/fusion-a2a-env-builder:1754752208
      runtimeImage: ghcr.io/tuna-headquater/fusion-a2a-fastapi-runtime:1754752208
      builderPodServiceAccountName: fusion-builder-sa
      runPerPod: 100
      ttlPerPod: 86400

  executor:
    baseUrl: http://ns-limited-test-1-tuna-fusion-executor.tuna-fusion-system.svc.cluster.local

---
apiVersion: fusion.tuna.ai/v1
kind: AgentDeployment
metadata:
  name: test-deploy-1
spec:
  agentCard:
    name: hello-agent
    description: an A2A agent that speaks hello
    capabilities:
      streaming: true
      pushNotifications: false
      stateTransitionHistory: false
    defaultInputModes:
      - "text/plain"
      - "application/json"
    defaultOutputModes:
      - "application/json"
      - "image/png"
    skills:
      - description: "hello world"
        id: say_hello
        name: say hello
        tags:
          - hello
    provider:
      organization: tuna
      url: https://github.com/tuna-headquarter
    version: "1.0.0"
  environmentName: env-1
  git:
    watchedBranchName: "refs/heads/main"
  a2a:
    queueManager:
      provider: InMemory
    taskStore:
      provider: InMemory
  entrypoint: app.handle


---

apiVersion: v1
kind: ConfigMap
metadata:
  name: test-deploy-1
data:
  config.json: |-
    {"hello":  "world"}

---

apiVersion: v1
kind: Secret
metadata:
  name: test-deploy-1
type: Opaque
stringData:
  secret.json: |-
    {
      "apikey": "you-guess-it"
    }

EOF




cat << EOF | kubectl apply -n "$ns" -f -
apiVersion: fusion.tuna.ai/v1
kind: PodFunctionBuild
metadata:
  name: $podfunctionbuild_name
spec:
  podFunctionName: test-deploy-1-function
  sourceArchive:
    httpZipSource:
      url: https://gist.github.com/RobinQu/bd00d0ac55f8bbfc269a90cfd04f0512/archive/0f88839098bc83a25f3f1e0e2b48fb509f9ae609.zip
      sha256Checksum: 53f4c76729bca42a5f29139aadc9e4481d0e0743a0c9a5c585cddf824d29c95f

EOF


echo "=========================================="
echo "Wait for PFB to complete"
echo "=========================================="

# Wait for PodFunctionBuild to complete (Succeeded or Failed)
max_attempts=30
attempt=0
while true; do
  status=$(kubectl get podfunctionbuild "$podfunctionbuild_name" -n "$ns" -o jsonpath='{.status.phase}' 2>/dev/null || echo "Unknown")
  echo "PodFunctionBuild status: $status"

  if [[ "$status" == "Succeeded" ]]; then
    echo "PodFunctionBuild succeeded!"
    break
  elif [[ "$status" == "Failed" ]]; then
    echo "PodFunctionBuild failed!"
    kubectl get podfunctionbuild "$podfunctionbuild_name" -n "$ns" -o yaml
    exit 1
  fi

  attempt=$((attempt + 1))
  if [[ $attempt -ge $max_attempts ]]; then
    echo "Timeout waiting for PodFunctionBuild to complete"
    kubectl get podfunctionbuild "$podfunctionbuild_name" -n "$ns" -o yaml
    exit 1
  fi

  echo "Waiting for PodFunctionBuild to complete... (attempt $attempt/$max_attempts)"
  sleep 10
done


echo "=========================================="
echo "Assuming tuna-fusion is installed in tuna-fusion-system namespace...."
echo "Let's fetching agent card!"
echo "=========================================="

curl -v http://"$release_name"-tuna-fusion-executor.tuna-fusion-system.svc.cluster.local/a2a/namespaces/"$ns"/agents/test-deploy-1/.well-known/agent.json




