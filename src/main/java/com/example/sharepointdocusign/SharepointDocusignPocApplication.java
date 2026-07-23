package com.example.sharepointdocusign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SharepointDocusignPocApplication {

    public static void main(String[] args) {
        SpringApplication.run(SharepointDocusignPocApplication.class, args);
    }
}
