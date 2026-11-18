# Rà soát các phần giao diện còn thiếu - Phiên bản 1

**Dự án:** RELAY - Hệ thống chat  
**Ngày rà soát:** 18/11/2026  
**Căn cứ:** [Final Project.pdf](<Final Project.pdf>), trang 2: phân hệ quản trị; trang 3: phân hệ người sử dụng; trang 4: phạm vi phiên bản 1.

Phiên bản 1 yêu cầu thiết kế giao diện và cơ sở dữ liệu; phần màn hình chưa cần lập trình sự kiện. Bản này chỉ ghi các thành phần giao diện còn thiếu, nội dung màn hình chưa khớp đề và lỗi hiển thị đã quan sát. Ví dụ trong bảng có thể trình bày bằng dữ liệu mẫu.

Số mục được giữ theo từng phân hệ trong đề. Các chức năng có thể bố trí chung trong màn hình, tab hoặc dialog; không bắt buộc mỗi dòng dưới đây phải là một màn hình riêng.

## 1. Phân hệ dành cho người quản trị

### 1.1. Quản lý người dùng

| Mục trong đề | Tên yêu cầu | Giao diện hiện có | Thành phần giao diện còn thiếu | Ví dụ trình bày |
| --- | --- | --- | --- | --- |
| **1** | **Quản lý danh sách người dùng. Thông tin người dùng gồm: tên đăng nhập, họ tên, địa chỉ, ngày sinh, giới tính, email.** | User details có họ tên, email, username, ngày tạo và trạng thái. | Chưa có vùng hiển thị **địa chỉ, ngày sinh, giới tính** trong chi tiết người dùng. Các trường này cũng cần được thể hiện trong form thêm/sửa. | Chi tiết An hiển thị địa chỉ, ngày sinh `15/05/2003`, giới tính; có nội dung mẫu cho trường chưa khai báo. |
| **1.b** | **Thêm/cập nhật/xoá** | Có danh sách người dùng, tìm kiếm, bộ lọc và mở chi tiết. | Thiếu nút **Thêm người dùng**, form **thêm/sửa**, thao tác **Xóa** và hộp xác nhận xóa. Nút khóa tài khoản hiện có là một thao tác khác. | Thiết kế form thêm người dùng, form sửa thông tin và dialog “Xóa tài khoản An?”. |
| **1.d** | **Cập nhật mật khẩu** | Có màn hình chi tiết người dùng. | Thiếu nút hoặc dialog để Admin cập nhật mật khẩu của người dùng đang xem; thiếu trường mật khẩu mới, xác nhận mật khẩu và vùng thông báo. | Trong chi tiết Bình có nút “Cập nhật mật khẩu”, mở form hai ô mật khẩu. |
| **1.f** | **Danh sách bạn bè** | Chi tiết người dùng có lịch sử đăng nhập và hoạt động. | Thiếu tab hoặc khu vực **danh sách bạn bè của người dùng đang được xem**. | Trong chi tiết An có tab “Bạn bè”, hiển thị Bình, Chi và trạng thái danh sách rỗng. |

### 1.2. Lịch sử đăng nhập và báo cáo spam

| Mục trong đề | Tên yêu cầu | Giao diện hiện có | Thành phần giao diện còn thiếu | Ví dụ trình bày |
| --- | --- | --- | --- | --- |
| **2** | **Xem danh sách đăng nhập theo thứ tự thời gian. Thông tin gồm: thời gian, tên đăng nhập, họ tên.** | Có lịch sử đăng nhập riêng trong chi tiết từng người dùng. | Thiếu giao diện danh sách đăng nhập **toàn hệ thống**, có ba cột thời gian, tên đăng nhập và họ tên. | Một bảng có các dòng `08:00 - an - Nguyễn An`, `08:05 - binh - Trần Bình`, `08:10 - an - Nguyễn An`. |
| **4.a** | **Sắp xếp theo thời gian/tên đăng nhập** — thuộc mục **4. Xem danh sách báo cáo spam**. | Có menu sắp xếp mới nhất/cũ nhất. | Menu chưa có lựa chọn **sắp xếp theo tên đăng nhập**. | Menu sắp xếp có “Thời gian mới nhất”, “Thời gian cũ nhất”, “Tên đăng nhập A-Z”. |
| **4.b** | **Lọc theo thời gian** — thuộc mục **4. Xem danh sách báo cáo spam**. | Có ô tìm kiếm, lọc trạng thái và menu sắp xếp. | Thiếu bộ chọn **Từ ngày**, **Đến ngày**, nút áp dụng và xóa bộ lọc. | Phía trên bảng báo cáo có khoảng `01/11/2026 - 18/11/2026` và vùng hiển thị khi không có kết quả. |

