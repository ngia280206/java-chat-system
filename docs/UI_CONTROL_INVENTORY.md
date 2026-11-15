# UI control inventory

Initial baseline for the [approved design](superpowers/specs/2026-11-14-interactive-javafx-frontends-design.md) and [implementation plan](superpowers/plans/2026-11-14-interactive-javafx-frontends.md). Source screen names below resolve under `docs/UI_design/Materials/views/{App}/`. All 7 admin and 21 user source screens were inspected, including label-based choices, prototype rows, and the approved additions.

This is the Task 1 classification, before runtime controller wiring. **Pending — Task N** names the later implementation owner. **Disabled — Task N** is an intentional disabled classification that Task N must apply at runtime, with the exact quoted tooltip in Reason; it does not claim the source prototype is already disabled. **Decorative** means read-only display with no action. No application workflow is claimed implemented yet. Repeated controls are grouped only when their behavior is identical; multiplicities and row identities identify every source occurrence. Shared sidebar rows apply to every screen in the stated range, including active buttons. Update these statuses and verification evidence as tasks complete. Task 15 audits runtime controls and executes the manual acceptance script.

Disabled controls remain visible. Their tooltip must be attached to a hoverable wrapper if JavaFX does not deliver hover events to the disabled child. A validation/permission-disabled workflow keeps its specific validation reason; it is different from an undefined action intentionally disabled for the whole demo.

## Shared navigation and display conventions

| App | Screen | Control | Behavior | Status | Reason | Verification |
|---|---|---|---|---|---|---|
| admin | 01–07, each sidebar | Overview | Navigate to Dashboard; preserve admin session | Pending — Task 12 | Supplied route | AdminNavigatorTest; FxmlSmokeTest |
| admin | 01–07, each sidebar | People | Navigate to Users | Pending — Task 12 | Supplied route | AdminNavigatorTest |
| admin | 01–07, each sidebar | Groups | Navigate to Groups | Pending — Task 12 | Supplied route | AdminNavigatorTest |
| admin | 01–07, each sidebar | Warnings | Navigate to Moderation | Pending — Task 12 | Supplied route | AdminNavigatorTest |
| admin | 01–07, each sidebar | Settings | Remain visible and disabled | Disabled — Task 12 | Tooltip: “Admin settings are unavailable because this demo has no admin settings screen.” | AdminApplicationContextTest; wrapper hover in Task 15 |
| admin | 01–07, each sidebar | Help & support | Remain visible and disabled | Disabled — Task 12 | Tooltip: “Help and support are unavailable because no support destination is defined.” | AdminApplicationContextTest; wrapper hover in Task 15 |
| user | 06–20, each sidebar | Chats | Navigate to chat home | Pending — Task 6 | Supplied route | UserNavigatorTest; FxmlSmokeTest |
| user | 06–20, each sidebar | Friends | Navigate to friends | Pending — Task 6 | Supplied route | UserNavigatorTest |
| user | 06–20, each sidebar | Groups | Navigate to group management | Pending — Task 6 | Supplied route | UserNavigatorTest |
| user | 06–20, each sidebar | Spam reports | Navigate to report history | Pending — Task 6 | Supplied route | UserNavigatorTest |
| user | 06–20, each sidebar | My profile | Navigate to profile | Pending — Task 6 | Supplied route | UserNavigatorTest |
| user | 06–20, each sidebar | Settings | Navigate to settings | Pending — Task 6 | Supplied route | UserNavigatorTest |
| user | 06–20, each sidebar | Help & support | Remain visible and disabled | Disabled — Task 6 | Tooltip: “Help and support are unavailable because no support destination is defined.” | UserApplicationContextTest; wrapper hover in Task 15 |
| both | All applicable screens | Branding, avatars, breadcrumbs, current-user summary, date, preview badge, headings, timestamps, count/status badges | Read-only; values may reflect session state when migrated | Decorative | Labels are not navigation unless explicitly listed below | FXML review; Task 15 visual acceptance |
| both | All applicable screens | ScrollPane and scrollbars | Scroll content using standard JavaFX behavior | Implemented workflow | Native source ScrollPane; no domain action | Task 15 scrolling acceptance |
| both | All applicable screens | Separators, spacers, shapes, chart bars, card frames | Layout/display only | Decorative | No actionable behavior | Task 15 visual acceptance |

## Admin screens

