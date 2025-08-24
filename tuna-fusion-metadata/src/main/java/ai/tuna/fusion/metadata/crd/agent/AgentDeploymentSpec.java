package ai.tuna.fusion.metadata.crd.agent;

import ai.tuna.fusion.metadata.a2a.AgentCard;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.generator.annotation.Default;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import io.fabric8.generator.annotation.ValidationRules;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author robinqu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class AgentDeploymentSpec {

    @Required
    private String environmentName;

    @Required
    @ValidationRule(value ="!has(self.url)", message = "URL cannot be set in AgentCard. It will be dynamic generated during reconciliation.")
    private AgentCard agentCard;

    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GitOptions {
        /**
         * Branch ref to watch
         */
        @Builder.Default
        private String watchedBranchName = "refs/heads/main";

        /**
         * Sub-folder relative to repository root, whose content will be included in the final archive
         */
        private String subPath;
    }


    @Builder.Default
    private GitOptions git = new GitOptions();

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
            @Builder.Default
            private TaskStoreProvider provider = TaskStoreProvider.InMemory;

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
            @Builder.Default
            private QueueManagerProvider provider = QueueManagerProvider.InMemory;

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
                @Builder.Default
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
        @Builder.Default
        private TaskStore taskStore = new TaskStore();

        @Required
        @ValidationRule("self.provider=='InMemory' || (self.provider=='redis' && has(self.redis))")
        @Builder.Default
        private QueueManager queueManager = new QueueManager();
    }

    @Builder.Default
    private A2ARuntime a2a = new A2ARuntime();


    @Required
    private String entrypoint;

    private List<PodFunction.FileAsset> fileAssets;
}
