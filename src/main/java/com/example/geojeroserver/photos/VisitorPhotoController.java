package com.example.geojeroserver.photos;

import com.example.geojeroserver.auth.SessionCookies;
import com.example.geojeroserver.exception.BusinessException;
import com.example.geojeroserver.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 방문자 사진 — 스팟 상세의 「방문자 사진」 섹션.
 *
 * **TourAPI 사진과 섞지 않는다.** PoiController 의 detail.images 는 24시간 캐시 객체이고
 * 저작권(cpyrhtDivCd)을 장 단위로 거르는 경로를 탄다. 방문자 사진은 별도 테이블(V21)·별도 경로다.
 *
 * 보기는 비로그인이다. 토큰이 있으면 isMine 만 계산하고, 토큰이 깨졌어도 401 을 주지 않는다 —
 * 만료된 사용자에게 보기가 막히면 비로그인 규칙(디자인브리프 §6)을 어긴다.
 * 작성자 닉네임·userId 는 내려주지 않는다(작성자 표기 규칙 미정).
 */
@RestController
public class VisitorPhotoController {
  /** 캡션 상한(코드포인트). 결정값이다(기준문서 §6 「방문자 사진」). DB 제약이 아니라 여기서 검사한다. */
  public static final int MAX_CAPTION_CODE_POINTS = 200;

  public record VisitorPhoto(long photoId, String imageUrl, int width, int height,
      String caption, String uploadedDate, boolean isMine) {}

  public record VisitorPhotosRes(long poiId, int count, List<VisitorPhoto> photos) {}

  private final JdbcTemplate jdbc;
  private final SessionCookies sessions;

  public VisitorPhotoController(JdbcTemplate jdbc, SessionCookies sessions) {
    this.jdbc = jdbc;
    this.sessions = sessions;
  }

  @GetMapping("/api/pois/{poiId}/visitor-photos")
  public VisitorPhotosRes list(HttpServletRequest req, @PathVariable long poiId) {
    requireSpot(poiId);
    Long uid = sessions.verify(req); // require() 가 아니다 — 보기는 비로그인
    // 목록은 바이트(visitor_photo_blobs)를 읽지 않는다.
    var photos = jdbc.query("""
        SELECT photo_id, user_id, caption, width, height,
               (created_at AT TIME ZONE 'Asia/Seoul')::date::text AS uploaded_date
        FROM visitor_photos WHERE poi_id = ? AND hidden_at IS NULL
        ORDER BY created_at DESC, photo_id DESC""",
        (rs, i) -> toPhoto(rs, uid), poiId);
    return new VisitorPhotosRes(poiId, photos.size(), photos);
  }

