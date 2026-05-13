package com.benchmark;

public class ExperimentConfig {

    private final String broker;
    private final int msgSize;
    private final int targetRate;
    private final int duration;
    private final String experimentName;

    public ExperimentConfig(String broker, int msgSize, int targetRate,
                            int duration, String experimentName) {
        this.broker = broker;
        this.msgSize = msgSize;
        this.targetRate = targetRate;
        this.duration = duration;
        this.experimentName = experimentName;
    }

    public String getBroker()         { return broker; }
    public int    getMsgSize()        { return msgSize; }
    public int    getTargetRate()     { return targetRate; }
    public int    getDuration()       { return duration; }
    public String getExperimentName() { return experimentName; }

    @Override
    public String toString() {
        return String.format("%s | size=%,d B | rate=%,d msg/s | %ds",
                broker.toUpperCase(), msgSize, targetRate, duration);
    }
}