| App | Screen | Control | Behavior | Status | Reason | Verification |
|---|---|---|---|---|---|---|
| admin | 01-dashboard | Export overview | Visible, disabled | Disabled — Task 13 | Tooltip: “Overview export is unavailable in the frontend-only demo.” | DashboardControllerTest; wrapper hover |
| admin | 01-dashboard | Last 12 days v | Open range choices and recompute metrics/chart | Pending — Task 13 | Dashboard range workflow | DashboardControllerTest |
| admin | 01-dashboard | View all (recent platform activity) | Navigate to corresponding admin directory | Pending — Task 13 | Dashboard list navigation | DashboardControllerTest; Task 15 navigation |
| admin | 01-dashboard | Four metric cards, weekly activity chart, platform snapshot | Display derived metrics for selected range | Decorative | Data projection, no independent card action | DashboardControllerTest |
| admin | 01-dashboard | Four recent activity rows: registration, report, group creation, resolved moderation | Display fixture/current activity | Decorative | No row action supplied | Task 15 visual acceptance |
| admin | 02-users | Search users by name, email, or ID | Filter directory with empty-result feedback | Pending — Task 13 | User search | UserManagementControllerTest |
| admin | 02-users | Sort: recent login v | Choose deterministic ordering | Pending — Task 13 | User sort | UserManagementControllerTest |
| admin | 02-users | Filters | Choose status/login filters and update directory | Pending — Task 13 | User filters | UserManagementControllerTest |
| admin | 02-users | Download CSV | Visible, disabled | Disabled — Task 13 | Tooltip: “User CSV download is unavailable in the frontend-only demo.” | UserManagementControllerTest; wrapper hover |
| admin | 02-users | Six user rows / View details labels: Alex, Mia, Jordan, Taylor, Sam, Casey | Select/open details using stable user ID | Pending — Task 13 | Labels represent row actions, not static text | UserManagementControllerTest |
| admin | 02-users | Previous | Previous result page; disabled at first page | Pending — Task 13 | Pagination | UserManagementControllerTest |
| admin | 02-users | Next | Next result page; disabled at last page | Pending — Task 13 | Pagination | UserManagementControllerTest |
| admin | 03-user-details | Back to users | Return to user directory | Pending — Task 13 | Typed navigation | UserManagementControllerTest |
| admin | 03-user-details | Restrict user / corresponding unlock state | Confirm lock/unlock and refresh account/list/metrics | Pending — Task 13 | Account sanction separate from report status | UserManagementControllerTest; InMemoryAdminServiceTest |
| admin | 03-user-details | Full history | Reveal full selected user's login history within existing history area | Pending — Task 13 | No separate history screen is supplied | UserManagementControllerTest |
| admin | 03-user-details | Four recent login rows and three activity timeline entries | Display selected user's fixture/activity history | Decorative | No per-record action supplied | Task 15 history acceptance |
| admin | 04-groups | Search group name or owner | Filter groups | Pending — Task 13 | Admin group search | GroupManagementControllerTest |
| admin | 04-groups | Sort: member count v | Choose deterministic group ordering | Pending — Task 13 | Admin group sort | GroupManagementControllerTest |
| admin | 04-groups | Five group rows / Inspect labels: Design Circle, Study Hub, Weekend Crew, Dev Community, Marketplace | Open selected group by ID | Pending — Task 13 | Labels represent row actions | GroupManagementControllerTest |
| admin | 05-group-details | Back to groups | Return to group directory | Pending — Task 13 | Typed navigation | GroupManagementControllerTest |
| admin | 05-group-details | Open moderation history | Navigate to moderation filtered to selected group's reports | Pending — Task 13 | Existing moderation destination | GroupManagementControllerTest |
| admin | 05-group-details | Export members | Visible, disabled | Disabled — Task 13 | Tooltip: “Group member export is unavailable in the frontend-only demo.” | GroupManagementControllerTest; wrapper hover |
| admin | 05-group-details | Member rows: Mia, Alex, Jordan, Casey; three group activity entries | Display members, roles, joined dates and activity | Decorative | No member mutation action supplied in admin screen | Task 15 group-detail acceptance |
| admin | 06-moderation | Export report log | Visible, disabled | Disabled — Task 14 | Tooltip: “Report log export is unavailable in the frontend-only demo.” | ModerationWorkflowControllerTest; wrapper hover |
| admin | 06-moderation | Search case ID, user, or reason | Filter moderation queue | Pending — Task 14 | Report search | ModerationWorkflowControllerTest |
| admin | 06-moderation | All statuses v | Filter report status | Pending — Task 14 | Report filter | ModerationWorkflowControllerTest |
| admin | 06-moderation | Newest first v | Choose report ordering | Pending — Task 14 | Report sort | ModerationWorkflowControllerTest |
| admin | 06-moderation | Five case rows: WR-219, WR-218, WR-217, WR-216, WR-215 | Open warning details with stable report ID | Pending — Task 14 | Selectable queue rows | ModerationWorkflowControllerTest |
| admin | 06-moderation | Workflow step pills 1–4; metric cards | Read-only workflow explanation and derived counts | Decorative | Not wizard navigation | Task 15 acceptance |
| admin | 07-warning-detail | Back to warning queue | Return to moderation | Pending — Task 14 | Navigation | ModerationWorkflowControllerTest |
| admin | 07-warning-detail | Moderation note TextArea | Collect required review notes; show validation | Pending — Task 14 | Screen-defined review input | ModerationWorkflowControllerTest |
| admin | 07-warning-detail | Dismiss report | Confirm dismissal; update report only | Pending — Task 14 | Review and sanction are separate operations | ModerationWorkflowControllerTest; InMemoryReportServiceTest |
| admin | 07-warning-detail | Warn user | Confirm warning decision; update report without account lock | Pending — Task 14 | Explicit warning action | ModerationWorkflowControllerTest; InMemoryReportServiceTest |
| admin | 07-warning-detail | Suspend user | Confirm decision plus account lock; update queue/detail/dashboard | Pending — Task 14 | Explicit combined sanction action | ModerationWorkflowControllerTest; InMemoryReportServiceTest |

