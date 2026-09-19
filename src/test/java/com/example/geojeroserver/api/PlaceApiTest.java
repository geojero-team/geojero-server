package com.example.geojeroserver.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.geojeroserver.tour.TourApiClient;
import com.example.geojeroserver.tour.TourApiClient.PlaceInfo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 맛집 12 · 숙소 7 (V40, 2026-09-19 사용자 결정 — 기준문서 §6 「맛집 · 숙소」). TourAPI 는 가짜로 바꿔 끼운다 — 테스트에는 키가 없다.
 * 가까운 스팟은 진짜 pois(V1~)로 잰다 — 거리 · 제외 규칙이 실제 데이터에서 맞는지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlaceApiTest {
  @Autowired MockMvc mvc;
  @MockitoBean TourApiClient tourApi;

  private static final String IMG = "https://tong.visitkorea.or.kr/cms/resource/00/first.jpg";

  private static PlaceInfo food(String menu, String restDay) {
    return new PlaceInfo("멸치쌈밥 원문 소개", "경상남도 거제시 일운면 지세포해안로 12", IMG,
        Map.of("firstmenu", menu, "restdatefood", restDay, "opentimefood", "10:30~20:30\n준비시간 15:00~17:00",
            "treatmenu", "멸치쌈밥정식 B코스 / 멸치회무침 등"));
  }

  private static PlaceInfo stay() {
    return new PlaceInfo("호텔 자기 홍보 글", "경상남도 거제시 일운면 거제대로 2660", IMG,
        Map.of("checkintime", "15:00", "checkouttime", "11:00", "subfacility", "사우나 / 산책로 / 노래방"));
  }

  @Test void 맛집_목록은_T맵_인기순_12곳이고_카드는_대표메뉴_쉬는날_사진_가까운스팟() throws Exception {
    when(tourApi.placeInfo(any(), eq("39"))).thenReturn(food("문어해물칼국수", "연중무휴"));
    mvc.perform(get("/api/places?kind=FOOD"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.places.length()").value(12))
        .andExpect(jsonPath("$.places[0].placeId").value(2783696))
        .andExpect(jsonPath("$.places[0].kind").value("FOOD"))
        .andExpect(jsonPath("$.places[0].name").value("대박난맛집"))
        .andExpect(jsonPath("$.places[0].category").value("문어해물칼국수"))
        .andExpect(jsonPath("$.places[0].restDay").value("연중무휴"))
        .andExpect(jsonPath("$.places[0].imageUrl").value(IMG))
        .andExpect(jsonPath("$.places[0].grade").doesNotExist())
        .andExpect(jsonPath("$.places[0].nearSpot.shortName").value("학동몽돌해변"))
        .andExpect(jsonPath("$.places[11].name").value("장수굴국밥"));
  }

  @Test void 숙소_목록은_7곳이고_종류는_우리가_가진_등급_값이다() throws Exception {
    when(tourApi.placeInfo(any(), eq("32"))).thenReturn(stay());
    mvc.perform(get("/api/places?kind=STAY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.places.length()").value(7))
        .andExpect(jsonPath("$.places[0].name").value("소노캄 거제"))
        .andExpect(jsonPath("$.places[0].category").value("콘도"))
        .andExpect(jsonPath("$.places[5].name").value("호텔상상"))
        .andExpect(jsonPath("$.places[5].category").value("2성 호텔"))
        .andExpect(jsonPath("$.places[5].grade").value(2))
        .andExpect(jsonPath("$.places[5].restDay").doesNotExist());
  }

  @Test void 관광정보를_못받아도_목록은_이름_종류_가까운스팟으로_나온다() throws Exception {
    when(tourApi.placeInfo(any(), any())).thenReturn(null);
    mvc.perform(get("/api/places?kind=STAY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.places.length()").value(7))
        .andExpect(jsonPath("$.places[0].category").value("콘도"))
        .andExpect(jsonPath("$.places[0].imageUrl").doesNotExist())
        .andExpect(jsonPath("$.places[0].nearSpot.shortName").value("거제씨월드"));
  }

  @Test void 종류가_틀리면_400() throws Exception {
    mvc.perform(get("/api/places?kind=CAFE")).andExpect(status().isBadRequest());
  }

  @Test void 없는_곳은_404() throws Exception {
    mvc.perform(get("/api/places/129479")).andExpect(status().isNotFound());
  }

  @Test void 맛집_상세는_주소_사진_소개_영업시간_쉬는날과_가까운스팟() throws Exception {
    when(tourApi.placeInfo("2858010", "39")).thenReturn(food("멸치쌈밥정식 A코스", "매월 두번째·네번째 수요일"));
    when(tourApi.placeImages("2858010", null)).thenReturn(List.of(IMG, "https://tong.visitkorea.or.kr/cms/resource/00/second.jpg"));
    mvc.perform(get("/api/places/2858010"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.placeId").value(2858010))
        .andExpect(jsonPath("$.name").value("거제멸치쌈밥"))
        .andExpect(jsonPath("$.category").value("멸치쌈밥정식 A코스"))
        .andExpect(jsonPath("$.bookingUrl").doesNotExist())
        .andExpect(jsonPath("$.detail.source").value("TourAPI"))
        .andExpect(jsonPath("$.detail.address").value("경상남도 거제시 일운면 지세포해안로 12"))
        // 대표 사진이 첫 장 · 추가 사진과 겹치면 한 번만
        .andExpect(jsonPath("$.detail.images", contains(IMG, "https://tong.visitkorea.or.kr/cms/resource/00/second.jpg")))
        .andExpect(jsonPath("$.detail.overview").value("멸치쌈밥 원문 소개"))
        .andExpect(jsonPath("$.detail.openTime").value("10:30~20:30\n준비시간 15:00~17:00"))
        .andExpect(jsonPath("$.detail.restDay").value("매월 두번째·네번째 수요일"))
        // 취급 메뉴(detailIntro2 treatmenu) — 12곳 모두 있다. 가격은 TourAPI 에 없다(2026-09-19 조회)
        .andExpect(jsonPath("$.detail.menus").value("멸치쌈밥정식 B코스 / 멸치회무침 등"))
        // 5km 안에서 가까운 순 — 배로만 가는 공곶이·내도(4.7km)는 빠진다
        .andExpect(jsonPath("$.nearSpots[*].shortName", contains("조선해양문화관", "거제씨월드")))
        .andExpect(jsonPath("$.nearSpots[0].distanceM").isNumber())
        .andExpect(jsonPath("$.nearSpots[0].lat").isNumber());
  }

  @Test void 숙소_상세는_체크인_체크아웃_부대시설_예약주소이고_소개는_싣지_않는다() throws Exception {
    when(tourApi.placeInfo("2578495", "32")).thenReturn(stay());
    when(tourApi.placeImages(eq("2578495"), isNull())).thenReturn(List.of());
    mvc.perform(get("/api/places/2578495"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("소노캄 거제"))
        .andExpect(jsonPath("$.category").value("콘도"))
        .andExpect(jsonPath("$.bookingUrl").value("https://www.yeogi.com/domestic-accommodations/6605"))
        .andExpect(jsonPath("$.detail.checkIn").value("15:00"))
        .andExpect(jsonPath("$.detail.checkOut").value("11:00"))
        .andExpect(jsonPath("$.detail.facilities").value("사우나 / 산책로 / 노래방"))
        .andExpect(jsonPath("$.detail.overview").doesNotExist())
        .andExpect(jsonPath("$.detail.images", contains(IMG)));
  }

  @Test void 한화는_국문사진이_없어_영문_contentId_로_사진을_받는다() throws Exception {
    when(tourApi.placeInfo("2660777", "32")).thenReturn(new PlaceInfo(null, "경상남도 거제시 장목면 거제북로 2501-40", null, Map.of()));
    when(tourApi.placeImages("2660777", "3445089")).thenReturn(List.of("https://tong.visitkorea.or.kr/87.jpg", "https://tong.visitkorea.or.kr/76.jpg"));
    mvc.perform(get("/api/places/2660777"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.detail.images", contains("https://tong.visitkorea.or.kr/87.jpg", "https://tong.visitkorea.or.kr/76.jpg")));
    mvc.perform(get("/api/places?kind=STAY"))
        .andExpect(jsonPath("$.places[1].name").value("한화리조트 거제 벨버디어"))
        .andExpect(jsonPath("$.places[1].imageUrl").value("https://tong.visitkorea.or.kr/87.jpg"));
  }

  @Test void 관광정보를_못받으면_FALLBACK_이유와_시각_가까운스팟과_예약은_남는다() throws Exception {
    when(tourApi.placeInfo(any(), any())).thenReturn(null);
    mvc.perform(get("/api/places/976736"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.detail.source").value("FALLBACK"))
        .andExpect(jsonPath("$.detail.reason").value("관광정보 확인 실패"))
        .andExpect(jsonPath("$.detail.checkedAt").isString())
        .andExpect(jsonPath("$.bookingUrl").value("https://www.yeogi.com/domestic-accommodations/57408"))
        .andExpect(jsonPath("$.nearSpots[*].shortName", contains("거제씨월드", "조선해양문화관", "양지암조각공원")));
  }

  @Test void 배로만_가는_스팟은_바다_건너_직선이라_가까운스팟에서_빠진다() throws Exception {
    when(tourApi.placeInfo(any(), any())).thenReturn(null);
    // 강성횟집 — 직선으로는 지심도(3.4km)가 5km 안이지만 배로만 간다
    mvc.perform(get("/api/places/2753311"))
        .andExpect(jsonPath("$.nearSpots[*].shortName", not(hasItem("지심도"))))
        .andExpect(jsonPath("$.nearSpots[*].shortName", contains("조선해양문화관", "거제씨월드")));
  }
}
