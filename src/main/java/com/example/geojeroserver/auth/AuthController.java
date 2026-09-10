package com.example.geojeroserver.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 카카오 로그인(무이메일) — 로그인은 저장 기능에만 쓰이고, 판정·조회는 이 경로를 전혀 거치지 않는다. */
@RestController
public class AuthController {
  public record KakaoLoginReq(String code, String redirectUri) {}

  private final KakaoGateway kakao;
  private final JdbcTemplate jdbc;
  private final SessionCookies sessions;

  public AuthController(KakaoGateway kakao, JdbcTemplate jdbc, SessionCookies sessions) {
    this.kakao = kakao;
    this.jdbc = jdbc;
    this.sessions = sessions;
  }

  /**
   * 카카오 인증 화면으로 보내는 앞단. REST 키를 클라이언트에 노출하지 않으려고 서버가 302한다.
   *
   * redirectUri는 클라이언트가 정한다 — 배포 주소가 vercel.app일 수도 geojero.com일 수도 있다.
   * 값을 그대로 kauth에 넘기되 최종 검증은 카카오가 한다(등록 안 된 URI는 KOE006으로 거절).
   * 열린 리다이렉트가 되지 않는 이유도 같다 — 우리는 kauth.kakao.com으로만 보낸다.
   */
  @GetMapping("/api/auth/kakao/start")
  public ResponseEntity<Void> start(@RequestParam String redirectUri) {
    String url = kakao.authorizeUrl(redirectUri);
    if (url == null) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "카카오 로그인이 아직 설정되지 않았습니다 — 비로그인 기능은 계속 사용 가능");
    }
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
  }

  @PostMapping("/api/auth/kakao")
  public Map<String, Object> login(@RequestBody KakaoLoginReq req, HttpServletResponse res) {
    KakaoGateway.KakaoUser u;
    try {
      u = kakao.exchange(req.code(), req.redirectUri());
    } catch (Exception e) {
      // 카카오 장애가 판정을 막지 않는다 — 로그인만 실패로 알린다
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "카카오 인증 실패 — 비로그인 기능은 계속 사용 가능");
    }
    var row = jdbc.queryForMap("""
        INSERT INTO users (provider, oauth_id, nickname) VALUES ('KAKAO', ?, ?)
        ON CONFLICT (provider, oauth_id)
        DO UPDATE SET nickname = COALESCE(EXCLUDED.nickname, users.nickname),
                      updated_at = now()
        RETURNING user_id, nickname""", u.oauthId(), u.nickname());
    long userId = ((Number) row.get("user_id")).longValue();
    res.addHeader(HttpHeaders.SET_COOKIE, sessions.issue(userId).toString());
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("ok", true);
    body.put("nickname", row.get("nickname"));
    // 쿠키와 같은 토큰. 프론트와 API가 다른 사이트인 동안은 쿠키가 붙지 않아
    // 클라이언트가 이걸 들고 Authorization: Bearer로 보낸다. 같은 사이트가 되면 쿠키가 이긴다.
    body.put("token", sessions.issueToken(userId));
    return body;
  }

  @PostMapping("/api/auth/logout")
  public Map<String, Object> logout(HttpServletResponse res) {
    res.addHeader(HttpHeaders.SET_COOKIE, sessions.clear().toString());
    return Map.of("ok", true);
  }

  @GetMapping("/api/me")
  public Map<String, Object> me(HttpServletRequest req) {
    long uid = sessions.require(req);
    var rows = jdbc.query("SELECT nickname FROM users WHERE user_id = ?",
        (rs, i) -> rs.getString(1), uid);
    if (rows.isEmpty()) { // 세션은 유효하나 계정이 삭제된 경우
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("nickname", rows.get(0));
    return body;
  }
}
