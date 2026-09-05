package com.example.geojeroserver.engine;

public sealed interface Leg permits Leg.Bus, Leg.Fixed {
  record Bus(String from, String to, boolean boardOnly) implements Leg {}
  record Fixed(String endStop, int endMin) implements Leg {}
}
