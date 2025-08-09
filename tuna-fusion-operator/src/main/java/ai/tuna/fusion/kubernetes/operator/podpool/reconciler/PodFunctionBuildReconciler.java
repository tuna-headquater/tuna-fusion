package ai.tuna.fusion.kubernetes.operator.podpool.reconciler;

import ai.tuna.fusion.kubernetes.operator.podpool.dr.PodFunctionBuildConfigMapsDependentResource;
import ai.tuna.fusion.kubernetes.operator.podpool.dr.PodFunctionBuildJobDependentResource;
import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildStatus;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildStatus.Phase.Failed;
import static ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildStatus.Phase.Succeeded;

/**
 * @author robinqu
 */
@Slf4j
@Component
@ControllerConfiguration(name = "podFunctionBuildReconciler")
@Workflow(dependents = {
        @Dependent(type = PodFunctionBuildConfigMapsDependentResource.class, name = "pfbConfigMaps"),
        @Dependent(type = PodFunctionBuildJobDependentResource.class, dependsOn = "pfbConfigMaps", name = "pfbJob"),
})
public class PodFunctionBuildReconciler implements Reconciler<PodFunctionBuild>, Cleaner<PodFunctionBuild> {
    public static final String SELECTOR = "fusion.tuna.ai/managed-by-pfb";

    @Override
    public DeleteControl cleanup(PodFunctionBuild resource, Context<PodFunctionBuild> context) {
        return DeleteControl.defaultDelete();
    }

    @Override
    public UpdateControl<PodFunctionBuild> reconcile(PodFunctionBuild resource, Context<PodFunctionBuild> context) {
        var jobResource = context.getSecondaryResource(Job.class)
                .orElse(null);

        if (!Objects.isNull(jobResource)) {
            var fullJob = ResourceUtils.getBatchJob(
                    context.getClient(),
                    jobResource.getMetadata().getName(),
                    jobResource.getMetadata().getNamespace()
            ).orElseThrow();
            var jobStatus = Optional.ofNullable(fullJob.getStatus()).orElse(new JobStatus());
            log.info("[reconcile] Job(namespace={},name={}) has already been created: ready={}, failed={}, active={}, succeeded={}", fullJob.getMetadata().getNamespace(), fullJob.getMetadata().getName(), jobStatus.getReady(), jobStatus.getFailed(), jobStatus.getActive(), jobStatus.getSucceeded());

            var podFunction = PodPoolResourceUtils.getReferencedPodFunction(resource, context.getClient()).orElseThrow(()-> new IllegalArgumentException("PodFunction not found for PodFunctionBuild " + resource.getMetadata().getName()));

            var podFunctionBuildPatch = new PodFunctionBuild();
            podFunctionBuildPatch.getMetadata().setName(resource.getMetadata().getName());
            podFunctionBuildPatch.getMetadata().setNamespace(resource.getMetadata().getNamespace());
            ResourceUtils.addOwnerReference(podFunctionBuildPatch, podFunction, false);

            var status = new PodFunctionBuildStatus();
            // deployArchive field is updated by builder script
            podFunctionBuildPatch.setStatus(status);
            if(Optional.ofNullable(jobStatus.getSucceeded()).orElse(0) >= jobResource.getSpec().getCompletions()) {
                status.setPhase(Succeeded);
                status.setCompletionTime(Instant.now());
            } else if (Optional.ofNullable(jobStatus.getFailed()).orElse(0) > jobResource.getSpec().getBackoffLimit()) {
                status.setPhase(Failed);
                status.setCompletionTime(Instant.now());
            } else if (Optional.ofNullable(jobStatus.getActive()).orElse(0)>0) {
                status.setPhase(PodFunctionBuildStatus.Phase.Running);
            } else  {
                status.setPhase(PodFunctionBuildStatus.Phase.Scheduled);
            }

            getJobPod(
                    context.getClient(),
                    jobResource.getMetadata().getName(),
                    jobResource.getMetadata().getNamespace()
            )
                    .ifPresent(jobPod -> {
                        PodFunctionBuildStatus.JobPodInfo jobPodInfo = new PodFunctionBuildStatus.JobPodInfo();
                        Optional.ofNullable(jobPod.getStatus())
                                        .map(PodStatus::getPhase)
                                                .ifPresent(jobPodInfo::setPodPhase);
                        jobPodInfo.setPodName(jobPod.getMetadata().getName());
                        log.info("[reconcile] JobPodInfo: {}", jobPodInfo);
                        PodPoolResourceUtils
                                .getPodLog(context.getClient(), resource.getMetadata().getNamespace(), jobPodInfo)
                                .ifPresent(jobPodInfo::setLogs);
                        status.setJobPod(jobPodInfo);
                    });

            // Update PodFunction with build info
            var patchPodFunction = new PodFunction();
            patchPodFunction.getMetadata().setName(podFunction.getMetadata().getName());
            patchPodFunction.getMetadata().setNamespace(podFunction.getMetadata().getNamespace());
            var podFunctionStatus = new PodFunctionStatus();
            patchPodFunction.setStatus(podFunctionStatus);
            var buildInfo = new PodFunctionStatus.BuildInfo();
            buildInfo.setName(resource.getMetadata().getName());
            buildInfo.setStartTimestamp(Instant.now().getEpochSecond());
            buildInfo.setUid(resource.getMetadata().getUid());

            Optional.ofNullable(resource.getStatus())
                    .map(PodFunctionBuildStatus::getPhase)
                    .ifPresent(buildInfo::setPhase);


            switch (podFunctionBuildPatch.getStatus().getPhase()) {
                case Succeeded -> {
                    log.info("[reconcile] Patching Succeeded PodFunctionBuild: {}", podFunctionBuildPatch.getMetadata().getName());
                    podFunctionStatus.setCurrentBuild(null);
                    podFunctionStatus.setEffectiveBuild(buildInfo);
                }
                case Failed -> {
                    log.info("[reconcile] Patching Failed PodFunctionBuild: {}", podFunctionBuildPatch.getMetadata().getName());
                    podFunctionStatus.setCurrentBuild(null);
                    podFunctionStatus.setEffectiveBuild(podFunction.getStatus().getEffectiveBuild());
                }
                default -> {
                    log.info("[reconcile] Patching On-going PodFunctionBuild: {}", podFunctionBuildPatch.getMetadata().getName());
                    podFunctionStatus.setCurrentBuild(buildInfo);
                    podFunctionStatus.setEffectiveBuild(podFunction.getStatus().getEffectiveBuild());
                }
            }
            // update PodFunction status
            var updatedPodFunction = context.getClient()
                    .resource(patchPodFunction)
                    .updateStatus();
            log.info("[reconcile] Updated PodFunction.status {}", updatedPodFunction.getStatus());


            if (isExpired(podFunctionBuildPatch)) {
                context.getClient().resource(resource).delete();
                log.info("PodFunctionBuild(namespace={},name={}) has expired and has been deleted", resource.getMetadata().getNamespace(), resource.getMetadata().getName());
                return UpdateControl.noUpdate();
            }

            // update PodFunctionBuild status
            return UpdateControl.patchResourceAndStatus(podFunctionBuildPatch)
                    .rescheduleAfter(getNextCheckDelay(resource));
        }
        log.debug("[reconcile] Job is not created or already finished for PodFunctionBuild: {}", ResourceUtils.computeResourceMetaKey(resource));
        return UpdateControl.<PodFunctionBuild>noUpdate()
                .rescheduleAfter(getNextCheckDelay(resource));

    }

