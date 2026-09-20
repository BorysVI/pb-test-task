package com.example.pb_test_task.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.Random;
import java.util.random.RandomGenerator;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderProperties.class)
@EnableResilientMethods
@EnableScheduling
public class ApplicationConfig {

    @Bean
    public Clock clock(OrderProperties properties) {
        return Clock.system(properties.timeZone());
    }

    @Bean
    public RandomGenerator randomGenerator() {
        return new Random();
    }
}
