package com.vtchat.config;

import com.vtchat.service.LocalMediaStorageService;
import com.vtchat.service.MediaStorageService;
import com.vtchat.service.OrphanRecorder;
import com.vtchat.storage.CloudinaryMediaStorageService;
import com.vtchat.storage.CloudinaryRef;
import com.vtchat.storage.RoutingMediaStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class MediaStorageConfig {

    @Bean
    public LocalMediaStorageService localMediaStorage(AppProperties app, OrphanRecorder orphans) {
        return new LocalMediaStorageService(Paths.get(app.getMedia().getUploadDir()), orphans);
    }

    @Bean
    @ConditionalOnProperty(prefix = "media-storage", name = "provider", havingValue = "cloudinary")
    public CloudinaryMediaStorageService cloudinaryMediaStorage(MediaStorageProperties props, OrphanRecorder orphans) {
        List<String> problems = cloudinaryProblems(props.getCloudinary());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("MEDIA_STORAGE_PROVIDER=cloudinary but the Cloudinary configuration is not valid: "
                    + String.join("; ", problems) + ". See the 'Cloudinary media storage' section of the README.");
        }
        props.getCloudinary().setFolder(props.getCloudinary().getFolder().trim());
        CloudinaryMediaStorageService storage = new CloudinaryMediaStorageService(props.getCloudinary(), orphans);
        if (props.getCloudinary().isVerifyOnStartup()) {
            storage.verify();
        }
        return storage;
    }

    @Bean
    @Primary
    public MediaStorageService mediaStorageService(LocalMediaStorageService local, ObjectProvider<CloudinaryMediaStorageService> cloudinary,
                                                   MediaStorageProperties props, Environment env) {
        String provider = props.getProvider() == null ? "" : props.getProvider().trim();
        if ("r2".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("MEDIA_STORAGE_PROVIDER=r2 is no longer supported: Cloudflare R2 was replaced by Cloudinary. "
                    + "Use 'cloudinary' (production) or 'local' (development). Historical R2 files: see the README migration notes.");
        }
        boolean production = isProduction(env);
        if (props.isCloudinary()) {
            CloudinaryMediaStorageService primary = cloudinary.getIfAvailable();
            if (primary == null) {
                throw new IllegalStateException("MEDIA_STORAGE_PROVIDER=cloudinary but the Cloudinary storage could not be created");
            }
            log.info("Media storage: Cloudinary (private 'authenticated' delivery for chat media); historical files in '{}' stay readable from local disk",
                    env.getProperty("app.media.upload-dir"));
            return new RoutingMediaStorageService(primary, local);
        }
        if (!props.isLocal()) {
            throw new IllegalStateException("MEDIA_STORAGE_PROVIDER must be 'local' or 'cloudinary'");
        }
        if (production && !props.isAllowLocalInProduction()) {
            throw new IllegalStateException("The production profile is active but MEDIA_STORAGE_PROVIDER is 'local'. Set MEDIA_STORAGE_PROVIDER=cloudinary "
                    + "(recommended), or MEDIA_ALLOW_LOCAL_IN_PRODUCTION=true if this server really has a persistent disk for uploads.");
        }
        if (production) {
            log.warn("Media storage: LOCAL DISK in production (MEDIA_ALLOW_LOCAL_IN_PRODUCTION=true). Files are lost if the disk is ephemeral.");
        } else {
            log.info("Media storage: local disk ({})", env.getProperty("app.media.upload-dir"));
        }
        return local;
    }

    static boolean isProduction(Environment env) {
        return env.acceptsProfiles(Profiles.of("prod", "production"));
    }

    static List<String> cloudinaryProblems(MediaStorageProperties.Cloudinary c) {
        List<String> problems = new ArrayList<>();
        if (blank(c.getCloudName())) problems.add("CLOUDINARY_CLOUD_NAME is empty");
        if (blank(c.getApiKey())) problems.add("CLOUDINARY_API_KEY is empty");
        if (blank(c.getApiSecret())) problems.add("CLOUDINARY_API_SECRET is empty");
        if (!CloudinaryRef.validFolder(c.getFolder() == null ? null : c.getFolder().trim())) {
            problems.add("CLOUDINARY_FOLDER must be 1-4 segments of letters, digits, '_' or '-' separated by '/' (for example vtchat)");
        }
        if (c.getApiTimeoutMs() < 1000 || c.getUploadTimeoutMs() < 1000 || c.getDownloadTimeoutMs() < 1000) {
            problems.add("Cloudinary timeouts must be at least 1000 ms");
        }
        return problems;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
