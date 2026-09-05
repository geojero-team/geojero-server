package com.example.geojeroserver.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlertController {
  public record AlertDto(String kind, String stop, String route,
                         String dateFrom, String dateTo, String reason) {}
  public record AlertsRes(List<AlertDto> alerts) {}

  private final SnapshotService snapshots;

  public AlertController(SnapshotService snapshots) {
    this.snapshots = snapshots;
  }

  @GetMapping("/api/alerts")
  public AlertsRes alerts(@RequestParam String date) {
    var snap = snapshots.forDate(date);
    return new AlertsRes(snap.alerts().stream()
        .map(a -> new AlertDto(a.kind(), a.stop(), a.routeNo(), null, null, a.reason()))
        .toList());
  }
}
