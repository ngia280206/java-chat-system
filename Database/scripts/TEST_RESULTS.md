# Kết quả xác minh bộ SQL đồng bộ

Môi trường: PostgreSQL **18.6 native**, node-postgres **8.23.1**. Kiểm tra bằng các kết nối PostgreSQL thật.

| Trường hợp | Kết quả |
| --- | --- |
| Cài mới vào database trống + seed | Đủ 18 bảng, thành công |
| SQL regression trên bản cài mới | 58 nền + 65 đồng bộ + 7 vòng đời = **130/130** |
| Nâng từ đúng 14 bảng trong sqlclone.rar | Thành công; giữ user và report lịch sử, không tạo admin/notes giả |
| SQL regression sau nâng từ 14 bảng | **130/130** |
| Nâng từ 18 bảng chỉ có DDL | Thành công; giữ cài đặt, bổ sung membership còn thiếu |
| Thành viên rời nhóm sau khi đã chấp nhận | Không tự thêm lại |
| SUSPEND đã ghi nhưng report còn PENDING | Áp dụng khóa và cập nhật report |
| Yêu cầu reset cũ hết hạn | Được thu hồi |
| SQL regression sau nâng từ 18 bảng | **130/130** |
| Chạy lại migration trên dữ liệu phù hợp | Thành công, không reset database |
| User đã được mở khóa sau một SUSPEND cũ | Chạy lại migration không khóa lại user |
| Dữ liệu cũ có lời mời trỏ vào DIRECT | Báo lỗi, rollback cả hàm/trigger/backfill |
| Cấu trúc bảng cũ không phù hợp | Báo lỗi cột cụ thể, rollback toàn bộ |
| Role relay_backend | Đọc được bảng mới và gọi được API sau cập nhật |
| CHECK_AFTER_INSTALL.sql | Chạy thành công trên cả ba nhánh cài/cập nhật |

Kịch bản khóa/đồng thời: **9 nền + 5 đồng bộ = 14/14**. Bao gồm chống trùng DIRECT, thứ tự tin nhắn, xóa lịch sử, admin cuối nhóm, block/lock, cấp khóa thiết bị, từ chối isolation không hỗ trợ, lời mời trùng, chấp nhận hai lần, reset song song, dùng token hai lần và hai admin xử lý cùng một report.

Phạm vi là database. Chưa kiểm tra tích hợp frontend JavaFX/JDBC, gửi email, benchmark tải hoặc kiểm định mật mã. Chưa chạy riêng PostgreSQL 16/17.
