package com.course.springlearning.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Enables @Async; Spring Boot runs those methods on its auto-configured "applicationTaskExecutor" thread pool
@Configuration
@EnableAsync
public class AsyncConfig {
}
