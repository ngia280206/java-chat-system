# Interactive UI acceptance demo

Run each application from a new process so it starts from deterministic fixtures. The automated controller/service suites execute the same mutations with fresh application contexts; the steps below are the graphical acceptance route.

## User application

Start with `Frontend\gradlew.bat :user-client:run`.

1. Confirm the fixed 1240×780 login window and dark styling. Submit empty and invalid credentials and confirm field/global feedback. Open and cancel registration; then register a unique email, confirm the app returns to Login, and sign in with the new account. Restart before continuing.
2. From login, request a reset for `alex.morgan@example.com`, reject a wrong code, accept `123456`, reject an invalid/mismatched password, and complete the reset. Restart to restore the documented password.
3. Log in as `alex.morgan@example.com` / `DemoPass123!`. Confirm the fixed 1540×930 shell and navigate every sidebar destination without a dead actionable control.
4. Chats: open private and group conversations; send a message; search and open a result; open Ask AI; hide only the selected message after confirmation; cancel once; clear history after confirmation and verify a later message remains visible. Open and cancel the minimal New conversation dialog.
5. Friends: search/sort, open Requests and Blocked, accept/decline/cancel a request, open and cancel Add friend, send a valid request, block a selected non-self user with confirmation, and unblock that account. Verify relevant lists update together.
6. Groups: create a group with validation, open it, edit details, invite a member, remove an eligible member, change an eligible member between `ADMIN` and `MEMBER`, verify the final-admin constraint, then leave with confirmation. Exercise invitation accept/decline/cancel and verify the group lists update.
7. Account: update profile and settings, reject an invalid password change, complete a valid change, and log out. Confirm the session returns to the 1240×780 login window.
8. Reports: open New report, validate required input, submit, filter/search history, open the new detail, and confirm its status/progress projection.
9. Hover each disabled category and confirm its specific reason: Help; chat contact details and attachments; image/file search; profile preview/avatar; Light/System theme.

## Admin application

Start a fresh process with `Frontend\gradlew.bat :admin-client:run`.

1. Confirm the fixed 1480×920 Dashboard shell, dark styling, and working Dashboard/Users/Groups/Moderation navigation. Hover disabled Admin settings and Help controls and confirm their specific tooltips.
2. Dashboard: change among the 7/30/365-day presets and confirm metrics/trend/activity projections update. Use View all navigation.
3. Users: search, filter, sort, paginate, and open a detail. Cancel a lock/unlock once, then confirm it and verify list/detail/dashboard status and metrics update.
4. Groups: search, sort, open a detail, inspect members/activity, and use the moderation-context action to reach the relevant queue.
5. Moderation: search by numeric ID, reporter/target/reason; filter and sort; open a case; validate required notes; cancel each consequential action once; then exercise dismiss, warn, and suspend on fresh pending cases. Verify queue, detail, warning progression, locked-user state, and dashboard counts update consistently.

## Acceptance evidence (2026-11-15)

- Source audit: 7 admin + 21 user source FXML files retained.
- Runtime audit: 7 admin + 20 user regular screens, the supplied AI popup, 5 approved minimal dialogs, and 2 application shells are classpath resources.
- All 35 runtime FXML controller declarations resolve; no runtime FXML references `docs` or `../../styles`.
- Final suite: 153 tests, 0 failures, 0 errors across all modules.
- Stage/FXML tests verify fixed dimensions, CSS resolution, all screens/dialogs, navigation, disabled wrapper tooltips, validation, confirmations/cancellation, permissions, and cross-screen observable-state updates.

Known non-blocking limitations are listed in [`Frontend/README.md`](../Frontend/README.md).
