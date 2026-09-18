package org.mobilecms.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    public StaticResourceConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String mediaLocation = appProperties.getMediaDir().toUri().toString();
        if (!mediaLocation.endsWith("/")) {
            mediaLocation += "/";
        }
        registry.addResourceHandler("/media/**")
                .addResourceLocations(mediaLocation);
    }
}
