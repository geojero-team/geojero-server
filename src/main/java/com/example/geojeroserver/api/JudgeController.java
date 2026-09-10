package com.example.geojeroserver.api;

import com.example.geojeroserver.engine.Judge;
import com.example.geojeroserver.engine.Leg;
import com.example.geojeroserver.engine.Snapshot;
import com.example.geojeroserver.engine.TimeUtil;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JudgeController {
  public record LegReq(String type, String from, String to, Boolean boardOnly,
                       String endStop, String endTime) {}
  public record JudgeReq(String date, String startTime, List<LegReq> legs) {}
  public record LegRes(String ok, String depart, String arrive, String reason) {}
  public record AlertRes(String kind, String stop, String reason) {}
  public record JudgeRes(String feasible, String dayClass, List<LegRes> legs,
                         List<AlertRes> alerts) {}

  private final SnapshotService snapshots;

  public JudgeController(SnapshotService snapshots) {
    this.snapshots = snapshots;
  }

  public static List<Leg> toEngineLegs(List<LegReq> legs) {
    return legs.stream().<Leg>map(l -> "FIXED".equals(l.type())
        ? new Leg.Fixed(l.endStop(), TimeUtil.hhmmToMin(l.endTime()))
        : new Leg.Bus(l.from(), l.to(), Boolean.TRUE.equals(l.boardOnly()))).toList();
  }

  static JudgeRes toRes(Snapshot snap, com.example.geojeroserver.engine.JudgeResult r) {
    return new JudgeRes(r.feasible().name(), snap.dayClass().name(),
        r.legs().stream().map(x -> new LegRes(x.ok().name(),
            Fmt.hm(x.departMin()), Fmt.hm(x.arriveMin()), x.reason())).toList(),
        snap.alerts().stream()
            .map(a -> new AlertRes(a.kind(), a.stop(), a.reason())).toList());
  }

  @PostMapping("/api/judge")
  public JudgeRes judge(@RequestBody JudgeReq req) {
    var snap = snapshots.forDate(req.date());
    var r = Judge.judge(snap, toEngineLegs(req.legs()), TimeUtil.hhmmToMin(req.startTime()));
    return toRes(snap, r);
  }
}
