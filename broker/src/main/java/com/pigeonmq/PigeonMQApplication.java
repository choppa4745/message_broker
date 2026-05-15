package com.pigeonmq;

import com.pigeonmq.config.BrokerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BrokerProperties.class)
public class PigeonMQApplication {

    public static void main(String[] args) {
        SpringApplication.run(PigeonMQApplication.class, args);
    }
}