## User authentication

| App | Screen | Control | Behavior | Status | Reason | Verification |
|---|---|---|---|---|---|---|
| user | 01-login | Email TextField | Collect/validate mock account email | Implemented — Task 7 | Authentication input | AuthWorkflowControllerTest |
| user | 01-login | Password PasswordField | Collect required password | Implemented — Task 7 | Authentication input | AuthWorkflowControllerTest |
| user | 01-login | Remember me | Keep mock preference for current process only | Implemented — Task 7 | No durable authentication/persistence | AuthWorkflowControllerTest; Task 15 restart acceptance |
| user | 01-login | Forgot password? | Navigate to forgot password | Implemented — Task 7 | Authentication route | AuthWorkflowControllerTest |
| user | 01-login | Sign in → | Validate and authenticate; show failure or open application shell | Implemented — Task 7 | Authentication workflow | AuthWorkflowControllerTest |
| user | 01-login | Create an account | Navigate to registration | Implemented — Task 7 | Authentication route | AuthWorkflowControllerTest |
| user | 02-register | Full name TextField | Collect required display name | Implemented — Task 7 | Registration validation | AuthWorkflowControllerTest |
| user | 02-register | Email TextField | Validate email and duplicate account | Implemented — Task 7 | Registration validation | AuthWorkflowControllerTest |
| user | 02-register | Password PasswordField | Validate password strength | Implemented — Task 7 | Registration validation | AuthWorkflowControllerTest |
| user | 02-register | Confirm password PasswordField | Validate password match | Implemented — Task 7 | Registration validation | AuthWorkflowControllerTest |
| user | 02-register | I agree to the Terms and Privacy Policy CheckBox | Require checked consent for mock registration | Implemented — Task 7 | No separate terms/privacy link or destination supplied | AuthWorkflowControllerTest |
| user | 02-register | Create account → | Create in-memory account with field/terms validation | Implemented — Task 7 | Registration workflow | AuthWorkflowControllerTest |
| user | 02-register | Sign in | Return to login | Implemented — Task 7 | Authentication route | AuthWorkflowControllerTest |
| user | 03-forgot-password | Email TextField | Collect/validate reset account email | Implemented — Task 7 | Reset input | AuthWorkflowControllerTest |
| user | 03-forgot-password | Send verification code → | Start mock reset session and navigate to verification | Implemented — Task 7 | Local fixed code, no real email | AuthWorkflowControllerTest |
| user | 03-forgot-password | ← Back to sign in | Return to login | Implemented — Task 7 | Authentication route | AuthWorkflowControllerTest |
| user | 04-verification-code | Six otp-box/otp-digit placeholders (positions 1–6) | Replace static digits with visually equivalent editable six-digit code entry | Implemented — Task 7 | Runtime digit inputs preserve source boxes; prototypes remain labels | AuthWorkflowControllerTest |
| user | 04-verification-code | Verify code → | Validate code and reset session; navigate to new password | Implemented — Task 7 | Reset verification | AuthWorkflowControllerTest |
| user | 04-verification-code | Resend code | Refresh mock code/reset session with visible feedback | Implemented — Task 7 | Simulated send | AuthWorkflowControllerTest |
| user | 05-reset-password | New password PasswordField | Validate password strength | Implemented — Task 7 | Reset validation | AuthWorkflowControllerTest |
| user | 05-reset-password | Confirm password PasswordField | Validate match | Implemented — Task 7 | Reset validation | AuthWorkflowControllerTest |
| user | 05-reset-password | Reset password → | Apply reset to verified account, return to login | Implemented — Task 7 | Reset workflow | AuthWorkflowControllerTest |
| user | 05-reset-password | ← Return to sign in | Return to login without changing password | Implemented — Task 7 | Cancellation/navigation | AuthWorkflowControllerTest |

