package com.example.geojeroserver.photos;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.imageio.ImageIO;

/**
 * 방문자 사진 테스트 픽스처 — 전부 코드에서 조립한다.
 *
 * 실제 폰 사진을 저장소에 두지 않는다. 그 파일의 GPS 가 누군가의 실제 위치이기 때문이다.
 * 좌표는 12°34′56″ 같은 **가짜 값**이고, 결과에 흔적이 남았는지 찾기 쉽게 표식 문자열을 함께 넣는다.
 */
public final class TestImages {
  public static final String FAKE_GPS_MARK = "FAKE-GPS-DATUM-0000";
  public static final String FAKE_XMP_MARK = "FAKE-XMP-GPS-0000";
  public static final String FAKE_PNG_TEXT_MARK = "FAKE-PNG-TEXT-GPS-0000";

  private TestImages() {}

  // ── 기본 그림 ─────────────────────────────────────────────────────────────

  /** 파란 바탕에 왼쪽 위 1/4 × 1/4 칸만 빨간 그림. 방향을 돌렸을 때 빨간 칸이 어디로 가는지로 확인한다. */
  public static BufferedImage marked(int w, int h) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.BLUE);
    g.fillRect(0, 0, w, h);
    g.setColor(Color.RED);
    g.fillRect(0, 0, Math.max(1, w / 4), Math.max(1, h / 4));
    g.dispose();
    return img;
  }

  public static byte[] jpeg(int w, int h) {
    return write(marked(w, h), "jpeg");
  }

  public static byte[] png(BufferedImage img) {
    return write(img, "png");
  }

  /** EXIF APP1(IFD0 Orientation, 선택적으로 GPS IFD)을 JFIF APP0 뒤에 끼운 JPEG. */
  public static byte[] jpegWithExif(int w, int h, int orientation, boolean littleEndian,
      boolean withGps) {
    byte[] payload = concat("Exif\0\0".getBytes(StandardCharsets.US_ASCII),
        tiff(orientation, littleEndian, withGps));
    return insertAfterApp0(jpeg(w, h), segment(0xE1, payload));
  }

  /** GPS 가 XMP(APP1 http://ns.adobe.com/xap/1.0/)에만 들어 있는 JPEG. */
  public static byte[] jpegWithXmpGps(int w, int h) {
    String xmp = "http://ns.adobe.com/xap/1.0/\0"
        + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF"
        + " xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
        + "<rdf:Description xmlns:exif=\"http://ns.adobe.com/exif/1.0/\""
        + " exif:GPSLatitude=\"12,34.56N\" exif:GPSLongitude=\"56,12.34E\""
        + " exif:GPSMapDatum=\"" + FAKE_XMP_MARK + "\"/></rdf:RDF></x:xmpmeta>";
    return insertAfterApp0(jpeg(w, h), segment(0xE1, xmp.getBytes(StandardCharsets.UTF_8)));
  }

  /** eXIf(GPS 포함 TIFF)와 tEXt 청크를 IHDR 바로 뒤에 끼운 PNG. */
  public static byte[] pngWithExifAndText(int w, int h) {
    byte[] png = png(marked(w, h));
    byte[] exif = chunk("eXIf", tiff(1, false, true));
    byte[] text = chunk("tEXt", ("Comment\0" + FAKE_PNG_TEXT_MARK)
        .getBytes(StandardCharsets.ISO_8859_1));
    int afterIhdr = 8 + 4 + 4 + 13 + 4;
    return concat(Arrays.copyOfRange(png, 0, afterIhdr), exif, text,
        Arrays.copyOfRange(png, afterIhdr, png.length));
  }

  /** 왼쪽 절반이 완전히 투명한 ARGB PNG. 오른쪽 절반은 초록. */
  public static byte[] alphaPng(int w, int h) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setColor(new Color(0, 160, 0, 255));
    g.fillRect(w / 2, 0, w - w / 2, h);
    g.dispose();
    return png(img);
  }

  /** IHDR 만 w×h 라고 주장하는 PNG. 실제 픽셀 데이터는 없다 — 디코드하면 안 되는 파일이다. */
  public static byte[] pngHeaderClaiming(int w, int h) {
    ByteBuffer ihdr = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN);
    ihdr.putInt(w).putInt(h).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0)
        .put((byte) 0);
    Deflater d = new Deflater();
    d.finish();
    byte[] z = new byte[64];
    int n = d.deflate(z);
    d.end();
    return concat(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
        chunk("IHDR", ihdr.array()), chunk("IDAT", Arrays.copyOf(z, n)),
        chunk("IEND", new byte[0]));
  }

  public static byte[] heic() {
    return concat(new byte[] {0, 0, 0, 0x18}, "ftypheic\0\0\0\0mif1heic"
        .getBytes(StandardCharsets.US_ASCII));
  }

  public static byte[] webp() {
    return concat("RIFF".getBytes(StandardCharsets.US_ASCII), new byte[] {0x24, 0, 0, 0},
        "WEBPVP8 ".getBytes(StandardCharsets.US_ASCII), new byte[16]);
  }

  public static byte[] gif() {
    return concat("GIF89a".getBytes(StandardCharsets.US_ASCII), new byte[16]);
  }

  // ── 결과 검사 ─────────────────────────────────────────────────────────────

  /** SOI 뒤부터 SOS 까지의 마커 코드(0xE0 = APP0 …). 검사 대상 코드에 기대지 않고 따로 센다. */
  public static List<Integer> headerMarkers(byte[] jpeg) {
    if ((jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
      throw new AssertionError("JPEG SOI 로 시작하지 않는다");
    }
    var out = new ArrayList<Integer>();
    int i = 2;
    while (i + 4 <= jpeg.length) {
      if ((jpeg[i] & 0xFF) != 0xFF) {
        throw new AssertionError("마커 자리에 마커가 없다: " + i);
      }
      int m = jpeg[i + 1] & 0xFF;
      if (m == 0xFF) {
        i++;
        continue;
      }
      out.add(m);
      if (m == 0xDA || m == 0xD9) {
        break;
      }
      i += 2 + (((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF));
    }
    return out;
  }

  /** APP0(JFIF) 말고는 APPn(0xE1~0xEF)·COM(0xFE) 이 몇 개인가. */
  public static long metadataSegments(byte[] jpeg) {
    return headerMarkers(jpeg).stream()
        .filter(m -> (m >= 0xE1 && m <= 0xEF) || m == 0xFE).count();
  }

  public static boolean containsAscii(byte[] bytes, String s) {
    return new String(bytes, StandardCharsets.ISO_8859_1).contains(s);
  }

  public static BufferedImage decode(byte[] bytes) {
    try {
      return ImageIO.read(new ByteArrayInputStream(bytes));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // ── 조립 ──────────────────────────────────────────────────────────────────

  /**
   * TIFF(EXIF 본체). IFD0 에 Orientation(0x0112), withGps 면 GPS IFD 포인터(0x8825)와
   * GPS IFD(위도 참조·위도·경도 참조·측지계 이름)를 붙인다. 위도는 12/1, 34/1, 56/1 — 가짜다.
   */
  static byte[] tiff(int orientation, boolean littleEndian, boolean withGps) {
    ByteBuffer t = ByteBuffer.allocate(512)
        .order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    t.put((byte) (littleEndian ? 'I' : 'M')).put((byte) (littleEndian ? 'I' : 'M'));
    t.putShort((short) 42).putInt(8);
    int entries = withGps ? 2 : 1;
    t.putShort((short) entries);
    t.putShort((short) 0x0112).putShort((short) 3).putInt(1)
        .putShort((short) orientation).putShort((short) 0);
    int gpsIfd = 8 + 2 + entries * 12 + 4;
    if (withGps) {
      t.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(gpsIfd);
    }
    t.putInt(0);
    if (withGps) {
      int n = 4;
      int data = gpsIfd + 2 + n * 12 + 4;
      byte[] mark = (FAKE_GPS_MARK + "\0").getBytes(StandardCharsets.US_ASCII);
      t.putShort((short) n);
      t.putShort((short) 0x0001).putShort((short) 2).putInt(2).put(new byte[] {'N', 0, 0, 0});
      t.putShort((short) 0x0002).putShort((short) 5).putInt(3).putInt(data);
      t.putShort((short) 0x0003).putShort((short) 2).putInt(2).put(new byte[] {'E', 0, 0, 0});
      t.putShort((short) 0x0012).putShort((short) 2).putInt(mark.length).putInt(data + 24);
      t.putInt(0);
      t.putInt(12).putInt(1).putInt(34).putInt(1).putInt(56).putInt(1);
      t.put(mark);
    }
    return Arrays.copyOf(t.array(), t.position());
  }

  static byte[] segment(int marker, byte[] payload) {
    int len = payload.length + 2;
    return concat(new byte[] {(byte) 0xFF, (byte) marker, (byte) (len >> 8), (byte) len},
        payload);
  }

  static byte[] insertAfterApp0(byte[] jpeg, byte[] segment) {
    if ((jpeg[2] & 0xFF) != 0xFF || (jpeg[3] & 0xFF) != 0xE0) {
      throw new AssertionError("ImageIO 가 APP0 를 먼저 쓰지 않았다");
    }
    int end = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
    return concat(Arrays.copyOfRange(jpeg, 0, end), segment,
        Arrays.copyOfRange(jpeg, end, jpeg.length));
  }

  static byte[] chunk(String type, byte[] data) {
    byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
    CRC32 crc = new CRC32();
    crc.update(typeBytes);
    crc.update(data);
    return ByteBuffer.allocate(12 + data.length).order(ByteOrder.BIG_ENDIAN)
        .putInt(data.length).put(typeBytes).put(data).putInt((int) crc.getValue()).array();
  }

  static byte[] concat(byte[]... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] p : parts) {
      out.writeBytes(p);
    }
    return out.toByteArray();
  }

  private static byte[] write(BufferedImage img, String format) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      if (!ImageIO.write(img, format, out)) {
        throw new AssertionError("ImageIO 가 " + format + " 를 쓰지 못했다");
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
