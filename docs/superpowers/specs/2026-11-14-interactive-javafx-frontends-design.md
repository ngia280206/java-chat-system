# Interactive JavaFX Frontends — Approved Design

## Status

Approved by the user on 2026-11-14. This design supersedes the architectural direction in `2026-11-14-javafx-client-ui-migration-design.md`. The earlier migration implementation plan is superseded and must not be executed.

## Intent

Build two coherent, fully interactive JavaFX demonstration applications—admin and user—using realistic in-memory data and mock services. All 28 supplied FXML screens retain their visual designs while their defined controls support navigation, validation, state changes, dialogs, filtering, sorting, and end-to-end workflows. Real backend adapters must be introducible later without rewriting screen controllers or interaction logic wholesale.

This is a complete frontend demonstration, not a static preview and not a production distributed system.

## Goals

- Launch separate admin and user applications from the existing Gradle multi-project build.
- Make every defined interaction in the supplied screens and approved requirements functional.
- Maintain consistent state across screens for the lifetime of each application process.
- Use realistic initial fixtures consistent with the ERD and data dictionary PDFs.
- Preserve the supplied visual language and fixed desktop dimensions.
- Keep controllers maintainable by placing cross-screen operations and business rules in focused services.
- Keep backend integration behind meaningful use-case boundaries so future adapters can replace mock behavior without major controller rewrites; asynchronous backend APIs may still require adapter-level coordination.
- Account explicitly for every visible actionable control as implemented or intentionally disabled.

## Non-goals

- Real servers, REST clients, databases, or durable persistence.
- Real authentication/authorization infrastructure.
- Multi-process or multi-user synchronization.
- Networking, production notifications, or background push delivery.
- Encryption, device-key management, or cryptography.
- Real AI integration.
- Responsive/mobile layouts.
- New screens, workflows, or features that are not required by the existing UI, domain documents, or approved frontend requirements.

Minimal additions of initiating controls are permitted only for the four approved workflows documented below. This is a narrow exception, not general permission to expand the interface.

## Fixed Constraints

- Java 17 and JavaFX 17.0.2.
- Existing Gradle modules: `shared-client`, `user-client`, and `admin-client`.
- Separate user and admin applications with independently instantiated mock state.
- Existing 7 admin and 21 user visual designs remain the baseline.
- Fixed, non-resizable windows: admin 1480x920; user authentication 1240x780; user application 1540x930; AI popup 890x890.
- Original materials remain under `docs/UI_design/Materials` as references.

## Selected Architecture

### Focused services with observable in-memory state

`shared-client` contains frontend domain models, meaningful service contracts, in-memory implementations, fixtures, validation/business rules shared by the two applications, and session composition. Models cover only concepts required by screens and workflows; they do not mechanically mirror all database tables.

Services own the mutable application session and expose operations such as:

- authenticate, register, request/verify reset, update password, and sign out;
- send/search/hide/clear messages and update conversation previews;
- send/accept/reject/cancel friend requests and block/unblock users;
- create/update/leave groups, manage members/roles, and accept/reject invitations;
- update profiles/settings;
- submit and inspect reports;
- search/filter/sort admin users, groups, and reports;
- lock/unlock users and review/dismiss reports independently from sanctions;
- derive dashboard metrics from current mock state;
- return predefined AI-assistant responses.

Service interfaces contain these real operations and queries. There are no empty marker services, generic repositories, DTO mapping layers, or speculative database abstractions. In-memory implementations are the only implementations in this phase; future backend adapters can implement the same contracts.

### Lightweight JavaFX reactivity

Services expose `ObservableList` and read-only JavaFX properties when multiple screens need to observe the same changing state. Models use JavaFX properties only for fields that are displayed and can change during a session, such as status, profile values, report state, conversation preview/unread count, and group membership.

Controllers bind or listen to these observable values and render filtered/sorted projections. Service methods remain the only mutation path. Controllers must not mutate shared collections or domain properties directly.

There is no centralized state store, command/reducer system, event bus, or application-wide pub/sub framework.

### Manual composition, not a DI framework