## User conversations, messages, and assistant

| App | Screen | Control | Behavior | Status | Reason | Verification |
|---|---|---|---|---|---|---|
| user | 06-chat-home, 07-chat-private, 08-chat-group | + New (one per screen) | Open New Conversation recipient-selection modal | Pending — Task 8 | Existing initiating controls | ChatWorkflowControllerTest |
| user | 06-chat-home, 07-chat-private, 08-chat-group | Search conversations TextField (one per screen) | Filter conversation previews | Pending — Task 8 | Conversation search | ChatWorkflowControllerTest |
| user | 06-chat-home, 07-chat-private, 08-chat-group | All / Unread / Groups pills (three per screen) | Select conversation filter | Pending — Task 8 | Label-based filter choices | ChatWorkflowControllerTest |
| user | 06-chat-home, 07-chat-private, 08-chat-group | Five conversation rows per screen: Mia, Design Circle, Jordan, Study Hub, Sam | Open private/group chat by conversation ID and update unread state | Pending — Task 8 | Includes list-item-selected variant | ChatWorkflowControllerTest |
| user | 06-chat-home | Start a conversation | Open same recipient-selection modal as + New | Pending — Task 8 | Existing New Conversation initiator | ChatWorkflowControllerTest |
| user | 06-chat-home | Explore groups | Navigate to group management | Pending — Task 8 | Supplied destination | ChatWorkflowControllerTest |
| user | 07-chat-private, 08-chat-group | Search (one per screen) | Open message search with current conversation filter | Pending — Task 8 | Chat search route | ChatWorkflowControllerTest |
| user | 07-chat-private | Details | Visible, disabled | Disabled — Task 8 | Tooltip: “Private contact details are unavailable because no contact details screen is supplied.” | ChatWorkflowControllerTest; wrapper hover |
| user | 08-chat-group | Details | Open selected group details | Pending — Task 8 | Existing supplied group screen | ChatWorkflowControllerTest |
| user | 07-chat-private, 08-chat-group | ✦ Ask AI (one per screen) | Open owned fixed-size assistant popup with selected message context | Pending — Task 8 | Local simulated assistant | ChatWorkflowControllerTest |
| user | 07-chat-private, 08-chat-group | + attachment (one per composer) | Visible, disabled | Disabled — Task 8 | Tooltip: “Attachments are unavailable in the frontend-only demo.” | ChatWorkflowControllerTest; wrapper hover |
| user | 07-chat-private, 08-chat-group | Write a message... TextField (one per screen) | Collect message; prevent empty submission; support submit | Pending — Task 8 | Composer input | ChatWorkflowControllerTest |
| user | 07-chat-private, 08-chat-group | Send → (one per screen) | Send through service and update messages/preview/time/indicators | Pending — Task 8 | Observable message workflow | ChatWorkflowControllerTest; InMemoryChatServiceTest |
| user | 07-chat-private, 08-chat-group | Four message bubbles per source screen; each dynamic message | Provide selected message context for AI and Hide Message | Pending — Task 8 | Message identity required for contextual actions | ChatWorkflowControllerTest |
| user | 08-chat-group | Manage group | Open selected group's management/details | Pending — Task 8 | Existing destination | ChatWorkflowControllerTest |
| user | 08-chat-group | Right-pane online member rows: Mia, Jordan, Sam | Read-only member/role projection | Decorative | No member action in supplied chat sidebar | Task 15 acceptance |
| user | 09-message-search | Back to chats | Return to chat | Pending — Task 8 | Navigation | ChatWorkflowControllerTest |
| user | 09-message-search | Search query TextField (mockup placeholder) | Collect search text and show empty state | Pending — Task 8 | Message search input | ChatWorkflowControllerTest |
| user | 09-message-search | All conversations v | Choose conversation scope | Pending — Task 8 | Approved search filter | ChatWorkflowControllerTest |
| user | 09-message-search | Search | Query visible messages respecting hidden/cleared state | Pending — Task 8 | Per-user search semantics | ChatWorkflowControllerTest; InMemoryChatServiceTest |
| user | 09-message-search | All dates pill | Read-only all-dates scope indicator | Decorative | No date-selection workflow supplied | Task 15 acceptance |
| user | 09-message-search | Images pill | Visible, disabled | Disabled — Task 8 | Tooltip: “Image search is unavailable because attachments are not supported in this demo.” | ChatWorkflowControllerTest; wrapper hover |
| user | 09-message-search | Links pill | Visible, disabled | Disabled — Task 8 | Tooltip: “Link-specific filtering is unavailable; use message text search.” | ChatWorkflowControllerTest; wrapper hover |
| user | 09-message-search | Files pill | Visible, disabled | Disabled — Task 8 | Tooltip: “File search is unavailable because attachments are not supported in this demo.” | ChatWorkflowControllerTest; wrapper hover |
| user | 09-message-search | Four result rows: Mia Nov 09, You Nov 07, Mia Nov 05, You Oct 29 | Open corresponding conversation/message using stable IDs | Pending — Task 8 | Selectable search results | ChatWorkflowControllerTest |
| user | 21-ai-assistant-popup | ✦ Ask AI (message preview) | Select/reuse displayed message context in existing assistant | Pending — Task 8 | No recursive popup | ChatWorkflowControllerTest |
| user | 21-ai-assistant-popup | × | Close popup | Pending — Task 8 | Owned popup close | ChatWorkflowControllerTest |
| user | 21-ai-assistant-popup | Summarize / Draft a reply / Explain pills | Choose predefined prompt and show simulated contextual response | Pending — Task 8 | Label-based assistant shortcuts | ChatWorkflowControllerTest |
| user | 21-ai-assistant-popup | Ask about this message... TextField | Collect and validate prompt | Pending — Task 8 | Assistant input | ChatWorkflowControllerTest |
| user | 21-ai-assistant-popup | Send | Show predefined local response; visibly identify simulation | Pending — Task 8 | Real AI is out of scope | ChatWorkflowControllerTest; PredefinedAssistantServiceTest |

