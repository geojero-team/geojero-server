package com.example.geojeroserver.engine;

import java.util.List;

public record Snapshot(String date, DayClass dayClass, List<Trip> trips, List<Alert> alerts) {}
