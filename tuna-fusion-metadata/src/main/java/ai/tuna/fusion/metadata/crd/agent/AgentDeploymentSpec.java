package ai.tuna.fusion.metadata.crd.agent;

import ai.tuna.fusion.metadata.a2a.AgentCard;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import io.fabric8.generator.annotation.ValidationRules;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author robinqu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentDeploymentSpec {

    @Required
    private String environmentName;

    @Required
    @ValidationRule(value ="!has(self.url)", message = "URL cannot be set in AgentCard. It will be dynamic generated during reconciliation.")
    private AgentCard agentCard;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GitOptions {
        private String watchedBranchName;
    }

    public static final GitOptions DefaultGitOptions = AgentDeploymentSpec.GitOptions.builder()
            .watchedBranchName("refs/heads/main")
            .build();

    private GitOptions git;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class A2ARuntime {
        public enum TaskStoreProvider {
            Postgres,
            MySQL,
            SQLite,
            InMemory
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class TaskStore {
            /**
             * table name is auto-generated in operator
             */
            @Data
            public static class SQLConfig {
                @Required
                private String databaseUrl;
                @Required
                private boolean createTable;
                private String taskStoreTableName;
            }


            @Required
            private TaskStoreProvider provider;


            @ValidationRule(value = "!has(self.taskStoreTableName)", message = "sql.taskStoreTableName cannot be included in resource definition.")
            private SQLConfig sql;


        }

        public enum QueueManagerProvider {
            Redis,
            InMemory
        }

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class QueueManager {
            @Required
            private QueueManagerProvider provider;

            /**
             * channel key prefix and registry key are auto-generated in operator
             */
            @Data
            @Builder
            @AllArgsConstructor
            @NoArgsConstructor
            public static class RedisConfig {
                @Required
                private String redisUrl;
                @Required
                private int taskIdTtlInSecond = 60;

                private String taskRegistryKey;
                private String relayChannelKeyPrefix;
            }

            @ValidationRules(
                    @ValidationRule(value = "!has(self.taskRegistryKey) && !has(self.relayChannelKeyPrefix)", message = "self.taskIdRegistryKey and self.channelKeyPrefix cannot be included in resource definition.")
            )
            private RedisConfig redis;
        }

        @Required
        @ValidationRule("self.provider=='InMemory' || (self.provider=='sql' && has(self.sql))")
        private TaskStore taskStore;

        @Required
        @ValidationRule("self.provider=='InMemory' || (self.provider=='redis' && has(self.redis))")
        private QueueManager queueManager;
    }

    public static final A2ARuntime DEFAULT_A2A_RUNTIME = AgentDeploymentSpec.A2ARuntime.builder()
            .queueManager(AgentDeploymentSpec.A2ARuntime.QueueManager.builder()
                    .provider(AgentDeploymentSpec.A2ARuntime.QueueManagerProvider.InMemory)
                    .build())
            .taskStore(AgentDeploymentSpec.A2ARuntime.TaskStore.builder()
                    .provider(AgentDeploymentSpec.A2ARuntime.TaskStoreProvider.InMemory)
                    .build())
            .build();

    private A2ARuntime a2a = DEFAULT_A2A_RUNTIME;


    @Required
    private String entrypoint;

    private List<PodFunction.FileAsset> fileAssets;
}
