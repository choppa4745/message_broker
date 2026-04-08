package com.pigeonmq.controller;

import com.pigeonmq.dto.CreateTopicRequest;
import com.pigeonmq.dto.MutationResult;
import com.pigeonmq.dto.TopicDto;
import com.pigeonmq.exception.ResourceNotFoundException;
import com.pigeonmq.service.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/topics")
@Tag(name = "Topics", description = "Topic management API")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    @Operation(summary = "List all topics")
    public List<TopicDto> list() {
        return topicService.getAll().stream()
                .map(TopicDto::from)
                .toList();
    }

    @GetMapping("/{name}")
    @Operation(summary = "Get topic details")
    public TopicDto get(@PathVariable String name) {
        return TopicDto.from(topicService.getOrThrow(name));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new topic")
    public MutationResult create(@Valid @RequestBody CreateTopicRequest request) {
        topicService.create(request.name());
        return MutationResult.created(request.name());
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "Delete a topic")
    public MutationResult delete(@PathVariable String name) {
        if (!topicService.delete(name)) {
            throw new ResourceNotFoundException("Topic", name);
        }
        return MutationResult.deleted(name);
    }
}
