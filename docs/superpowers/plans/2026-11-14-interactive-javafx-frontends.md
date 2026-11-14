# Interactive JavaFX Frontends Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build fully interactive admin and user JavaFX demo applications whose defined workflows operate on coherent observable in-memory state across all 28 supplied screens.

**Architecture:** Focused service interfaces expose meaningful use-case operations and read-only observable state; concrete in-memory implementations share one process-local data store built from stable fixtures. Thin JavaFX controllers receive explicit dependencies through application composition/controller factories, persistent shells own navigation, and only approved minimal dialogs or initiating controls extend the supplied FXML.

**Tech Stack:** Java 17, JavaFX 17.0.2 (`javafx.controls`, `javafx.fxml`), Gradle multi-project build, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-11-14-interactive-javafx-frontends-design.md`

## Global Constraints

- Preserve all originals under `docs/UI_design/Materials`; runtime copies live in the appropriate client module.
- Keep separate admin and user applications and independent in-memory sessions.
- Use fixed non-resizable windows: admin 1480x920, user authentication 1240x780, user application 1540x930, AI popup 890x890.
- Preserve the supplied visual language; dynamic rows must remain visually equivalent to the static prototypes.
- Mutate shared state only through focused services; controllers own presentation state only.
- Use JavaFX observable collections/properties where cross-screen updates benefit; do not add a global store, event bus, reducers, repositories, DTO mapping, DI framework, or service locator.
- Do not implement networking, persistence, real authentication, synchronization, cryptography, notifications, or AI.
- Create minimal modals only for approved workflows with missing inputs; add only the four approved initiating controls.
- Every actionable control must appear in the control inventory as implemented or intentionally disabled with a specific tooltip.
- Do not execute or copy architecture from `docs/superpowers/plans/2026-11-14-javafx-client-ui-migration.md`; it is superseded.

## Review Focus

- Cross-screen state consistency: a mutation in one workflow must immediately appear in every relevant observable projection without controller-to-controller calls.
- Permission and invariant enforcement: self-blocking, invalid group-role changes, ownerless groups, and actions on stale/missing entities must be rejected with specific validation results.
- Per-user visibility: hidden messages and cleared history must affect only the current user and never delete global message data.
- Disabled-control completeness: every undefined control must remain visually present, be non-actionable, and expose its specific tooltip through a hoverable wrapper when needed.
- Navigation/session boundaries: logout must clear authenticated UI state, detail routes must use entity IDs, and independent admin/user process contexts must not share mutable state.

---

### Task 1: Baseline test infrastructure and actionable-control inventory

**Files:**
- Modify: `Frontend/shared-client/build.gradle`
- Modify: `Frontend/user-client/build.gradle`
- Modify: `Frontend/admin-client/build.gradle`
- Create: `Frontend/shared-client/src/test/java/com/chatsystem/shared/ArchitectureSmokeTest.java`
- Create: `docs/UI_CONTROL_INVENTORY.md`

**Interfaces:**
- Consumes: 28 source FXML files and the approved design.
- Produces: JUnit 5 configuration for all modules; an inventory row format of `App | Screen | Control | Behavior | Status | Reason | Verification`; a complete initial classification of every actionable control.

- [ ] **Step 1: Write the failing baseline test**

`ArchitectureSmokeTest` asserts Java 17, the presence of 7 admin plus 21 user source FXML files, and the existence of the approved design and control inventory.

- [ ] **Step 2: Run the baseline test and verify it fails because the inventory/test configuration is incomplete**

Run: `Frontend\gradlew.bat :shared-client:test --tests "com.chatsystem.shared.ArchitectureSmokeTest"`

- [ ] **Step 3: Configure JUnit 5 consistently in all modules**

Add `testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'` and `useJUnitPlatform()` without adding TestFX.

- [ ] **Step 4: Build the initial control inventory from every Button, form control, selectable row, and approved added control**

Classify each as implemented workflow, disabled with exact reason/tooltip, decorative, or pending a named later task. Include New Conversation, Add Friend, Create Group, Invite Members, New Report, Block User, Hide Message, Clear History, Leave Group, and Change Group Role.

- [ ] **Step 5: Run the baseline test**

Run: `Frontend\gradlew.bat :shared-client:test --tests "com.chatsystem.shared.ArchitectureSmokeTest"`
Expected: PASS.

- [ ] **Step 6: Commit the verified baseline**

```powershell
git add Frontend/*/build.gradle Frontend/shared-client/src/test docs/UI_CONTROL_INVENTORY.md
git commit -m "test: establish interactive frontend baseline"
```

### Task 2: Observable domain models, validation results, and stable fixtures

