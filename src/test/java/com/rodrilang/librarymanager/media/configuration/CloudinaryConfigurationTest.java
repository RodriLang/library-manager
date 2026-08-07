package com.rodrilang.librarymanager.media.configuration;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CloudinaryConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(CloudinaryConfiguration.class)
                    .withPropertyValues(
                            "cloudinary.cloud-name=test-cloud",
                            "cloudinary.api-key=test-key",
                            "cloudinary.api-secret=test-secret",
                            "cloudinary.root-folder=anaquel"
                    );

    @Test
    void shouldCreateCloudinaryBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Cloudinary.class);
            assertThat(context).hasSingleBean(CloudinaryProperties.class);
        });
    }
}