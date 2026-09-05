package com.example.geojeroserver.api;

import com.example.geojeroserver.engine.Snapshot;
import com.example.geojeroserver.snapshot.SnapshotRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** 날짜별 스냅샷 10분 캐시. 개편 반영은 재적재 + TTL 만료로. */
@Service
public class SnapshotService {
  private static final long TTL_MS = 10 * 60 * 1000;

  private record Entry(Snapshot snap, long at) {}

  private final Map<String, Entry> cache = new ConcurrentHashMap<>();
  private final SnapshotRepository repo;

  public SnapshotService(SnapshotRepository repo) {
    this.repo = repo;
  }

  public Snapshot forDate(String date) {
    var e = cache.get(date);
    if (e != null && System.currentTimeMillis() - e.at() < TTL_MS) return e.snap();
    var snap = repo.build(LocalDate.parse(date));
    cache.put(date, new Entry(snap, System.currentTimeMillis()));
    return snap;
  }
}