**Files:**
- Create: `Frontend/shared-client/src/main/java/com/chatsystem/shared/model/{UserAccount,UserSettings,FriendRequest,Conversation,ConversationMember,Message,GroupInvitation,SpamReport,LoginEvent,ActivityEvent}.java`
- Create: `Frontend/shared-client/src/main/java/com/chatsystem/shared/model/status/{UserStatus,FriendRequestStatus,ConversationType,GroupRole,ReportStatus,ActivityType}.java`
- Create: `Frontend/shared-client/src/main/java/com/chatsystem/shared/validation/{ValidationError,ValidationResult}.java`
- Create: `Frontend/shared-client/src/main/java/com/chatsystem/shared/mock/{MockDataStore,MockFixtures}.java`
- Test: `Frontend/shared-client/src/test/java/com/chatsystem/shared/mock/MockFixturesTest.java`
- Test: `Frontend/shared-client/src/test/java/com/chatsystem/shared/model/ObservableModelTest.java`

**Interfaces:**
- Consumes: ERD/data-dictionary vocabulary and stable demo requirements.
- Produces: UI-focused observable models with immutable IDs; `ValidationResult<T>` with success value or field/global errors; `MockFixtures.createStore()` returning a new independent `MockDataStore` each call.

- [ ] **Step 1: Write failing fixture and observable-model tests**

Assert fixtures contain linked active/locked users, direct/group conversations, messages, pending/processed requests, friendships/blocks, memberships/invitations, reports, login/activity events, and stable demo IDs; assert mutable displayed fields notify JavaFX property listeners.

- [ ] **Step 2: Run the model tests and verify they fail because models do not exist**

Run: `Frontend\gradlew.bat :shared-client:test --tests "com.chatsystem.shared.model.*" --tests "com.chatsystem.shared.mock.MockFixturesTest"`

- [ ] **Step 3: Implement only the fields required by the approved screens and workflows**

Use immutable scalar IDs and JavaFX properties for displayed mutable fields. Keep `MockDataStore` package-focused and expose mutation through later services, not publicly mutable controller access.

- [ ] **Step 4: Implement realistic independent fixtures**

Include documented mock credentials and reset code in fixture constants, enough records for pagination/filter empty states, and relationally coherent IDs.

- [ ] **Step 5: Run model/fixture tests**

Run: `Frontend\gradlew.bat :shared-client:test --tests "com.chatsystem.shared.model.*" --tests "com.chatsystem.shared.mock.MockFixturesTest"`
Expected: PASS.

- [ ] **Step 6: Commit domain foundations**

```powershell
git add Frontend/shared-client/src
git commit -m "feat: add observable mock domain state"
```

### Task 3: Authentication, profile, and settings services

**Files:**
- Create: `Frontend/shared-client/src/main/java/com/chatsystem/shared/service/{AuthService,ProfileService,SettingsService}.java`
- Create: `Frontend/shared-client/src/main/java/com/chatsystem/shared/mock/service/{InMemoryAuthService,InMemoryProfileService,InMemorySettingsService}.java`
- Create: `Frontend/shared-client/src/main/java/com/chatsystem/shared/session/UserSession.java`
- Test: `Frontend/shared-client/src/test/java/com/chatsystem/shared/mock/service/{InMemoryAuthServiceTest,InMemoryProfileServiceTest,InMemorySettingsServiceTest}.java`

**Interfaces:**
- Consumes: models, validation, and `MockDataStore` from Task 2.
- Produces: meaningful operations `login`, `register`, `beginPasswordReset`, `verifyResetCode`, `resetPassword`, `changePassword`, `logout`, `updateProfile`, and `updateSettings`; read-only current-user/authentication properties through `UserSession`.

- [ ] **Step 1: Write failing service tests for success, failure, and no-mutation cases**

Cover invalid credentials, locked account, registration validation/duplicate email, fixed-code reset sequence, current-password validation, profile propagation, settings session persistence, remember-email session behavior, cancellation/no call, and logout session clearing.

- [ ] **Step 2: Run the focused service tests and verify failure**

Run: `Frontend\gradlew.bat :shared-client:test --tests "com.chatsystem.shared.mock.service.InMemoryAuthServiceTest" --tests "com.chatsystem.shared.mock.service.InMemoryProfileServiceTest" --tests "com.chatsystem.shared.mock.service.InMemorySettingsServiceTest"`

- [ ] **Step 3: Define focused contracts and implement in-memory behavior**

Expose typed validation results and read-only observable session properties. Keep password handling explicitly mock-only and in memory.

- [ ] **Step 4: Run the focused service tests**

Expected command from Step 2: PASS.

- [ ] **Step 5: Commit account services**

```powershell
git add Frontend/shared-client/src
git commit -m "feat: add mock account workflows"
```

