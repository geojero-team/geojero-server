package com.example.geojeroserver.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
 * 맛집 13 · 숙소 7 (V40 · V43, 2026-09-19 사용자 결정 — 기준문서 §6 「맛집 · 숙소」). TourAPI 는 가짜로 바꿔 끼운다 — 테스트에는 키가 없다.
 * 가까운 스팟은 진짜 pois(V1~)로 잰다 — 거리 · 제외 규칙이 실제 데이터에서 맞는지 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlaceApiTest {
  @Autowired MockMvc mvc;
  @MockitoBean TourApiClient tourApi;

  private static final String IMG = "https://tong.visitkorea.or.kr/cms/resource/00/first.jpg";

  /** TourAPI 사진 주소 — 「62/2787162」 → https://tong.visitkorea.or.kr/cms/resource/62/2787162_image2_1.jpg */
  private static String tong(String path) {
    return "https://tong.visitkorea.or.kr/cms/resource/" + path + "_image2_1.jpg";
  }

  private static PlaceInfo food(String menu, String restDay) {
    return new PlaceInfo("멸치쌈밥 원문 소개", "경상남도 거제시 일운면 지세포해안로 12", IMG,
        Map.of("firstmenu", menu, "restdatefood", restDay, "opentimefood", "10:30~20:30\n준비시간 15:00~17:00"));
  }

  private static PlaceInfo stay() {
    return new PlaceInfo("호텔 자기 홍보 글", "경상남도 거제시 일운면 거제대로 2660", IMG,
        Map.of("checkintime", "15:00", "checkouttime", "11:00", "subfacility", "사우나 / 산책로 / 노래방"));
  }

  @Test void 맛집_목록은_T맵_인기순_13곳이고_카드는_대표메뉴_쉬는날_사진_가까운스팟() throws Exception {
    when(tourApi.placeInfo(any(), eq("39"))).thenReturn(food("문어해물칼국수", "연중무휴"));
    mvc.perform(get("/api/places?kind=FOOD"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.places.length()").value(13))
        .andExpect(jsonPath("$.places[0].placeId").value(2783696))
        .andExpect(jsonPath("$.places[0].kind").value("FOOD"))
        .andExpect(jsonPath("$.places[0].name").value("대박난맛집"))
        .andExpect(jsonPath("$.places[0].category").value("문어해물칼국수"))
        .andExpect(jsonPath("$.places[0].restDay").value("연중무휴"))
        .andExpect(jsonPath("$.places[0].imageUrl").value(tong("62/2787162")))
        .andExpect(jsonPath("$.places[0].grade").doesNotExist())
        .andExpect(jsonPath("$.places[0].nearSpot.shortName").value("학동몽돌해변"))
        .andExpect(jsonPath("$.places[11].name").value("장수굴국밥"))
        // 2026-09-19 사용자 — 점순이네밥집(음식 사진 0장)은 두고, T맵 인기순에서 음식 사진이 있는 다음 곳을 더했다
        .andExpect(jsonPath("$.places[12].placeId").value(2783397))
        .andExpect(jsonPath("$.places[12].name").value("성포끝집"))
        .andExpect(jsonPath("$.places[12].lat").value(34.9224143))
        .andExpect(jsonPath("$.places[12].lng").value(128.5256862));
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

  /**
   * 맛집 목록 사진(카드 · 홈 지도 핀)은 **첫 음식 사진**이다(2026-09-19 사용자 — 「음식점이니 음식 사진이 대부분 차지했으면」).
   * 대표 사진은 대개 가게 외관이다. 음식 사진은 TourAPI 추가 사진 · 메뉴 사진 중 등록 순 첫 장(V43).
   */
  @Test void 맛집_목록_사진은_첫_음식_사진이다_없으면_대표사진() throws Exception {
    when(tourApi.placeInfo(any(), eq("39"))).thenReturn(food("해산물", "연중무휴"));
    mvc.perform(get("/api/places?kind=FOOD"))
        // 어방가 — V42 의 첫 가로 사진(가게 안)이 아니라 첫 음식 사진
        .andExpect(jsonPath("$.places[6].name").value("어방가"))
        .andExpect(jsonPath("$.places[6].imageUrl").value(tong("06/2778406")))
        // 백만석 — 음식 사진은 메뉴 사진(imageYN=N) 칸에만 있다
        .andExpect(jsonPath("$.places[9].imageUrl").value("https://tong.visitkorea.or.kr/cms/resource/29/3043329_image2_1.JPG"))
        // 점순이네밥집 — TourAPI 에 음식 사진이 없어 대표 사진 그대로
        .andExpect(jsonPath("$.places[5].name").value("점순이네밥집"))
        .andExpect(jsonPath("$.places[5].imageUrl").value(IMG))
        // 초정명가 — 대표 사진이 이미 음식이라 그대로
        .andExpect(jsonPath("$.places[10].imageUrl").value(IMG));
  }

  @Test void 목록에도_좌표가_있어_홈_지도에_찍는다_관광정보와_무관한_우리_DB_값() throws Exception {
    when(tourApi.placeInfo(any(), any())).thenReturn(null);
    mvc.perform(get("/api/places?kind=STAY"))
        .andExpect(jsonPath("$.places[0].name").value("소노캄 거제"))
        .andExpect(jsonPath("$.places[0].lat").value(34.8433682))
        .andExpect(jsonPath("$.places[0].lng").value(128.7029354));
    mvc.perform(get("/api/places?kind=FOOD"))
        .andExpect(jsonPath("$.places[0].lat").value(34.7721525))
        .andExpect(jsonPath("$.places[0].lng").value(128.6380248));
  }

  @Test void 종류가_틀리면_400() throws Exception {
    mvc.perform(get("/api/places?kind=CAFE")).andExpect(status().isBadRequest());
  }

  @Test void 없는_곳은_404() throws Exception {
    mvc.perform(get("/api/places/129479")).andExpect(status().isNotFound());
  }

  @Test void 맛집_상세는_주소_사진_영업시간_쉬는날과_가까운스팟() throws Exception {
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
        // 소개문은 싣지 않는다 — 네이버 · 카카오도 첫 화면에 긴 소개글을 두지 않는다(2026-09-19 사용자)
        .andExpect(jsonPath("$.detail.overview").doesNotExist())
        .andExpect(jsonPath("$.detail.openTime").value("10:30~20:30\n준비시간 15:00~17:00"))
        .andExpect(jsonPath("$.detail.restDay").value("매월 두번째·네번째 수요일"))
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
    // 메뉴 사진(imageYN=N)은 음식점 칸이다 — 숙소는 부르지 않는다
    verify(tourApi, never()).placeMenuImages(any());
  }

  /** 맛집 상세 사진은 음식 사진이 먼저(등록 순), 그다음 대표 사진과 나머지(TourAPI 순서) — 2026-09-19 사용자. */
  @Test void 맛집_상세_사진은_음식_사진이_먼저다() throws Exception {
    String front = tong("67/2787167");
    String inside = tong("65/2787165");
    when(tourApi.placeInfo("2783696", "39")).thenReturn(new PlaceInfo(null, "경상남도 거제시 동부면 거제대로 910", front, Map.of()));
    // TourAPI 가 주는 순서 그대로 — 등록 번호 _2 · _3 · _1 · _4
    when(tourApi.placeImages("2783696", null)).thenReturn(List.of(tong("57/2787157"), tong("60/2787160"), tong("62/2787162"), inside));
    mvc.perform(get("/api/places/2783696"))
        .andExpect(jsonPath("$.detail.images", contains(tong("62/2787162"), tong("57/2787157"), tong("60/2787160"), front, inside)));
  }

  /** TourAPI 가 지금 주지 않는 사진은 DB 에 적혀 있어도 내보내지 않는다 — 순서만 정하는 칸이다. */
  @Test void 음식_사진이라도_TourAPI가_주지_않으면_빠진다() throws Exception {
    String front = tong("67/2787167");
    when(tourApi.placeInfo("2783696", "39")).thenReturn(new PlaceInfo(null, null, front, Map.of()));
    when(tourApi.placeImages("2783696", null)).thenReturn(List.of(tong("57/2787157")));
    mvc.perform(get("/api/places/2783696"))
        .andExpect(jsonPath("$.detail.images", contains(tong("57/2787157"), front)));
  }

  /** 백만석은 음식 사진이 메뉴 사진(detailImage2 imageYN=N) 칸에만 있다 — 맛집은 그 칸도 받는다. */
  @Test void 맛집은_메뉴_사진도_받아_음식_사진으로_앞에_둔다_백만석() throws Exception {
    String front = "https://tong.visitkorea.or.kr/cms/resource/24/3043324_image2_1.JPG";
    String sign = "https://tong.visitkorea.or.kr/cms/resource/17/3043317_image2_1.JPG";
    String m1 = "https://tong.visitkorea.or.kr/cms/resource/29/3043329_image2_1.JPG";
    String m2 = "https://tong.visitkorea.or.kr/cms/resource/28/3043328_image2_1.JPG";
    String m3 = "https://tong.visitkorea.or.kr/cms/resource/27/3043327_image2_1.JPG";
    when(tourApi.placeInfo("578976", "39")).thenReturn(new PlaceInfo(null, "경상남도 거제시 계룡로 47", front, Map.of()));
    when(tourApi.placeImages("578976", null)).thenReturn(List.of(sign));
    when(tourApi.placeMenuImages("578976")).thenReturn(List.of(m1, m2, m3));
    mvc.perform(get("/api/places/578976"))
        .andExpect(jsonPath("$.detail.images", contains(m1, m2, m3, front, sign)));
  }

  @Test void 음식_사진이_없는_점순이네밥집은_TourAPI_순서_그대로() throws Exception {
    String front = tong("95/2916395");
    when(tourApi.placeInfo("2916411", "39")).thenReturn(new PlaceInfo(null, null, front, Map.of()));
    when(tourApi.placeImages("2916411", null)).thenReturn(List.of(tong("92/2916392"), tong("93/2916393")));
    mvc.perform(get("/api/places/2916411"))
        .andExpect(jsonPath("$.detail.images", contains(front, tong("92/2916392"), tong("93/2916393"))));
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

  /**
   * 운영 서버 키로는 영문 사진이 0장이다(2026-09-19 — 운영 키에 영문 관광정보 활용신청이 없는 것으로 보인다. 사용자 키로는 2장).
   * 그래서 한화만 영문 TourAPI 사진 주소 2개를 DB 에 두고(V41), TourAPI 가 0장을 주면 그것을 쓴다. 사진 파일은 그대로 TourAPI 사진 서버에서 온다.
   */
  @Test void TourAPI가_사진을_0장_주면_DB에_둔_사진_주소를_쓴다_한화() throws Exception {
    when(tourApi.placeInfo("2660777", "32")).thenReturn(new PlaceInfo(null, "경상남도 거제시 장목면 거제북로 2501-40", null, Map.of()));
    when(tourApi.placeImages("2660777", "3445089")).thenReturn(List.of());
    mvc.perform(get("/api/places/2660777"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.detail.images", contains(
            "https://tong.visitkorea.or.kr/cms/resource/87/4057087_image2_1.jpg",
            "https://tong.visitkorea.or.kr/cms/resource/76/4057076_image2_1.jpg")));
    mvc.perform(get("/api/places?kind=STAY"))
        .andExpect(jsonPath("$.places[1].imageUrl").value("https://tong.visitkorea.or.kr/cms/resource/87/4057087_image2_1.jpg"));
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

  /**
   * 5km 안에 스팟이 없으면 가장 가까운 한 곳은 둔다(2026-09-19 사용자) — 카드가 말하는 스팟과 같고, 위치 지도가 그곳까지 담아
   * 섬 어디쯤인지 보인다. 세 곳까지 채우지 않는다 — 그다음은 거제식물원 8.7km · 거제현 관아 10km 라 「가까운」이 아니다.
   */
  @Test void 오km_안에_스팟이_없으면_가장_가까운_한_곳만_성포끝집() throws Exception {
    when(tourApi.placeInfo(any(), any())).thenReturn(null);
    mvc.perform(get("/api/places/2783397"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nearSpots[*].shortName", contains("청마기념관")))
        .andExpect(jsonPath("$.nearSpots[0].distanceM").value(org.hamcrest.Matchers.greaterThan(5000)));
  }

  @Test void 배로만_가는_스팟은_바다_건너_직선이라_가까운스팟에서_빠진다() throws Exception {
    when(tourApi.placeInfo(any(), any())).thenReturn(null);
    // 강성횟집 — 직선으로는 지심도(3.4km)가 5km 안이지만 배로만 간다
    mvc.perform(get("/api/places/2753311"))
        .andExpect(jsonPath("$.nearSpots[*].shortName", not(hasItem("지심도"))))
        .andExpect(jsonPath("$.nearSpots[*].shortName", contains("조선해양문화관", "거제씨월드")));
  }
}
