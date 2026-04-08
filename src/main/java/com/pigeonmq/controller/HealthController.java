package com.pigeonmq.controller;

import com.pigeonmq.dto.HealthDto;
import com.pigeonmq.service.QueueService;
import com.pigeonmq.service.SessionService;
import com.pigeonmq.service.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health", description = "Health check and basic metrics")
public class HealthController {

    private static final String STATUS_OK = "UP";

    private final TopicService topicService;
    private final QueueService queueService;
    private final SessionService sessionService;

    public HealthController(TopicService topicService,
                            QueueService queueService,
                            SessionService sessionService) {
        this.topicService = topicService;
        this.queueService = queueService;
        this.sessionService = sessionService;
    }

    @GetMapping("/health")
    @Operation(summary = "Health check with basic broker stats")
    public HealthDto health() {
        return new HealthDto(
                STATUS_OK,
                topicService.getAll().size(),
                queueService.getAll().size(),
                sessionService.getActiveCount());
    }
}