### Task 4: Social and group services with permission rules

**Files:**
- Create: `Frontend/shared-client/src/main/java/com/chatsystem/shared/service/{SocialService,GroupService}.java`
- Create: `Frontend/shared-client/src/main/java/com/chatsystem/shared/mock/service/{InMemorySocialService,InMemoryGroupService}.java`
- Test: `Frontend/shared-client/src/test/java/com/chatsystem/shared/mock/service/{InMemorySocialServiceTest,InMemoryGroupServiceTest}.java`

**Interfaces:**
- Consumes: `MockDataStore`, observable models, `UserSession`.
- Produces: observable friends/requests/blocks/groups/invitations/member projections and operations `sendRequest`, `acceptRequest`, `rejectRequest`, `cancelRequest`, `blockUser`, `unblockUser`, `createGroup`, `updateGroup`, `inviteMember`, `acceptInvitation`, `rejectInvitation`, `removeMember`, `leaveGroup`, and `changeRole`.

- [ ] **Step 1: Write failing social consistency tests**

Assert accepting a request removes it and adds a friend; cancel/reject transitions are correct; blocking is directional, rejects self-block, removes conflicting friendship/pending requests, and appears in blocked users; unblock restores only block state.

- [ ] **Step 2: Write failing group workflow and permission tests**

Assert create/invite/join/reject/update/remove/leave changes every observable projection; only admins change roles/remove members; roles are `ADMIN`/`MEMBER`; no action may leave a group with no valid admin; invalid/stale IDs fail without mutation.

- [ ] **Step 3: Run the tests and verify they fail because services are missing**

Run: `Frontend\gradlew.bat :shared-client:test --tests "com.chatsystem.shared.mock.service.InMemorySocialServiceTest" --tests "com.chatsystem.shared.mock.service.InMemoryGroupServiceTest"`

- [ ] **Step 4: Implement focused contracts and in-memory services**

Return read-only observable projections and enforce all invariants in services, not controllers.

- [ ] **Step 5: Run social/group tests**

Expected command from Step 3: PASS.

- [ ] **Step 6: Commit social/group workflows**

```powershell
git add Frontend/shared-client/src
git commit -m "feat: add mock social and group workflows"
```

### Task 5: Chat, reports, moderation, metrics, and simulated assistant services

**Files:**
- Create: `Frontend/shared-client/src/main/java/com/chatsystem/shared/service/{ChatService,ReportService,AdminService,AssistantService}.java`
- Create: `Frontend/shared-client/src/main/java/com/chatsystem/shared/mock/service/{InMemoryChatService,InMemoryReportService,InMemoryAdminService,PredefinedAssistantService}.java`
- Test: `Frontend/shared-client/src/test/java/com/chatsystem/shared/mock/service/{InMemoryChatServiceTest,InMemoryReportServiceTest,InMemoryAdminServiceTest,PredefinedAssistantServiceTest}.java`

**Interfaces:**
- Consumes: Task 2 store/models and Task 3 session.
- Produces: conversation/message selection and queries; `startConversation`, `sendMessage`, `searchMessages`, `hideMessage`, `clearHistory`; report submission/review/dismiss/warn/suspend; user lock/unlock; user/group/report filters/sorts; derived dashboard metrics; contextual predefined assistant replies.

- [ ] **Step 1: Write failing chat state/visibility tests**

Assert sending updates messages, preview, timestamp, ordering, and unread projection; search respects per-user hidden/cleared state; hide affects one user only; clear uses the current sequence boundary without global deletion; starting direct chat reuses an existing pair.

- [ ] **Step 2: Write failing report/admin/assistant tests**

Assert report submission references visible message/user, review/dismiss update queue independently of sanctions, warn records decision and reviews, suspend reviews plus locks, lock/unlock updates metrics, filters/sorts/pagination are deterministic, and assistant responses are predefined/contextual.

- [ ] **Step 3: Run focused tests and verify failure**

Run: `Frontend\gradlew.bat :shared-client:test --tests "com.chatsystem.shared.mock.service.InMemoryChatServiceTest" --tests "com.chatsystem.shared.mock.service.InMemoryReportServiceTest" --tests "com.chatsystem.shared.mock.service.InMemoryAdminServiceTest" --tests "com.chatsystem.shared.mock.service.PredefinedAssistantServiceTest"`

- [ ] **Step 4: Implement the four focused service contracts and mock implementations**

Keep filter/sort criteria as small named value types only where multiple controllers/tests use them; do not add repositories or transport DTOs.

- [ ] **Step 5: Run focused and complete shared-client tests**

Run: `Frontend\gradlew.bat :shared-client:test`
Expected: PASS.

- [ ] **Step 6: Commit conversation/admin services**

