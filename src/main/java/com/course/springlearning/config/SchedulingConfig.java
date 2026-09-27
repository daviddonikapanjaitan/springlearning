package com.course.springlearning.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Enables @Scheduled, e.g. the job that marks expired tokens in the tokens table
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
