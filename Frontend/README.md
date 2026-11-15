# RELAY interactive JavaFX frontends

This Gradle multi-project contains the Java 17 / JavaFX 17.0.2 admin and user applications. Both applications use a fresh in-memory fixture store on every launch; no network, database, durable authentication, persistence, or cryptography is included.

## Run

From `Frontend` with JDK 17 selected:

```powershell
.\gradlew.bat :user-client:run
.\gradlew.bat :admin-client:run
.\gradlew.bat clean test
```

User demo credentials:

- Email: `alex.morgan@example.com`
- Password: `DemoPass123!`
- Password-reset code: `123456`

The login window is fixed at 1240×780. Successful login opens the fixed 1540×930 user shell. The admin application opens a fixed 1480×920 shell and intentionally has no authentication workflow in this frontend phase.

## Structure and backend replacement

- `shared-client` owns observable domain state, operation-focused service interfaces, deterministic fixtures, and in-memory service implementations.
- `user-client` and `admin-client` own their FXML, CSS, navigation, controllers, and application contexts.
- Controllers depend on service interfaces rather than the fixture store. A future backend adapter can implement those interfaces while preserving controller/FXML behavior; thread marshalling and asynchronous error/loading states should be added with that adapter.

All defined functionality is interactive. Undefined controls remain visible but disabled with a specific tooltip on a hoverable wrapper. Approved missing workflow inputs use only the minimal dialogs documented in [`INTERACTIVE_UI_DEMO.md`](../docs/INTERACTIVE_UI_DEMO.md).

## Known limitations

- State, remembered email, registrations, password changes, messages, and moderation changes reset when the application restarts.
- Attachments, calls, public-profile preview, avatar selection, theme switching, help/support, and admin settings are intentionally unavailable because their workflows are not defined.
- Admin moderation search accepts a numeric case ID such as `501`, not the displayed `WR-501` prefix.
- If an entity is removed while its detail route is open, some admin detail views show an unavailable state without a dedicated recovery action.
- OpenJFX can print harmless cache warnings under `C:\.openjfx` on this Windows environment.
