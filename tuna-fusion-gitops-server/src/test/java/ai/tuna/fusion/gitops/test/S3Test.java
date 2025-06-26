package ai.tuna.fusion.gitops.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.endpoints.Endpoint;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author robinqu
 */
@Slf4j
public class S3Test {

    @Test
    public void testMinio() {
        Region region = Region.AWS_GLOBAL;
        String awsS3EndpointUrl = "http://localhost:9000";
        String accessKeyId = "root";
        String accessKeySecret = "MXAxgk4LXzLZHA-";
        S3Client s3 = S3Client.builder()
                .region(region)
                .endpointProvider(endpointParams -> CompletableFuture.completedFuture(Endpoint.builder()
                        .url(URI.create(awsS3EndpointUrl + "/" + Optional.ofNullable(endpointParams.bucket()).orElse("")))
                        .build()))
                .credentialsProvider(()-> AwsBasicCredentials.create(accessKeyId, accessKeySecret))
                .build();

        try {
            ListBucketsResponse response = s3.listBuckets();
            List<Bucket> bucketList = response.buckets();
            bucketList.forEach(bucket -> {
                System.out.println("Bucket Name: " + bucket.name());
            });

        } catch (S3Exception e) {
            log.error("s3 exception", e);
            System.exit(1);
        }
    }
}