### 1.3. Danh sách và biểu đồ thống kê

| Mục trong đề | Tên yêu cầu và các tiểu mục | Giao diện hiện có | Thành phần giao diện còn thiếu | Ví dụ trình bày |
| --- | --- | --- | --- | --- |
| **5; 5.a; 5.b** | **5. Xem danh sách người dùng đăng ký mới:** chọn khoảng thời gian, hiện ra danh sách người dùng đăng ký mới.<br>**5.a. Sắp xếp theo tên/thời gian tạo.**<br>**5.b. Lọc theo tên.** | Có danh sách người dùng chung; Dashboard có thông tin người đăng ký gần nhất. | Thiếu bảng hoặc tab **người đăng ký mới** kèm bộ chọn khoảng ngày đăng ký, ô lọc tên và lựa chọn sắp xếp tên/thời gian tạo. Có thể bổ sung trực tiếp vào màn hình Users. | Tab “Đăng ký mới” có khoảng ngày, ô “Tên”, menu sắp xếp và bảng dữ liệu mẫu. |
| **6** | **Biểu đồ số lượng người đăng ký mới theo năm:** chọn năm, vẽ biểu đồ với trục hoành là tháng, trục tung là số lượng người đăng ký mới. | Dashboard có khu vực hoạt động theo ngày và các khoảng 7/30/365 ngày. | Thiếu **bộ chọn năm** và **biểu đồ đăng ký mới theo 12 tháng**, có nhãn trục và đơn vị số người. | Chọn năm `2026`; biểu đồ có tháng 1-12, cột tháng 1 là 12 người, tháng 2 là 8 người. |
| **7; 7.a; 7.b; 7.c** | **7. Xem danh sách người dùng và số lượng bạn bè** (1 cột bạn bè trực tiếp, 1 cột tính luôn số lượng bạn của bạn).<br>**7.a. Sắp xếp theo tên/thời gian tạo.**<br>**7.b. Lọc theo tên.**<br>**7.c. Lọc theo số lượng bạn trực tiếp (bằng, nhỏ hơn, lớn hơn 1 số được nhập).** | Có danh sách người dùng chung. | Thiếu bảng có hai cột **bạn trực tiếp** và **tính cả bạn của bạn**. Thiếu ô lọc tên, lựa chọn sắp xếp, bộ chọn phép so sánh **bằng/nhỏ hơn/lớn hơn** và ô nhập số. | Hàng mẫu: `An - 6 bạn trực tiếp - 18 tính cả bạn của bạn`. Bộ lọc hiển thị “Bạn trực tiếp > 5”. |
| **8; 8.a; 8.b; 8.c** | **8. Xem danh sách người dùng hoạt động:** chọn khoảng thời gian, hiện ra danh sách người dùng có hoạt động và các số liệu (mở ứng dụng, chat với bao nhiêu người, chat bao nhiêu nhóm).<br>**8.a. Sắp xếp theo tên/thời gian tạo.**<br>**8.b. Lọc theo tên.**<br>**8.c. Lọc theo số lượng hoạt động (bằng, nhỏ hơn, lớn hơn 1 số được nhập).** | Dashboard có thông tin hoạt động tổng hợp theo ngày. | Thiếu bảng **từng người dùng** với các cột mở ứng dụng, số người đã chat, số nhóm đã chat. Thiếu khoảng ngày, ô tên, menu sắp xếp, bộ chọn chỉ số/phép so sánh và ô nhập số. | Hàng mẫu: `An - mở ứng dụng 10 lần - chat 3 người - chat 2 nhóm`; phía trên có khoảng ngày và bộ lọc số lượng. |
| **9** | **Biểu đồ số lượng người hoạt động theo năm:** chọn năm, vẽ biểu đồ với trục hoành là tháng, trục tung là số lượng người có mở ứng dụng. | Có khu vực hoạt động theo ngày. | Thiếu **bộ chọn năm** và **biểu đồ số người mở ứng dụng theo 12 tháng**, có nhãn trục thể hiện đúng đại lượng. | Biểu đồ năm `2026`, trục ngang tháng 1-12, trục dọc ghi “Số người mở ứng dụng”. |