Each application creates one explicit session/composition object at startup. It owns concrete in-memory services and passes the required service dependencies to controllers through an `FXMLLoader` controller factory or explicit controller construction. Dependencies are named and typed; there is no global singleton or generic service-locator lookup.

Admin and user processes load equivalent initial fixtures but evolve independently. Cross-process synchronization is intentionally absent.

## Navigation and Screen Lifecycle

### User application

- Starts on Login in a 1240x780 fixed window.
- Authentication screens replace the current root while preserving the mock authentication/reset session.
- Successful login changes to the persistent 1540x930 user application shell.
- The shell owns the common sidebar, current-user summary, navigation selection, and replaceable content region.
- Screen controllers may be recreated on navigation because persistent state resides in services, not controller instances.
- Sign out clears the authenticated session and returns to Login.
- The AI assistant opens as the supplied fixed-size popup owned by the chat window.

### Admin application

- Starts on Dashboard as a pre-authenticated mock administrator because no admin login screen is supplied.
- A persistent 1480x920 admin shell owns the common sidebar and content region.
- Detail views receive the selected entity identifier through navigation parameters rather than global controller state.

Application shells remain justified because navigation, current identity, selected-menu styling, and view replacement are shared application concerns. Repeated sidebar markup is extracted from content views without changing the visible design.

## Domain and Mock State

The ERD and data dictionary define the vocabulary and invariants. Frontend models include only what the workflows display or modify:

- User/account profile and account status.
- Authentication/reset session state.
- Friend request, friendship, and block relationship.
- Conversation, group membership/role/invitation, and message.
- User-visible hidden/cleared message state.
- Login/activity records needed by admin displays and metrics.
- Spam report and moderation status/notes.
- User settings.

Device keys, group-key versions, and key envelopes are not modeled as functional frontend state because cryptography is out of scope. Any related control is disabled with an explicit tooltip.

Fixtures include enough linked users, conversations, messages, groups, invitations, friend requests, reports, login events, and activity events to exercise empty, filtered, success, validation-error, and state-change cases. IDs remain stable for navigation and test assertions.

Example mock authentication values are documented in the UI/readme. Registration creates an in-memory account; password reset uses a documented fixed verification code; neither survives restart.

## Controller Responsibilities

Controllers may:

- bind controls to observable service data;
- maintain view-only state such as the current search text, selected filter, or form feedback;
- translate control actions into meaningful service calls;
- render validation results and confirmation/cancellation outcomes;
- request navigation or open an approved minimal modal.

Controllers may not:

- own authoritative cross-screen collections;
- duplicate domain validation enforced by a service, except immediate presentation-friendly field checks;
- fabricate backend results;
- directly manipulate another controller;
- perform SQL, networking, persistence, or cryptography.

Repeated controller mechanics may use small focused helpers only after real duplication appears. No general controller superclass or dialog framework is introduced upfront. Static repeated prototype rows may be replaced by visually equivalent dynamic containers or list cells where live filtering, sorting, or state updates require it.

## Required User Workflows

### Authentication

- Valid and invalid mock login with visible feedback.
- Navigation among login, registration, forgot password, verification, and reset password.
- Required-field, email, password-strength, confirmation, terms, and verification-code validation.
- In-memory registration and password changes.
- Logout back to Login.

### Chat and messaging

- Select a private/group conversation and display its messages.
- Start a conversation from `+ New` or `Start a conversation` through a minimal recipient-selection modal.
- Send a message and immediately update history, last-message preview, timestamp, and relevant indicators.
- Search conversations and messages; filter message search by conversation.
- Hide an individual message and clear visible history according to documented per-user semantics.
- Open details/group management from chat where provided.
- Open the AI popup from a message, submit a prompt, show a predefined contextual response, and close it.
- Attachment controls remain disabled unless the supplied UI/domain defines a concrete frontend-only attachment behavior; the current scope does not.

### Friends

- Search and sort friends, start a conversation, and add a friend through a minimal modal.
- Send, accept, reject, and cancel supported friend requests.
- Block/unblock users with confirmation and consistent updates to friendships/requests.

