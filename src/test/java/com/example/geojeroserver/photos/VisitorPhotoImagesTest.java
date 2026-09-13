package com.example.geojeroserver.photos;

import static org.junit.jupiter.api.Assertions.*;

import com.example.geojeroserver.photos.VisitorPhotoImages.Rejection;
import com.example.geojeroserver.photos.VisitorPhotoImages.RejectedImageException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 방문자 사진 재인코딩 — Spring·DB 무관.
 *
 * 화면이 "사진 속 위치 정보는 저장하지 않아요"(Figma 268:529)라고 약속한다. 이 약속은
 * 클라가 아니라 서버가 지킨다 — API 를 직접 부르면 클라 처리를 건너뛸 수 있기 때문이다.
 */
class VisitorPhotoImagesTest {

  // ── 형식: 매직 바이트로만 가른다 ─────────────────────────────────────────

  /** HEIC·WebP 는 JDK ImageIO 가 못 읽는다. GIF·텍스트도 받지 않는다. Content-Type 은 믿지 않는다. */
  @Test void JPEG_PNG가_아니면_지원안함() {
    assertRejected(Rejection.UNSUPPORTED_FORMAT, TestImages.heic());
    assertRejected(Rejection.UNSUPPORTED_FORMAT, TestImages.webp());
    assertRejected(Rejection.UNSUPPORTED_FORMAT, TestImages.gif());
    assertRejected(Rejection.UNSUPPORTED_FORMAT,
        "not an image".getBytes(StandardCharsets.US_ASCII));
    assertRejected(Rejection.UNSUPPORTED_FORMAT, new byte[0]);
  }

  @Test void JPEG와_PNG는_받는다() {
    assertEquals(40, VisitorPhotoImages.reencode(TestImages.jpeg(40, 30)).width());
    assertEquals(40, VisitorPhotoImages.reencode(TestImages.png(TestImages.marked(40, 30)))
        .width());
  }

  // ── 픽셀 상한: 헤더만 읽고 디코드 전에 거부한다 ────────────────────────────

  /**
   * 1MB 안에도 가로·세로를 크게 주장하는 파일을 만들 수 있다. 디코드부터 하면 픽셀 버퍼를
   * 먼저 잡아 메모리가 터진다(-Xmx768m). 마지막 줄의 6만×6만은 디코드했다면 OOM 이 났을 크기다.
   */
  @Test void 헤더상_픽셀이_상한을_넘으면_디코드_전에_거부() {
    assertTrue(4033L * 3024L > VisitorPhotoImages.MAX_PIXELS);
    assertRejected(Rejection.TOO_MANY_PIXELS, TestImages.pngHeaderClaiming(4033, 3024));
    assertRejected(Rejection.TOO_MANY_PIXELS, TestImages.pngHeaderClaiming(60000, 60000));
  }