## User friends and relationships

| App | Screen | Control | Behavior | Status | Reason | Verification |
|---|---|---|---|---|---|---|
| user | 10-friends | + Add friend | Open minimal target-selection modal | Pending — Task 9 | Existing initiator | SocialWorkflowControllerTest |
| user | 10-friends | All friends / Requests / Blocked pills | Navigate to corresponding supplied friend view | Pending — Task 9 | Label-based tabs | SocialWorkflowControllerTest |
| user | 10-friends | Search by name or username TextField | Filter friends | Pending — Task 9 | Friend search | SocialWorkflowControllerTest |
| user | 10-friends | Online first v | Choose deterministic friend order | Pending — Task 9 | Friend sort | SocialWorkflowControllerTest |
| user | 10-friends | Four friend rows: Mia, Jordan, Sam, Casey | Select valid target for row actions; identity/status remains read-only | Pending — Task 9 | Target selection for Message/Block | SocialWorkflowControllerTest |
| user | 10-friends | Message (four buttons, one per friend) | Start/open private conversation with selected friend | Pending — Task 9 | Existing friend action | SocialWorkflowControllerTest |
| user | 11-friend-requests | Back to friends | Return to friends | Pending — Task 9 | Navigation | SocialWorkflowControllerTest |
| user | 11-friend-requests | Accept (Priya, Bao, Emery; three buttons) | Accept incoming request; refresh friendships/requests | Pending — Task 9 | Relationship mutation | SocialWorkflowControllerTest; InMemorySocialServiceTest |
| user | 11-friend-requests | Decline (Priya, Bao, Emery; three buttons) | Reject incoming request | Pending — Task 9 | Relationship mutation | SocialWorkflowControllerTest; InMemorySocialServiceTest |
| user | 11-friend-requests | Sent request rows: Taylor, Nora | Select supported outgoing request and cancel with confirmation | Pending — Task 9 | Required cancel workflow uses existing sent-request rows | SocialWorkflowControllerTest; InMemorySocialServiceTest |
| user | 12-blocked-users | Back to friends | Return to friends | Pending — Task 9 | Navigation | SocialWorkflowControllerTest |
| user | 12-blocked-users | Unblock (Taylor, Unknown User; two buttons) | Confirm unblock; update blocked state | Pending — Task 9 | Relationship mutation | SocialWorkflowControllerTest; InMemorySocialServiceTest |
| user | 12-blocked-users | Blocked account cards | Read-only blocked identity/date | Decorative | Actions accounted for by Unblock buttons | Task 15 acceptance |