### Groups

- Search/browse groups, open details, and display members/roles/activity.
- Create a group through a minimal modal.
- Edit defined group fields, invite members through a minimal modal, change roles where allowed, and remove members.
- Accept/reject invitations and leave a group with confirmation.
- Reflect changes across group lists, details, invitations, and conversations.

### Profile and settings

- Edit and save profile fields with validation and propagate displayed identity values.
- Change mock preferences and preserve them during the session.
- Change password with current/new/confirmation validation.
- Navigate to blocked accounts and sign out.
- Avatar/file selection is disabled unless a concrete mock-avatar behavior is approved later.

### Reports and AI

- Submit a report through a minimal modal using an existing visible message/user as its target.
- Search/filter report history and open report details.
- Report status display remains consistent with mock state.
- AI responses are predefined/local and clearly simulated.

## Required Admin Workflows

### Dashboard

- Metrics and chart data derive from the current admin mock session.
- Existing time-range/filter controls update the derived view.
- Dashboard links navigate to corresponding lists.

### Users

- Search, filter, sort, paginate/select, and open user details.
- Lock/unlock with confirmation and immediate list/detail/metric updates.
- Login/activity history uses fixture data and current filters.

### Groups

- Search/sort groups, open details, inspect members/activity, and perform management actions explicitly represented by the screen.
- Group changes update list/detail/metric projections.

### Moderation

- Search/filter/sort reports and open warning details.
- Require review notes where the screen calls for them.
- Review, dismiss, warn, or suspend only through the explicit actions provided.
- Report review status and account sanctions are separate service operations; combined effects occur only when an explicit action requires both.

## Minimal Modal Policy

A new modal is permitted only when all of the following are true:

1. The workflow is explicitly required.
2. An initiating control already exists in the supplied UI.
3. No supplied screen contains the required input.
4. The modal contains only essential fields, validation, confirm, and cancel actions.

Expected minimal modals include New Conversation/recipient selection, Add Friend, Create Group, Invite Members, New Report, and confirmations for destructive/state-changing actions. They reuse `dark.css` and existing component styles. No generic dialog framework or extra standalone screens are introduced.

## Approved Minimal Initiating-Control Additions

The supplied FXML omits an initiating control for four explicitly required workflows. The following additions are approved as a narrow design-preservation exception:

1. **Block User:** add a `Block` action in the natural friend/user details action area. Show or enable it only for a valid, non-self, currently unblocked target. Confirmation invokes the relationship service, removes conflicting friendship/request state according to the domain rules, and updates friends, requests, and blocked-user views.
2. **Hide Message / Clear History:** add `Hide message` to a message context menu and `Clear history` to the existing conversation action area. Hide affects only the current user's visibility of one message. Clear records the current conversation sequence boundary for that user and does not delete messages globally. Consequential actions require confirmation.
3. **Leave Group:** add `Leave group` to the natural group-details action area. It is visible only for current membership, requires confirmation, updates membership/conversation/group projections, and respects the rule preventing an invalid owner/admin outcome.
4. **Change Group Role:** add a compact role-selection control to member rows. It is available only to a current group admin, cannot target an invalid member/current state, and supports only the documented `ADMIN` and `MEMBER` roles while preserving at least one valid group admin.

These controls reuse existing button/menu styles, spacing, typography, and nearby action placement. They must not trigger broader screen redesign or introduce unrelated capabilities. All four appear in the control inventory and manual acceptance script.

## Undefined and Disabled Controls

Controls whose behavior is not defined by the existing screens, approved requirements, or domain documents remain visible but disabled. Each receives a concise, specific tooltip, for example “Export is unavailable in the frontend-only demo” rather than a generic “Not implemented.” Because disabled JavaFX controls may not reliably receive hover events, a hoverable wrapper/parent may own the tooltip while preserving the disabled control's appearance.

Likely disabled categories include file exports/downloads, attachment upload, undefined Help/Support destinations, admin settings without a supplied screen, real notification delivery, and cryptographic/device-key operations. Final classification is recorded in a control inventory after inspecting every actionable control.

