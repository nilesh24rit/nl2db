package com.nl2db.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.nl2db"})
@ConfigurationPropertiesScan("com.nl2db.gateway.config")
public class Nl2dbApplication {

    public static void main(String[] args) {
        SpringApplication.run(Nl2dbApplication.class, args);
    }
}