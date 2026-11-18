# RELAY — Version 1 UI implementation report

Basis: `RA_SOAT_GIAO_DIEN_PHIEN_BAN_1.md` (18 November 2026). The original `Final Project.pdf` cited in that document was not included; this implementation follows the review checklist itself.

## Delivered source changes

- **UI-01:** Style JavaFX menu context popups and menu items explicitly in `admin-client/src/main/resources/css/dark.css` for normal, hover and selected states.
- **UI-02:** Set UTF-8 Java source compilation in root `build.gradle` and use a normal middle-dot separator in Admin Dashboard. This addresses the probable encoding issue; runtime verification on Windows is still needed.
- **Admin 1/1.b/1.d/1.f:** Personal details in the `UserDetails` view; add/edit/delete (soft delete) user and password dialog mock operations; friends tab showing the target account's relationships.
- **Admin 2:** New `Statistics → System logins` tab, listing all logins across the system with time, username and full name.
- **Admin 4.a/4.b:** Moderation sort by username and from/to date controls with apply and clear actions.
- **Admin 5/5.a/5.b:** New registrations tab with date range, name filter and name/creation sort.
- **Admin 6/9:** Year selector and two monthly JavaFX bar charts using registration and APP_OPEN mock events.
- **Admin 7/7.a/7.b/7.c:** Direct and two-hop friends table, with name, date, comparison and count filters.
- **Admin 8/8.a/8.b/8.c:** Per-user activity table (app opens, distinct direct chats and group chats), with date range, name, sort and numeric comparison filters.
- **User 2:** Editable address, date of birth and gender alongside existing profile information.
- **User 3:** Added a mock-only confirmation-state design preview on Forgot Password without claiming an email was sent. The existing OTP flow is preserved. Actual random password generation and email delivery remain unimplemented by design.
- **User 6.a/6.b/6.c/7.a/8/9:** Dedicated mock online/offline indicator, online-only filter, searchable people results excluding blocks in both directions, unfriend confirmation, friend-request name filtering, and chat/group actions from search results.
- **User 12.a:** Group form includes multi-selection of initial members, member search and selected usernames. Requires at least one selected account and issues invitations in the existing mock service.
- **Database design:** `database/schema_v1.sql` documents a proposed PostgreSQL relational schema. It is **not connected to the Java clients**.

## Scope and verification limitations

- This remains a **JavaFX frontend + in-memory mock-service demo**. Data mutations are not persisted across restarts. Creating a group sends invitations; it does not automatically make other accounts group members.
- An account delete is a soft delete using `UserStatus.DELETED`, not removal of historical data.
- Online presence is seeded demo state, not a real presence service.
- Email delivery, password-reset email generation and backend database integration require a separate implementation phase.
- The Gradle wrapper requires downloading Gradle 8.14.6; in this environment its download failed (`UnknownHostException: services.gradle.org`). Build and JavaFX rendering tests could not be executed here. XML and Java source parsing were used instead (see validation summary).
- For accurate UI validation on a Windows machine, run `gradlew.bat test`, `gradlew.bat :admin-client:run`, and `gradlew.bat :user-client:run` with network access and a JDK configured.

## Static validation conducted in this workspace

- Java compiler parser: **141 Java translation units parsed; 0 syntax errors** (parse-only, not a full type-checked build).
- XML parser: **36 FXML documents parsed; 0 XML syntax errors**.
- Included Gradle/JUnit suite: **not executed** because Gradle distribution download was inaccessible in this environment.
- Runtime UX/layout: **requires visual smoke test** in JavaFX with the documented fixed window sizes.
