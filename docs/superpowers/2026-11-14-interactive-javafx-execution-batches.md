# Interactive JavaFX Frontends — Execution Batches

**Approved plan:** `docs/superpowers/plans/2026-11-14-interactive-javafx-frontends.md`

**Status legend:** `[ ]` pending · `[~]` in progress · `[x]` complete · `[!]` blocked

This checklist organizes the approved 15 tasks for subagent-driven execution. It does not change task scope, architecture, interfaces, tests, or acceptance requirements. Execute only the batch explicitly authorized by the user, complete its review and verification, then stop and report before requesting authorization for the next batch.

## Execution rules

- Execute remaining Tasks 8-15 directly through `superpowers:executing-plans`; do not automatically dispatch implementation subagents or per-task reviewers.
- For each task, load only its extracted brief, relevant global constraints, task-specific interfaces, acceptance criteria, carried findings, and directly affected files. Expand context only for a named ambiguity.
- For each task: direct TDD implementation → focused tests → affected module test → self-review → separate task commit → ledger/checklist update.
- Use one independent combined batch review for high-risk behavior at the end of Batches 4-7, and one independent whole-project final review in Batch 8. Do not duplicate specification and code-quality reviewers.
- Do not start the next task while the current task has unresolved review findings or failing required tests.
- Run the affected module tests once before each task commit. At batch end, run the batch verification commands below and record exact results. Use `clean test` only for shared build changes, suspected stale output, or Task 15.
- Reviewer-requested Critical/Important fixes require a focused RED→GREEN test and rerun of affected tests; module and batch verification requirements remain unchanged.
- Keep task reports to changed files, test results, review findings, commit, known issue, and next task. Consolidate progress-document commits at batch boundaries.
- A completed batch is a safe stopping point. Commit or otherwise preserve all changes before stopping.
- On resume, read this checklist and the approved plan, inspect recorded commits/status, and verify the current worktree. Do not recreate or reimplement `[x]` tasks.
- If prior work has no commit, inspect the listed files and test results before deciding what remains; never assume it is absent.

## Batch status

| Batch | Objective | Tasks | Status |
|---|---|---:|---|
| 1 | Establish verified domain and test foundation | 1–2 | `[x]` |
| 2 | Complete headless mock-service behavior | 3–5 | `[x]` |
| 3 | Build user runtime and authentication workflows | 6–7 | `[x]` |
| 4 | Build user chat and social workflows | 8–9 | `[ ]` |
| 5 | Complete remaining user workflows | 10–11 | `[ ]` |
| 6 | Build admin runtime and core management workflows | 12–13 | `[ ]` |
| 7 | Complete admin moderation | 14 | `[ ]` |
| 8 | Perform whole-product acceptance | 15 | `[ ]` |

---

## Batch 1 — Foundation

**Objective:** Establish JUnit, the actionable-control inventory, observable domain models, validation primitives, and stable independent fixtures.

**Included tasks:** Task 1, Task 2.

**Prerequisites:** Approved design and implementation plan; no product implementation prerequisite.

**Checklist:**

- [x] Task 1: Baseline test infrastructure and actionable-control inventory
- [x] Task 1 specification review
- [x] Task 1 code-quality review
- [x] Task 2: Observable domain models, validation results, and stable fixtures
- [x] Task 2 specification review
- [x] Task 2 code-quality review

**Batch verification:**

```powershell
Frontend\gradlew.bat :shared-client:test
```

Confirm the inventory accounts for all source FXML controls, fixtures are relationally coherent, two fixture stores are independent, and observable fields notify listeners.

**Completion criteria:** Tasks 1–2 are reviewed, all shared tests pass, inventory format/classification exists, and foundation commits are recorded.

**Safe stopping point:** Domain/store APIs and stable fixture IDs are committed; later service work can begin without revisiting their structure.

---

## Batch 2 — Mock services

**Objective:** Implement and verify all headless use-case operations and cross-screen state transitions before controller work begins.

**Included tasks:** Task 3, Task 4, Task 5.

**Prerequisites:** Batch 1 complete.

**Checklist:**

- [x] Task 3: Authentication, profile, and settings services
- [x] Task 3 specification review
- [x] Task 3 code-quality review
- [x] Task 4: Social and group services with permission rules
- [x] Task 4 specification review
- [x] Task 4 code-quality review
- [x] Task 5: Chat, reports, moderation, metrics, and assistant services
- [x] Task 5 specification review
- [x] Task 5 code-quality review

**Batch verification:**

```powershell
Frontend\gradlew.bat :shared-client:test
```

Confirm authentication/reset, social/group permissions, per-user message visibility, reports/moderation separation, derived metrics, observable propagation, and no-mutation failures are covered and passing.

**Completion criteria:** Every approved use case has a meaningful service operation and passing state-transition tests; no repository/DTO/DI/event-bus layer was introduced.

**Safe stopping point:** The complete frontend behavior core is committed and usable independently of JavaFX controllers.

---

## Batch 3 — User runtime and authentication

**Objective:** Launch the user application, establish fixed-window navigation/composition, and complete the five-screen authentication workflow.

**Included tasks:** Task 6, Task 7.