```powershell
git add Frontend/shared-client/src
git commit -m "feat: add mock chat and moderation workflows"
```

### Task 6: User application composition, navigation, shell, resources, and FXML test harness

**Files:**
- Create: `Frontend/user-client/src/main/java/com/chatsystem/user/{UserMain,UserApplicationContext}.java`
- Create: `Frontend/user-client/src/main/java/com/chatsystem/user/navigation/{UserRoute,UserNavigator,ViewRequest}.java`
- Create: `Frontend/user-client/src/main/java/com/chatsystem/user/controller/UserShellController.java`
- Create: `Frontend/user-client/src/main/java/com/chatsystem/user/ui/{FxmlViewLoader,DisabledControlSupport}.java`
- Create: `Frontend/user-client/src/main/resources/fxml/shell/user-shell.fxml`
- Copy: `docs/UI_design/Materials/styles/dark.css` to `Frontend/user-client/src/main/resources/css/dark.css`
- Create: `Frontend/user-client/src/main/resources/images/.gitkeep`
- Test: `Frontend/user-client/src/test/java/com/chatsystem/user/{JavaFxTestSupport,UserApplicationContextTest}.java`
- Test: `Frontend/user-client/src/test/java/com/chatsystem/user/navigation/UserNavigatorTest.java`
- Test: `Frontend/user-client/src/test/java/com/chatsystem/user/ui/DisabledControlSupportTest.java`

**Interfaces:**
- Consumes: shared service interfaces and in-memory implementations from Tasks 2-5.
- Produces: one explicit `UserApplicationContext`; typed routes with optional entity IDs; fixed window profiles; controller factory with explicit constructor mapping; wrapper-based disabled tooltip support; persistent user shell.

- [ ] **Step 1: Write failing composition/navigation/window/disabled-tooltip tests**

Assert fresh contexts are independent, routes require valid IDs where applicable, Login is startup, successful transition can create the shell at 1540x930, fixed/resizable flags are exact, missing resources name their path, and disabled controls expose their reason through the wrapper.

- [ ] **Step 2: Run user infrastructure tests and verify failure**

Run: `Frontend\gradlew.bat :user-client:test --tests "com.chatsystem.user.*"`

- [ ] **Step 3: Implement explicit composition and navigation infrastructure**

Instantiate one store and services per context; map controller types to explicit constructors; do not provide generic type lookup.

- [ ] **Step 4: Extract the visually equivalent user shell and classpath CSS loading**

The shell owns the sidebar/current user summary/content host; no sidebar button is left unwired—defined routes work and undefined Help is disabled with inventory tooltip.

- [ ] **Step 5: Run user infrastructure tests**

Expected command from Step 2: PASS.

- [ ] **Step 6: Commit user infrastructure**

```powershell
git add Frontend/user-client
git commit -m "feat: add user application infrastructure"
```

### Task 7: User authentication screens and account workflow controllers

**Files:**
- Create: `Frontend/user-client/src/main/resources/fxml/auth/{login,register,forgot-password,verification-code,reset-password}.fxml`
- Create: `Frontend/user-client/src/main/java/com/chatsystem/user/controller/auth/{Login,Register,ForgotPassword,VerificationCode,ResetPassword}Controller.java`
- Test: `Frontend/user-client/src/test/java/com/chatsystem/user/controller/auth/AuthWorkflowControllerTest.java`
- Extend: `Frontend/user-client/src/test/java/com/chatsystem/user/FxmlSmokeTest.java`

**Interfaces:**
- Consumes: `AuthService`, `UserSession`, `UserNavigator`.
- Produces: validated five-screen authentication/reset workflow and transition to the user shell.

- [ ] **Step 1: Write failing controller tests for navigation, validation, success, and failure feedback**

Cover empty/invalid email, password requirements, terms, incorrect credentials, locked user, registration success, reset-code sequence, cancellation/back links, remember-email, and logout-compatible session state.

- [ ] **Step 2: Run controller tests and verify failure**

Run: `Frontend\gradlew.bat :user-client:test --tests "com.chatsystem.user.controller.auth.AuthWorkflowControllerTest"`

- [ ] **Step 3: Migrate five FXML designs, add only required `fx:id`/handlers, and implement thin controllers**

Remove docs-relative CSS paths; preserve layouts and show specific field/global feedback without generic click messages.

- [ ] **Step 4: Add all five views to the FXML smoke test and run tests**

Run: `Frontend\gradlew.bat :user-client:test --tests "com.chatsystem.user.controller.auth.*" --tests "com.chatsystem.user.FxmlSmokeTest"`
Expected: PASS.

- [ ] **Step 5: Update inventory entries and commit authentication UI**

