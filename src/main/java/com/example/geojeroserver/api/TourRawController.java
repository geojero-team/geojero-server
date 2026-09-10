package com.example.geojeroserver.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
 * [임시] 사진이 왜 안 나오는지 가르기 위한 진단 경로.
 *
 * 명사해수욕장·도장포유람선·신선대는 overview는 오는데 사진만 비어 있다. 원인이
 * (a) firstimage 자체가 없음 (b) cpyrhtDivCd Type3라 우리가 뺌 (c) 추가 사진 0건
 * 중 어느 것인지 응답만 봐서는 알 수 없다. 저작권 판단을 바꿀지 정하려면 원문이 필요하다.
 *
 * **확인이 끝나면 이 파일을 통째로 지운다.** 제품 기능이 아니다.
 */
@RestController
public class TourRawController {
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
  private final ObjectMapper om = new ObjectMapper();

  @Value("${TOUR_INFO_KEY:}")
  String serviceKey;

  @GetMapping("/api/dev/tour-raw")
  public Map<String, Object> raw(@RequestParam String contentId) throws Exception {
    Map<String, Object> out = new LinkedHashMap<>();
    if (serviceKey == null || serviceKey.isBlank()) {
      out.put("error", "TOUR_INFO_KEY 미설정");
      return out;
    }
    String base = "https://apis.data.go.kr/B551011/KorService2/";
    String tail = "?serviceKey=" + serviceKey + "&MobileOS=WEB&MobileApp=geojero&_type=json"
        + "&contentId=" + contentId;

    var common = get(base + "detailCommon2" + tail).path("response").path("body")
        .path("items").path("item").path(0);
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("title", common.path("title").asText(null));
    c.put("firstimage", common.path("firstimage").asText(null));
    c.put("firstimage2", common.path("firstimage2").asText(null));
    c.put("cpyrhtDivCd", common.path("cpyrhtDivCd").asText(null));
    c.put("hasField_cpyrhtDivCd", common.has("cpyrhtDivCd"));
    out.put("detailCommon2", c);

    var body = get(base + "detailImage2" + tail + "&imageYN=Y&numOfRows=20")
        .path("response").path("body");
    List<Map<String, String>> imgs = new ArrayList<>();
    for (var it : body.path("items").path("item")) {
      Map<String, String> row = new LinkedHashMap<>();
      row.put("originimgurl", it.path("originimgurl").asText(null));
      row.put("cpyrhtDivCd", it.path("cpyrhtDivCd").asText(null));
      imgs.add(row);
    }
    out.put("detailImage2_totalCount", body.path("totalCount").asText(null));
    out.put("detailImage2", imgs);
    return out;
  }

  private com.fasterxml.jackson.databind.JsonNode get(String url) throws Exception {
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200) {
      throw new IllegalStateException("TourAPI HTTP " + res.statusCode());
    }
    return om.readTree(res.body());
  }
}
