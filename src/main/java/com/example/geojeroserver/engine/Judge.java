package com.example.geojeroserver.engine;

import java.util.List;

public final class Judge {
  private Judge() {}
  public static JudgeResult judge(Snapshot s, List<Leg> legs, int startMin) {
    throw new UnsupportedOperationException("not ported");
  }
  public static Integer lastDeparture(Snapshot s, String from, String to) {
    throw new UnsupportedOperationException("not ported");
  }
  public static long countTripsOfRoute(Snapshot s, String routeNo, Integer direction) {
    throw new UnsupportedOperationException("not ported");
  }
  public static long countTripsServingStop(Snapshot s, String stop, int direction) {
    throw new UnsupportedOperationException("not ported");
  }
  public static Snapshot subsetByRoute(Snapshot s, String routeNo) {
    throw new UnsupportedOperationException("not ported");
  }
}
