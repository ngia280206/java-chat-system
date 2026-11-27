# Backend - Kiểm tra kết nối PostgreSQL bằng JDBC

Hướng dẫn này dành cho Gia và Kỳ thực hiện phần kiểm tra kết nối của issue #20. Chương trình Java truy vấn bảng `chat.users` và in thông tin tài khoản ra terminal.

## Chuẩn bị

- Cài JDK 17 và cấu hình `JAVA_HOME` trỏ đến thư mục JDK.
- Cài và khởi động PostgreSQL 16 trở lên.
- Có tài khoản PostgreSQL được phép kết nối và đọc bảng trong schema `chat`.
- Có kết nối mạng trong lần chạy đầu để Gradle tải bản phân phối và thư viện cần thiết.
- Mở terminal tại thư mục gốc repo `java-chat-system`, nơi chứa `gradlew.bat` và `settings.gradle`.

Gradle Wrapper được dùng chung ở gốc repo. Module Backend được khai báo bằng tên `backend`, trỏ đến thư mục `Backend/`.

## Tạo database và nạp dữ liệu mẫu

### Dùng pgAdmin 4

1. Kết nối server PostgreSQL, chuột phải vào **Databases**, chọn **Create > Database** và tạo database `relay_chat`.
2. Chuột phải database vừa tạo, chọn **Query Tool**.
3. Mở file `Database/scripts/INSTALL_NEW_DATABASE.sql` và chạy toàn bộ trên database trống.
4. Mở file `Database/scripts/16_seed_demo.sql` trong Query Tool. Xóa dòng `\set ON_ERROR_STOP on` trong ô soạn thảo trước khi chạy, vì đây là lệnh dành cho psql. Giữ file nguồn trong repo để dùng với psql.
5. Chạy toàn bộ phần SQL còn lại để thêm dữ liệu mẫu.
6. Mở và chạy `Database/scripts/CHECK_AFTER_INSTALL.sql`. Kết quả `total_chat_tables` cần bằng `18`.

Nếu database đã có schema `chat`, xem hướng dẫn cập nhật trong [README bộ SQL](../Database/scripts/README.md) để chọn script phù hợp.

### Dùng terminal với psql

Nếu `createdb` và `psql` đã có trong PATH, chạy từ thư mục gốc repo:

```powershell
createdb -h localhost -p 5432 -U postgres relay_chat
psql -X -h localhost -p 5432 -U postgres -d relay_chat -v ON_ERROR_STOP=1 -f Database/scripts/00_install.sql
psql -X -h localhost -p 5432 -U postgres -d relay_chat -v ON_ERROR_STOP=1 -f Database/scripts/16_seed_demo.sql
psql -X -h localhost -p 5432 -U postgres -d relay_chat -v ON_ERROR_STOP=1 -f Database/scripts/CHECK_AFTER_INSTALL.sql
```

Nhập mật khẩu tài khoản PostgreSQL khi được yêu cầu. Điều chỉnh host, port và username theo máy đang dùng.

Script seed tạo 5 tài khoản, 1 hội thoại riêng, 1 nhóm, 4 tin nhắn và 1 báo cáo spam. Chạy seed một lần trên mỗi database thử. Script sẽ dừng nếu tài khoản demo đã tồn tại.

## Cấu hình kết nối và cách đổi DB

Thông tin kết nối hiện được khai báo bằng các biến `url`, `username`, `password` trong:

```text
Backend/src/test/java/com/chatsystem/backend/repository/TestConnection.java
```

Trên máy Kỳ, sửa ba giá trị này theo PostgreSQL của máy mình rồi lưu file:

```java
String url = "jdbc:postgresql://localhost:5432/relay_chat";
String username = "postgres";
String password = "MAT_KHAU_POSTGRESQL_TREN_MAY";
```

