package com.example.geojeroserver.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * [임시] 시드에 넣을 tour_content_id·좌표를 사람이 확인하려고 잠깐 두는 경로다.
 *
 * TourAPI 키는 배포 환경에만 있어서 로컬에서는 조회할 수 없다. 추측으로 채우면
 * 안 되는 값이라(절대규칙 1) 실호출로 확인한다.
 *
 * **확인이 끝나면 이 파일을 통째로 지운다.** 제품 기능이 아니다.
 *
 * 공모전 준수: areacode/sigungucode 대신 lDongRegnCd=48 / lDongSignguCd=310(거제).
 */
@RestController
public class TourLookupController {
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
  private final ObjectMapper om = new ObjectMapper();

  @Value("${TOUR_INFO_KEY:}")
  String serviceKey;

  @GetMapping("/api/dev/tour-lookup")
  public List<Map<String, String>> lookup(@RequestParam String keyword) throws Exception {
    if (serviceKey == null || serviceKey.isBlank()) {
      return List.of(Map.of("error", "TOUR_INFO_KEY 미설정"));
    }
    String url = "https://apis.data.go.kr/B551011/KorService2/searchKeyword2"
        + "?serviceKey=" + serviceKey + "&MobileOS=WEB&MobileApp=geojero&_type=json"
        + "&keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
        + "&lDongRegnCd=48&lDongSignguCd=310&numOfRows=50";
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200) {
      return List.of(Map.of("error", "TourAPI HTTP " + res.statusCode()));
    }

    List<Map<String, String>> out = new ArrayList<>();
    for (var it : om.readTree(res.body()).path("response").path("body")
        .path("items").path("item")) {
      Map<String, String> row = new LinkedHashMap<>();
      row.put("title", it.path("title").asText(null));
      row.put("contentId", it.path("contentid").asText(null));
      row.put("contentTypeId", it.path("contenttypeid").asText(null));
      row.put("lat", it.path("mapy").asText(null));   // TourAPI는 mapy가 위도다
      row.put("lng", it.path("mapx").asText(null));
      row.put("addr", it.path("addr1").asText(null));
      row.put("firstImage", it.path("firstimage").asText(null));
      out.add(row);
    }
    return out;
  }

  /** 대표 사진의 저작권 구분까지 봐야 화면에 실을 수 있는지 안다. */
  @GetMapping("/api/dev/tour-raw")
  public Map<String, Object> raw(@RequestParam String contentId) throws Exception {
    Map<String, Object> out = new LinkedHashMap<>();
    String base = "https://apis.data.go.kr/B551011/KorService2/";
    String tail = "?serviceKey=" + serviceKey + "&MobileOS=WEB&MobileApp=geojero&_type=json"
        + "&contentId=" + contentId;

    var common = om.readTree(get(base + "detailCommon2" + tail))
        .path("response").path("body").path("items").path("item").path(0);
    out.put("title", common.path("title").asText(null));
    out.put("firstimage", common.path("firstimage").asText(null));
    out.put("cpyrhtDivCd", common.path("cpyrhtDivCd").asText(null));

    List<String> imgs = new ArrayList<>();
    for (var it : om.readTree(get(base + "detailImage2" + tail + "&imageYN=Y&numOfRows=20"))
        .path("response").path("body").path("items").path("item")) {
      imgs.add(it.path("cpyrhtDivCd").asText("?"));
    }
    out.put("imageCopyrights", imgs);
    return out;
  }

  private String get(String url) throws Exception {
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200) {
      throw new IllegalStateException("TourAPI HTTP " + res.statusCode());
    }
    return res.body();
  }
}
