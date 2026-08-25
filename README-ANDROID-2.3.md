# J2ME-Loader 1.8.3 — Android 2.3+ legacy build

Đây là bản backport dài hạn tương thích Android 2.3/API 10+. APK dùng shell
widget của Android API 10, chạy launcher và MIDlet trong cùng process, và chỉ
nhận game local trên thẻ nhớ. Sharp AQUOS IS14SH là thiết bị API 10 được xác
thực, còn các ánh xạ phím riêng của máy chỉ được bật khi nhận đúng model.

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

## Probe IS14SH (thiết bị xác thực)

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
adb install -r app/build/outputs/apk/release/J2ME-Loader-Android-2.3-1.8.3.apk
adb shell mkdir -p /sdcard/J2ME-Loader/incoming
adb push game.jar /sdcard/J2ME-Loader/incoming/
adb push game.jad /sdcard/J2ME-Loader/incoming/   # tùy chọn
```

Mở ứng dụng, chạm nút `+` (Install JAR/JAD), duyệt `/sdcard`, rồi chạm game
trong catalog. JAD phải trỏ đến JAR ở cùng thư mục bằng tên tương đối; URL
`http://`, `https://`, scheme khác, đường dẫn tuyệt đối và `..` đều bị từ chối.

Trên launcher, nút bánh răng hoặc phím Menu mở `Settings`; Profiles nằm trong
Settings để dùng được trên thiết bị không có phím vật lý. Settings dùng file
`legacy-preferences` (giữ màn hình sáng, status bar và rung; thay đổi áp dụng
ở lần chạy MIDlet kế tiếp). Trong Profiles có thể tạo, sửa, đổi tên, xóa có
xác nhận, đặt/bỏ mặc định; giữ game để mở editor riêng. Editor chỉ ghi khi
nhấn `Save` hoặc `Save & Play`; Back chỉ bỏ bản nháp. Profile mặc định chỉ
được bootstrap cho game chưa có `config.json` hoặc `config.xml`.

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

## Phím vật lý tùy thiết bị

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
Bluetooth, Location, HTTP/HTTPS installer, MMF/ADPCM, crash upload và Google
Play. Các API này trả lỗi “unsupported” có kiểm soát.

`fixtures/is14sh/KeyAudioRmsMidlet.java` là MIDlet fixture tự viết để kiểm tra
keypad, âm thanh và RMS; không commit JAR game của người dùng.