  /** 매직 바이트는 JPEG 인데 본문이 깨졌으면 읽을 수 없음. 형식 거부(415)와 다른 답이다. */
  @Test void 본문이_깨진_JPEG는_읽을_수_없음() {
    byte[] jpeg = TestImages.jpeg(200, 100);
    assertRejected(Rejection.UNREADABLE, java.util.Arrays.copyOf(jpeg, 40));
    assertRejected(Rejection.UNREADABLE, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
  }
  // ── 위치정보: 결과에 APP0(JFIF) 말고는 메타데이터 세그먼트가 없어야 한다 ─────

  /**
   * EXIF GPS IFD 가 든 JPEG. 긴 변 1600 미만이라 "작으면 그대로 두는" 최적화가 있으면 여기서 걸린다.
   * 앞의 두 줄은 픽스처 확인이다 — 검사기가 GPS 를 **찾아낼 수 있다**는 것을 먼저 보인다.
   */
  @Test void EXIF_GPS가_결과에_남지_않는다() {
    byte[] input = TestImages.jpegWithExif(200, 100, 1, false, true);
    assertTrue(TestImages.metadataSegments(input) > 0);
    assertTrue(TestImages.containsAscii(input, TestImages.FAKE_GPS_MARK));

    byte[] out = VisitorPhotoImages.reencode(input).jpeg();
    assertCleanJpeg(out);
    assertFalse(TestImages.containsAscii(out, TestImages.FAKE_GPS_MARK));
  }

  /** GPS 가 EXIF 가 아니라 XMP(APP1 http://ns.adobe.com/xap/1.0/)에만 있는 경우. */
  @Test void XMP_GPS가_결과에_남지_않는다() {
    byte[] input = TestImages.jpegWithXmpGps(200, 100);
    assertTrue(TestImages.metadataSegments(input) > 0);
    assertTrue(TestImages.containsAscii(input, TestImages.FAKE_XMP_MARK));

    byte[] out = VisitorPhotoImages.reencode(input).jpeg();
    assertCleanJpeg(out);
    assertFalse(TestImages.containsAscii(out, TestImages.FAKE_XMP_MARK));
    assertFalse(TestImages.containsAscii(out, "GPSLatitude"));
    assertFalse(TestImages.containsAscii(out, "ns.adobe.com"));
  }

  /** PNG 는 eXIf·tEXt 청크에 위치를 담을 수 있다. 결과는 JPEG 이고 둘 다 사라져야 한다. */
  @Test void PNG_eXIf_tEXt가_결과에_남지_않는다() {
    byte[] input = TestImages.pngWithExifAndText(200, 100);
    assertTrue(TestImages.containsAscii(input, TestImages.FAKE_GPS_MARK));
    assertTrue(TestImages.containsAscii(input, TestImages.FAKE_PNG_TEXT_MARK));

    byte[] out = VisitorPhotoImages.reencode(input).jpeg();
    assertCleanJpeg(out);
    assertFalse(TestImages.containsAscii(out, TestImages.FAKE_GPS_MARK));
    assertFalse(TestImages.containsAscii(out, TestImages.FAKE_PNG_TEXT_MARK));
  }

  // ── 방향: 태그를 지우는 대신 픽셀을 돌린다 ────────────────────────────────

  /**
   * 폰은 센서 방향 그대로 저장하고 Orientation 태그로 "돌려서 보라"고 적는다. 재인코딩이 태그를
   * 지우므로 픽셀을 돌리지 않으면 사진이 영구히 눕는다 — 원본은 브라우저가 태그를 따라 똑바로
   * 보여줘서 알아채기 어렵다. 치수만 보면 시계·반시계를 헷갈려도 통과하므로 빨간 칸 위치도 본다.
   */
  @Test void Orientation_6이면_시계방향으로_세운다() {
    var out = VisitorPhotoImages.reencode(TestImages.jpegWithExif(200, 100, 6, true, false));
    assertEquals(100, out.width());
    assertEquals(200, out.height());
    var img = TestImages.decode(out.jpeg());
    assertEquals(100, img.getWidth());
    assertEquals(200, img.getHeight());
    assertNear(0xFF0000, img.getRGB(87, 25), "왼쪽 위 빨간 칸은 오른쪽 위로");
    assertNear(0x0000FF, img.getRGB(12, 174), "왼쪽 아래는 파랑");
  }

  @Test void Orientation_8이면_반시계방향으로_세운다() {
    var out = VisitorPhotoImages.reencode(TestImages.jpegWithExif(200, 100, 8, false, false));
    assertEquals(100, out.width());
    assertEquals(200, out.height());
    var img = TestImages.decode(out.jpeg());
    assertNear(0xFF0000, img.getRGB(12, 174), "왼쪽 위 빨간 칸은 왼쪽 아래로");
    assertNear(0x0000FF, img.getRGB(87, 25), "오른쪽 위는 파랑");
  }

  @Test void Orientation_3이면_뒤집는다() {
    var out = VisitorPhotoImages.reencode(TestImages.jpegWithExif(200, 100, 3, false, false));
    assertEquals(200, out.width());
    assertEquals(100, out.height());
    var img = TestImages.decode(out.jpeg());
    assertNear(0xFF0000, img.getRGB(174, 87), "왼쪽 위 빨간 칸은 오른쪽 아래로");
    assertNear(0x0000FF, img.getRGB(25, 12), "왼쪽 위는 파랑");
  }

  /** 깨진 EXIF 는 방향 없음(1)으로 본다. 사진을 거부하거나 500 을 내지 않는다. */
  @Test void 깨진_EXIF는_방향_없음으로_본다() {
    byte[] hugeOffset = TestImages.concat("Exif\0\0MM".getBytes(StandardCharsets.US_ASCII),
        new byte[] {0, 42, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xF0});
    byte[] truncated = "Exif\0\0II".getBytes(StandardCharsets.US_ASCII);
    for (byte[] payload : new byte[][] {hugeOffset, truncated}) {
      byte[] input = TestImages.insertAfterApp0(TestImages.jpeg(200, 100),
          TestImages.segment(0xE1, payload));
      var out = VisitorPhotoImages.reencode(input);
      assertEquals(200, out.width());
      assertEquals(100, out.height());
      assertCleanJpeg(out.jpeg());
    }
  }

  // ── 크기: 긴 변을 줄이되 키우지 않는다 ────────────────────────────────────

  @Test void 긴_변이_상한을_넘으면_비율을_지켜_줄인다() {
    int max = VisitorPhotoImages.MAX_LONG_EDGE;
    var wide = VisitorPhotoImages.reencode(TestImages.jpeg(3000, 1500));
    assertEquals(max, wide.width());
    assertEquals(max / 2, wide.height());
    var img = TestImages.decode(wide.jpeg());
    assertEquals(max, img.getWidth());
    assertEquals(max / 2, img.getHeight());

    var tall = VisitorPhotoImages.reencode(TestImages.jpeg(1500, 3000));
    assertEquals(max / 2, tall.width());
    assertEquals(max, tall.height());
  }

  /** 줄이는 기준은 **세운 뒤의** 긴 변이다. 3000×1500 에 Orientation 6 이면 세로 사진이다. */
  @Test void 방향을_세운_뒤의_긴_변으로_줄인다() {
    int max = VisitorPhotoImages.MAX_LONG_EDGE;
    var out = VisitorPhotoImages.reencode(TestImages.jpegWithExif(3000, 1500, 6, true, true));
    assertEquals(max / 2, out.width());
    assertEquals(max, out.height());
    assertCleanJpeg(out.jpeg());
  }

  @Test void 상한보다_작으면_키우지_않는다() {
    var out = VisitorPhotoImages.reencode(TestImages.jpeg(1000, 500));
    assertEquals(1000, out.width());
    assertEquals(500, out.height());
  }

  // ── 알파: JPEG 에는 투명이 없다. 흰 바탕에 그린다 ──────────────────────────

  @Test void 알파_PNG는_흰_바탕_JPEG가_된다() {
    var out = VisitorPhotoImages.reencode(TestImages.alphaPng(200, 100));
    assertCleanJpeg(out.jpeg());
    var img = TestImages.decode(out.jpeg());
    assertFalse(img.getColorModel().hasAlpha());
    assertNear(0xFFFFFF, img.getRGB(50, 50), "투명했던 왼쪽 절반");
    assertNear(0x00A000, img.getRGB(150, 50), "불투명한 오른쪽 절반");
  }

  /** JPEG 는 손실 압축이라 채널마다 조금씩 흔들린다. 넓은 단색 칸의 한가운데만 본다. */
  static void assertNear(int expectedRgb, int actualArgb, String where) {
    for (int shift : new int[] {16, 8, 0}) {
      int e = (expectedRgb >> shift) & 0xFF;
      int a = (actualArgb >> shift) & 0xFF;
      assertTrue(Math.abs(e - a) <= 24, where + String.format(": 기대 #%06X 실제 #%06X",
          expectedRgb, actualArgb & 0xFFFFFF));
    }
  }

  /** FF D8 로 시작하고, SOS 앞까지 APP0 말고는 APPn·COM 이 0개. */
  static void assertCleanJpeg(byte[] out) {
    assertEquals(0xFF, out[0] & 0xFF);
    assertEquals(0xD8, out[1] & 0xFF);
    assertEquals(0, TestImages.metadataSegments(out), "APPn/COM: " + TestImages.headerMarkers(out));
    assertFalse(TestImages.containsAscii(out, "Exif"));
  }

  private static void assertRejected(Rejection expected, byte[] input) {
    var e = assertThrows(RejectedImageException.class, () -> VisitorPhotoImages.reencode(input));
    assertEquals(expected, e.reason());
  }
}