## 2. Phân hệ dành cho người sử dụng

### 2.1. Hồ sơ và khôi phục mật khẩu

| Mục trong đề | Tên yêu cầu | Giao diện hiện có | Thành phần giao diện còn thiếu hoặc chưa khớp | Ví dụ trình bày |
| --- | --- | --- | --- | --- |
| **2** | **Cập nhật thông tin tài khoản** | Profile có tên hiển thị, username, bio và lựa chọn hiển thị hồ sơ. | Chưa có trường **địa chỉ, ngày sinh, giới tính**. Đây là phần đối chiếu với bộ thông tin người dùng được mô tả tại mục Admin 1; mục User 2 không liệt kê lại từng trường. | Bổ sung ô địa chỉ, ô chọn ngày sinh và lựa chọn giới tính trong form hồ sơ. |
| **3** | **Khởi tạo lại mật khẩu: mật khẩu sẽ được random và gởi tới email.** | Có màn hình quên mật khẩu, nhập OTP và tự đặt mật khẩu mới. Nhãn hiện tại nói gửi mã xác minh. | **Nội dung và chuỗi màn hình khác mô tả đề.** Thiếu cách trình bày yêu cầu khởi tạo lại mật khẩu và thông báo mật khẩu mới được gửi tới email. Đây là điểm cần thống nhất thiết kế, không đánh dấu là thiếu toàn bộ giao diện quên mật khẩu. | Có form nhập email và trạng thái minh họa “Mật khẩu mới đã được gửi tới email của bạn”. Nếu nhóm giữ thiết kế OTP, cần làm rõ phương án đó với giảng viên. |

### 2.2. Bạn bè, lời mời và tìm người

| Mục trong đề | Tên yêu cầu | Giao diện hiện có | Thành phần giao diện còn thiếu | Ví dụ trình bày |
| --- | --- | --- | --- | --- |
| **6.a** | **Hiển thị danh sách và cho lọc theo tên (cho thấy trạng thái online/offline)** | Friends có danh sách, ô tìm theo tên/username, nút Message và Block User. | Thiếu **nhãn hoặc dấu chỉ báo online/offline** tại từng người trong danh sách. | Bên cạnh An có chấm xanh và chữ “Online”; bên cạnh Bình có chữ “Offline”. |
| **6.b** | **Tìm theo tên và yêu cầu kết bạn** | Có dialog Add friend, danh sách chọn tài khoản và nút Send request. | Thiếu ô **tìm theo tên** hoặc ô chọn có tìm kiếm được thể hiện rõ; thiếu khu vực/trạng thái kết quả tìm kiếm. | Dialog có ô “Nhập tên”, danh sách người phù hợp và nút “Gửi yêu cầu”. |
| **6.c** | **Huỷ kết bạn** | Có nút Message và Block User. | Thiếu nút/menu **Hủy kết bạn** và hộp xác nhận cho thao tác này. | Dòng bạn bè có “Hủy kết bạn”; có mẫu dialog “Hủy kết bạn với Bình?”. |
| **7.a** | **Hiển thị danh sách và cho lọc theo tên** — thuộc mục **7. Danh sách yêu cầu kết bạn**. | Friend requests có danh sách Incoming/Sent và các nút Accept, Decline, Cancel request. | Thiếu ô **lọc lời mời theo tên**; cần thể hiện rõ áp dụng cho lời mời nhận hoặc gửi. | Phía trên danh sách có ô “Tìm theo tên”; có mẫu danh sách kết quả và trạng thái rỗng. |
| **8** | **Danh sách bạn bè đang online: cho lọc theo tên, có thể chat, tạo group** | **Đã có `Online first`**, ô tìm theo tên/username và nút Message trong Friends. | Thiếu lựa chọn/tab **chỉ bạn đang online**, dấu online/offline trong danh sách và nút **Tạo nhóm** từ người được chọn. Không ghi nhận mục này là thiếu toàn bộ. | Friends có tab “Đang online”, ô tìm tên, nút “Chat” và “Tạo nhóm”. Mẫu danh sách thể hiện những người đang online. |
| **9** | **Tìm người dựa vào tên đăng nhập hoặc tên (không thấy người block mình). Sau đó có thể chat, tạo group** | Có danh sách chọn tài khoản trong New conversation/Add friend; có nút bắt đầu chat. | Thiếu giao diện tìm người rõ ràng theo **tên/username**, danh sách kết quả và nút **Tạo nhóm từ kết quả**. Có thể dùng chung khu vực tìm người với mục 6.b. | Một ô tìm người và các hàng kết quả gồm tên, username, nút “Chat”, nút “Tạo nhóm”; có mẫu khi không tìm thấy người dùng. |