```powershell
git add Frontend/user-client docs/UI_CONTROL_INVENTORY.md
git commit -m "feat: implement user authentication demo"
```

### Task 8: User chat, message search, and AI workflows

**Files:**
- Create: `Frontend/user-client/src/main/resources/fxml/views/{chat-home,chat-private,chat-group,message-search}.fxml`
- Create: `Frontend/user-client/src/main/resources/fxml/dialogs/{new-conversation,ai-assistant-popup}.fxml`
- Create: `Frontend/user-client/src/main/java/com/chatsystem/user/controller/chat/{ChatHome,ChatPrivate,ChatGroup,MessageSearch,NewConversationDialog,AiAssistantPopup}Controller.java`
- Create: `Frontend/user-client/src/main/java/com/chatsystem/user/ui/cell/{ConversationCell,MessageBubble}.java`
- Test: `Frontend/user-client/src/test/java/com/chatsystem/user/controller/chat/ChatWorkflowControllerTest.java`
- Extend: `Frontend/user-client/src/test/java/com/chatsystem/user/FxmlSmokeTest.java`

**Interfaces:**
- Consumes: `ChatService`, `AssistantService`, `ReportService`, `UserNavigator`.
- Produces: dynamic conversation/message rendering; send/search/select/new conversation; approved Hide Message and Clear History controls; AI popup; disabled attachment behavior.

- [ ] **Step 1: Write failing controller tests for selection, sending, search, hide/clear, dialog validation, AI, and cancel/no-mutation**

Assert views react to observable service changes and context-menu/action visibility matches current message/conversation state.

- [ ] **Step 2: Run chat controller tests and verify failure**

Run: `Frontend\gradlew.bat :user-client:test --tests "com.chatsystem.user.controller.chat.ChatWorkflowControllerTest"`

- [ ] **Step 3: Migrate four screens and replace static rows with visually equivalent dynamic nodes**

Add the approved Hide Message context item and Clear History action naturally; preserve per-user semantics and confirmation.

- [ ] **Step 4: Implement the two minimal dialogs and thin controllers**

New Conversation collects only a recipient; AI popup accepts a prompt, renders predefined response, and closes. Attachment `+` remains disabled with its inventory explanation.

- [ ] **Step 5: Run chat and FXML tests**

Run: `Frontend\gradlew.bat :user-client:test --tests "com.chatsystem.user.controller.chat.*" --tests "com.chatsystem.user.FxmlSmokeTest"`
Expected: PASS.

- [ ] **Step 6: Update inventory and commit chat UI**

```powershell
git add Frontend/user-client docs/UI_CONTROL_INVENTORY.md
git commit -m "feat: implement interactive chat workflows"
```

### Task 9: User friends and relationship workflows

**Files:**
- Create: `Frontend/user-client/src/main/resources/fxml/views/{friends,friend-requests,blocked-users}.fxml`
- Create: `Frontend/user-client/src/main/resources/fxml/dialogs/add-friend.fxml`
- Create: `Frontend/user-client/src/main/java/com/chatsystem/user/controller/social/{Friends,FriendRequests,BlockedUsers,AddFriendDialog}Controller.java`
- Create: `Frontend/user-client/src/main/java/com/chatsystem/user/ui/cell/{FriendCell,FriendRequestCell,BlockedUserCell}.java`
- Test: `Frontend/user-client/src/test/java/com/chatsystem/user/controller/social/SocialWorkflowControllerTest.java`
- Extend: `Frontend/user-client/src/test/java/com/chatsystem/user/FxmlSmokeTest.java`

**Interfaces:**
- Consumes: `SocialService`, `ChatService`, `UserNavigator`.
- Produces: search/sort/add/message/request actions and approved Block/Unblock interactions with confirmation and observable consistency.

- [ ] **Step 1: Write failing controller tests for search/sort, request transitions, add modal, message navigation, block/unblock visibility, confirmation, and cancellation**

- [ ] **Step 2: Run social controller tests and verify failure**

Run: `Frontend\gradlew.bat :user-client:test --tests "com.chatsystem.user.controller.social.SocialWorkflowControllerTest"`

- [ ] **Step 3: Migrate three screens, implement dynamic cells, and add the minimal Add Friend modal**

Place Block naturally in friend details/actions, only for a valid unblocked target; keep undefined controls disabled per inventory.

- [ ] **Step 4: Implement controllers and run social/FXML tests**

Run: `Frontend\gradlew.bat :user-client:test --tests "com.chatsystem.user.controller.social.*" --tests "com.chatsystem.user.FxmlSmokeTest"`
Expected: PASS.

- [ ] **Step 5: Update inventory and commit relationship UI**

