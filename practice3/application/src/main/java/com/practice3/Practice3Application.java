package com.practice3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class Practice3Application {
    public static void main(String[] args) {
        SpringApplication.run(Practice3Application.class, args);
    }
}