## User groups

| App | Screen | Control | Behavior | Status | Reason | Verification |
|---|---|---|---|---|---|---|
| user | 13-group-management | + Create group | Open minimal group-creation modal | Pending — Task 10 | Existing initiator | GroupWorkflowControllerTest |
| user | 13-group-management | View all | Show complete group collection in existing list | Pending — Task 10 | Browse groups without new screen | GroupWorkflowControllerTest |
| user | 13-group-management | Four group rows: Design Circle, Study Hub, Weekend Crew, Dev Community | Open selected group details by ID | Pending — Task 10 | Selectable groups | GroupWorkflowControllerTest |
| user | 13-group-management | Group invitations | Navigate to invitations | Pending — Task 10 | Supplied route | GroupWorkflowControllerTest |
| user | 13-group-management | Manage members | Select valid group and open member management in group details | Pending — Task 10 | Use existing group selection; explain missing selection | GroupWorkflowControllerTest |
| user | 14-group-details | Back to groups | Return to group management | Pending — Task 10 | Navigation | GroupWorkflowControllerTest |
| user | 14-group-details | Group description TextArea | Edit supplied group field with validation/permission checks | Pending — Task 10 | Existing editable field | GroupWorkflowControllerTest |
| user | 14-group-details | Save changes | Save authorized group-field edits and update projections | Pending — Task 10 | Existing group mutation | GroupWorkflowControllerTest; InMemoryGroupServiceTest |
| user | 14-group-details | Invite members | Open minimal member-selection modal | Pending — Task 10 | Existing initiator | GroupWorkflowControllerTest |
| user | 14-group-details | Edit group | Focus/enable existing editable group details | Pending — Task 10 | Reuse existing description form | GroupWorkflowControllerTest |
| user | 14-group-details | — (Alex owner row) | Visible, disabled for protected member | Disabled — Task 10 | Tooltip: “This protected group admin cannot be removed; the group must retain a valid admin.” | GroupWorkflowControllerTest; wrapper hover |
| user | 14-group-details | Remove (Mia, Jordan, Casey; three buttons) | Confirm authorized member removal, preserving valid admin | Pending — Task 10 | Group permission/invariant checks | GroupWorkflowControllerTest; InMemoryGroupServiceTest |
| user | 14-group-details | Member row identity, role label, joined date | Read-only except approved role-selection control below | Decorative | Source Owner/Moderator wording maps to documented ADMIN/MEMBER domain | Task 15 acceptance |
| user | 15-group-invitations | Back to groups | Return to group management | Pending — Task 10 | Navigation | GroupWorkflowControllerTest |
| user | 15-group-invitations | Join (Product Roundtable, Java Community, Movie Group; three buttons) | Accept selected invitation; update groups/membership/conversation | Pending — Task 10 | Cross-screen mutation | GroupWorkflowControllerTest; InMemoryGroupServiceTest |
| user | 15-group-invitations | Decline (same three invitation cards) | Reject invitation | Pending — Task 10 | Group invitation mutation | GroupWorkflowControllerTest; InMemoryGroupServiceTest |

## User profile, settings, password, and reports

