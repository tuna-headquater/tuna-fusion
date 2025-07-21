package ai.tuna.fusion.metadata.crd.agent;

import ai.tuna.fusion.metadata.a2a.AgentCard;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import io.fabric8.generator.annotation.ValidationRules;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
public class AgentDeploymentSpec {

    @Required
    private String environmentName;

    @Required
    private String catalogueName;

    @Required
    @ValidationRule(value ="!has(self.url)", message = "URL cannot be set in AgentCard. It will be dynamic generated during reconciliation.")
    private AgentCard agentCard;

    @Data
    public static class GitOptions {
        String watchedBranchName = "refs/heads/master";
    }

    @Required
    private GitOptions git;


    @Data
    public static class A2ARuntime {
        public enum TaskStoreProvider {
            Postgres,
            MySQL,
            SQLite,
            InMemory
        }

        @Data
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
        public static class QueueManager {
            @Required
            private QueueManagerProvider provider;

            /**
             * channel key prefix and registry key are auto-generated in operator
             */
            @Data
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

    @Required
    private A2ARuntime a2a;

    @Required
    private String entrypoint;
}
