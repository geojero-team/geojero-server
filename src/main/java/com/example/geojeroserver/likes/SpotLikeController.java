package com.example.geojeroserver.likes;

import com.example.geojeroserver.auth.SessionCookies;
import com.example.geojeroserver.exception.BusinessException;
import com.example.geojeroserver.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 스팟 하트(V49, 2026-09-21) — 스팟 목록 「추천순」의 근거.
 *
 * 누르기(PUT)·취소(DELETE)만 여기 있다. 하트 수와 「내가 눌렀는가」는 스팟 목록·상세(PoiController)가
 * 같은 서브쿼리로 준다 — 두 화면이 다른 수를 말하지 않게.
 *
 * 둘 다 로그인이 필요하다(방문자 사진 올리기와 같은 규칙). 응답은 누른 뒤의 상태이고 누가 눌렀는지는
 * 내려주지 않는다. 두 번 눌러도, 안 누른 것을 취소해도 같은 답(200)이다 — 화면이 보는 결과가 같기 때문이다.
 */
@RestController
public class SpotLikeController {
  public record SpotLikeRes(long poiId, long likeCount, boolean liked) {}

  private final JdbcTemplate jdbc;
  private final SessionCookies sessions;

  public SpotLikeController(JdbcTemplate jdbc, SessionCookies sessions) {
    this.jdbc = jdbc;
    this.sessions = sessions;
  }

  @PutMapping("/api/pois/{poiId}/like")
  public SpotLikeRes like(HttpServletRequest req, @PathVariable long poiId) {
    long uid = requireExistingUser(req);
    requireSpot(poiId);
    // (poi_id, user_id) 가 기본키라 두 번 눌러도 행은 하나다
    jdbc.update("INSERT INTO spot_likes (poi_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        poiId, uid);
    return new SpotLikeRes(poiId, count(poiId), true);
  }

  @DeleteMapping("/api/pois/{poiId}/like")
  public SpotLikeRes unlike(HttpServletRequest req, @PathVariable long poiId) {
    long uid = sessions.require(req); // 지울 뿐이라 계정 행 확인이 없어도 FK 위반이 없다
    requireSpot(poiId);
    jdbc.update("DELETE FROM spot_likes WHERE poi_id = ? AND user_id = ?", poiId, uid);
    return new SpotLikeRes(poiId, count(poiId), false);
  }

  private long count(long poiId) {
    return jdbc.queryForObject("SELECT count(*) FROM spot_likes WHERE poi_id = ?", Long.class,
        poiId);
  }

  /**
   * 토큰 서명만으로는 계정이 살아 있는지 모른다(SessionCookies 는 users 를 보지 않는다).
   * 확인하지 않으면 삭제된 계정의 토큰이 FK 위반 500 으로 떨어진다. /api/me 와 같은 401 을 준다
   * (VisitorPhotoController 와 같은 방식).
   */
  private long requireExistingUser(HttpServletRequest req) {
    long uid = sessions.require(req);
    if (jdbc.queryForList("SELECT 1 FROM users WHERE user_id = ?", Integer.class, uid).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
    }
    return uid;
  }

  /**
   * 화면에 나오는 스팟 19곳(theme IS NOT NULL)만 하트 대상이다. 고현터미널(TERMINAL)은 방문자 사진 칸은
   * 있지만(V22) 스팟 목록에 없으므로 여기서는 뺀다 — 사진과 다르다.
   * queryForObject 는 빈 결과에서 500 이라 쓰지 않는다.
   */
  private void requireSpot(long poiId) {
    if (jdbc.queryForList("SELECT 1 FROM pois WHERE poi_id = ? AND theme IS NOT NULL",
        Integer.class, poiId).isEmpty()) {
      throw new BusinessException(ErrorCode.POI_NOT_FOUND);
    }
  }
}
