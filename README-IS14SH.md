# J2ME-Loader 1.8.2 — Sharp AQUOS IS14SH

Đây là nhánh backport dài hạn cho firmware gốc Android 2.3.5/API 10 của Sharp
AQUOS IS14SH (màn hình 960×540, bàn phím số trượt). APK dùng shell widget của
Android API 10, chạy launcher và MIDlet trong cùng process, và chỉ nhận game
local trên thẻ nhớ.

## Môi trường build

- JDK 17 (không dùng JDK 21+), Gradle wrapper 8.7 và Android Gradle Plugin 8.5.1.
- Android SDK Platform 34 và build-tools tương ứng.
- `minSdk=10`, `targetSdk=10`, Java 8 bytecode/desugaring.
- Không cấu hình NDK/native build. Release chỉ chứa một `classes.dex` DEX 035,
  không có `lib/`; chữ ký v1 được bật.

Build và kiểm tra artifact:

```sh
export JAVA_HOME=/path/to/jdk-17
sh gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease
sh gradlew verifyLegacyArtifact
```

Keystore release không nằm trong Git. Có thể cung cấp `keystore.properties`
ngoài repo (hoặc biến môi trường CI `BITRISE_*`); nếu không, local release dùng
debug key để kiểm tra hình dạng APK, không dùng để phát hành.

## Probe IS14SH

Kết nối ADB với USB debugging bật rồi chạy:

```sh
tools/probe-is14sh.sh ./artifacts/is14sh-probe.txt
```

Probe ghi API/ABI/RAM/OpenGL và `Build.MODEL/DEVICE`. Sau khi cài APK chẩn đoán
API 10, ghi thêm từng `keyCode`, `scanCode` và action cho các phím số, `*`, `#`,
D-pad, Enter, Call, End, Back, Mail và Browser. Gate của probe là cài/khởi động
được APK, `DexClassLoader` tải DEX 035, Canvas/GLES2 render được, và MIDI/WAV/MP3
phát được mà logcat không có `VerifyError`, `NoClassDefFoundError` hay
`UnsatisfiedLinkError`.

## Cài và chạy game

```sh
adb install -r app/build/outputs/apk/release/J2ME-Loader-IS14SH-1.8.2.apk
adb shell mkdir -p /sdcard/J2ME-Loader/incoming
adb push game.jar /sdcard/J2ME-Loader/incoming/
adb push game.jad /sdcard/J2ME-Loader/incoming/   # tùy chọn
```

Mở ứng dụng, chọn `Install JAR/JAD`, duyệt `/sdcard`, rồi chạm game trong
catalog. JAD phải trỏ đến JAR ở cùng thư mục bằng tên tương đối; URL
`http://`, `https://`, scheme khác, đường dẫn tuyệt đối và `..` đều bị từ chối.

Dữ liệu giữ nguyên tương thích với bản cũ:

```text
/sdcard/J2ME-Loader/
  converted/<game>/converted.dex
  converted/<game>/res.jar
  converted/<game>/converted.dex.conf
  configs/<game>/...
  data/<game>/...       # RMS
  fs/...
```

Update game thay nguyên tử thư mục trong `converted` và không xóa `configs` hay
`data`, vì vậy RMS vẫn còn sau update, force-stop hoặc đổi orientation.

## Phím IS14SH

Profile tự áp dụng khi `Build.MODEL` hoặc `Build.DEVICE` chứa `IS14SH` (hoặc
Sharp/IS14). Ánh xạ mặc định là `0–9`, `*`, `#`, D-pad lên/xuống/trái/phải,
D-pad center→FIRE, Enter, Back, Call, End, Mail và Browser. Người dùng vẫn có
thể remap trong cấu hình game. Manifest chỉ xử lý
`orientation|keyboard|keyboardHidden`, nên trượt bàn phím không tạo lại MIDlet.

## Capability của MVP

Có: duyệt thẻ nhớ, cài JAR/JAD local, chuyển JAR→DEX 035 bằng ASM 5.2/dx,
Canvas software, GLES2 khi probe ổn định, RMS/config, MIDI/Tone/WAV/MP3.
Archive bị giới hạn 32 MiB, 4.096 entry và 128 MiB giải nén; tên tuyệt đối,
`..`, ZIP traversal/bomb đều bị từ chối.

Không hỗ trợ có chủ ý: M3G, Mascot Capsule 3D, MIDI native, camera J2ME,
Bluetooth, Location, HTTP/HTTPS installer, MMF/ADPCM, crash upload, Google
Play và Android 4+. Các API này trả lỗi “unsupported” có kiểm soát.

`fixtures/is14sh/KeyAudioRmsMidlet.java` là MIDlet fixture tự viết để kiểm tra
keypad, âm thanh và RMS; không commit JAR game của người dùng.
