package com.example.geojeroserver.photos;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.Semaphore;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * 방문자 사진 재인코딩 — 받은 바이트를 **픽셀만 남긴 새 JPEG** 로 다시 쓴다. DB·HTTP 를 모른다.
 *
 * 왜 항상 다시 쓰는가: 화면이 "사진 속 위치 정보는 저장하지 않아요"(Figma 268:529)라고 약속한다.
 * 폰 사진의 EXIF·XMP 에는 GPS 가 들어 있고, 클라가 canvas 로 먼저 줄여 보내더라도 API 를 직접
 * 부르면 그 처리를 건너뛸 수 있다. 그래서 서버가 크기와 무관하게 **매번** 디코드 → 픽셀 → 새 JPEG 로
 * 쓴다. 인코더에 IIOMetadata 를 넘기지 않으므로 결과에는 JFIF(APP0)만 있다.
 *
 * 순서가 중요하다
 *   1. 매직 바이트로 JPEG·PNG 만 받는다. Content-Type·파일명은 믿지 않는다(HEIC·WebP 는 JDK 가 못 읽는다).
 *   2. 헤더의 가로·세로만 먼저 읽어 픽셀 상한을 넘으면 **디코드 전에** 거부한다.
 *   3. JPEG 이면 EXIF Orientation 하나만 읽는다 — 태그를 지우고 픽셀을 안 돌리면 사진이 눕는다.
 *   4. 디코드 → 방향 적용 → 흰 바탕 RGB(알파 제거) → 긴 변 축소 → JPEG.
 *
 * 원본 바이트와 메타데이터 내용은 로그·예외 메시지에 넣지 않는다. 스트림은 메모리 캐시만 쓴다 —
 * ImageIO 기본 스트림은 디스크 임시 파일을 만들 수 있고, 그러면 GPS 가 든 원본이 디스크에 닿는다.
 */
public final class VisitorPhotoImages {
  /** 결과 JPEG 의 긴 변 상한(px). 이보다 작으면 키우지 않는다. */
  public static final int MAX_LONG_EDGE = 1600;
  public static final float JPEG_QUALITY = 0.8f;
  /**
   * 헤더상 가로×세로 상한. 12MP(4032×3024)를 디코드하면 힙 약 37~49MB 를 잡는다
   * (JVM -Xmx768m · 컨테이너 1G). 근거 있는 확정값이 아니라 [제안]값이다.
   */
  public static final long MAX_PIXELS = 4032L * 3024L;
  /** 동시에 디코드하는 장수 상한 [제안]. 큰 사진이 겹쳐 힙이 터지지 않게 줄 세운다. */
  static final int MAX_CONCURRENT_DECODES = 2;

  private static final Semaphore DECODE_SLOTS = new Semaphore(MAX_CONCURRENT_DECODES);

