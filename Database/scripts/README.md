# Bộ SQL đồng bộ đủ 18 bảng

## Bạn đã có database: chỉ chạy một file này

1. Chép nội dung gói vào thư mục gốc đồ án để có `Database/scripts/`; ghi đè các file SQL cùng tên.
2. Dừng backend/ứng dụng đang dùng database, mở **pgAdmin > database hiện tại > Query Tool**.
3. Mở **`UPDATE_EXISTING_DATABASE.sql`**, chạy toàn bộ nội dung một lần.

File này dùng được khi database đang có **14 bảng hoặc đã thêm thành 18 bảng** theo `sqlclone.rar`. Nó tạo phần còn thiếu, kiểm tra cấu trúc, cập nhật hàm/trigger và đồng bộ dữ liệu trong một transaction. Không cần xóa database, không cần chạy lại từng file tạo bảng.

Sau khi chạy, kết quả cuối phải là `total_chat_tables = 18`. Có thể chạy `CHECK_AFTER_INSTALL.sql` để xem nhanh. File cập nhật chạy lại được nếu cấu trúc/dữ liệu phù hợp; không phát lại lệnh khóa của các report đã xử lý xong.

Nếu cấu trúc hoặc dữ liệu cũ mâu thuẫn, script báo rõ trường hợp và rollback toàn bộ. Ví dụ: một lời mời nhóm trỏ vào DIRECT; quyết định DISMISS nhưng report đã là REVIEWED; admin không có quyền trong quyết định chưa áp dụng. Script không tự xóa những dòng đó hoặc tự bịa người xử lý.

## Khi tạo database mới

Chọn database trống rồi chạy toàn bộ **`INSTALL_NEW_DATABASE.sql`**. Sau đó có thể chạy **`16_seed_demo.sql`** để thêm dữ liệu mẫu. Không chạy file cài mới lên database đã có schema `chat`.

File `00_install.sql` là lựa chọn tương đương cho psql, không phải pgAdmin:

```bash
psql -X -h localhost -U postgres -d relay_chat -v ON_ERROR_STOP=1 -f Database/scripts/00_install.sql
```

Hai file INSTALL/UPDATE đều là SQL thuần, không có lệnh riêng của psql, nên có thể mở rồi copy toàn bộ vào Query Tool. Không chạy tất cả file trong thư mục bằng wildcard.

## Các phần đã đồng bộ

| Phần | Hành vi |
| --- | --- |
| Lời mời nhóm | Chỉ mời vào GROUP; người mời phải là admin đang hoạt động; kiểm tra trùng lời mời |
| Chấp nhận lời mời | Đổi trạng thái và thêm/kích hoạt membership trong cùng transaction |
| Thêm thành viên qua API admin cũ | Hủy lời mời PENDING đã không còn cần thiết; không giả lập người nhận đã bấm chấp nhận |
| Admin nhóm rời nhóm/hạ quyền | Hủy lời mời PENDING do người đó gửi |
| Cài đặt | Tạo cho user cũ và user đăng ký mới; giữ cài đặt đã lưu; tự cập nhật updated_at |
| Đặt lại mật khẩu | Tự thu hồi yêu cầu cũ khi tạo yêu cầu mới; theo dõi hạn, số lần sai, xác minh và dùng một lần |
| Xử lý báo cáo | Ghi admin/ghi chú/hành động, cập nhật report; SUSPEND khóa tài khoản cùng transaction |
| Hàm review_spam_report cũ | Vẫn gọi được, nay cũng ghi moderation_actions |
| User bị khóa/xóa mềm | Thu hồi reset còn mở; khi xóa mềm, hủy lời mời bạn bè/nhóm đang chờ |
| Quyền backend | Cấp quyền cho các đối tượng mới nếu role server `relay_backend` đã có |

Các cột/bảng cũ được giữ lại. Chỉ thêm đúng 4 bảng đã thống nhất: group_invitations, user_settings, password_reset_requests, moderation_actions. Không thêm các cột bio, mô tả nhóm hoặc mốc đọc của bản ERD dài.

## Dữ liệu đã có trong bản chỉ CREATE TABLE

- Lời mời ACCEPTED mà chưa từng có membership được bổ sung membership theo thời điểm chấp nhận. Nếu membership đã rời nhóm sau lần chấp nhận đó thì giữ nguyên trạng thái đã rời. Nếu cần sửa membership nhưng tài khoản không còn ACTIVE, script dừng để kiểm tra dữ liệu.
- Quyết định đã ghi nhưng report còn PENDING được áp dụng. Với SUSPEND, tài khoản đích sẽ bị khóa. Các report đã xử lý từ trước mà chưa có quyết định vẫn được giữ nguyên, không tự tạo admin/ghi chú giả.
- Yêu cầu reset hết hạn/hết lượt được thu hồi; cài đặt cũ được giữ nguyên.

Khóa trực tiếp ở màn hình Users vẫn dùng `set_account_locked`; thao tác không có report_id nên không tạo quyết định báo cáo.