**Prerequisites:** Batch 2 complete.

**Checklist:**

- [x] Task 6: User composition, navigation, shell, resources, and FXML harness
- [x] Task 6 combined specification/code-quality review
- [x] Task 7: Authentication screens and controllers
- [x] Task 7 independent combined review

**Batch verification:**

```powershell
Frontend\gradlew.bat :user-client:test
Frontend\gradlew.bat :user-client:run
```

Confirm Login starts at 1240x780, successful mock login opens the fixed 1540x930 shell, validation/failure/reset/registration flows work, CSS resolves, and defined shell navigation has no dead controls.

**Completion criteria:** Tasks 6–7 pass review/tests; user startup and complete authentication demo work from clean fixtures.

**Safe stopping point:** A runnable authenticated user shell is committed; remaining user features can be added route-by-route.

---

## Batch 4 — User chat and social features

**Objective:** Complete observable chat/message/AI workflows and friend/request/block workflows.

**Included tasks:** Task 8, Task 9.

**Prerequisites:** Batch 3 complete.

**Checklist:**

- [x] Task 8: Chat, message search, and AI workflows
- [x] Task 9: Friends and relationship workflows
- [x] Batch 4 independent combined risk review (Tasks 8–9)

**Batch verification:**

```powershell
Frontend\gradlew.bat :shared-client:test
Frontend\gradlew.bat :user-client:test
```

Manually verify new conversation, send/search/hide/clear, preview updates, AI popup, add/request/accept/reject/cancel, message friend, block/unblock, confirmations, disabled attachments, and inventory entries.

**Completion criteria:** Tasks 8–9 pass review/tests; chat and social mutations remain consistent after navigation.

**Safe stopping point:** Chat and relationship workflows are independently demoable and committed.

---

## Batch 5 — Remaining user features

**Objective:** Complete group, profile, settings, password, logout, and report workflows and finish all 21 user screens.

**Included tasks:** Task 10, Task 11.

**Prerequisites:** Batch 4 complete.

**Checklist:**

- [x] Task 10: Group workflows and approved membership controls
- [x] Task 11: Profile, settings, password, and reporting workflows
- [x] Batch 5 independent combined risk review (Tasks 10–11)

**Batch verification:**

```powershell
Frontend\gradlew.bat :shared-client:test
Frontend\gradlew.bat :user-client:test
Frontend\gradlew.bat :user-client:run
```

Verify create/invite/join/reject/role/remove/leave group, profile propagation, preferences, password validation, blocked navigation, report submission/filter/detail, logout, all user FXML smoke tests, and completed user inventory entries.

**Completion criteria:** Tasks 10–11 pass review/tests; all 21 user screens and approved user dialogs are functional or explicitly disabled/documented.

**Safe stopping point:** The user application is feature-complete and committed independently of admin UI completion.

---

## Batch 6 — Admin runtime and management

**Objective:** Launch the fixed admin shell and complete dashboard, user-management, and group-management workflows.

**Included tasks:** Task 12, Task 13.

**Prerequisites:** Batch 2 complete; Batch 5 should normally be complete to keep execution linear, but admin work depends only on shared services.

**Checklist:**

- [x] Task 12: Admin composition, navigation, shell, and resources — `4faa792`
- [x] Task 13: Dashboard, users, and groups workflows — `13c2eca`
- [x] Batch 6 independent combined risk review (Tasks 12–13) — Important findings fixed in `4f459d9`

**Batch verification:**

```powershell
Frontend\gradlew.bat :shared-client:test
Frontend\gradlew.bat :admin-client:test
Frontend\gradlew.bat :admin-client:run
```

Confirm 1480x920 fixed Dashboard startup, live metric ranges, search/filter/sort/page/detail routes, lock/unlock, group actions, disabled undefined/export controls, and inventory coverage.

**Completion criteria:** Tasks 12–13 pass review/tests; five admin screens are interactive and consistent with observable mock state.

**Safe stopping point:** Admin runtime and management features are committed; moderation remains isolated to the next batch.

---

## Batch 7 — Admin moderation

**Objective:** Complete the remaining two admin screens and moderation decision workflows.

**Included tasks:** Task 14.

**Prerequisites:** Batch 6 complete.

**Checklist:**

- [x] Task 14: Admin moderation workflows — `39511da`
- [x] Batch 7 independent combined moderation review — Important findings fixed in `fe61636`

**Batch verification:**

```powershell
Frontend\gradlew.bat :shared-client:test
Frontend\gradlew.bat :admin-client:test
```

Verify search/filter/sort/detail, required notes, cancel/no mutation, dismiss, warn, suspend-plus-lock, stale-report handling, queue/detail/dashboard updates, all seven admin FXML smoke tests, and final admin inventory entries.

**Completion criteria:** Task 14 passes review/tests; all seven admin screens are functional or explicitly disabled/documented.

**Safe stopping point:** Both applications are feature-complete and committed; only whole-product acceptance remains.

---

## Batch 8 — Whole-product acceptance

**Objective:** Execute Task 15 and produce final evidence that both applications satisfy the approved design.

**Included tasks:** Task 15.

