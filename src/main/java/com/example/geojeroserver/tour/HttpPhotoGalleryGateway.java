package com.example.geojeroserver.tour;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * gallerySearchList1 실호출. 키는 TOUR_PHOTO_KEY(없으면 TOUR_INFO_KEY로 대체) —
 * 팀원이 EC2 .env에 넣어둔 이름을 그대로 쓴다.
 *
 * 서비스 이름의 끝자리 1은 오타가 아니다. PhotoGalleryService(1 없음)와
 * gallerySearchList(1 없음)는 둘 다 "폐기됨"으로 400을 준다(2026-09-10 실측).
 */
@Component
public class HttpPhotoGalleryGateway implements PhotoGalleryGateway {
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
  private final ObjectMapper om = new ObjectMapper();

  @Value("${TOUR_PHOTO_KEY:}")
  String photoKey;

  @Value("${TOUR_INFO_KEY:}")
  String infoKey;

  private String key() {
    return photoKey != null && !photoKey.isBlank() ? photoKey : infoKey;
  }

  @Override
  public boolean isConfigured() {
    String k = key();
    return k != null && !k.isBlank();
  }

  @Override
  public List<GalleryPhoto> search(String keyword) throws Exception {
    String url = "https://apis.data.go.kr/B551011/PhotoGalleryService1/gallerySearchList1"
        + "?serviceKey=" + key() + "&MobileOS=WEB&MobileApp=geojero&_type=json"
        + "&arrange=A&numOfRows=30"
        + "&keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200) {
      throw new IllegalStateException("PhotoGallery HTTP " + res.statusCode());
    }
    List<GalleryPhoto> out = new ArrayList<>();
    for (var it : om.readTree(res.body()).path("response").path("body")
        .path("items").path("item")) {
      out.add(new GalleryPhoto(
          it.path("galTitle").asText(null),
          it.path("galPhotographyLocation").asText(null),
          it.path("galWebImageUrl").asText(null)));
    }
    return out;
  }
}
