package com.incidentdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IncidentDeskApplication {
    public static void main(String[] args) {
        SpringApplication.run(IncidentDeskApplication.class, args);
    }
}
