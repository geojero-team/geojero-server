package com.example.geojeroserver.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.geojeroserver.tour.TourApiClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 목록 썸네일 폴백(2026-09-15) — 대표 사진도 관광사진도 없으면 상세 추가 사진(detailImage2) 첫 장.
 *
 * 공곶이(V30, 9경 7경)가 그 모양이다: TourAPI 대표 사진이 없고 추가 사진 12장이 전부 Type1 이다. 폴백이 없으면
 * 스팟 카드 · 시간표 탭 줄 · 홈 지도 핀이 자리 그림으로 나간다. TourAPI 는 가짜로 바꿔 끼운다 — 테스트에는 키가 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PoiThumbnailTest {
  @Autowired MockMvc mvc;
  @MockitoBean TourApiClient tourApi;

  private static final String GONGGOTI = "2536196";
  private static final String FIRST = "https://tong.visitkorea.or.kr/cms2/website/00/gonggoti-1.jpg";

  @Test void 대표사진도_관광사진도_없으면_추가사진_첫장을_쓴다() throws Exception {
    // 대표 사진 없음(imageUrl 이 없는 폴백) · 관광사진 0건(가짜의 기본값 빈 목록) · 공곶이만 추가 사진이 있다
    when(tourApi.detail(any(), any(), any())).thenReturn(Map.of("source", "FALLBACK"));
    when(tourApi.images(GONGGOTI, "ko")).thenReturn(List.of(FIRST, "https://tong.visitkorea.or.kr/cms2/website/00/gonggoti-2.jpg"));

    mvc.perform(get("/api/pois?withImages=true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pois[?(@.shortName == '공곶이·내도')].imageUrl",
            org.hamcrest.Matchers.contains(FIRST)));

    // 화면에 안 뜨는 곳(theme NULL — 신선대 129508)은 부르지 않는다. 목록에서 추가 사진을 아끼던 이유(하루 호출 한도)를 지킨다.
    verify(tourApi, never()).images(eq("129508"), any());
  }
}
