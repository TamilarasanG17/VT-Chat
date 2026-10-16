// package com.vtchat.storage;

// import com.vtchat.config.MediaStorageProperties;
// import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
// import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
// import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
// import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
// import software.amazon.awssdk.http.apache.ApacheHttpClient;
// import software.amazon.awssdk.regions.Region;
// import software.amazon.awssdk.services.s3.S3Client;
// import software.amazon.awssdk.services.s3.S3Configuration;
// import software.amazon.awssdk.services.s3.presigner.S3Presigner;

// import java.net.URI;
// import java.time.Duration;

// /**
//  * Builds the two long-lived AWS SDK objects used for R2: one {@link S3Client} (shared, thread-safe, pooled connections)
//  * and one {@link S3Presigner}. R2 specifics: account endpoint, region "auto", path-style addressing. Bounded timeouts
//  * and a bounded retry count; the SDK only retries errors it knows to be transient.
//  * <p>
//  * Credentials come only from {@link MediaStorageProperties} (environment variables) and are never logged.
//  */
// final class R2ClientFactory {

//     private R2ClientFactory() {
//     }

//     static S3Client client(MediaStorageProperties.R2 r2) {
//         ClientOverrideConfiguration override = ClientOverrideConfiguration.builder()
//                 .apiCallTimeout(Duration.ofMillis(r2.getApiTimeoutMs()))
//                 .apiCallAttemptTimeout(Duration.ofMillis(r2.getSocketTimeoutMs() + r2.getConnectTimeoutMs()))
//                 .retryStrategy(AwsRetryStrategy.standardRetryStrategy().toBuilder().maxAttempts(Math.max(1, r2.getMaxAttempts())).build())
//                 .build();
//         return S3Client.builder()
//                 .endpointOverride(URI.create(r2.resolvedEndpoint()))
//                 .region(Region.of(regionOf(r2)))
//                 .credentialsProvider(credentials(r2))
//                 .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
//                 .httpClientBuilder(ApacheHttpClient.builder()
//                         .connectionTimeout(Duration.ofMillis(r2.getConnectTimeoutMs()))
//                         .socketTimeout(Duration.ofMillis(r2.getSocketTimeoutMs()))
//                         .maxConnections(50))
//                 .overrideConfiguration(override)
//                 .build();
//     }

//     static S3Presigner presigner(MediaStorageProperties.R2 r2) {
//         return S3Presigner.builder()
//                 .endpointOverride(URI.create(r2.resolvedEndpoint()))
//                 .region(Region.of(regionOf(r2)))
//                 .credentialsProvider(credentials(r2))
//                 .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
//                 .build();
//     }

//     private static StaticCredentialsProvider credentials(MediaStorageProperties.R2 r2) {
//         return StaticCredentialsProvider.create(AwsBasicCredentials.create(r2.getAccessKeyId().trim(), r2.getSecretAccessKey().trim()));
//     }

//     private static String regionOf(MediaStorageProperties.R2 r2) {
//         String region = r2.getRegion() == null ? "" : r2.getRegion().trim();
//         return region.isEmpty() ? "auto" : region;
//     }
// }
