# RELAY - PostgreSQL database scripts

Bộ script triển khai **đúng 14 bảng** của ERD và từ điển dữ liệu trong đồ án. Mục tiêu PostgreSQL **16+**; đã chạy thực tế trên **PostgreSQL 18.6**. Không yêu cầu extension.

## 1. Cài đặt nhanh

Giải nén vào dự án để được thư mục `Database/scripts`. Chọn **một** cách cài:

### psql / SQL Shell

Tạo database mới `relay_chat` bằng pgAdmin hoặc:

```bash
createdb -h localhost -U postgres -E UTF8 relay_chat
```

Từ thư mục gốc dự án:

```bash
psql -X -h localhost -U postgres -d relay_chat -v ON_ERROR_STOP=1 -f Database/scripts/00_install.sql
```

`00_install.sql` tự gọi 01-08 bằng `\ir`, đường dẫn tính từ chính file cài đặt. Toàn bộ cài đặt nằm trong một transaction, lỗi sẽ không để lại nửa schema.

### pgAdmin Query Tool

1. Tạo database `relay_chat` với encoding UTF8 và chọn database đó.
2. Mở Query Tool, mở **`bundles/install_pgadmin.sql`**, chạy toàn bộ file.
3. Refresh `Schemas > chat > Tables`: phải có **14 bảng**.

Bundle chứa cùng SQL 01-08, không có meta-command của psql. Không chạy bundle sau `00_install.sql`.

Đây là migration khởi tạo, **không phải script reset**: chạy lại sẽ báo schema `chat` đã tồn tại và dừng, không tự xóa dữ liệu. Không chạy mọi `*.sql` bằng wildcard vì sẽ chạy trùng hai cách cài và cả seed tùy chọn.

## 2. Cách chia file

| File | Nội dung |
| --- | --- |
| `00_install.sql` | Điểm chạy psql, gọi 01-08 trong một transaction |
| `01_schema_types.sql` | Schema `chat`, 8 ENUM, quyền mặc định |
| `02_accounts_social.sql` | `users`, `friend_requests`, `friendships`, `user_blocks` |
| `03_conversations_keys.sql` | `conversations`, `conversation_members`, `user_devices`, `group_key_versions`, `group_key_envelopes` |
| `04_messages_activity_reports.sql` | `messages`, `message_hidden_for_users`, `login_history`, `activity_logs`, `spam_reports` |
| `05_indexes.sql` | Index cho FK, lịch sử, thống kê, nonce |
| `06_integrity_triggers.sql` | Quy tắc liên bảng, sequence và ghi log tự động |
| `07_service_functions.sql` | Các hàm nghiệp vụ backend gọi |
| `08_views.sql` | Thống kê nhóm, đăng nhập cuối, hoạt động theo ngày |
| `09_seed_demo.sql` | Seed tùy chọn, chạy riêng sau khi cài |
| `bundles/install_pgadmin.sql` | Bản ghép 01-08 cho Query Tool |
| `bundles/seed_demo_pgadmin.sql` | Seed không có meta-command psql |
| `examples/01_queries.sql` | 10 truy vấn đọc/thống kê mẫu |
| `examples/02_backend_permissions.sql.example` | Mẫu cấp quyền cho server tin cậy |
| `tests/01_smoke_test.sql` | 58 kiểm tra, rollback dữ liệu test |
| `tests/02_concurrency_test.mjs` | 9 kịch bản hai kết nối thật |
| `TEST_RESULTS.md` | Kết quả kiểm tra và phạm vi |

Giữ tên bảng/cột theo ERD. Dùng tiền tố `chat.`, không phải `public.users`.

## 3. Quy đổi và quyết định triển khai

| ERD / nghiệp vụ | PostgreSQL |
| --- | --- |
| ID đơn BIGINT | `GENERATED ALWAYS AS IDENTITY` |
| BLOB / TIMESTAMP | `bytea` / `timestamptz` |
| Username, email duy nhất | Unique index trên `lower(...)`, kể cả tài khoản xóa mềm |
| Bạn bè không đảo cặp | PK ghép và `user_id_1 < user_id_2` |
| Một request PENDING mỗi cặp | Partial unique index với LEAST/GREATEST |
| DIRECT đúng hai người, không trùng cặp | Deferred constraint trigger và advisory lock theo cặp |
| Nhóm mới | Creator là ADMIN, ít nhất hai thành viên khi commit |
| Nhóm đã tồn tại | Có thể còn một người, nhưng phải còn ADMIN; chưa hỗ trợ nhóm rỗng |
| Xóa tài khoản | `status='DELETED'`, `deleted_at`; cấm DELETE vật lý và khôi phục |
| Rời nhóm | `left_at`, giữ membership cũ để bảo toàn FK |
| Tham gia lại | Giữ joined_at gốc và watermark cũ; backend quyết định chính sách lịch sử |
| Xóa một tin / cả lịch sử riêng | `message_hidden_for_users` / watermark số thứ tự |
| message_seq | Trigger khóa hội thoại và cấp max+1; không dùng sequence toàn cục |
| Tin nhắn và thống kê | Mỗi INSERT tự ghi một MESSAGE_SENT trong cùng transaction |
| Payload | Bản rõ hoặc bản mã, không đồng thời cả hai |
| E2EE | Chỉ GROUP vì ERD chỉ mô hình hóa phân phối khóa nhóm; DIRECT dùng NONE |
| Nonce | Không trùng `(conversation_id,key_version,nonce)` |
| Block | Cấm kết bạn/gửi DIRECT hai chiều; hủy request chờ, xóa friendship; không chặn tin nhóm chung |
| Lịch sử | Tin nhắn/log append-only; ẩn riêng thay cho sửa/xóa bản tin gốc |