| Thành phần | Ý nghĩa | Ví dụ thay đổi |
| --- | --- | --- |
| `localhost` | Địa chỉ máy chạy PostgreSQL | Điền địa chỉ server đang dùng |
| `5432` | Port PostgreSQL | Đổi nếu server dùng port khác |
| `relay_chat` | Tên database | Đổi thành `relay_chat_ky` nếu Kỳ đặt tên đó |
| `username` | Tài khoản đăng nhập PostgreSQL | Điền tài khoản có quyền truy vấn |
| `password` | Mật khẩu của tài khoản PostgreSQL | Điền mật khẩu trên máy đang chạy |

Có thể xem host, port và username tại **pgAdmin > chuột phải server > Properties > Connection**. Password là mật khẩu của tài khoản PostgreSQL.

Ví dụ, sau khi tạo database `relay_chat_ky` và chạy script cài đặt cùng seed vào database đó, đổi URL thành:

```java
String url = "jdbc:postgresql://localhost:5432/relay_chat_ky";
```

Phần đọc cấu hình ngoài của issue #20 cần được bổ sung tiếp. Khi đã triển khai, cập nhật mục này với đường dẫn file cấu hình và cách truyền đường dẫn cho chương trình.

## Lệnh chạy

Từ thư mục gốc repo:

```powershell
.\gradlew.bat :backend:testConnection
```

Tác vụ `testConnection` biên dịch source của Backend và chạy hàm `main()` trong `com.chatsystem.backend.repository.TestConnection`, với thư viện JDBC trên classpath.

## Kết quả mong đợi

Terminal hiển thị thông tin của 5 tài khoản mẫu:

| Username | Email | Họ tên | Vai trò |
| --- | --- | --- | --- |
| `alex.morgan` | `alex.morgan@example.com` | Alex Morgan | USER |
| `sophia.chen` | `sophia.chen@example.com` | Sophia Chen | USER |
| `marcus.johnson` | `marcus.johnson@example.com` | Marcus Johnson | USER |
| `sarah.connor` | `sarah.connor@example.com` | Sarah Connor | USER |
| `relay.admin` | `admin@relay.example` | RELAY Administrator | ADMIN |

Ví dụ một tài khoản được in ra:

```text
Username: alex.morgan
Email: alex.morgan@example.com
Full Name: Alex Morgan
System Role: USER
---------------------------
```

Đối chiếu đủ các tài khoản trong bảng. Thứ tự hiển thị có thể thay đổi vì truy vấn hiện tại lấy dữ liệu trực tiếp từ `chat.users`.

Hàm `main()` hiện bắt lỗi và in stack trace. Vì vậy, xác nhận kết quả bằng thông tin tài khoản trong terminal và kiểm tra các lỗi `Exception`, kể cả khi Gradle báo `BUILD SUCCESSFUL`.

## Xử lý lỗi thường gặp

| Thông báo hoặc hiện tượng | Cách kiểm tra |
| --- | --- |
| `Unable to access jarfile` | Kiểm tra `gradle/wrapper/gradle-wrapper.jar` nằm ở gốc repo cạnh `gradlew.bat` |
| `ClassNotFoundException: org.postgresql.Driver` | Chạy bằng tác vụ Gradle `:backend:testConnection` để có thư viện JDBC trên classpath |
| `Connection refused` | Kiểm tra PostgreSQL đang chạy, đúng host và port |
| `password authentication failed` | Kiểm tra username và password PostgreSQL |
| `database ... does not exist` | Kiểm tra tên database trong URL |
| `relation "chat.users" does not exist` | Kiểm tra đã chạy script cài đặt trên đúng database |
| `permission denied` | Kiểm tra quyền của tài khoản PostgreSQL đối với schema và bảng |
| Chương trình kết thúc nhưng thiếu tài khoản mẫu | Chạy `SELECT username, email, full_name, system_role FROM chat.users;` trong pgAdmin và đối chiếu dữ liệu seed |

Khi báo lỗi cho Gia, Kỳ ghi lại lệnh chạy, host/port/tên database, kết quả mong đợi và log thực tế. Che mật khẩu trong ảnh hoặc đoạn code gửi kèm.
