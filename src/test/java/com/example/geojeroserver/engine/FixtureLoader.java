package com.example.geojeroserver.engine;

import com.fasterxml.jackson.databind.ObjectMapper;

/** TS 스냅샷 빌더의 최종 산출물(JSON)을 정답 데이터로 로드한다 (geojero repo 커밋 e1a98dd). */
public final class FixtureLoader {
  private FixtureLoader() {}

  public static Snapshot load(String name) {
    try (var in = FixtureLoader.class.getResourceAsStream("/" + name)) {
      return new ObjectMapper().readValue(in, Snapshot.class);
    } catch (Exception e) {
      throw new IllegalStateException("픽스처 로드 실패: " + name, e);
    }
  }
}
