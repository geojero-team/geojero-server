package com.example.geojeroserver.api;

import com.fasterxml.jackson.databind.JsonNode;
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
 * [임시] 관광사진 API(PhotoGalleryService) 조사.
 *
 * 명사해수욕장·도장포유람선·신선대는 KorService2 사진이 전 장 Type3(제3자 저작권)라
 * 쓸 수 없다. 관광사진 API는 별도 승인분(기준문서 §9)이고 키도 따로 있는데 아직
 * 한 번도 부르지 않았다. 여기에 쓸 수 있는 사진이 있는지, 저작권 표시가 어떻게 오는지
 * 확인한다.
 *
 * **확인이 끝나면 이 파일을 통째로 지운다.** 제품 기능이 아니다.
 */
@RestController
public class PhotoProbeController {
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
  private final ObjectMapper om = new ObjectMapper();

  @Value("${TOUR_PHOTO_KEY:}")
  String photoKey;

  @Value("${TOUR_INFO_KEY:}")
  String infoKey;

  @GetMapping("/api/dev/photo-probe")
  public Map<String, Object> probe(@RequestParam String keyword) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("photoKeySet", photoKey != null && !photoKey.isBlank());
    out.put("infoKeySet", infoKey != null && !infoKey.isBlank());

    String key = (photoKey != null && !photoKey.isBlank()) ? photoKey : infoKey;
    if (key == null || key.isBlank()) {
      out.put("error", "키 없음");
      return out;
    }

    // 서비스 이름이 버전업됐을 수 있어 알려진 후보를 차례로 두드린다.
    for (String service : List.of("PhotoGalleryService1", "PhotoGalleryService")) {
      for (String op : List.of("gallerySearchList1", "gallerySearchList")) {
        String url = "https://apis.data.go.kr/B551011/" + service + "/" + op
            + "?serviceKey=" + key + "&MobileOS=WEB&MobileApp=geojero&_type=json"
            + "&arrange=A&numOfRows=10"
            + "&keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        Map<String, Object> attempt = new LinkedHashMap<>();
        try {
          HttpResponse<String> res = http.send(
              HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
              HttpResponse.BodyHandlers.ofString());
          attempt.put("http", res.statusCode());
          String body = res.body();
          if (res.statusCode() == 200 && body.trim().startsWith("{")) {
            JsonNode b = om.readTree(body).path("response").path("body");
            attempt.put("totalCount", b.path("totalCount").asText(null));
            List<Map<String, String>> rows = new ArrayList<>();
            for (var it : b.path("items").path("item")) {
              Map<String, String> r = new LinkedHashMap<>();
              r.put("galTitle", it.path("galTitle").asText(null));
              r.put("galWebImageUrl", it.path("galWebImageUrl").asText(null));
              r.put("galPhotographyLocation", it.path("galPhotographyLocation").asText(null));
              r.put("galPhotographer", it.path("galPhotographer").asText(null));
              r.put("fieldNames", fieldNames(it));
              rows.add(r);
            }
            attempt.put("rows", rows);
          } else {
            // XML 오류 응답이면 앞부분만 — 키가 섞여 나가지 않게 짧게 자른다.
            attempt.put("bodyHead", body.length() > 240 ? body.substring(0, 240) : body);
          }
        } catch (Exception e) {
          attempt.put("exception", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        out.put(service + "/" + op, attempt);
      }
    }
    return out;
  }

  /** 응답에 어떤 필드가 오는지 봐야 저작권 표시 필드를 찾을 수 있다. */
  private static String fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return String.join(",", names);
  }
}