**Prerequisites:** Batches 1–7 complete with no unresolved blocking review findings.

**Checklist:**

- [x] Task 15: End-to-end acceptance, documentation, and final verification
- [ ] Whole-project final review

**Batch verification:**

```powershell
Frontend\gradlew.bat clean test
Frontend\gradlew.bat :user-client:run
Frontend\gradlew.bat :admin-client:run
```

Execute both documented demo scripts from fresh fixtures; audit all 28 screens, approved dialogs/additions, fixed dimensions, resource paths, disabled tooltips, control inventory, and absence of runtime exceptions.

**Completion criteria:** Task 15 and final review pass; automated suite is green; both demo scripts succeed; documentation/inventory are complete; known non-blocking limitations are recorded.

**Safe stopping point:** Implementation is ready for final user handoff.

---

## Execution log

Update this section at every task and batch boundary.

| Date/time | Batch | Task | Status | Commit(s) | Tests/results | Known issues/review findings | Next task |
|---|---:|---:|---|---|---|---|---|
| 2026-11-14 | — | — | Checklist awaiting approval | — | Not run | No implementation started | Batch 1 / Task 1 after approval |
| 2026-11-14 | 1 | 1 | Complete | `5075b04` | 5/5 baseline; full suite; external working-directory run passed | Review clean | Task 2 |
| 2026-11-14 | 1 | 2 | Complete; Batch 1 complete | `93defe9` | 12/12 focused; 17/17 clean suite; Batch verification passed | Platform-neutral `javafx.base` config added as required; review clean | Task 3 |
| 2026-11-14 | 2 | 3 | Paused in RED; uncommitted | — | Focused suite compiles; 25 tests run, 20 expected failures against placeholders | Services not implemented; preserved safely | Resume Task 3 Step 3 |
| 2026-11-15 | 2 | 3 | Complete after resume | `b8e6e9a` | 25/25 focused; 42/42 clean full suite | Review clean | Task 4 |
| 2026-11-15 | 2 | 4 | Complete | `c5c691e` | 27/27 focused; 69/69 clean full suite | Review clean; minor projection-refresh suggestion recorded | Task 5 |
| 2026-11-15 | 2 | 5 | Complete after fix round 1; Batch 2 complete | `56e81dc`, `8d81f8a` | 28/28 focused; 98/98 clean full suite; Batch verification passed | Login/APP_OPEN metrics gap fixed and re-review passed | Task 6, pending authorization |
| 2026-11-15 | 3 | 6 | Complete | `56d2d7b` | 11/11 focused; 109/109 fresh module/full suite | Combined review clean | Task 7 |
| 2026-11-15 | 3 | 7 | Complete; Batch 3 complete | `36662a2` | 7/7 focused auth/FXML; 18/18 user module; batch test passed; graphical launch reached stable UserMain | Independent review clean; standard JavaFX/cache warnings only | Task 8, pending authorization |
| 2026-11-15 | 4 | 8–9 | Complete after review fix; Batch 4 complete | `1669241`, `b47312b`, `df1ad3e` | 98/98 shared tests and 28/28 user tests; zero failures/errors; Batch verification passed | Important review findings fixed: confirmations/cancellation, reachable modals, filters/scope/result navigation, contextual AI/message actions, row actions, inventory and regression coverage | Task 10, pending authorization |
| 2026-11-15 | 5 | 10–11 | Complete after review fix; Batch 5 complete | `a939fba`, `960ad1c`, `1cf404f` | 98/98 shared tests and 33/33 user tests; zero failures/errors; JavaFX launch stable until stopped manually | Important review findings fixed: row-scoped reactive group permissions, supplied controls/content, observable report filtering/detail and inventory alignment | Task 12, pending authorization |
| 2026-11-15 | 6 | 12–13 | Complete after review fix; Batch 6 complete | `4faa792`, `13c2eca`, `4f459d9` | 98/98 shared tests and 12/12 admin tests; zero failures/errors; JavaFX launch stable until stopped manually | 0 Critical; six Important findings fixed in one RED→GREEN pass; stale-entity feedback deferred as Minor | Task 14, pending authorization |
| 2026-11-15 | 7 | 14 | Complete after review fix; Batch 7 complete | `39511da`, `fe61636` | 98/98 shared tests and 19/19 admin tests; zero failures/errors | 0 Critical; three Important findings fixed in one RED→GREEN pass; `WR-` search prefix deferred as Minor | Task 15, pending authorization |
| 2026-11-15 | 8 | 15 | Complete; final review pending | pending commit | 150/150 clean tests; resource/controller audit passed; both graphical launches stable | Known OpenJFX cache warning; documented non-blocking limitations | Whole-project final review |

## Resume checklist

- [ ] Read the approved design and implementation plan.
- [ ] Read the latest execution-log row and batch/task statuses above.
- [ ] Inspect `git status`, relevant commits, and existing files before changing anything.
- [ ] Re-run only the last completed task/batch verification needed to establish the baseline.
- [ ] Continue from the recorded **Next task**; do not redo `[x]` tasks.
- [ ] Stop after the currently authorized batch and report commits, tests, issues, and next pending batch.
