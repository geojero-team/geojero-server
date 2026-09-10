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
 * overview 원문 무수정, KTO 명칭·로고 미노출. 키는 TOUR_INFO_KEY 환경변수(.env)로만.
 *
 * 기반 URL은 여기서 조립한다 — application-prod.yml 의 tour.info-base-url 은 KorService2 로
 * 고정돼 있어 영문(EngService2) 분기를 못 한다. service 인자로 국문·영문을 가른다.
 */
@Component
public class HttpTourApiGateway implements TourApiGateway {
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
  private final ObjectMapper om = new ObjectMapper();

  @Value("${TOUR_INFO_KEY:}")
  String serviceKey;

  @Override
  public boolean isConfigured() {
    return serviceKey != null && !serviceKey.isBlank();
  }

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
