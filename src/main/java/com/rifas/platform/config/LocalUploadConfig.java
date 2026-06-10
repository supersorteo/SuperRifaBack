package com.rifas.platform.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
@Profile("!prod")
public class LocalUploadConfig implements WebMvcConfigurer {

    @Value("${app.upload-path:${user.home}/rifas-uploads}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/")
                .setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());
    }

    // (d) Security headers: prevent browser from executing uploaded files as scripts
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(@NonNull HttpServletRequest req,
                                     @NonNull HttpServletResponse res,
                                     @NonNull Object handler) {
                if (req.getRequestURI().startsWith("/uploads/")) {
                    res.setHeader("X-Content-Type-Options", "nosniff");
                    res.setHeader("Content-Security-Policy",
                            "default-src 'none'; img-src 'self'; sandbox");
                }
                return true;
            }
        }).addPathPatterns("/uploads/**");
    }
}
