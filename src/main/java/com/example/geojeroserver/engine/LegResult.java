package com.example.geojeroserver.engine;

public record LegResult(Verdict ok, Integer departMin, Integer arriveMin, String reason) {}