**Lưu ý về mục User 8:** bản này ghi nhận `Online first` là thành phần đã có. Nhận xét ở đây chỉ nói về những lựa chọn và dấu hiệu hiển thị còn thiếu trên màn hình.

### 2.3. Tạo nhóm

| Mục trong đề | Tên yêu cầu | Giao diện hiện có | Thành phần giao diện còn thiếu | Ví dụ trình bày |
| --- | --- | --- | --- | --- |
| **12.a** | **Tạo nhóm: khi tạo cho thêm tối thiểu 1 tên đăng nhập** | Dialog Create group có tên nhóm, mô tả, nút Create và Cancel. | Thiếu ô **nhập/tìm/chọn thành viên ban đầu** và khu vực hiển thị danh sách thành viên đã chọn ngay trong form tạo nhóm. | Form có tên “Nhóm Java”, ô tìm username và thành viên đã chọn `@binh`. Người xem giao diện nhận biết cần chọn ít nhất một người khác. |

## 3. Lỗi hiển thị trên giao diện Admin

Các lỗi sau được ghi nhận từ hai ảnh người dùng cung cấp trong phiên rà soát. Chúng không có số mục chức năng riêng trong đề.

| Mã lỗi | Vị trí | Hiện tượng trên giao diện | Phần hiển thị cần sửa | Ví dụ hình thức mong muốn |
| --- | --- | --- | --- | --- |
| **UI-01** | Dashboard → menu chọn khoảng thời gian `Last 30 days`. | **Chữ và nền gần trùng màu.** Popup có nền sáng, chữ ở các lựa chọn chưa hover cũng rất sáng nên khó đọc; dòng được hover có nền xanh nên rõ hơn. | Chỉnh màu chữ và nền menu để mọi lựa chọn đều đọc rõ ở trạng thái bình thường, hover và đang chọn. Kiểm tra thêm các menu Admin có cùng kiểu hiển thị. | Các dòng “Last 7 days”, “Last 30 days”, “Last 365 days” đều nhìn rõ khi vừa mở menu. |
| **UI-02** | Dashboard → `Platform snapshot`; ảnh tổng thể còn có dấu hiệu tương tự tại `Weekly platform activity`. | **Lỗi hiển thị ký tự/font:** dấu phân cách bị biến thành chuỗi như `â€¢`, xuất hiện ký tự thừa trong nội dung. | Hiển thị đúng dấu phân cách và chữ, không còn ký tự rác. Phạm vi bản này chỉ ghi nhận kết quả nhìn thấy, không kết luận nguyên nhân kỹ thuật. | `22 active · 2 restricted · 1 pending warnings` và `0 messages · 1 opens` hiển thị rõ, đúng ký tự. |

Các ví dụ và gợi ý trên chỉ mô tả hình thức màn hình cần có; không đặt thêm yêu cầu xử lý sự kiện cho phiên bản 1.