Độ dài VARCHAR, giới hạn trang 1-200, không phân biệt hoa/thường của username/email và các lựa chọn ở trên là quyết định triển khai. Email chỉ kiểm tra định dạng cơ bản. Database kiểm tra độ dài password_hash, **không** xác minh thuật toán/hash hợp lệ.

## 4. Transaction và Java backend

Ghi dữ liệu **yêu cầu READ COMMITTED**, mặc định PostgreSQL. Trigger từ chối ghi ở isolation khác với SQLSTATE `25000`: sau khi chờ advisory lock, kiểm tra liên bảng phải nhìn thấy dữ liệu mới commit. Đọc/báo cáo vẫn có thể dùng snapshot riêng.

Tạo hội thoại và thành viên ban đầu phải cùng transaction. Kiểm tra đủ người/admin chạy ở COMMIT, nên backend phải xử lý cả lỗi commit. Có thể cấp admin mới rồi hạ quyền/rời nhóm trong cùng transaction.

```java
connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
connection.setAutoCommit(false);
try {
    // PreparedStatement: SELECT chat.create_direct_conversation(?, ?)
    // Hoặc SELECT chat.send_text_message(?, ?, ?)
    // actorId lấy từ session đã xác thực ở server.
    connection.commit();
} catch (SQLException ex) {
    connection.rollback();
    throw ex;
}
```

Khóa theo hội thoại bảo vệ gửi tin/xóa lịch sử/thay đổi thành viên; khóa theo cặp bảo vệ DIRECT/block/bạn bè; khóa đọc tài khoản/thiết bị đồng bộ với lock/revoke. Giữ transaction ngắn, không chờ mạng/mã hóa trong transaction. Khi ghép nhiều nghiệp vụ, backend cần retry toàn transaction khi gặp deadlock `40P01`, khóa nhiều đối tượng theo thứ tự ID thống nhất.

SQLSTATE: `23505` trùng khóa/cặp/nonce; `23503` FK; `23514` vi phạm nghiệp vụ; `42501` thiếu quyền trong hàm; `25000` sai isolation. Identity ID có thể có khoảng trống sau rollback.

### Hàm backend

| Hàm | Mục đích |
| --- | --- |
| `create_direct_conversation(actor,other)` | Trả ID có sẵn hoặc tạo DIRECT mới |
| `create_group(actor,name,member_ids,mode)` | Tạo nhóm, tự thêm creator làm admin |
| `add_group_member(actor,group,user)` | Admin thêm/tham gia lại thành viên |
| `remove_group_member(actor,group,user)` | Admin cho người khác rời nhóm |
| `change_group_role(actor,group,user,role)` | Đổi quyền, bảo vệ admin cuối |
| `leave_group(actor,group)` | Tự rời nhóm |
| `send_text_message(actor,conversation,text)` | Tạo tin, tự cấp thứ tự và ghi log |
| `send_encrypted_message(actor,conversation,ciphertext,nonce,version)` | Lưu bản mã client tạo |
| `get_visible_messages(actor,conversation,limit,before_seq)` | Phân trang mới nhất trước, lọc tin ẩn/watermark |
| `search_visible_messages(actor,conversation,query,limit)` | Tìm chuỗi literal không phân biệt hoa/thường |
| `hide_message(actor,message)` / `clear_history(actor,conversation)` | Ẩn riêng tin / lịch sử đến mốc hiện tại |
| `send_friend_request(actor,receiver)` | Tạo request PENDING |
| `respond_friend_request(actor,request,status)` | Receiver accept/reject, sender cancel |
| `block_user(actor,target)` / `unblock_user(actor,target)` | Chặn / bỏ chặn đúng hướng |
| `submit_spam_report(actor,message,reason)` | Báo cáo tin nhìn thấy, tự lấy đúng sender |
| `review_spam_report(admin,report,status)` | REVIEWED hoặc DISMISSED |
| `set_account_locked(admin,target,locked)` | Khóa/mở tài khoản khác chưa DELETED |

