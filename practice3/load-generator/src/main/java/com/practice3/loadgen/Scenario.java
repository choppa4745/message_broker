package com.practice3.loadgen;

public class Scenario {
    public final String name;
    public final double readRatio; // 0..1

    public Scenario(String name, double readRatio) {
        this.name = name;
        this.readRatio = readRatio;
    }
}

