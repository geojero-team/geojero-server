package com.example.geojeroserver.likes;

import com.example.geojeroserver.exception.BusinessException;
import com.example.geojeroserver.exception.ErrorCode;
import com.example.geojeroserver.auth.SessionCookies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 맛집 · 숙소 · 카페 하트(V51, 2026-09-22 사용자) — 스팟 하트(SpotLikeController)와 같은 규칙이다.
 *
 * 누르기(PUT) · 취소(DELETE)만 여기 있다. 하트 수와 「내가 눌렀는가」는 목록 · 상세(PlaceController)가
 * 같은 서브쿼리로 준다 — 두 화면이 다른 수를 말하지 않게.
 *
 * 둘 다 로그인이 필요하다. 두 번 눌러도, 안 누른 것을 취소해도 같은 답(200)이다 — 화면이 보는 결과가 같다.
 */
@RestController
public class PlaceLikeController {
  public record PlaceLikeRes(long placeId, long likeCount, boolean liked) {}

  private final JdbcTemplate jdbc;
  private final SessionCookies sessions;

  public PlaceLikeController(JdbcTemplate jdbc, SessionCookies sessions) {
    this.jdbc = jdbc;
    this.sessions = sessions;
  }

  @PutMapping("/api/places/{placeId}/like")
  public PlaceLikeRes like(HttpServletRequest req, @PathVariable long placeId) {
    long uid = requireExistingUser(req);
    requirePlace(placeId);
    jdbc.update("INSERT INTO place_likes (place_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        placeId, uid);
    return new PlaceLikeRes(placeId, count(placeId), true);
  }

  @DeleteMapping("/api/places/{placeId}/like")
  public PlaceLikeRes unlike(HttpServletRequest req, @PathVariable long placeId) {
    long uid = sessions.require(req); // 지울 뿐이라 계정 행 확인이 없어도 FK 위반이 없다
    requirePlace(placeId);
    jdbc.update("DELETE FROM place_likes WHERE place_id = ? AND user_id = ?", placeId, uid);
    return new PlaceLikeRes(placeId, count(placeId), false);
  }

  private long count(long placeId) {
    return jdbc.queryForObject("SELECT count(*) FROM place_likes WHERE place_id = ?", Long.class,
        placeId);
  }

  /** 토큰 서명만으로는 계정이 살아 있는지 모른다 — 확인하지 않으면 지운 계정의 토큰이 FK 위반 500 이 된다. */
  private long requireExistingUser(HttpServletRequest req) {
    long uid = sessions.require(req);
    if (jdbc.queryForList("SELECT 1 FROM users WHERE user_id = ?", Integer.class, uid).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
    }
    return uid;
  }

  /** 우리 목록에 있는 곳만 하트 대상이다. queryForObject 는 빈 결과에서 500 이라 쓰지 않는다. */
  private void requirePlace(long placeId) {
    if (jdbc.queryForList("SELECT 1 FROM places WHERE content_id = ?", Integer.class, placeId)
        .isEmpty()) {
      throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
    }
  }
}
