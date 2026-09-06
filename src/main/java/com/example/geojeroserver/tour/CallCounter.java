package com.example.geojeroserver.tour;

/** 일일 호출 한도 게이트. true면 호출 허용. */
public interface CallCounter {
  boolean tryAcquire();
}