  private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] PNG_MAGIC =
      {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
  private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};

  /** 형식 아님(→415) / 헤더상 픽셀 초과(→400) / 디코드 실패, 예: 깨진 본문(→400). */
  public enum Rejection { UNSUPPORTED_FORMAT, TOO_MANY_PIXELS, UNREADABLE }

  /** 거부 사유만 담는다. 입력 바이트나 메타데이터를 메시지에 싣지 않는다. */
  public static final class RejectedImageException extends RuntimeException {
    private final Rejection reason;

    RejectedImageException(Rejection reason) {
      super(reason.name());
      this.reason = reason;
    }

    public Rejection reason() {
      return reason;
    }
  }

  public record Encoded(byte[] jpeg, int width, int height) {}

  private enum Format {
    JPEG("jpeg"), PNG("png");

    final String readerName;

    Format(String readerName) {
      this.readerName = readerName;
    }
  }

  private VisitorPhotoImages() {}

  /** @throws RejectedImageException 받을 수 없는 사진 */
  public static Encoded reencode(byte[] input) {
    Format format = sniff(input);
    DECODE_SLOTS.acquireUninterruptibly();
    try {
      BufferedImage decoded = decode(input, format);
      int orientation = format == Format.JPEG ? exifOrientation(input) : 1;
      BufferedImage out = drawUpright(decoded, orientation);
      return new Encoded(writeJpeg(out), out.getWidth(), out.getHeight());
    } finally {
      DECODE_SLOTS.release();
    }
  }

  /**
   * 방향을 픽셀에 적용하고 긴 변을 줄여 흰 바탕 RGB 에 그린다. 알파는 여기서 사라진다.
   * 줄이는 기준은 세운 뒤의 긴 변이고, 작은 사진을 키우지는 않는다.
   */
  private static BufferedImage drawUpright(BufferedImage src, int orientation) {
    int w = src.getWidth();
    int h = src.getHeight();
    boolean swap = orientation >= 5; // 5~8 은 가로·세로가 바뀐다
    int uw = swap ? h : w;
    int uh = swap ? w : h;
    double scale = Math.min(1.0, (double) MAX_LONG_EDGE / Math.max(uw, uh));
    int tw = Math.max(1, (int) Math.round(uw * scale));
    int th = Math.max(1, (int) Math.round(uh * scale));

    BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, tw, th);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      AffineTransform at = AffineTransform.getScaleInstance((double) tw / uw, (double) th / uh);
      at.concatenate(orientationTransform(orientation, w, h));
      g.drawImage(src, at, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  /**
   * EXIF Orientation 1~8 → 저장된 좌표를 똑바로 선 좌표로 옮기는 변환.
   * AffineTransform(m00, m10, m01, m11, m02, m12): x' = m00·x + m01·y + m02, y' = m10·x + m11·y + m12.
   */
  private static AffineTransform orientationTransform(int orientation, int w, int h) {
    return switch (orientation) {
      case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // 좌우 뒤집기
      case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // 180°
      case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // 상하 뒤집기
      case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // 왼쪽 위–오른쪽 아래 대각선으로 뒤집기
      case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // 시계 90°
      case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // 반대 대각선으로 뒤집기
      case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);   // 반시계 90°
      default -> new AffineTransform();
    };
  }

  /**
   * JPEG 의 EXIF(APP1) IFD0 에서 Orientation(0x0112) **하나만** 읽는다. 없거나 깨졌으면 1.
   * 의존성을 들이지 않으려고 직접 읽는다. GPS 등 다른 태그는 건너뛰고 어디에도 기록하지 않는다.
   */
  static int exifOrientation(byte[] b) {
    try {
      int i = 2; // SOI 다음
      while (i + 4 <= b.length) {
        if ((b[i] & 0xFF) != 0xFF) {
          return 1;
        }
        int marker = b[i + 1] & 0xFF;
        if (marker == 0xFF) { // 채움 바이트
          i++;
          continue;
        }
        if (marker == 0xDA || marker == 0xD9) { // 스캔 시작 — 이 뒤로는 메타데이터가 없다
          return 1;
        }
        int len = u16(b, i + 2, false);
        if (len < 2) {
          return 1;
        }
        int payload = i + 4;
        int end = Math.min(b.length, i + 2 + len);
        if (marker == 0xE1 && startsWith(b, payload, EXIF_HEADER)) {
          return orientationInTiff(b, payload + EXIF_HEADER.length, end);
        }
        i += 2 + len;
      }
    } catch (RuntimeException e) {
      // 깨진 EXIF — 방향 없음으로 본다. 내용은 기록하지 않는다.
    }
    return 1;
  }

  private static int orientationInTiff(byte[] b, int tiff, int end) {
    if (tiff + 8 > end) {
      return 1;
    }
    boolean le;
    if (b[tiff] == 'I' && b[tiff + 1] == 'I') {
      le = true;
    } else if (b[tiff] == 'M' && b[tiff + 1] == 'M') {
      le = false;
    } else {
      return 1;
    }
    if (u16(b, tiff + 2, le) != 42) {
      return 1;
    }
    long ifd0 = tiff + u32(b, tiff + 4, le);
    if (ifd0 + 2 > end) {
      return 1;
    }
    int count = u16(b, (int) ifd0, le);
    for (int k = 0; k < count; k++) {
      int entry = (int) ifd0 + 2 + k * 12;
      if (entry + 12 > end) {
        return 1;
      }
      if (u16(b, entry, le) == 0x0112) {
        int value = u16(b, entry + 2, le) == 3 ? u16(b, entry + 8, le) : 1; // 형식은 SHORT
        return value >= 1 && value <= 8 ? value : 1;
      }
    }
    return 1;
  }

  private static int u16(byte[] b, int at, boolean littleEndian) {
    int x = b[at] & 0xFF;
    int y = b[at + 1] & 0xFF;
    return littleEndian ? (y << 8) | x : (x << 8) | y;
  }

  private static long u32(byte[] b, int at, boolean littleEndian) {
    long hi = u16(b, littleEndian ? at + 2 : at, littleEndian);
    long lo = u16(b, littleEndian ? at : at + 2, littleEndian);
    return (hi << 16) | lo;
  }

  private static Format sniff(byte[] b) {
    if (startsWith(b, 0, JPEG_MAGIC)) {
      return Format.JPEG;
    }
    if (startsWith(b, 0, PNG_MAGIC)) {
      return Format.PNG;
    }
    throw new RejectedImageException(Rejection.UNSUPPORTED_FORMAT);
  }

  private static BufferedImage decode(byte[] input, Format format) {
    ImageReader reader = ImageIO.getImageReadersByFormatName(format.readerName).next();
    try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
      reader.setInput(in, true, true); // ignoreMetadata — 메타데이터 객체를 만들지 않는다
      // getWidth/getHeight 는 헤더(SOF·IHDR)만 읽는다. 픽셀 버퍼는 read() 에서야 잡힌다.
      if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
        throw new RejectedImageException(Rejection.TOO_MANY_PIXELS);
      }
      return reader.read(0);
    } catch (RejectedImageException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      throw new RejectedImageException(Rejection.UNREADABLE);
    } finally {
      reader.dispose();
    }
  }

  private static byte[] writeJpeg(BufferedImage img) {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(JPEG_QUALITY);
      writer.setOutput(out);
      // 메타데이터 자리를 null 로 둔다 — EXIF·XMP 가 따라 나갈 길이 없다(JFIF APP0 만 쓰인다).
      writer.write(null, new IIOImage(img, null, null), param);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
  }

  private static boolean startsWith(byte[] b, int at, byte[] prefix) {
    if (b == null || at < 0 || at + prefix.length > b.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (b[at + i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }
}