  @PostMapping("/api/pois/{poiId}/visitor-photos")
  public ResponseEntity<VisitorPhoto> upload(HttpServletRequest req, @PathVariable long poiId,
      @RequestParam(required = false) MultipartFile file,
      @RequestParam(required = false) String caption) throws IOException {
    long uid = requireExistingUser(req);
    requireSpot(poiId);
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.FILE_REQUIRED);
    }
    String text = normalizeCaption(caption);

    // 원본 바이트는 이 메서드 안에서만 산다 — 저장·로그 어디에도 넘기지 않는다.
    // 크기와 무관하게 항상 다시 인코딩한다. 작다고 건너뛰면 GPS 가 그대로 저장된다.
    VisitorPhotoImages.Encoded img;
    try {
      img = VisitorPhotoImages.reencode(file.getBytes());
    } catch (VisitorPhotoImages.RejectedImageException e) {
      throw new BusinessException(switch (e.reason()) {
        case UNSUPPORTED_FORMAT -> ErrorCode.UNSUPPORTED_IMAGE_TYPE;
        case TOO_MANY_PIXELS -> ErrorCode.IMAGE_TOO_MANY_PIXELS;
        case UNREADABLE -> ErrorCode.IMAGE_UNREADABLE;
      });
    }

    // 바이트는 별도 테이블이다. @Transactional 을 쓰는 곳이 없으므로 한 문장으로 두 테이블에 넣는다.
    var row = jdbc.queryForMap("""
        WITH p AS (
          INSERT INTO visitor_photos (poi_id, user_id, caption, width, height, byte_size)
          VALUES (?, ?, ?, ?, ?, ?)
          RETURNING photo_id, caption, width, height, created_at
        ), b AS (
          INSERT INTO visitor_photo_blobs (photo_id, jpeg) SELECT photo_id, ? FROM p
        )
        SELECT photo_id, caption, width, height,
               (created_at AT TIME ZONE 'Asia/Seoul')::date::text AS uploaded_date
        FROM p""",
        poiId, uid, text, img.width(), img.height(), img.jpeg().length, img.jpeg());

    long photoId = ((Number) row.get("photo_id")).longValue();
    return ResponseEntity.status(HttpStatus.CREATED).body(new VisitorPhoto(photoId,
        imageUrl(photoId), img.width(), img.height(), (String) row.get("caption"),
        (String) row.get("uploaded_date"), true));
  }

  /**
   * 본인 사진만 지운다(행 삭제 — 바이트는 CASCADE). 남의 사진은 403 이다.
   * saved-trips 는 남의 것도 404 로 숨기지만, 방문자 사진은 목록으로 이미 공개돼 있어 숨길 이유가 없다.
   */
  @DeleteMapping("/api/visitor-photos/{photoId}")
  public ResponseEntity<Void> delete(HttpServletRequest req, @PathVariable long photoId) {
    long uid = sessions.require(req);
    var owners = jdbc.queryForList("SELECT user_id FROM visitor_photos WHERE photo_id = ?",
        Long.class, photoId);
    if (owners.isEmpty()) {
      throw new BusinessException(ErrorCode.PHOTO_NOT_FOUND);
    }
    if (owners.get(0) != uid) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    if (jdbc.update("DELETE FROM visitor_photos WHERE photo_id = ? AND user_id = ?",
        photoId, uid) == 0) {
      throw new BusinessException(ErrorCode.PHOTO_NOT_FOUND); // 그 사이 지워졌다
    }
    return ResponseEntity.noContent().build();
  }

  /**
   * 신고 — 부적절한 사진을 이용자가 그 자리에서 내릴 수 있는 수단(V33, 2026-09-16).
   *
   * 한 건이라도 들어오면 **그 자리에서 감춘다**(hidden_at). 지우지는 않는다 — 잘못된 신고를 되돌릴 수
   * 있어야 하고, 남이 올린 사진을 다른 사람이 영구히 없앨 수 있으면 안 된다.
   *
   * 로그인한 사람만 신고한다(올리기와 같은 규칙). 누가 신고했는지 남으므로 장난 신고를 되짚을 수 있다.
   * 같은 사람의 두 번째 신고도, 이미 감춰진 사진도 204 다 — 신고한 사람이 보는 결과가 같기 때문이다.
   */
  @PostMapping("/api/visitor-photos/{photoId}/report")
  public ResponseEntity<Void> report(HttpServletRequest req, @PathVariable long photoId) {
    long uid = requireExistingUser(req);
    if (jdbc.queryForList("SELECT 1 FROM visitor_photos WHERE photo_id = ?",
        Integer.class, photoId).isEmpty()) {
      throw new BusinessException(ErrorCode.PHOTO_NOT_FOUND);
    }
    jdbc.update("""
        INSERT INTO visitor_photo_reports (photo_id, user_id) VALUES (?, ?)
        ON CONFLICT (photo_id, user_id) DO NOTHING""", photoId, uid);
    jdbc.update("UPDATE visitor_photos SET hidden_at = now() WHERE photo_id = ? AND hidden_at IS NULL",
        photoId);
    return ResponseEntity.noContent().build();
  }

  /**
   * 비로그인. 저장된 재인코딩 JPEG 를 그대로 내려준다. 캐시 헤더는 기본값(no-store)이라 지운 사진이 바로 사라진다.
   * 신고로 감춰진 사진(V33)은 여기서도 막는다 — 목록에서만 빼면 주소를 아는 사람에게는 계속 보인다.
   */
  @GetMapping("/api/visitor-photos/{photoId}/image")
  public ResponseEntity<byte[]> image(@PathVariable long photoId) {
    var jpeg = jdbc.query("""
        SELECT b.jpeg FROM visitor_photo_blobs b
          JOIN visitor_photos p ON p.photo_id = b.photo_id
        WHERE b.photo_id = ? AND p.hidden_at IS NULL""",
        (rs, i) -> rs.getBytes(1), photoId);
    if (jpeg.isEmpty()) {
      throw new BusinessException(ErrorCode.PHOTO_NOT_FOUND);
    }
    return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(jpeg.get(0));
  }

  /**
   * 앞뒤 공백(유니코드 공백 포함)을 지우고, 비면 null. 길이는 **코드포인트**로 센다 —
   * String.length() 는 이모지를 2로 세서 200자를 쓴 사용자를 거부한다.
   */
  static String normalizeCaption(String caption) {
    if (caption == null) {
      return null;
    }
    String s = caption.strip();
    if (s.isEmpty()) {
      return null;
    }
    if (s.codePointCount(0, s.length()) > MAX_CAPTION_CODE_POINTS) {
      throw new BusinessException(ErrorCode.CAPTION_TOO_LONG, MAX_CAPTION_CODE_POINTS);
    }
    return s;
  }

  /**
   * 토큰 서명만으로는 계정이 살아 있는지 모른다(SessionCookies 는 users 를 보지 않는다).
   * 확인하지 않으면 삭제된 계정의 토큰이 FK 위반 500 으로 떨어진다. /api/me 와 같은 401 을 준다.
   */
  private long requireExistingUser(HttpServletRequest req) {
    long uid = sessions.require(req);
    if (jdbc.queryForList("SELECT 1 FROM users WHERE user_id = ?", Integer.class, uid).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
    }
    return uid;
  }

  /**
   * 화면에 나오는 17곳(theme IS NOT NULL)과 고현터미널(V22 — 홈 지도에서 스팟처럼 펼쳐진다)만 사진 칸이 있다.
   * queryForObject 는 빈 결과에서 500 이라 쓰지 않는다.
   */
  private void requireSpot(long poiId) {
    if (jdbc.queryForList(
        "SELECT 1 FROM pois WHERE poi_id = ? AND (theme IS NOT NULL OR poi_kind = 'TERMINAL')",
        Integer.class, poiId).isEmpty()) {
      throw new BusinessException(ErrorCode.POI_NOT_FOUND);
    }
  }

  private static VisitorPhoto toPhoto(ResultSet rs, Long uid) throws SQLException {
    long id = rs.getLong("photo_id");
    return new VisitorPhoto(id, imageUrl(id), rs.getInt("width"), rs.getInt("height"),
        rs.getString("caption"), rs.getString("uploaded_date"),
        uid != null && uid == rs.getLong("user_id"));
  }

  private static String imageUrl(long photoId) {
    return "/api/visitor-photos/" + photoId + "/image";
  }
}
