package com.prephub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class InterviewPrepHubApplication {
    public static void main(String[] args) { SpringApplication.run(InterviewPrepHubApplication.class, args); }
}
