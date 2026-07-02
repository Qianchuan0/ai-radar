package com.airadar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiRadarApplication.class, args);
    }
}
