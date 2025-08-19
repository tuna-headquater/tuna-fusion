{{/*
Expand the name of the chart.
*/}}
{{- define "tuna-fusion-operator.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "tuna-fusion-operator.fullname" -}}
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
{{- define "tuna-fusion-operator.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "tuna-fusion-operator.labels" -}}
helm.sh/chart: {{ include "tuna-fusion-operator.chart" . }}
{{ include "tuna-fusion-operator.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "tuna-fusion-operator.selectorLabels" -}}
app.kubernetes.io/name: {{ include "tuna-fusion-operator.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "tuna-fusion-operator.operatorServiceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "tuna-fusion-operator.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}


{{- define "tuna-fusion-operator.runtimeServiceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- printf "%s-%s" (include "tuna-fusion-operator.fullname" .) "runtime-sa" }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}


{{- define "tuna-fusion-operator.clusterRoleName" -}}
{{- if .Values.clusterRole.create }}
{{- default (include "tuna-fusion-operator.fullname" .) .Values.clusterRole.name }}
{{- else }}
{{- default "default" .Values.clusterRole.name }}
{{- end }}
{{- end }}


{{- define "tuna-fusion-operator.configmapName" -}}
{{- default (include "tuna-fusion-operator.fullname" .) .Values.configmap.name }}
{{- end }}

{{- define "tuna-fusion-operator.archivePvc" -}}
{{- default (include "tuna-fusion-operator.fullname" .) .Values.archivePvc.name }}
{{- end}}
