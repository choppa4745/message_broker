package com.benchmark;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ReportGenerator {

    private final Map<String, List<ExperimentResult>> results;
    private final Path outputDir;

    public ReportGenerator(Map<String, List<ExperimentResult>> results, Path outputDir) {
        this.results = results;
        this.outputDir = outputDir;
    }

    public void generate() throws IOException {
        Files.createDirectories(outputDir);
        saveRawJson();
        saveMarkdown();
    }


    private void saveRawJson() throws IOException {
        Map<String, Object> raw = new LinkedHashMap<>();
        results.forEach((key, list) ->
                raw.put(key, list.stream().map(ExperimentResult::toMap).collect(Collectors.toList())));
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(raw);
        Files.writeString(outputDir.resolve("raw_results.json"), json);
    }


    private void saveMarkdown() throws IOException {
        List<String> md = new ArrayList<>();

        md.add("# Сравнение RabbitMQ и Redis как брокеров сообщений\n");
        md.add("**Дата:** " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
        md.add("---\n");

        appendBaseComparison(md);
        appendSizeImpact(md);
        appendStressTest(md);
        appendConclusions(md);

        Files.writeString(outputDir.resolve("report.md"), String.join("\n", md));
    }


    private void appendBaseComparison(List<String> md) {
        List<ExperimentResult> base = results.getOrDefault("base", List.of());
        if (base.size() < 2) return;
        ExperimentResult rmq = base.get(0), rds = base.get(1);

        md.add("## 1. Базовое сравнение (1 KB, 1 000 msg/s)\n");
        md.add("| Метрика | RabbitMQ | Redis |");
        md.add("|---------|----------|-------|");
        row(md, "Отправлено",                  fmt(rmq.getMessagesSent()),     fmt(rds.getMessagesSent()));
        row(md, "Получено",                    fmt(rmq.getMessagesReceived()), fmt(rds.getMessagesReceived()));
        row(md, "Потеряно",                    fmt(rmq.getMessagesLost()),     fmt(rds.getMessagesLost()));
        row(md, "Ошибки отправки",             fmt(rmq.getSendErrors()),       fmt(rds.getSendErrors()));
        row(md, "Ошибки получения",            fmt(rmq.getRecvErrors()),       fmt(rds.getRecvErrors()));
        row(md, "Скорость отправки (msg/s)",   fmtD(rmq.getActualSendRate()), fmtD(rds.getActualSendRate()));
        row(md, "Скорость получения (msg/s)",  fmtD(rmq.getActualRecvRate()), fmtD(rds.getActualRecvRate()));
        row(md, "Средняя задержка (ms)",       fmtL(rmq.getAvgLatencyMs()),   fmtL(rds.getAvgLatencyMs()));
        row(md, "P50 задержка (ms)",           fmtL(rmq.getP50LatencyMs()),   fmtL(rds.getP50LatencyMs()));
        row(md, "P95 задержка (ms)",           fmtL(rmq.getP95LatencyMs()),   fmtL(rds.getP95LatencyMs()));
        row(md, "P99 задержка (ms)",           fmtL(rmq.getP99LatencyMs()),   fmtL(rds.getP99LatencyMs()));
        row(md, "Макс задержка (ms)",          fmtL(rmq.getMaxLatencyMs()),   fmtL(rds.getMaxLatencyMs()));
        md.add("");
    }

    // ── Section 2: Size ────────────────────────────────────

    private void appendSizeImpact(List<String> md) {
        List<ExperimentResult> list = results.getOrDefault("size", List.of());
        if (list.isEmpty()) return;

        md.add("## 2. Влияние размера сообщения\n");
        md.add("| Размер | Брокер | Отправлено | Получено | Потеряно | Avg Latency (ms) | P95 Latency (ms) | Recv Rate (msg/s) |");
        md.add("|--------|--------|-----------|----------|----------|-------------------|-------------------|--------------------|");
        for (ExperimentResult r : list) {
            md.add(String.format("| %s | %s | %s | %s | %s | %s | %s | %s |",
                    sizeLabel(r.getMsgSize()), r.getBroker(),
                    fmt(r.getMessagesSent()), fmt(r.getMessagesReceived()),
                    fmt(r.getMessagesLost()),
                    fmtL(r.getAvgLatencyMs()), fmtL(r.getP95LatencyMs()),
                    fmtD(r.getActualRecvRate())));
        }
        md.add("");
    }


    private void appendStressTest(List<String> md) {
        List<ExperimentResult> list = results.getOrDefault("stress", List.of());
        if (list.isEmpty()) return;

        md.add("## 3. Влияние интенсивности потока\n");
        md.add("| Target Rate | Брокер | Отправлено | Получено | Потеряно | Ошибки | Avg Latency (ms) | P95 Latency (ms) | Actual Recv Rate |");
        md.add("|-------------|--------|-----------|----------|----------|--------|-------------------|-------------------|--------------------|");
        for (ExperimentResult r : list) {
            md.add(String.format("| %s | %s | %s | %s | %s | %s | %s | %s | %s |",
                    fmt(r.getTargetRate()), r.getBroker(),
                    fmt(r.getMessagesSent()), fmt(r.getMessagesReceived()),
                    fmt(r.getMessagesLost()),
                    fmt(r.getSendErrors() + r.getRecvErrors()),
                    fmtL(r.getAvgLatencyMs()), fmtL(r.getP95LatencyMs()),
                    fmtD(r.getActualRecvRate())));
        }
        md.add("");
    }


    private void appendConclusions(List<String> md) {
        md.add("## 4. Выводы\n");

        List<ExperimentResult> base = results.getOrDefault("base", List.of());
        if (base.size() >= 2) {
            ExperimentResult rmq = base.get(0), rds = base.get(1);

            md.add("### Пропускная способность\n");
            if (rds.getActualRecvRate() > rmq.getActualRecvRate() * 1.05) {
                md.add(String.format("В базовом сценарии **Redis** показал более высокую пропускную способность " +
                        "(%.1fx).\n", rds.getActualRecvRate() / rmq.getActualRecvRate()));
            } else if (rmq.getActualRecvRate() > rds.getActualRecvRate() * 1.05) {
                md.add(String.format("В базовом сценарии **RabbitMQ** показал более высокую пропускную способность " +
                        "(%.1fx).\n", rmq.getActualRecvRate() / rds.getActualRecvRate()));
            } else {
                md.add("В базовом сценарии оба брокера показали сопоставимую пропускную способность.\n");
            }

            md.add("### Задержка\n");
            String lowerLatency = rds.getAvgLatencyMs() < rmq.getAvgLatencyMs() ? "Redis" : "RabbitMQ";
            md.add(String.format("**%s** показал более низкую среднюю задержку в базовом сценарии " +
                            "(%.2f ms vs %.2f ms).\n",
                    lowerLatency,
                    Math.min(rmq.getAvgLatencyMs(), rds.getAvgLatencyMs()),
                    Math.max(rmq.getAvgLatencyMs(), rds.getAvgLatencyMs())));
        }

        appendSizeConclusion(md);
        appendDegradationConclusion(md);

        md.add("### Рекомендации\n");
        md.add("- **RabbitMQ** лучше подходит для сценариев, требующих надёжной доставки, " +
                "маршрутизации и сложной топологии очередей.");
        md.add("- **Redis** лучше подходит для сценариев с высокой пропускной способностью и " +
                "низкими требованиями к гарантиям доставки (кэширование, простые очереди задач).");
        md.add("");
    }

    private void appendSizeConclusion(List<String> md) {
        List<ExperimentResult> list = results.getOrDefault("size", List.of());
        if (list.isEmpty()) return;

        List<ExperimentResult> rmqList = list.stream()
                .filter(r -> "rabbitmq".equals(r.getBroker())).toList();
        List<ExperimentResult> rdsList = list.stream()
                .filter(r -> "redis".equals(r.getBroker())).toList();

        if (rmqList.size() < 2 || rdsList.size() < 2) return;

        double rmqSmallRate = rmqList.get(0).getActualRecvRate();
        double rmqLargeRate = rmqList.get(rmqList.size() - 1).getActualRecvRate();
        double rdsSmallRate = rdsList.get(0).getActualRecvRate();
        double rdsLargeRate = rdsList.get(rdsList.size() - 1).getActualRecvRate();

        double rmqDeg = rmqSmallRate > 0 ? (1 - rmqLargeRate / rmqSmallRate) * 100 : 0;
        double rdsDeg = rdsSmallRate > 0 ? (1 - rdsLargeRate / rdsSmallRate) * 100 : 0;

        md.add("### Влияние размера сообщения\n");
        md.add(String.format("- RabbitMQ: деградация пропускной способности — **%.1f%%**", rmqDeg));
        md.add(String.format("- Redis: деградация пропускной способности — **%.1f%%**", rdsDeg));
        String better = rmqDeg < rdsDeg ? "RabbitMQ" : "Redis";
        md.add(String.format("- **%s** лучше справляется с увеличением размера сообщения.\n", better));
    }

    private void appendDegradationConclusion(List<String> md) {
        List<ExperimentResult> list = results.getOrDefault("stress", List.of());
        if (list.isEmpty()) return;

        md.add("### Точка деградации\n");
        for (String broker : new String[]{"rabbitmq", "redis"}) {
            List<ExperimentResult> bl = list.stream()
                    .filter(r -> broker.equals(r.getBroker())).toList();
            Integer degradationRate = null;
            for (ExperimentResult r : bl) {
                if (r.getLossRate() > 5 || r.getP95LatencyMs() > 100
                        || r.getSendErrors() > 0) {
                    degradationRate = r.getTargetRate();
                    break;
                }
            }
            if (degradationRate != null) {
                md.add(String.format("- **%s**: деградация начинается при ~%,d msg/s " +
                        "(потери >5%%, P95 >100ms или ошибки отправки)",
                        broker.toUpperCase(), degradationRate));
            } else {
                md.add(String.format("- **%s**: не показал значительной деградации в тестируемом диапазоне",
                        broker.toUpperCase()));
            }
        }
        md.add("");
    }


    private static void row(List<String> md, String label, String a, String b) {
        md.add(String.format("| %s | %s | %s |", label, a, b));
    }

    private static String fmt(long v) { return String.format("%,d", v); }
    private static String fmtD(double v) { return String.format("%,.0f", v); }
    private static String fmtL(double v) { return String.format("%.2f", v); }

    static String sizeLabel(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
}
