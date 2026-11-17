# Kết quả kiểm tra

Ngày thực hiện: 17/11/2026. PostgreSQL **18.6 native**, node-postgres **8.23.1**. Mục tiêu tương thích PostgreSQL 16+; chưa chạy riêng server 16/17.

| Kiểm tra | Kết quả |
| --- | --- |
| 8 migration trong một transaction | Thành công, đúng 14 base table |
| Smoke test | **58/58**, rollback dòng dữ liệu test |
| Tạo DIRECT đồng thời qua hàm | Cùng trả một ID |
| Tạo DIRECT đồng thời bằng SQL trực tiếp | Chặn bản trùng |
| Gửi đồng thời | Sequence 1,2; đúng 2 log |
| Xóa lịch sử đồng thời với gửi | Tin sau mốc xóa vẫn thấy |
| Hai admin đồng thời hạ quyền | Luôn giữ được admin cuối |
| Block đồng thời với gửi | Lệnh gửi chờ rồi bị từ chối |
| Khóa tài khoản đồng thời với gửi | Lệnh gửi nhìn thấy LOCKED vừa commit |
| Revoke thiết bị đồng thời với cấp khóa | Chặn envelope mới |
| Ghi ở REPEATABLE READ | Từ chối bằng SQLSTATE 25000 |
| Seed demo | Thành công; hash bcrypt khớp DemoPass123! |
| 10 truy vấn đọc/thống kê mẫu | Chạy thành công sau seed |
| Bản cài và seed pgAdmin | Chạy thành công trên database trống riêng |
| Chạy lại migration khởi tạo | Báo schema đã tồn tại, giữ nguyên dữ liệu |

**9/9 kịch bản cạnh tranh** thành công. Các kịch bản khóa dùng hai kết nối thật và xác nhận transaction thứ hai chờ qua pg_stat_activity trước khi commit transaction thứ nhất.

Phạm vi là tích hợp database, chưa benchmark tải lớn, kiểm định mật mã hoặc tích hợp JavaFX/JDBC. Hướng dẫn chạy lại ở README.
