# Relay — JavaFX Scene Builder UI Preview Kit

A **static, controller-free** UI prototype for a chat platform with separate administrator and end-user interfaces. Every `.fxml` file can be opened on its own in **Gluon Scene Builder** without Maven, Gradle, or compiling a Java application. The brand name **Relay** and all sample names, messages, counts, and dates are placeholders.

## Quick start

1. **Extract the entire ZIP**, preserving the `views/` and `styles/` directory structure.
2. Install/open **Gluon Scene Builder**: <https://gluonhq.com/products/scene-builder/>.
3. Select **File → Open** and choose any `.fxml` file inside `views/admin/` or `views/user/`.
4. In Scene Builder, use **Preview → Show Preview in Window** to see the layout in a separate window, or inspect the editor canvas.
5. Edit layouts in Scene Builder and visual properties in `styles/dark.css`. Keep the original folder structure so the stylesheet resolves correctly.

If you see an unstyled/light interface, check that the FXML file's `stylesheets` attribute points to `@../../styles/dark.css` and that you extracted the `styles` folder with the FXML files.

**No functionality is implemented.** Buttons, search bars, filters, sorting, moderation decisions, and navigation are static visual placeholders. Each `.fxml` file is independently previewable; clicking between screens is not wired up. No `fx:controller`, custom controls, external icon fonts, image assets, or third-party libraries are required.

## Admin screens (7)

| FXML | Purpose |
| --- | --- |
| `views/admin/01-dashboard.fxml` | Dashboard, KPI statistics, activity chart, platform health, activity feed |
| `views/admin/02-users.fxml` | User directory, search, login-history sorting control, statuses |
| `views/admin/03-user-details.fxml` | User identity, sign-in history, recent activity |
| `views/admin/04-groups.fxml` | Group directory, owners, member counts, safety status |
| `views/admin/05-group-details.fxml` | Member list, group details, group activity |
| `views/admin/06-moderation.fxml` | Spam/warnings queue and moderation workflow |
| `views/admin/07-warning-detail.fxml` | Warning evidence, review notes, moderation action previews |

## User screens (21)

| FXML | Purpose |
| --- | --- |
| `views/user/01-login.fxml` | Login |
| `views/user/02-register.fxml` | Registration |
| `views/user/03-forgot-password.fxml` | Password recovery — request code |
| `views/user/04-verification-code.fxml` | Password recovery — verification code |
| `views/user/05-reset-password.fxml` | Password recovery — choose new password |
| `views/user/06-chat-home.fxml` | Conversation list, search, unread/group filters, empty state |
| `views/user/07-chat-private.fxml` | Private conversation, message composer, inline AI trigger |
| `views/user/08-chat-group.fxml` | Group conversation, participant sidebar, AI trigger |
| `views/user/09-message-search.fxml` | Message search results and filters |
| `views/user/10-friends.fxml` | Friends directory and search |
| `views/user/11-friend-requests.fxml` | Incoming and sent requests |
| `views/user/12-blocked-users.fxml` | Blocked accounts list |
| `views/user/13-group-management.fxml` | User groups and management tools |
| `views/user/14-group-details.fxml` | Group information and editable member list/removal placeholders |
| `views/user/15-group-invitations.fxml` | Pending invitations |
| `views/user/16-profile.fxml` | Personal profile and editable fields |
| `views/user/17-settings.fxml` | Appearance, notifications, privacy, account settings |
| `views/user/18-change-password.fxml` | Change password |
| `views/user/19-spam-reports.fxml` | Report history and report status |
| `views/user/20-report-detail.fxml` | Report detail and progress |
| `views/user/21-ai-assistant-popup.fxml` | Contextual AI assistant next to a selected chat message |

## Visual design

- Background: `#0B1119`
- Sidebar: `#111B27`
- Card surfaces: `#15212E` and `#172433`
- Accent: `#758BFF`
- Main text: `#E9EFF7`
- Secondary text: `#8DA0B5`
- Layout uses standard JavaFX `BorderPane`, `HBox`, `VBox`, `StackPane`, `ScrollPane`, and controls.

## Using these screens in your JavaFX application later

Copy `views/` and `styles/` into your JavaFX resources directory, retaining their relative paths. Then connect views to controllers and services, replace sample content with real models, implement navigation, and handle networking on background threads (updates to controls must run on the JavaFX Application Thread).

The FXML files represent visual layouts rather than production-ready app behavior. The fake chart is a set of styled JavaFX regions, and tables are static row layouts, so they will need data-bound controls or generated row components later.