```powershell
git add Frontend/user-client docs/UI_CONTROL_INVENTORY.md
git commit -m "feat: implement friend workflows"
```

### Task 10: User group workflows and approved membership controls

**Files:**
- Create: `Frontend/user-client/src/main/resources/fxml/views/{group-management,group-details,group-invitations}.fxml`
- Create: `Frontend/user-client/src/main/resources/fxml/dialogs/{create-group,invite-members}.fxml`
- Create: `Frontend/user-client/src/main/java/com/chatsystem/user/controller/group/{GroupManagement,GroupDetails,GroupInvitations,CreateGroupDialog,InviteMembersDialog}Controller.java`
- Create: `Frontend/user-client/src/main/java/com/chatsystem/user/ui/cell/{GroupCell,GroupMemberCell,GroupInvitationCell}.java`
- Test: `Frontend/user-client/src/test/java/com/chatsystem/user/controller/group/GroupWorkflowControllerTest.java`
- Extend: `Frontend/user-client/src/test/java/com/chatsystem/user/FxmlSmokeTest.java`

**Interfaces:**
- Consumes: `GroupService`, `ChatService`, `UserNavigator`.
- Produces: browse/create/edit/invite/join/reject/remove flows; approved Leave Group action and role selector with permission-aware visibility.

- [ ] **Step 1: Write failing controller tests for full group workflow and control permissions**

Cover minimal modal validation/cancel, group updates across views, join/reject, remove, Leave Group confirmation, role selector visibility, last-admin protection, and invalid/stale member handling.

- [ ] **Step 2: Run group controller tests and verify failure**

Run: `Frontend\gradlew.bat :user-client:test --tests "com.chatsystem.user.controller.group.GroupWorkflowControllerTest"`

- [ ] **Step 3: Migrate three screens and create visually equivalent dynamic member/group/invitation rows**

Add Leave Group to group-detail actions and the compact `ADMIN`/`MEMBER` selector to authorized member rows without redesigning the screen.

- [ ] **Step 4: Implement minimal Create Group/Invite Members dialogs and controllers**

- [ ] **Step 5: Run group and FXML tests**

Run: `Frontend\gradlew.bat :user-client:test --tests "com.chatsystem.user.controller.group.*" --tests "com.chatsystem.user.FxmlSmokeTest"`
Expected: PASS.

- [ ] **Step 6: Update inventory and commit group UI**

```powershell
git add Frontend/user-client docs/UI_CONTROL_INVENTORY.md
git commit -m "feat: implement group workflows"
```

### Task 11: User profile, settings, password, and reporting workflows

**Files:**
- Create: `Frontend/user-client/src/main/resources/fxml/views/{profile,settings,change-password,spam-reports,report-detail}.fxml`
- Create: `Frontend/user-client/src/main/resources/fxml/dialogs/new-report.fxml`
- Create: `Frontend/user-client/src/main/java/com/chatsystem/user/controller/account/{Profile,Settings,ChangePassword}Controller.java`
- Create: `Frontend/user-client/src/main/java/com/chatsystem/user/controller/report/{SpamReports,ReportDetail,NewReportDialog}Controller.java`
- Create: `Frontend/user-client/src/main/java/com/chatsystem/user/ui/cell/ReportCell.java`
- Test: `Frontend/user-client/src/test/java/com/chatsystem/user/controller/account/AccountWorkflowControllerTest.java`
- Test: `Frontend/user-client/src/test/java/com/chatsystem/user/controller/report/UserReportWorkflowControllerTest.java`
- Extend: `Frontend/user-client/src/test/java/com/chatsystem/user/FxmlSmokeTest.java`

**Interfaces:**
- Consumes: `ProfileService`, `SettingsService`, `AuthService`, `ReportService`, `UserNavigator`.
- Produces: live profile/settings/password/logout workflows and report submission/search/filter/detail workflows.

- [ ] **Step 1: Write failing account controller tests**

Cover profile field validation/propagation, preference save/cancel semantics, password validation, blocked-account navigation, logout, and disabled avatar/public-preview controls with exact tooltips.

- [ ] **Step 2: Write failing report controller tests**

Cover minimal target/reason validation, submission/cancel, search/filter, detail navigation, and observable status updates.

- [ ] **Step 3: Run account/report tests and verify failure**

Run: `Frontend\gradlew.bat :user-client:test --tests "com.chatsystem.user.controller.account.*" --tests "com.chatsystem.user.controller.report.*"`

- [ ] **Step 4: Migrate five views, implement the New Report modal, dynamic report rows, and thin controllers**

- [ ] **Step 5: Complete all remaining user inventory classifications and run all user tests**

Run: `Frontend\gradlew.bat :user-client:test`
Expected: all 21 supplied screens and approved dialogs load; tests pass.

