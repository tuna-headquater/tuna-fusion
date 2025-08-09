{{/*
Expand the name of the chart.
*/}}
{{- define "tuna-fusion-executor.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "tuna-fusion-executor.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "tuna-fusion-executor.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "tuna-fusion-executor.labels" -}}
helm.sh/chart: {{ include "tuna-fusion-executor.chart" . }}
{{ include "tuna-fusion-executor.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "tuna-fusion-executor.selectorLabels" -}}
app.kubernetes.io/name: {{ include "tuna-fusion-executor.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "tuna-fusion-executor.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "tuna-fusion-executor.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}


{{- define "tuna-fusion-executor.configmapName" -}}
{{- default (include "tuna-fusion-executor.fullname" .) .Values.configmap.name }}
{{- end }}


{{- define "tuna-fusion-executor.clusterRoleName"}}
{{- default (include "tuna-fusion-executor.fullname" .) .Values.clusterRole.name }}
{{- end }}


{{- define "tuna-fusion-executor.namespacedRoleName"}}
{{- default (include "tuna-fusion-executor.fullname" .) .Values.namespacedRole.name }}
{{- end }}

