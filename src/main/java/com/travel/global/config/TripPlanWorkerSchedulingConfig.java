package com.travel.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "app.runtime-role",
        havingValue = "worker"
)
public class TripPlanWorkerSchedulingConfig {
}