- [ ] **Step 6: Commit remaining user workflows**

```powershell
git add Frontend/user-client docs/UI_CONTROL_INVENTORY.md
git commit -m "feat: complete interactive user application"
```

### Task 12: Admin application composition, navigation, shell, and resources

**Files:**
- Create: `Frontend/admin-client/src/main/java/com/chatsystem/admin/{AdminMain,AdminApplicationContext}.java`
- Create: `Frontend/admin-client/src/main/java/com/chatsystem/admin/navigation/{AdminRoute,AdminNavigator,ViewRequest}.java`
- Create: `Frontend/admin-client/src/main/java/com/chatsystem/admin/controller/AdminShellController.java`
- Create: `Frontend/admin-client/src/main/java/com/chatsystem/admin/ui/{FxmlViewLoader,DisabledControlSupport}.java`
- Create: `Frontend/admin-client/src/main/resources/fxml/shell/admin-shell.fxml`
- Copy: `docs/UI_design/Materials/styles/dark.css` to `Frontend/admin-client/src/main/resources/css/dark.css`
- Create: `Frontend/admin-client/src/main/resources/images/.gitkeep`
- Test: `Frontend/admin-client/src/test/java/com/chatsystem/admin/{JavaFxTestSupport,AdminApplicationContextTest,FxmlSmokeTest}.java`
- Test: `Frontend/admin-client/src/test/java/com/chatsystem/admin/navigation/AdminNavigatorTest.java`

**Interfaces:**
- Consumes: shared services/store/fixtures from Tasks 2-5.
- Produces: independent pre-authenticated admin context, typed entity-ID routes, 1480x920 fixed shell, explicit controller factory, and disabled-tooltip behavior.

- [ ] **Step 1: Write failing admin composition/navigation/window/resource tests**

Assert context independence, Dashboard startup, typed detail IDs, exact fixed stage configuration, one shell sidebar, named missing-resource failures, CSS resolution, and disabled undefined navigation (Settings/Help).

- [ ] **Step 2: Run admin infrastructure tests and verify failure**

Run: `Frontend\gradlew.bat :admin-client:test --tests "com.chatsystem.admin.*"`

- [ ] **Step 3: Implement explicit composition, navigation, loader, disabled support, and shell**

- [ ] **Step 4: Run admin infrastructure tests**

Expected command from Step 2: PASS.

- [ ] **Step 5: Commit admin infrastructure**

```powershell
git add Frontend/admin-client
git commit -m "feat: add admin application infrastructure"
```

### Task 13: Admin dashboard, users, and groups workflows

**Files:**
- Create: `Frontend/admin-client/src/main/resources/fxml/views/{dashboard,users,user-details,groups,group-details}.fxml`
- Create: `Frontend/admin-client/src/main/java/com/chatsystem/admin/controller/{Dashboard,Users,UserDetails,Groups,GroupDetails}Controller.java`
- Create: `Frontend/admin-client/src/main/java/com/chatsystem/admin/ui/cell/{AdminUserCell,AdminGroupCell,AdminMemberCell}.java`
- Test: `Frontend/admin-client/src/test/java/com/chatsystem/admin/controller/{DashboardControllerTest,UserManagementControllerTest,GroupManagementControllerTest}.java`
- Extend: `Frontend/admin-client/src/test/java/com/chatsystem/admin/FxmlSmokeTest.java`

**Interfaces:**
- Consumes: `AdminService`, `GroupService`, typed `AdminNavigator` routes.
- Produces: live dashboard range filters/links, user search/filter/sort/page/detail/lock workflows, and group search/sort/detail/management workflows.

- [ ] **Step 1: Write failing dashboard controller tests**

Assert time-range controls change derived metrics/chart projection and View All links navigate correctly; exports are disabled with exact tooltip.

- [ ] **Step 2: Write failing user/group controller tests**

Assert deterministic search/filter/sort/page behavior, row/detail selection by ID, lock/unlock confirmation and propagation, group management propagation, and disabled exports/settings/help.

- [ ] **Step 3: Run focused admin tests and verify failure**

Run: `Frontend\gradlew.bat :admin-client:test --tests "com.chatsystem.admin.controller.DashboardControllerTest" --tests "com.chatsystem.admin.controller.UserManagementControllerTest" --tests "com.chatsystem.admin.controller.GroupManagementControllerTest"`

- [ ] **Step 4: Migrate five views, replace static rows/charts with visually equivalent observable rendering, and implement controllers**

Use small menus behind existing filter/sort buttons so their visual appearance remains intact.

- [ ] **Step 5: Run focused and FXML tests**

Run: `Frontend\gradlew.bat :admin-client:test --tests "com.chatsystem.admin.controller.*" --tests "com.chatsystem.admin.FxmlSmokeTest"`
Expected: PASS.

