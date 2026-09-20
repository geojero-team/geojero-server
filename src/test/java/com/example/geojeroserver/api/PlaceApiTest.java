package com.example.geojeroserver.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
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
 * 맛집 14 · 숙소 7 · 카페 9 (V40 · V43 · V44~V46 · V47 — 기준문서 §6 「맛집 · 숙소 · 카페」). TourAPI 는 가짜로 바꿔 끼운다 — 테스트에는 키가 없다.
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

  @Test void 맛집_목록은_T맵_인기순_14곳이고_카드는_대표메뉴_쉬는날_사진_가까운스팟() throws Exception {
    when(tourApi.placeInfo(any(), eq("39"))).thenReturn(food("문어해물칼국수", "연중무휴"));
    mvc.perform(get("/api/places?kind=FOOD"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.places.length()").value(14))
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
        .andExpect(jsonPath("$.places[12].lng").value(128.5256862))
        // 2026-09-20 사용자 — 거제 9미 중 비어 있던 2미 「거제굴구이」를 채웠다(V47). 포로수용소에서 280m 다
        .andExpect(jsonPath("$.places[13].placeId").value(2900574))
        .andExpect(jsonPath("$.places[13].name").value("포로수용소굴구이"))
        .andExpect(jsonPath("$.places[13].nearSpot.shortName").value("포로수용소"))
        .andExpect(jsonPath("$.places[13].nearSpot.distanceM").value(280));
  }

  /**
   * 거제 9미(거제시 「9경9미9품」 — tour.geoje.go.kr)는 **음식 아홉 가지**이고 거제시가 식당을 지정하지 않는다
   * (9미 상세 페이지 아홉 곳 모두 식당 목록 없음, 2026-09-20 확인). 그래서 어느 가게가 어느 미인지는
   * **TourAPI 대표메뉴 · 가게 이름(원문)과 거제시 9미 목록(원문)의 대조**이고 그 결과를 V47 이 DB 에 적어 둔다
   * — 9경 번호를 `pois.nine_scenic_no` 가 쥐는 것과 같은 자리다(기준문서 §6).
   */
  @Test void 맛집_카드는_거제_9미_번호를_준다() throws Exception {
    when(tourApi.placeInfo(any(), eq("39"))).thenReturn(food("문어해물칼국수", "연중무휴"));
    mvc.perform(get("/api/places?kind=FOOD"))
        .andExpect(status().isOk())
        // 백만석 — 대표메뉴 「멍게비빔밥+대구탕+양념게장」에 1미(대구탕)와 3미(멍게 · 성게비빔밥)가 같이 있다
        .andExpect(jsonPath("$.places[9].name").value("백만석"))
        .andExpect(jsonPath("$.places[9].nineTasteNos").value(contains(1, 3)))
        // 6미 멸치쌈밥 — 이름 + 대표메뉴 / 대표메뉴
        .andExpect(jsonPath("$.places[8].name").value("거제멸치쌈밥"))
        .andExpect(jsonPath("$.places[8].nineTasteNos").value(contains(6)))
        .andExpect(jsonPath("$.places[5].name").value("점순이네밥집"))
        .andExpect(jsonPath("$.places[5].nineTasteNos").value(contains(6)))
        // 7미 생선회 · 물회 — 이름과 대표메뉴에 「물회」가 있다
        .andExpect(jsonPath("$.places[7].name").value("웅아물회"))
        .andExpect(jsonPath("$.places[7].nineTasteNos").value(contains(7)))
        .andExpect(jsonPath("$.places[10].name").value("초정명가횟집 물회"))
        .andExpect(jsonPath("$.places[10].nineTasteNos").value(contains(7)))
        // 2미 굴구이 — 가게 이름이 근거다(대표메뉴는 「굴코스요리세트」)
        .andExpect(jsonPath("$.places[13].nineTasteNos").value(contains(2)))
        // 9미가 아닌 곳은 빈 배열이다 — 「아직 모른다」가 아니라 「원문이 9미라고 말하지 않는다」다
        .andExpect(jsonPath("$.places[2].name").value("하면옥"))
        .andExpect(jsonPath("$.places[2].nineTasteNos").value(empty()));
  }

  /**
   * 강성횟집 · 어방가에는 9미 배지가 붙지 않는다 — 대표메뉴가 「강성스페셜」 · 「어방스페셜」이라 음식 이름이 걸리지 않고,
   * 「횟집」은 가게 종류지 9미 음식 이름이 아니다. 「횟집」을 7미로 세면 TourAPI 거제 횟집 41곳이 전부 7미가 되어
   * 배지가 뜻을 잃는다(2026-09-20 사용자 확인).
   */
  @Test void 횟집이라는_이름만으로는_9미가_되지_않는다() throws Exception {
    when(tourApi.placeInfo(any(), eq("39"))).thenReturn(food("강성스페셜", "연중무휴"));
    mvc.perform(get("/api/places?kind=FOOD"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.places[1].name").value("강성횟집"))
        .andExpect(jsonPath("$.places[1].nineTasteNos").value(empty()))
        .andExpect(jsonPath("$.places[6].name").value("어방가"))
        .andExpect(jsonPath("$.places[6].nineTasteNos").value(empty()));
  }

  @Test void 숙소와_카페에는_9미가_없다() throws Exception {
    when(tourApi.placeInfo(any(), eq("32"))).thenReturn(stay());
    when(tourApi.placeInfo(any(), eq("39"))).thenReturn(food("온더선셋라떼", "연중무휴"));
    mvc.perform(get("/api/places?kind=STAY"))
        .andExpect(jsonPath("$.places[0].nineTasteNos").value(empty()));
    mvc.perform(get("/api/places?kind=CAFE"))
        .andExpect(jsonPath("$.places[0].nineTasteNos").value(empty()));
  }

  @Test void 맛집_상세도_9미_번호를_준다() throws Exception {
    when(tourApi.placeInfo(eq("578976"), eq("39"))).thenReturn(food("멍게비빔밥+대구탕+양념게장", "연중무휴"));
    mvc.perform(get("/api/places/578976"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("백만석"))
        .andExpect(jsonPath("$.nineTasteNos").value(contains(1, 3)));
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
    // CAFE 는 2026-09-20 에 쓰는 종류가 됐다(V44) — 없는 종류로 바꿔 둔다
    mvc.perform(get("/api/places?kind=SHOP")).andExpect(status().isBadRequest());
  }

  /**
   * 카페 7곳(V44, 2026-09-20 사용자 — 팀 의논). 고른 방법은 맛집과 **같다** —
   * T맵 인기순 → TourAPI 에 있고 사진이 있을 것 → 체인 제외. 우리가 고르지 않는다(기준문서 §6).
   * TourAPI 분류가 맛집과 같은 음식점(39)이라 카드도 같은 모양이다 — 대표 메뉴 · 쉬는 날 · 사진 · 가까운 스팟.
   */
  @Test void 카페_목록은_T맵_인기순_9곳이고_카드는_맛집과_같은_모양이다() throws Exception {
    when(tourApi.placeInfo(any(), eq("39"))).thenReturn(food("선셋커피", "연중무휴"));
    mvc.perform(get("/api/places?kind=CAFE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.places.length()").value(9))
        .andExpect(jsonPath("$.places[0].placeId").value(2857087))
        .andExpect(jsonPath("$.places[0].kind").value("CAFE"))
        .andExpect(jsonPath("$.places[0].name").value("온더선셋"))
        // category 자리에 대표 메뉴가 온다(숙소의 「2성 호텔」자리) — 맛집과 같은 분기
        .andExpect(jsonPath("$.places[0].category").value("선셋커피"))
        .andExpect(jsonPath("$.places[0].restDay").value("연중무휴"))
        .andExpect(jsonPath("$.places[0].imageUrl").value(IMG))
        .andExpect(jsonPath("$.places[0].grade").doesNotExist())
        .andExpect(jsonPath("$.places[0].lat").value(34.9162065))
        .andExpect(jsonPath("$.places[0].lng").value(128.5197758))
        .andExpect(jsonPath("$.places[6].name").value("글래씨스"))
        // 남부권 둘은 인기순 자리 그대로다 — 무지개펜션 39위가 썬트리팜 46위보다 앞이다(V46)
        .andExpect(jsonPath("$.places[7].placeId").value(3060359))
        .andExpect(jsonPath("$.places[7].name").value("무지개펜션에스프레소"))
        .andExpect(jsonPath("$.places[8].placeId").value(2912639))
        .andExpect(jsonPath("$.places[8].name").value("썬트리팜 카페"));
  }

  /**
   * 남부권(바람의언덕 · 해금강 · 학동)에 카페가 한 곳도 없던 것을 채웠다(2026-09-20 사용자).
   * T맵 남부권 순위 1 · 4 · 6위 카페는 **TourAPI 에 아예 없어** 넣지 못했다 — 원천이 없으면 넣지 않는다.
   */
  @Test void 남부권_카페_둘이_남부면_스팟_옆에_있다() throws Exception {
    when(tourApi.placeInfo(any(), eq("39"))).thenReturn(food("아메리카노", "연중무휴"));
    mvc.perform(get("/api/places?kind=CAFE"))
        .andExpect(jsonPath("$.places[7].name").value("무지개펜션에스프레소"))
        .andExpect(jsonPath("$.places[7].nearSpot.shortName").value("여차홍포해안도로"))
        // 대표 사진이 통나무집 외관이라 카드가 펜션으로 읽혔다 — 목록 사진만 등록 순 첫 추가 사진으로(V46)
        .andExpect(jsonPath("$.places[7].imageUrl")
            .value("https://tong.visitkorea.or.kr/cms/resource/54/3052254_image2_1.JPG"))
        .andExpect(jsonPath("$.places[8].name").value("썬트리팜 카페"))
        .andExpect(jsonPath("$.places[8].nearSpot.shortName").value("도장포유람선"));
  }

  /**
   * 목록 사진(카드 · 홈 지도 핀)이 **세로**면 지도에서 액자가 길쭉해 다른 핀과 안 맞는다(2026-09-20 사용자).
   * 맛집 · 숙소에 이미 쓰는 규칙(V42)을 카페에도 쓴다 — 대표가 세로면 등록 순 첫 가로 사진.
   * 씨야드 · 짹짹커피는 TourAPI · 관광사진 API 어디에도 가로가 없어 그대로 둔다(2026-09-20 실측).
   */
  @Test void 엄마의_바다_목록사진은_세로인_대표가_아니라_첫_가로_사진이다() throws Exception {
    when(tourApi.placeInfo(any(), eq("39"))).thenReturn(food("바다 아이스크림 라떼", "연중무휴"));
    mvc.perform(get("/api/places?kind=CAFE"))
        .andExpect(jsonPath("$.places[2].name").value("엄마의 바다"))
        .andExpect(jsonPath("$.places[2].imageUrl").value(tong("74/3579074")));
  }

  /**
   * 카페를 넣은 이유가 이것이다 — 일곱 중 셋이 스팟에서 **걸어서** 간다.
   * 맛집 13곳은 지세포에 몰려 있어 코스와 떨어져 있었다. 이 거리가 멀어지면 카페를 넣은 근거가 약해진다.
   */
  @Test void 카페는_스팟_바로_옆에_있다() throws Exception {
    when(tourApi.placeInfo(any(), eq("39"))).thenReturn(food("아이스크림 라떼", "연중무휴"));
    mvc.perform(get("/api/places?kind=CAFE"))
        .andExpect(jsonPath("$.places[3].name").value("씨야드"))
        .andExpect(jsonPath("$.places[3].nearSpot.shortName").value("거제식물원"))
        .andExpect(jsonPath("$.places[4].name").value("짹짹커피 거제도본점"))
        .andExpect(jsonPath("$.places[4].nearSpot.shortName").value("포로수용소"))
        .andExpect(jsonPath("$.places[5].name").value("심해"))
        .andExpect(jsonPath("$.places[5].nearSpot.shortName").value("매미성"));
  }

  /**
   * 카페 상세는 **영업시간 · 쉬는 날**이다(숙소의 체크인 · 부대시설이 아니라). 사진은 **TourAPI 순서 그대로** —
   * 맛집의 「음식 사진 먼저」(V43)를 카페에는 쓰지 않는다(2026-09-20 사용자: 거제 카페는 바다 · 노을이 주인공이다).
   */
  @Test void 카페_상세는_영업시간과_쉬는날을_주고_사진은_TourAPI_순서_그대로다() throws Exception {
    String view = "https://tong.visitkorea.or.kr/cms/resource/00/second.jpg";
    when(tourApi.placeInfo("2783404", "39")).thenReturn(food("아이스크림 라떼", "연중무휴"));
    when(tourApi.placeImages("2783404", null)).thenReturn(List.of(IMG, view));
    mvc.perform(get("/api/places/2783404"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("심해"))
        .andExpect(jsonPath("$.kind").value("CAFE"))
        .andExpect(jsonPath("$.category").value("아이스크림 라떼"))
        .andExpect(jsonPath("$.grade").doesNotExist())
        .andExpect(jsonPath("$.bookingUrl").doesNotExist())
        .andExpect(jsonPath("$.detail.openTime").value("10:30~20:30\n준비시간 15:00~17:00"))
        .andExpect(jsonPath("$.detail.restDay").value("연중무휴"))
        .andExpect(jsonPath("$.detail.checkIn").doesNotExist())
        .andExpect(jsonPath("$.detail.facilities").doesNotExist())
        .andExpect(jsonPath("$.detail.images").value(contains(IMG, view)))
        .andExpect(jsonPath("$.nearSpots[0].shortName").value("매미성"));
    // 메뉴 사진 칸(imageYN=N)은 부르지 않는다 — 카페 일곱 곳 모두 0장이라 호출만 는다
    verify(tourApi, never()).placeMenuImages(any());
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