| App | Screen | Control | Behavior | Status | Reason | Verification |
|---|---|---|---|---|---|---|
| user | 16-profile | Preview public profile | Visible, disabled | Disabled — Task 11 | Tooltip: “Public profile preview is unavailable because no public profile screen is supplied.” | AccountWorkflowControllerTest; wrapper hover |
| user | 16-profile | Change avatar | Visible, disabled | Disabled — Task 11 | Tooltip: “Avatar selection is unavailable in the frontend-only demo.” | AccountWorkflowControllerTest; wrapper hover |
| user | 16-profile | Display name TextField | Validate/edit profile display name | Pending — Task 11 | Profile input | AccountWorkflowControllerTest |
| user | 16-profile | Username TextField | Validate/edit username | Pending — Task 11 | Profile input | AccountWorkflowControllerTest |
| user | 16-profile | Bio TextField | Validate/edit bio | Pending — Task 11 | Profile input | AccountWorkflowControllerTest |
| user | 16-profile | Save profile | Persist profile to session; propagate displayed identity | Pending — Task 11 | Observable profile workflow | AccountWorkflowControllerTest; InMemoryProfileServiceTest |
| user | 16-profile | Friends only / Everyone pills | Select/save mock profile-visibility preference for session | Pending — Task 11 | Label-based profile preference; no real access control | AccountWorkflowControllerTest |
| user | 16-profile | Email, member since, account status | Read-only account information | Decorative | No email/status mutation supplied | Task 15 acceptance |
| user | 17-settings | Save preferences | Save staged mock preferences for session | Pending — Task 11 | Navigation/cancel must not accidentally save staged edits | AccountWorkflowControllerTest; InMemorySettingsServiceTest |
| user | 17-settings | Dark pill | Current fixed theme indicator | Decorative | Approved visual language uses dark.css | Task 15 visual acceptance |
| user | 17-settings | Light pill | Visible, disabled | Disabled — Task 11 | Tooltip: “Light theme is unavailable because this demo supplies only the dark theme.” | AccountWorkflowControllerTest; wrapper hover |
| user | 17-settings | System pill | Visible, disabled | Disabled — Task 11 | Tooltip: “System theme switching is unavailable because this demo supplies only the dark theme.” | AccountWorkflowControllerTest; wrapper hover |
| user | 17-settings | Enable desktop notifications CheckBox | Save mock notification preference only | Pending — Task 11 | Session preference; no real notification delivery | AccountWorkflowControllerTest; InMemorySettingsServiceTest |
| user | 17-settings | Play a sound for new messages CheckBox | Save mock sound preference only | Pending — Task 11 | Session preference; no device/audio delivery | AccountWorkflowControllerTest; InMemorySettingsServiceTest |
| user | 17-settings | Show message previews CheckBox | Save preview preference for session | Pending — Task 11 | Mock setting | AccountWorkflowControllerTest; InMemorySettingsServiceTest |
| user | 17-settings | Friends and groups / Read receipts Enabled / Online status Visible to friends | Read-only privacy/security summaries | Decorative | Supplied as text with no editing action | Task 15 acceptance |
| user | 17-settings | Change password | Navigate to change-password form | Pending — Task 11 | Supplied route | AccountWorkflowControllerTest |
| user | 17-settings | Blocked accounts | Navigate to blocked users | Pending — Task 11 | Supplied route | AccountWorkflowControllerTest |
| user | 17-settings | Sign out | Clear authenticated session; return to Login | Pending — Task 11 | Authentication lifecycle | AccountWorkflowControllerTest |
| user | 18-change-password | Back to settings | Discard unsaved input; return to settings | Pending — Task 11 | Navigation/cancel | AccountWorkflowControllerTest |
| user | 18-change-password | Current password PasswordField | Validate existing credential | Pending — Task 11 | Password validation | AccountWorkflowControllerTest |
| user | 18-change-password | New password PasswordField | Validate new password strength/reuse rule | Pending — Task 11 | Password validation | AccountWorkflowControllerTest |
| user | 18-change-password | Confirm password PasswordField | Validate match | Pending — Task 11 | Password validation | AccountWorkflowControllerTest |
| user | 18-change-password | Cancel | Return without password mutation | Pending — Task 11 | Cancellation | AccountWorkflowControllerTest |
| user | 18-change-password | Update password | Apply validated mock password change with feedback | Pending — Task 11 | Password workflow | AccountWorkflowControllerTest; InMemoryAuthServiceTest |
| user | 19-spam-reports | New report | Open minimal target/reason report modal | Pending — Task 11 | Existing initiator | UserReportWorkflowControllerTest |
| user | 19-spam-reports | Search your reports TextField | Filter current user's submitted reports | Pending — Task 11 | Report search | UserReportWorkflowControllerTest |
| user | 19-spam-reports | Status: all v | Filter report history by status | Pending — Task 11 | Report filter | UserReportWorkflowControllerTest |
| user | 19-spam-reports | Three report rows: RP-1042, RP-1023, RP-1007 | Open selected report details by ID | Pending — Task 11 | Selectable report rows | UserReportWorkflowControllerTest |
| user | 20-report-detail | Back to reports | Return to report history | Pending — Task 11 | Navigation | UserReportWorkflowControllerTest |
| user | 20-report-detail | Report details/progress entries and Next label | Read-only report status progression | Decorative | Next is a timeline marker, not a button | UserReportWorkflowControllerTest; Task 15 acceptance |

