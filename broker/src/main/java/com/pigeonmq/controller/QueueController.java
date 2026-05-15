package com.pigeonmq.controller;

import com.pigeonmq.domain.QueueMode;
import com.pigeonmq.dto.CreateQueueRequest;
import com.pigeonmq.dto.MutationResult;
import com.pigeonmq.dto.QueueDto;
import com.pigeonmq.exception.InvalidRequestException;
import com.pigeonmq.exception.ResourceNotFoundException;
import com.pigeonmq.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/queues")
@Tag(name = "Queues", description = "Queue management API")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping
    @Operation(summary = "List all queues")
    public List<QueueDto> list() {
        return queueService.getAll().stream()
                .map(QueueDto::from)
                .toList();
    }

    @GetMapping("/{name}")
    @Operation(summary = "Get queue details")
    public QueueDto get(@PathVariable String name) {
        return QueueDto.from(queueService.getOrThrow(name));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new queue")
    public MutationResult create(@Valid @RequestBody CreateQueueRequest request) {
        QueueMode mode = resolveQueueMode(request.mode());
        queueService.create(request.name(), mode);
        return MutationResult.created(request.name());
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "Delete a queue")
    public MutationResult delete(@PathVariable String name) {
        if (!queueService.delete(name)) {
            throw new ResourceNotFoundException("Queue", name);
        }
        return MutationResult.deleted(name);
    }

    private QueueMode resolveQueueMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return QueueMode.FIFO;
        }
        try {
            return QueueMode.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid queue mode: " + raw + ". Allowed: FIFO, PRIORITY");
        }
    }
}
