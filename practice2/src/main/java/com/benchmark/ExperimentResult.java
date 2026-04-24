package com.benchmark;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExperimentResult {

    private final String broker;
    private final int msgSize;
    private final int targetRate;
    private final int duration;
    private final String experimentName;

    private long messagesSent;
    private long messagesReceived;
    private long sendErrors;
    private long recvErrors;
    private double avgLatencyMs;
    private double minLatencyMs;
    private double p50LatencyMs;
    private double p95LatencyMs;
    private double p99LatencyMs;
    private double maxLatencyMs;
    private double actualSendRate;
    private double actualRecvRate;

    public ExperimentResult(ExperimentConfig cfg) {
        this.broker = cfg.getBroker();
        this.msgSize = cfg.getMsgSize();
        this.targetRate = cfg.getTargetRate();
        this.duration = cfg.getDuration();
        this.experimentName = cfg.getExperimentName();
    }

    public long getMessagesLost() {
        return Math.max(0, messagesSent - messagesReceived);
    }

    public double getLossRate() {
        return messagesSent > 0 ? (double) getMessagesLost() / messagesSent * 100.0 : 0.0;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("broker", broker);
        m.put("experimentName", experimentName);
        m.put("msgSize", msgSize);
        m.put("targetRate", targetRate);
        m.put("duration", duration);
        m.put("messagesSent", messagesSent);
        m.put("messagesReceived", messagesReceived);
        m.put("messagesLost", getMessagesLost());
        m.put("sendErrors", sendErrors);
        m.put("recvErrors", recvErrors);
        m.put("avgLatencyMs", round(avgLatencyMs));
        m.put("minLatencyMs", round(minLatencyMs));
        m.put("p50LatencyMs", round(p50LatencyMs));
        m.put("p95LatencyMs", round(p95LatencyMs));
        m.put("p99LatencyMs", round(p99LatencyMs));
        m.put("maxLatencyMs", round(maxLatencyMs));
        m.put("actualSendRate", round(actualSendRate));
        m.put("actualRecvRate", round(actualRecvRate));
        m.put("lossRate", round(getLossRate()));
        return m;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ── getters / setters ──────────────────────────────────

    public String getBroker()           { return broker; }
    public int    getMsgSize()          { return msgSize; }
    public int    getTargetRate()       { return targetRate; }
    public int    getDuration()         { return duration; }
    public String getExperimentName()   { return experimentName; }
    public long   getMessagesSent()     { return messagesSent; }
    public long   getMessagesReceived() { return messagesReceived; }
    public long   getSendErrors()       { return sendErrors; }
    public long   getRecvErrors()       { return recvErrors; }
    public double getAvgLatencyMs()     { return avgLatencyMs; }
    public double getMinLatencyMs()     { return minLatencyMs; }
    public double getP50LatencyMs()     { return p50LatencyMs; }
    public double getP95LatencyMs()     { return p95LatencyMs; }
    public double getP99LatencyMs()     { return p99LatencyMs; }
    public double getMaxLatencyMs()     { return maxLatencyMs; }
    public double getActualSendRate()   { return actualSendRate; }
    public double getActualRecvRate()   { return actualRecvRate; }

    public void setMessagesSent(long v)     { this.messagesSent = v; }
    public void setMessagesReceived(long v) { this.messagesReceived = v; }
    public void setSendErrors(long v)       { this.sendErrors = v; }
    public void setRecvErrors(long v)       { this.recvErrors = v; }
    public void setAvgLatencyMs(double v)   { this.avgLatencyMs = v; }
    public void setMinLatencyMs(double v)   { this.minLatencyMs = v; }
    public void setP50LatencyMs(double v)   { this.p50LatencyMs = v; }
    public void setP95LatencyMs(double v)   { this.p95LatencyMs = v; }
    public void setP99LatencyMs(double v)   { this.p99LatencyMs = v; }
    public void setMaxLatencyMs(double v)   { this.maxLatencyMs = v; }
    public void setActualSendRate(double v) { this.actualSendRate = v; }
    public void setActualRecvRate(double v) { this.actualRecvRate = v; }
}
