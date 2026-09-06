package com.example.geojeroserver.tour;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * detailCommon2 실호출. 공모전 준수: areacode/sigungucode 미사용(상세 조회는 contentId 직접),
 * overview 원문 무수정, KTO 명칭·로고 미노출. 키는 TOURAPI_KEY 환경변수(.env)로만.
 */
@Component
public class HttpTourApiGateway implements TourApiGateway {
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
  private final ObjectMapper om = new ObjectMapper();

  @Value("${TOURAPI_KEY:}")
  String serviceKey;

  @Override
  public TourDetail fetch(String service, String contentId) throws Exception {
    String url = "https://apis.data.go.kr/B551011/" + service + "/detailCommon2"
        + "?serviceKey=" + serviceKey + "&MobileOS=WEB&MobileApp=geojero&_type=json"
        + "&contentId=" + contentId;
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200) {
      throw new IllegalStateException("TourAPI HTTP " + res.statusCode());
    }
    var item = om.readTree(res.body()).path("response").path("body")
        .path("items").path("item").path(0);
    return new TourDetail(item.path("overview").asText(null),
        item.path("firstimage").asText(null));
  }
}