- [ ] **Step 6: Update inventory and commit admin management UI**

```powershell
git add Frontend/admin-client docs/UI_CONTROL_INVENTORY.md
git commit -m "feat: implement admin management workflows"
```

### Task 14: Admin moderation workflows

**Files:**
- Create: `Frontend/admin-client/src/main/resources/fxml/views/{moderation,warning-detail}.fxml`
- Create: `Frontend/admin-client/src/main/java/com/chatsystem/admin/controller/{Moderation,WarningDetail}Controller.java`
- Create: `Frontend/admin-client/src/main/java/com/chatsystem/admin/ui/cell/ModerationCaseCell.java`
- Test: `Frontend/admin-client/src/test/java/com/chatsystem/admin/controller/ModerationWorkflowControllerTest.java`
- Extend: `Frontend/admin-client/src/test/java/com/chatsystem/admin/FxmlSmokeTest.java`

**Interfaces:**
- Consumes: `ReportService`, `AdminService`, `AdminNavigator`.
- Produces: search/filter/sort/detail, notes validation, dismiss/warn/suspend actions, confirmation, and consistent status/sanction updates.

- [ ] **Step 1: Write failing controller tests for report queue and moderation decisions**

Assert filter/sort/search, ID-based detail navigation, required notes, cancellation/no mutation, dismiss-only status, warn decision without sanction, suspend decision plus lock, stale report handling, and immediate queue/detail/dashboard consistency.

- [ ] **Step 2: Run moderation tests and verify failure**

Run: `Frontend\gradlew.bat :admin-client:test --tests "com.chatsystem.admin.controller.ModerationWorkflowControllerTest"`

- [ ] **Step 3: Migrate two views, implement dynamic case rows, and wire controllers**

Keep report decisions separate from sanctions except the explicit Suspend action; disable export with its inventory explanation.

- [ ] **Step 4: Complete admin inventory entries and run all admin tests**

Run: `Frontend\gradlew.bat :admin-client:test`
Expected: all 7 supplied screens load; tests pass.

- [ ] **Step 5: Commit moderation UI**

```powershell
git add Frontend/admin-client docs/UI_CONTROL_INVENTORY.md
git commit -m "feat: implement admin moderation workflows"
```

### Task 15: End-to-end acceptance, documentation, and final verification

**Files:**
- Create: `Frontend/README.md`
- Create: `docs/INTERACTIVE_UI_DEMO.md`
- Finalize: `docs/UI_CONTROL_INVENTORY.md`
- Modify: `docs/superpowers/plans/2026-11-14-interactive-javafx-frontends.md` (checkbox state only during execution)

**Interfaces:**
- Consumes: all completed tasks.
- Produces: launch instructions, mock credentials/reset code, architecture/extension notes, clean-fixture user/admin demo scripts, and complete control accounting.

- [ ] **Step 1: Run a clean complete build**

Run: `Frontend\gradlew.bat clean test`
Expected: `BUILD SUCCESSFUL` for all three modules.

- [ ] **Step 2: Verify resource/reference integrity**

Run checks confirming all 28 source FXML files remain, all 28 runtime screens plus approved modals exist, no runtime FXML references `docs` or `../../styles`, and every controller class named by FXML resolves.

- [ ] **Step 3: Audit the control inventory against runtime FXML**

Assert every actionable control/row action is accounted for; sample every disabled control category to verify its specific tooltip is hoverable; verify the four approved additions and all minimal modals are present.

- [ ] **Step 4: Write and execute the user demo script from fresh fixtures**

Cover invalid/valid login, registration/reset, chat send/search/hide/clear/AI, friend request/block flows, group create/invite/role/leave flows, profile/settings/password/logout, and report submission/detail.

- [ ] **Step 5: Write and execute the admin demo script from fresh fixtures**

Cover dashboard filters, user/group search/detail/actions, lock/unlock, moderation filtering/detail/notes/dismiss/warn/suspend, and derived-state updates.

- [ ] **Step 6: Launch both applications and verify fixed windows and visual preservation**

Run separately in a graphical environment: `Frontend\gradlew.bat :user-client:run` and `Frontend\gradlew.bat :admin-client:run`; confirm startup screen, styling, fixed dimensions, dialogs, navigation, and no runtime exceptions.

- [ ] **Step 7: Re-run the complete automated suite after acceptance fixes**

Run: `Frontend\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit acceptance documentation**

```powershell
git add Frontend/README.md docs/INTERACTIVE_UI_DEMO.md docs/UI_CONTROL_INVENTORY.md docs/superpowers
git commit -m "docs: add interactive frontend demo guide"
```