## Approved added controls and minimal dialogs

These are inventory commitments for the approved workflows, not new standalone screens. Dialogs reuse existing styling and collect only essential input. Confirmed mutations use services; Cancel/close leaves state unchanged. Confirmation dialogs provide Confirm and Cancel for every consequential operation listed above.

| App | Screen | Control | Behavior | Status | Reason | Verification |
|---|---|---|---|---|---|---|
| user | New Conversation modal from 06/07/08 | Recipient selector; Start/Create; Cancel/close | Choose valid recipient and open/create conversation; cancel unchanged | Pending — Task 8 | Required New Conversation workflow | ChatWorkflowControllerTest; InMemoryChatServiceTest |
| user | Add Friend modal from 10 | Target selector/search; Send request; Cancel/close | Submit valid request; reject self/blocked/duplicate targets | Pending — Task 9 | Required Add Friend workflow | SocialWorkflowControllerTest; InMemorySocialServiceTest |
| user | Create Group modal from 13 | Essential group-name/member inputs; Create; Cancel/close | Validate/create group and update lists/conversations | Pending — Task 10 | Required Create Group workflow | GroupWorkflowControllerTest; InMemoryGroupServiceTest |
| user | Invite Members modal from 14 | Eligible member selector; Invite; Cancel/close | Validate and invite selected eligible users | Pending — Task 10 | Required Invite Members workflow | GroupWorkflowControllerTest; InMemoryGroupServiceTest |
| user | New Report modal from 19 | Existing message/user target selector; reason/category/description inputs; Submit; Cancel/close | Validate and submit current user's report | Pending — Task 11 | Required New Report workflow | UserReportWorkflowControllerTest; InMemoryReportServiceTest |
| user | 10 friend/user action area (approved addition) | Block User | Show/enable for valid non-self unblocked target; confirm block; reconcile friendships/requests | Pending — Task 9 | Approved initiating-control addition 1 | SocialWorkflowControllerTest; InMemorySocialServiceTest; Task 15 demo |
| user | 07/08 message context menu (approved addition) | Hide Message | Confirm hide of selected message for current user only | Pending — Task 8 | Approved addition 2; no global message deletion | ChatWorkflowControllerTest; InMemoryChatServiceTest; Task 15 demo |
| user | 07/08 conversation action area (approved addition) | Clear History | Confirm per-user current-sequence visibility boundary | Pending — Task 8 | Approved addition 2; later messages remain visible | ChatWorkflowControllerTest; InMemoryChatServiceTest; Task 15 demo |
| user | 14 group-details action area (approved addition) | Leave Group | Show only for current membership; confirm leave; preserve valid admin | Pending — Task 10 | Approved initiating-control addition 3 | GroupWorkflowControllerTest; InMemoryGroupServiceTest; Task 15 demo |
| user | 14 member rows (approved addition) | Change Group Role selector | Current group admin can select ADMIN/MEMBER for eligible target; preserve one admin | Pending — Task 10 | Approved initiating-control addition 4 | GroupWorkflowControllerTest; InMemoryGroupServiceTest; Task 15 demo |
| both | Consequential workflow confirmations | Confirm / Cancel / dialog close | Confirm valid mutation; cancel/close without state change | Pending — Tasks 8–11, 13–14 | Hide/clear, block/unblock, request cancel, member removal/leave/role changes, lock/unlock and moderation | Respective controller/service tests; Task 15 cancellation demo |

No cryptographic/device-key control is present in the 28 source FXML files. If a migrated source control exposes such an operation, disable it with “Device-key and encryption operations are unavailable in the frontend-only demo.” Real export, file selection, external help, AI/network calls, notification delivery, and cryptography are not added by this inventory. Native window close and keyboard/focus/scroll mechanics remain JavaFX behavior; workflow dialog cancellation and fixed window configuration are verified in their owning tasks.