    private Duration getNextCheckDelay(PodFunctionBuild resource) {
        long ttl = Optional.ofNullable(resource.getSpec().getTtlSecondsAfterFinished()).orElse(0L);
        if (ttl <= 0) {
            return Duration.ofHours(1);
        }
        Instant now = Instant.now();
        Instant createTime = Instant.parse(resource.getMetadata().getCreationTimestamp());
        Instant expiryTime = createTime.plusSeconds(ttl);
        long secondsLeft = Duration.between(now, expiryTime).getSeconds();

        if (secondsLeft <= 0) {
            return Duration.ofSeconds(10);
        } else if (secondsLeft < 60) {
            return Duration.ofSeconds(secondsLeft);
        } else {
            return Duration.ofSeconds(30);
        }
    }

    private Optional<Pod> getJobPod(KubernetesClient client, String jobName, String namespace) {
        return client.pods()
                .inNamespace(namespace)
                .withLabel("job-name", jobName)
                .list()
                .getItems()
                .stream()
                .findFirst();
    }

    private boolean isExpired(PodFunctionBuild podFunctionBuild) {
        if (podFunctionBuild.getSpec().getTtlSecondsAfterFinished() == null) {
            return false;
        }
        var phase = podFunctionBuild.getStatus().getPhase();
        if (phase != Succeeded && phase != Failed) {
            return false;
        }
        return Optional.ofNullable(podFunctionBuild.getStatus().getCompletionTime())
                .map(completionTime -> completionTime.plusSeconds(podFunctionBuild.getSpec().getTtlSecondsAfterFinished())
                        .isBefore(Instant.now()))
                .orElse(false);
    }


}
