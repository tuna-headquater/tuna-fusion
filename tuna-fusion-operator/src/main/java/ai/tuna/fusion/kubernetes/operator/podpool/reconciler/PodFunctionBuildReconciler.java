package ai.tuna.fusion.kubernetes.operator.podpool.reconciler;

import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
import ai.tuna.fusion.kubernetes.operator.podpool.dr.PodFunctionBuildJobDependentResource;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildStatus;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
@ControllerConfiguration
@Workflow(dependents = {
        @Dependent(type = PodFunctionBuildJobDependentResource.class, reconcilePrecondition = PodFunctionBuildJobDependentResource.IsJobRequiredCondition.class),
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
            var fullJob = getBuildJob(
                    context.getClient(),
                    jobResource.getMetadata().getName(),
                    jobResource.getMetadata().getNamespace()
            );
            var jobStatus = fullJob.getStatus();
            log.info("Job(namespace={},name={}) has already been created: ready={}, failed={}, active={}, succeeded={}", fullJob.getMetadata().getNamespace(), fullJob.getMetadata().getName(), jobStatus.getReady(), jobStatus.getFailed(), jobStatus.getActive(), jobStatus.getSucceeded());
            var podFunctionBuildPatch = new PodFunctionBuild();
            podFunctionBuildPatch.getMetadata().setName(resource.getMetadata().getName());
            podFunctionBuildPatch.getMetadata().setNamespace(resource.getMetadata().getNamespace());
            var status = new PodFunctionBuildStatus();

            // deployArchive field is updated by builder script
//            // Only FilesystemFolderSource is supported, so we can compute the status beforehand.
//            var deployArchive = new PodFunctionBuildStatus.DeployArchive();
//            var folderSource = new PodFunction.FilesystemFolderSource();
//            folderSource.setPath(PodPoolResourceUtils.computeDeployArchivePath(resource));
//            deployArchive.setFilesystemFolderSource(folderSource);
//            status.setDeployArchive(deployArchive);
            podFunctionBuildPatch.setStatus(status);
            if(Optional.ofNullable(jobStatus.getSucceeded()).orElse(0) >= jobResource.getSpec().getCompletions()) {
                status.setPhase(Succeeded);
            } else if (Optional.ofNullable(jobStatus.getFailed()).orElse(0) > jobResource.getSpec().getBackoffLimit()) {
                status.setPhase(Failed);
            } else if (Optional.ofNullable(jobStatus.getActive()).orElse(0)>0) {
                status.setPhase(PodFunctionBuildStatus.Phase.Running);
            } else  {
                status.setPhase(PodFunctionBuildStatus.Phase.Scheduled);
            }
            PodFunctionBuildStatus.JobPodInfo jobPodInfo = new PodFunctionBuildStatus.JobPodInfo();
            status.setJobPod(jobPodInfo);
            var jobPod = getJobPod(context.getClient(), jobResource.getMetadata().getName(), jobResource.getMetadata().getNamespace());
            jobPodInfo.setPodName(jobPod.map(pod -> pod.getMetadata().getName()).orElseThrow());
            jobPodInfo.setPodPhase(jobPod.map(pod -> pod.getStatus().getPhase()).orElseThrow());
            log.info("JobPodInfo: {}", jobPodInfo);
            if (PodPoolResourceUtils.isJobTerminalPhase(jobPodInfo.getPodPhase())) {
                jobPodInfo.setLogs(
                        PodPoolResourceUtils.getPodLog(context.getClient(), resource.getMetadata().getNamespace(), jobPodInfo.getPodName())
                );
            }

            // Update PodFunction with build info
            var patchPodFunction = new PodFunction();
            switch (podFunctionBuildPatch.getStatus().getPhase()) {
                case Succeeded -> {
                    log.info("Patching Succeeded PodFunctionBuild: {}", podFunctionBuildPatch.getMetadata().getName());
                    var podFunctionStatus = new PodFunctionStatus();
                    var buildInfo = new PodFunctionStatus.BuildInfo();
                    buildInfo.setName(resource.getMetadata().getName());
                    buildInfo.setStartTimestamp(Instant.now().getEpochSecond());
                    podFunctionStatus.setCurrentBuild(null);
                    podFunctionStatus.setEffectiveBuild(buildInfo);
                    patchPodFunction.setStatus(podFunctionStatus);
                }
                case Failed -> {
                    log.info("Patching Failed PodFunctionBuild: {}", podFunctionBuildPatch.getMetadata().getName());
                    var podFunctionStatus = new PodFunctionStatus();
                    var buildInfo = new PodFunctionStatus.BuildInfo();
                    buildInfo.setName(resource.getMetadata().getName());
                    buildInfo.setStartTimestamp(Instant.now().getEpochSecond());
                    podFunctionStatus.setCurrentBuild(null);
                    podFunctionStatus.setEffectiveBuild(null);
                    patchPodFunction.setStatus(podFunctionStatus);
                }
                default -> {
                    log.info("Patching On-going PodFunctionBuild: {}", podFunctionBuildPatch.getMetadata().getName());
                    var podFunctionStatus = new PodFunctionStatus();
                    var buildInfo = new PodFunctionStatus.BuildInfo();
                    buildInfo.setName(resource.getMetadata().getName());
                    buildInfo.setStartTimestamp(Instant.now().getEpochSecond());
                    podFunctionStatus.setCurrentBuild(buildInfo);
                    podFunctionStatus.setEffectiveBuild(null);
                    patchPodFunction.setStatus(podFunctionStatus);
                }
            }
            context.getClient().resources(PodFunction.class)
                    .inNamespace(resource.getMetadata().getNamespace())
                    .withName(PodPoolResourceUtils.getReferencedPodFunctionName(resource).orElseThrow())
                    .patch(PatchContext.of(PatchType.SERVER_SIDE_APPLY), patchPodFunction);

            // update PodFunctionBuild
            return UpdateControl.patchResourceAndStatus(podFunctionBuildPatch);
        }
        log.debug("Job is not created or already finished for PodFunctionBuild: {}", resource.getMetadata().getName());
        return UpdateControl.noUpdate();

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


    private Job getBuildJob(KubernetesClient client, String jobName, String ns) {
        return client.batch()
                .v1()
                .jobs()
                .inNamespace(ns)
                .withName(jobName)
                .get();
    }
}