JDBC dùng `long`/`setLong` cho BIGINT, `createArrayOf("bigint", ...)` cho mảng thành viên, `setBytes` cho bytea, `OffsetDateTime`/`Instant` cho thời điểm. Múi giờ hiển thị do client quyết định. Trang lịch sử trả mới nhất trước, UI có thể đảo trang để hiển thị thời gian tăng dần.

## 5. Dữ liệu demo

Tùy chọn sau khi cài:

```bash
psql -X -h localhost -U postgres -d relay_chat -v ON_ERROR_STOP=1 -f Database/scripts/09_seed_demo.sql
```

pgAdmin dùng `bundles/seed_demo_pgadmin.sql`. Seed có transaction riêng, từ chối email demo đã tồn tại, không ghi đè dữ liệu cũ.

- User: `alex.morgan@example.com`; admin: `admin@relay.example`.
- Mật khẩu chung: `DemoPass123!`, lưu **bcrypt cost 12** thực sự.
- Tạo 5 tài khoản, 1 DIRECT, 1 nhóm, 4 tin nhắn, 1 báo cáo PENDING.

Backend phải tự xác thực bcrypt rồi mới ghi login_history. Cài database **không tự chuyển JavaFX từ mock sang PostgreSQL**; cần backend/JDBC adapter. Byte giả lập trong test E2EE chỉ kiểm tra cấu trúc, không dùng như bản mã/khóa thực tế.

## 6. Quyền truy cập và khoảng trống ERD

Hàm là **SECURITY INVOKER**. PUBLIC không được dùng schema/thực thi function. Mẫu cấp quyền chỉ dành cho **server tin cậy**; không nhúng tài khoản database vào JavaFX phát cho người dùng.

`p_actor_id` phải lấy từ session server; hàm kiểm tra vai trò nhưng không tự xác thực caller. Role backend có SQL trực tiếp nên vẫn phải kiểm soát mọi đường ghi/đọc khác. View không phải RLS. Không dùng DB owner cho kết nối phục vụ người dùng.

Giữ nguyên ERD nên chưa thêm lời mời nhóm, mô tả nhóm, avatar, settings, read receipt/unread count, token reset mật khẩu, ghi chú/cảnh cáo chi tiết. Frontend có một số khái niệm này: cần migration bổ sung khi chốt thiết kế. REVIEWED không tự đồng nghĩa warn/lock; backend có thể review và lock cùng transaction, nhưng lưu quyết định/ghi chú cần mở rộng ERD.

Khóa chỉ lưu public key, phiên bản và wrapped key. Không tạo/giải mã khóa, không tự rotate khi thành viên/thiết bị thay đổi, không xác minh thuật toán/độ dài nonce. Giao thức E2EE, thu hồi/cấp khóa, quyền lịch sử khi tham gia lại và bằng chứng báo cáo bản mã cần thiết kế ở client/server. Tìm nội dung E2EE thực hiện phía client sau giải mã.

## 7. Chạy lại kiểm thử

Tạo database riêng `relay_chat_test`, cài schema, rồi:

```bash
psql -X -h localhost -U postgres -d relay_chat_test -v ON_ERROR_STOP=1 -f Database/scripts/tests/01_smoke_test.sql
```

Smoke test rollback các dòng/temp object, sequence có thể tăng. Trong pgAdmin bỏ dòng `\set` trước khi chạy toàn bộ file.

Test đồng thời tùy chọn cần Node >=18 và package pg, chỉ là dependency kiểm thử:

```bash
cd Database/scripts/tests
npm install
```

Đặt PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE rồi `npm test`. Ví dụ PowerShell:

```powershell
$env:PGHOST='localhost'
$env:PGPORT='5432'
$env:PGUSER='postgres'
$env:PGDATABASE='relay_chat_test'
# Đặt PGPASSWORD theo database cục bộ của bạn.
npm test
```

Test yêu cầu hậu tố database `_test` hoặc `_validation`. Fixtures được commit và giữ lại để kiểm tra: không chạy trên database dùng thật. Không cần Node để cài hoặc sử dụng database.

## 8. Nguồn kỹ thuật

- [PostgreSQL 16 - Constraints](https://www.postgresql.org/docs/16/ddl-constraints.html).
- [PostgreSQL 16 - CREATE TRIGGER](https://www.postgresql.org/docs/16/sql-createtrigger.html).
- [PostgreSQL 16 - Explicit/advisory locking](https://www.postgresql.org/docs/16/explicit-locking.html).
