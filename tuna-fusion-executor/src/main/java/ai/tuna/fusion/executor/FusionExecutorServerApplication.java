package ai.tuna.fusion.executor;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author robinqu
 */
@SpringBootApplication
@EnableScheduling
public class FusionExecutorServerApplication {
    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(FusionExecutorServerApplication.class, args);
    }
}