The inventory records: screen, control label/identity, implemented behavior or disabled status, reason, and associated workflow/test. An initial inventory is produced before controller wiring and refined during implementation; the completed inventory is acceptance evidence and prevents silent dead buttons. It must explicitly include the four approved initiating-control additions.

## Validation and Feedback

- Field-level validation appears near the relevant form or in an existing status area.
- Service-level failures use specific user-facing messages.
- Destructive or consequential actions require confirmation.
- Successful mutations provide visible confirmation through the changed UI; transient confirmation text is used only where the change is otherwise not obvious.
- Cancellation leaves mock state unchanged.
- Empty search/filter results show a meaningful empty state.

## Testing Strategy

Testing prioritizes behavior and state consistency.

### Essential automated tests

- Service unit tests for authentication, registration/reset, message mutations, relationships, groups/invitations, reports/moderation, profile/settings, filters/sorts, and dashboard derivation.
- Validation tests for meaningful form/domain rules and cancellation/no-mutation behavior.
- Cross-workflow state tests, such as accepted request appearing in friends, sent message updating preview, joined invitation appearing in groups, and reviewed report leaving the pending queue.
- State/permission tests for blocking, per-user message hiding/history clearing, leaving groups, and changing group roles.
- FXML smoke tests for all 28 supplied screens plus approved modals, including controller creation, resource resolution, and CSS loading.
- Focused controller tests for critical wiring or presentation rules that service tests cannot cover, especially navigation parameters, enabled/disabled state, and form feedback.

### Deliberately avoided overhead

- No exhaustive pixel/screenshot testing.
- No tests for JavaFX framework behavior or trivial property getters.
- No exhaustive automation of every button when the same behavior is already covered at service level and by the control inventory/manual demo checklist.
- No TestFX dependency unless ordinary JavaFX controller/FXML tests prove insufficient for a critical interaction.
- No tests for real network, persistence, security, or cryptographic behavior.

### Manual acceptance

A documented demo script exercises complete user and admin workflows from clean fixtures. It verifies every inventory entry, fixed window behavior, dialogs, validation, cancellation, and consistent updates across navigation.

## Definition of Done

- Both applications launch and complete their documented end-to-end demo scripts from known fixtures.
- All 28 screens retain the supplied visual design and are functional to the extent defined.
- The four approved initiating-control additions preserve the surrounding design and complete their end-to-end workflows with permission and confirmation behavior.
- Every actionable control is recorded as implemented or intentionally disabled with a specific tooltip.
- Defined forms validate input and provide specific feedback.
- Searches, filters, sorts, dialogs, and navigation behave coherently.
- Mutations remain consistent across relevant screens for the application session.
- Mock services contain meaningful operations and can be replaced by backend adapters without major controller rewrites.
- Automated service/state tests and FXML smoke tests pass.
- No real backend, persistence, networking, cryptography, or AI integration is present.

## Approaches Considered

### Selected: Focused services plus lightweight JavaFX observability

This provides direct, testable operations and automatic cross-screen updates without a generalized state-management system. It is the smallest architecture that meets the interactive and backend-ready requirements.

### Rejected: Layered repositories, DTO mapping, and dependency-injection framework

This would anticipate backend structure that has not been designed and add mapping/abstraction overhead without improving the current demo workflows.

### Rejected: Central reactive store with commands/reducers/event bus

This would make all state transitions explicit but introduces a second architecture around JavaFX for a project whose state is naturally partitioned by focused services.

## Known Risks

- The supplied screens use many static row layouts; making them dynamic requires converting repeated rows to programmatically rendered nodes or list-cell components while preserving appearance.
- Extracting duplicated sidebars and binding live state can expose layout assumptions in the static prototypes.
- There are many actionable controls; the control inventory is necessary to prevent missed or meaningless handlers.
- JavaFX tests may require toolkit initialization or a graphical environment; service tests remain headless, and FXML/controller tests will use the least complex viable toolkit setup.
- Future backend APIs may not exactly match the mock contracts; meaningful use-case contracts minimize but cannot eliminate later adapter work.
