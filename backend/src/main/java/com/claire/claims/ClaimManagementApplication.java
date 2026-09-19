package com.claire.claims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ClaimManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimManagementApplication.class, args);
    }
}
