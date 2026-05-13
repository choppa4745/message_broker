package com.practice3.api;

import com.practice3.config.Practice3Props;
import com.practice3.metrics.AppMetrics;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequestMapping("/stats")
public class StatsController {

    private final AppMetrics metrics;
    private final Practice3Props props;
    private final Environment env;

    public StatsController(AppMetrics metrics, Practice3Props props, Environment env) {
        this.metrics = metrics;
        this.props = props;
        this.env = env;
    }

    @GetMapping
    public StatsResponse stats() {
        String mode = String.join(",", Arrays.asList(env.getActiveProfiles()));
        return new StatsResponse(
                mode,
                props.getDataset().getSize(),
                metrics.httpReads.count(),
                metrics.httpWrites.count(),
                metrics.httpErrors.count(),
                metrics.dbReads.count(),
                metrics.dbWrites.count(),
                metrics.cacheGets.count(),
                metrics.cacheHits.count(),
                metrics.cacheSets.count(),
                metrics.cacheDeletes.count(),
                metrics.getWriteBackDirtyCount(),
                metrics.writeBackEnqueued.count(),
                metrics.writeBackFlushed.count(),
                metrics.writeBackFlushBatches.count(),
                metrics.writeBackFlushErrors.count()
        );
    }
}

