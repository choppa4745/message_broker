package com.practice3.loadgen;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class Report {

    public static String renderMarkdown(List<RunResult> results, Config cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Practice3 — сравнение типов кеширования\n\n");
        sb.append("## Конфигурация теста\n\n");
        sb.append(String.format(Locale.US, "- **dataset**: %,d products%n", cfg.datasetSize));
        sb.append(String.format(Locale.US, "- **duration**: %d seconds%n", cfg.durationSeconds));
        sb.append(String.format(Locale.US, "- **warmup**: %d seconds%n", cfg.warmupSeconds));
        sb.append(String.format(Locale.US, "- **concurrency**: %d threads%n", cfg.concurrency));
        sb.append(String.format(Locale.US, "- **random seed**: %d%n", cfg.randomSeed));
        sb.append("\n");

        sb.append("## Результаты\n\n");
        sb.append("| Cache mode | Scenario | Throughput (req/s) | Avg latency (ms) | P95 latency (ms) | Cache hit rate | DB reads | DB writes | WB dirty end | WB flush errors |\n");
        sb.append("|-----------|----------|--------------------:|-----------------:|-----------------:|---------------:|--------:|---------:|------------:|----------------:|\n");

        results.stream()
                .sorted(Comparator.comparing((RunResult r) -> r.scenario).thenComparing(r -> r.cacheMode))
                .forEach(r -> sb.append(String.format(Locale.US,
                        "| %s | %s | %.0f | %.2f | %.2f | %.2f%% | %.0f | %.0f | %.0f | %.0f |%n",
                        r.cacheMode,
                        r.scenario,
                        r.throughputRps,
                        r.avgLatencyMs,
                        r.p95LatencyMs,
                        r.cacheHitRate * 100.0,
                        r.dbReadsDelta,
                        r.dbWritesDelta,
                        r.writeBackDirtyEnd,
                        r.writeBackFlushErrorsDelta
                )));

        sb.append("\n");
        sb.append("## Выводы (что смотреть)\n\n");
        sb.append("- **Read-heavy**: чаще всего выигрывает стратегия с максимальным cache hit rate и минимальными DB reads.\n");
        sb.append("- **Write-heavy**: сравните DB writes и latency; у write-back DB writes могут быть ниже в моменте, но растёт `WB dirty end`.\n");
        sb.append("- **Balanced**: смотрите компромисс throughput/latency и суммарную нагрузку на БД.\n");
        sb.append("\n");
        sb.append("Отдельно для **Write-Back**: если `WB dirty end` растёт — это означает накопление “грязных” записей, которые ещё не дошли до БД.\n");
        sb.append("\n");
        sb.append("### Важное ограничение бенчмарка\n\n");
        sb.append("Нагрузчик работает в **closed-loop** режиме (следующий запрос после ответа). Это может скрывать хвосты задержек (coordinated omission). ");
        sb.append("Для production-grade бенчмарка используйте open-loop rate (k6/JMeter) или планировщик запросов по времени.\n");

        return sb.toString();
    }
}

