package com.rodrilang.librarymanager.media.configuration;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rodrilang.librarymanager.cover.configuration.BookCoverProcessingProperties;
import com.rodrilang.librarymanager.cover.job.configuration.BookCoverJobProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        CloudinaryProperties.class,
        ImageProcessingProperties.class,
        BookCoverJobProperties.class,
        BookCoverProcessingProperties.class
})
public class CloudinaryConfiguration {

    @Bean
    public Cloudinary cloudinary(CloudinaryProperties properties) {
        validate(properties);

        return new Cloudinary(
                ObjectUtils.asMap(
                        "cloud_name", properties.cloudName(),
                        "api_key", properties.apiKey(),
                        "api_secret", properties.apiSecret(),
                        "secure", true
                )
        );
    }

    private void validate(CloudinaryProperties properties) {
        if (isBlank(properties.cloudName())) {
            throw new IllegalStateException(
                    "La propiedad cloudinary.cloud-name es obligatoria"
            );
        }

        if (isBlank(properties.apiKey())) {
            throw new IllegalStateException(
                    "La propiedad cloudinary.api-key es obligatoria"
            );
        }

        if (isBlank(properties.apiSecret())) {
            throw new IllegalStateException(
                    "La propiedad cloudinary.api-secret es obligatoria"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}