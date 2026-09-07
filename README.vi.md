# Mytube — bản di động

[English](README.md) · **Tiếng Việt**

Ứng dụng Android và iOS gốc cho [Local Mytube](https://github.com/kuteo-git/Mytube),
thư viện phim tự dựng chạy trên một máy Mac ở nhà.

Nó tồn tại để phát tiếng khi màn hình đã tắt, việc mà trình duyệt không làm được.
Trên máy tính thì web app vẫn tốt hơn và vẫn là bản chính; cái này để cầm điện
thoại đi quanh nhà.

<p align="center">
  <img src="docs/screenshots/demo.gif" width="30%" alt="Cuộn feed trong khi miniplayer vẫn chạy, mở lên, phụ đề trên hình, bảng cài đặt, rồi thu lại" />
</p>
<p align="center">
  <em>Quay trên iPhone. <a href="docs/screenshots/demo.mp4">Cùng đoạn đó, chất lượng đầy đủ.</a></em>
</p>

<p align="center">
  <img src="docs/screenshots/home.png" width="30%" alt="Trang chủ: hàng chip chủ đề ghim trên Xem tiếp, thanh tab nổi bên dưới" />
  <img src="docs/screenshots/missed.png" width="30%" alt="Chip Bỏ lỡ: những gì các kênh đang theo dõi đã đăng trong một ngày qua mà chưa ai xem" />
  <img src="docs/screenshots/watch.png" width="30%" alt="Màn hình xem: nút điều khiển trên kính đè lên hình, hàng kênh, và bình luận" />
</p>
<p align="center">
  <img src="docs/screenshots/player-settings.png" width="30%" alt="Bảng cài đặt của trình phát: phụ đề, giọng Việt kèm tiến độ, tự phát" />
  <img src="docs/screenshots/miniplayer.png" width="30%" alt="Miniplayer vẫn chạy sau khi kéo màn hình xem xuống" />
  <img src="docs/screenshots/settings.png" width="30%" alt="Cài đặt: địa chỉ máy chủ, hồ sơ, lịch sử, kênh đăng ký, giọng đọc, ngôn ngữ" />
</p>

## Nó làm được gì

- **Trang chủ**, với hàng chip chủ đề của máy chủ, *Xem tiếp*, và **Bỏ lỡ**, tức
  những gì các kênh bạn theo dõi đã đăng trong một ngày qua mà chưa ai xem.
- **Màn hình xem**, đúng kiểu app YouTube: nút điều khiển trên nền kính đè lên
  hình, thanh tua chạy dọc mép dưới, bình luận chỉ đọc, và dải "tiếp theo" cũng
  chính là hàng chờ phát.
- **Playlist**, tạo và sửa ngay tại đây. Kệ "Đã lưu" là hàng đầu tiên trong cùng
  cái sheet mà nút dấu trang mở ra.
- **Phụ đề**, nạp từ các tệp nằm cạnh video.
- **Thuyết minh tiếng Việt**: máy chủ đọc phụ đề, dịch và tổng hợp giọng; app này
  phát các đoạn tiếng đó đè lên video và hạ tiếng gốc xuống trong lúc đang nói.
  Hai mức, giọng đọc to bao nhiêu và video tụt xuống bao nhiêu dưới nó, là của
  người xem, và là hai con số riêng chứ không phải một cái nhân vào cái kia.
- **Phát nền**, mục trên màn hình khoá có ảnh và thanh tua kéo được, và một
  miniplayer đi theo qua mọi màn.
- **Tìm kiếm** chạm tới cả thư viện *lẫn* YouTube. Mở một kết quả từ YouTube thì
  hàng trong catalogue được ghi trước, nên trình phát không bao giờ mở vào chỗ
  trống.
- **Tiếng Anh và tiếng Việt**, chọn theo từng máy.

## Nó cần gì

Máy chủ. App này nói chuyện với gateway của Local Mytube trong wifi nhà và không
làm được gì nếu thiếu nó: không tài khoản, không đám mây, không bản sao thư viện
nào nằm trên điện thoại.

- **Chỉ trong wifi nhà.** Máy chủ chỉ chạy trong mạng LAN, HTTP trần, và
  `X-User-Id` là một header ai trong mạng cũng đặt được. Đó là lựa chọn có chủ ý
  cho một nhà năm người, và sẽ không thể biện minh ở bất kỳ đâu khác.
- **Chỉ phát trực tuyến.** Không tải gì về máy, nên phát nền chạy quanh nhà và
  không chạy trên đường đi làm.
- Địa chỉ máy chủ được **gõ tay** ở lần chạy đầu, như Home Assistant. mDNS chết
  trên router thường, còn địa chỉ của máy Mac thì do DHCP cấp.

## Dựng thế nào

Kotlin Multiplatform với Compose Multiplatform. Mọi thứ dùng chung, trừ ba đường
nối: trình phát video, trình phát thuyết minh, và phiên phát nền.

```sh
source env.sh    # bộ công cụ nằm cạnh dự án, không nằm trong ~/.zshrc

./gradlew jvmTest \
          :composeApp:compileDebugKotlinAndroid \
          :composeApp:compileKotlinIosSimulatorArm64
```

Đó là lệnh kiểm tra, và nó ngắn hơn `./gradlew check` vì `check` phải link một
binary test của Kotlin/Native, việc đó cần Xcode đầy đủ. Nên phần **không** được
phủ là khâu link cho iOS và `iosSimulatorArm64Test`, ghi ra là chưa phủ thay vì
lặng lẽ bỏ qua.

- **Android**: `./gradlew :composeApp:assembleDebug`.
- **iOS**: mở `iosApp/iosApp.xcodeproj`, dựng với SDK iOS 26. Framework Kotlin do
  một build phase biên dịch, và phase đó `source env.sh`. Thiếu dòng ấy thì bản
  dựng trong Xcode hỏng trong khi bản dựng ở terminal chạy tốt, đọc lên như một
  dự án lỗi.

## Nó được ghép ra sao

```
composeApp/src/
  commonMain/  domain · data · ui — toàn bộ ứng dụng
  androidMain/ Media3/ExoPlayer, MediaSessionService
  iosMain/     AVPlayer, AVAudioSession, MPNowPlayingInfoCenter
  jvmTest/     hai cái guard
```

Clean architecture, xét theo **chiều của phụ thuộc** chứ không phải theo số thư
mục: `domain` không import Ktor, không import serialization, không import
Compose. Hai guard trong `jvmTest` làm đỏ build khi điều đó không còn đúng, và cả
hai đã được chứng minh là biết đỏ: một dòng `import io.ktor` trong `domain`, và
một chuỗi `"Try again"` viết cứng trong màn hình.

Vài quyết định giải thích được phần lớn code:

- **Không dùng DI framework.** Constructor injection viết tay từ một composition
  root duy nhất. Service locator thiếu đăng ký thì hỏng lúc chạy, ngược hẳn với
  thói quen của dự án này là biến quy tắc thành lỗi biên dịch.
- **Từ điển là một interface, không phải tra khoá.** `Strings` khai báo mọi chữ
  hiển thị thành property, nên không thể tồn tại một bản dịch thiếu: thêm một
  property là mọi ngôn ngữ ngừng biên dịch cho tới khi được điền, và trình biên
  dịch gọi tên đúng cái field đó.
- **Không gì trong `domain` hay ViewModel được nullable; DTO thì có.** Dây thật
  sự có thể thiếu trường, nên sự vắng mặt được quyết định đúng một lần, ở mapper,
  và được đặt tên thay vì ngầm hiểu.
- **Không có Compose UI test.** Preview lo phần "màn hình trông thế nào", kể cả
  những trạng thái khó dựng trên máy thật, còn test ViewModel lo phần "nó làm
  gì".

`CLAUDE.md` là bản hiến chương: mọi quyết định trên đều được ghi ở đó kèm phép đo
đã dẫn tới nó, gồm cả những quyết định sau này hoá ra là sai.

## Tình trạng

Chạy trên cả Android và iOS. Phát nền, màn hình khoá và việc hạ tiếng khi thuyết
minh đều đã được nghe trên máy thật với màn hình tắt; ở đây không có gì được gọi
là xong chỉ vì nó biên dịch được.

Lớp kính là chất liệu của riêng app chứ không phải của hệ điều hành. Các tấm kính
lấy mẫu thứ nằm sau chúng và bẻ sáng ở viền, còn các nút đè lên video thì do
SwiftUI vẽ trên iOS 26, vì video là cái nền duy nhất mà Compose không lấy mẫu
được.

## Giấy phép

Chưa có. Đây là dự án của một nhà, công khai để đọc được.