## Thứ tự file nguồn mới

Các file được đánh số liên tục theo thứ tự phụ thuộc. Không còn bước nhảy từ 09 lên 15.

| File | Nội dung |
| --- | --- |
| `00_install.sql` | Chạy toàn bộ bằng psql |
| `01_schema_types.sql` | Schema và kiểu dữ liệu |
| `02_accounts_social.sql` | Tài khoản, bạn bè và chặn |
| `03_conversations_keys.sql` | Hội thoại, thành viên và khóa |
| `04_messages_activity_reports.sql` | Tin nhắn, hoạt động và báo cáo |
| `05_group_invitations.sql` | Lời mời nhóm |
| `06_user_settings.sql` | Cài đặt cá nhân |
| `07_password_reset_requests.sql` | Yêu cầu đặt lại mật khẩu |
| `08_moderation_actions.sql` | Quyết định xử lý báo cáo |
| `09_indexes.sql` | Index của phần nền |
| `10_integrity_triggers.sql` | Trigger kiểm tra phần nền |
| `11_service_functions.sql` | Hàm nghiệp vụ phần nền |
| `12_sync_triggers.sql` | Trigger đồng bộ các bảng |
| `13_sync_functions.sql` | Hàm đồng bộ và API tương thích |
| `14_views.sql` | Các view tổng hợp |
| `15_sync_data_permissions.sql` | Đồng bộ dữ liệu và phân quyền |
| `16_seed_demo.sql` | Dữ liệu mẫu, chạy riêng khi cần |

`00_install.sql` gọi 01-15 đúng thứ tự; 16 là seed tùy chọn. Hai file INSTALL/UPDATE vẫn chạy độc lập trong pgAdmin. Tên bảng, cột, hàm và logic SQL không đổi.

Khi cập nhật file trong repo, dùng thư mục scripts mới thay cho bản gói cũ để tránh giữ cả hai bộ tên. Không xóa database để đổi tên file.

## Backend gọi gì?

| Hàm | Công dụng |
| --- | --- |
| `save_user_settings(actor,notifications,sound,previews)` | Lưu cài đặt |
| `create_group_invitation(actor,group,invitee)` | Tạo lời mời, trả invitation_id |
| `respond_group_invitation(actor,invitation,status)` | ACCEPTED / REJECTED / CANCELLED |
| `create_password_reset(user,token_hash,ttl,attempt_limit)` | Tạo yêu cầu; mặc định 15 phút, 5 lần sai |
| `verify_password_reset(reset,candidate_hash)` | Kiểm tra mã, trả true/false |
| `complete_password_reset(reset,candidate_hash,new_password_hash)` | Hoàn tất và dùng hết yêu cầu |
| `moderate_report(admin,report,action,notes)` | REVIEW / DISMISS / WARN / SUSPEND |

Các ID actor/admin phải lấy từ session server. Các hàm là SECURITY INVOKER; kiểm tra dữ liệu/vai trò không thay thế xác thực danh tính. Role server có SQL trực tiếp cần được backend kiểm soát, không phát cùng JavaFX. Nếu backend dùng role khác `relay_backend`, quản trị viên cấp quyền tương ứng theo phần GRANT trong file 15.

SQL không tự gửi email, sinh OTP hoặc băm mật khẩu. Backend tạo token/hash/HMAC bằng thư viện phù hợp và truyền bytea, không lưu OTP gốc. Với OTP ngắn cần bảo vệ phù hợp và giới hạn tần suất gửi ở server. Password mới được backend băm bằng Argon2id/bcrypt; SQL chỉ kiểm tra hình dạng chuỗi hash.

Sau verify/complete trả **false**, commit để giữ số lần sai đã tăng; SQLException thì rollback. Ghi dữ liệu dùng READ COMMITTED như bộ nền; giữ transaction ngắn và retry toàn transaction khi gặp deadlock 40P01. Quyền xem dữ liệu và vô hiệu hóa session vẫn do backend thực hiện. Frontend mock không tự đổi thành kết nối database chỉ bằng việc chép SQL.

## Kiểm tra đã thực hiện

PostgreSQL 18.6 native: **130 kiểm tra SQL** (58 nền + 65 đồng bộ + 7 vòng đời), **14 kịch bản khóa/đồng thời**, cài mới, cập nhật từ 14 bảng, cập nhật từ 18 bảng có dữ liệu và chạy lại migration. Chi tiết ở `TEST_RESULTS.md`.

Thư mục `tests` để kiểm tra trên database phát triển. Các file SQL test rollback các dòng test, sequence có thể tăng. Test Node tùy chọn cần `npm install`, các biến PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE và database tên kết thúc `_test`/`_validation`; `npm test` giữ fixture đã commit để kiểm tra cạnh tranh. Không cần Node để sử dụng database.

Mục tiêu PostgreSQL 16+; đã chạy thực tế trên 18.6, chưa chạy riêng 16/17.
