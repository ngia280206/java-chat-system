# Interactive JavaFX Frontends — Execution Batches

**Approved plan:** `docs/superpowers/plans/2026-11-14-interactive-javafx-frontends.md`

**Status legend:** `[ ]` pending · `[~]` in progress · `[x]` complete · `[!]` blocked

This checklist organizes the approved 15 tasks for subagent-driven execution. It does not change task scope, architecture, interfaces, tests, or acceptance requirements. Execute only the batch explicitly authorized by the user, complete its review and verification, then stop and report before requesting authorization for the next batch.

## Execution rules

- Use `superpowers:subagent-driven-development` within every authorized batch.
- For each task: fresh implementer → specification review → code-quality review → required fixes → task tests → record commit/result.
- Do not start the next task while the current task has unresolved review findings or failing required tests.
- At batch end, run the batch verification commands below and record exact results.
- A completed batch is a safe stopping point. Commit or otherwise preserve all changes before stopping.
- On resume, read this checklist and the approved plan, inspect recorded commits/status, and verify the current worktree. Do not recreate or reimplement `[x]` tasks.
- If prior work has no commit, inspect the listed files and test results before deciding what remains; never assume it is absent.

## Batch status

| Batch | Objective | Tasks | Status |
|---|---|---:|---|
| 1 | Establish verified domain and test foundation | 1–2 | `[x]` |
| 2 | Complete headless mock-service behavior | 3–5 | `[~]` |
| 3 | Build user runtime and authentication workflows | 6–7 | `[ ]` |
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

- [ ] Task 3: Authentication, profile, and settings services
- [ ] Task 3 specification review
- [ ] Task 3 code-quality review
- [ ] Task 4: Social and group services with permission rules
- [ ] Task 4 specification review
- [ ] Task 4 code-quality review
- [ ] Task 5: Chat, reports, moderation, metrics, and assistant services
- [ ] Task 5 specification review
- [ ] Task 5 code-quality review

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

- [ ] Task 6: User composition, navigation, shell, resources, and FXML harness
- [ ] Task 6 specification review
- [ ] Task 6 code-quality review
- [ ] Task 7: Authentication screens and controllers
- [ ] Task 7 specification review
- [ ] Task 7 code-quality review

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

- [ ] Task 8: Chat, message search, and AI workflows
- [ ] Task 8 specification review
- [ ] Task 8 code-quality review
- [ ] Task 9: Friends and relationship workflows
- [ ] Task 9 specification review
- [ ] Task 9 code-quality review

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

- [ ] Task 10: Group workflows and approved membership controls
- [ ] Task 10 specification review
- [ ] Task 10 code-quality review
- [ ] Task 11: Profile, settings, password, and reporting workflows
- [ ] Task 11 specification review
- [ ] Task 11 code-quality review

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

- [ ] Task 12: Admin composition, navigation, shell, and resources
- [ ] Task 12 specification review
- [ ] Task 12 code-quality review
- [ ] Task 13: Dashboard, users, and groups workflows
- [ ] Task 13 specification review
- [ ] Task 13 code-quality review

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

- [ ] Task 14: Admin moderation workflows
- [ ] Task 14 specification review
- [ ] Task 14 code-quality review

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

- [ ] Task 15: End-to-end acceptance, documentation, and final verification
- [ ] Task 15 specification review
- [ ] Task 15 code-quality/documentation review
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

## Resume checklist

- [ ] Read the approved design and implementation plan.
- [ ] Read the latest execution-log row and batch/task statuses above.
- [ ] Inspect `git status`, relevant commits, and existing files before changing anything.
- [ ] Re-run only the last completed task/batch verification needed to establish the baseline.
- [ ] Continue from the recorded **Next task**; do not redo `[x]` tasks.
- [ ] Stop after the currently authorized batch and report commits, tests, issues, and next pending batch.
