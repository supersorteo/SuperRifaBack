package com.rifas.platform.config;

import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class CloudinaryConfig {

    private final CloudinaryProperties props;

    @Bean
    Cloudinary cloudinary() {
        return new Cloudinary(Map.of(
                "cloud_name", props.getCloudName(),
                "api_key",    props.getApiKey(),
                "api_secret", props.getApiSecret(),
                "secure",     true
        ));
    }
}
