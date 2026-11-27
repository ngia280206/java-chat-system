# Appendix B — AI Usage Notes

**Project:** RELAY — JavaFX Chat System  
**Course:** Computational Thinking (CS10014)  
**AI tools:** ChatGPT (OpenAI) and AI-assisted coding agent (exact historical product/model to be confirmed)  
**Platform:** ChatGPT web interface and the development environment recorded in the AI conversation  
**Usage period:** November 14–20, 2026  
**Exact model versions and access hours:** [Verify from original conversation history]

## B.1. Purpose and scope of AI assistance

AI assistance was used during the development of the RELAY frontend, particularly for implementation planning, JavaFX code generation and modification, debugging graphical defects, reviewing missing interface requirements, and troubleshooting build errors.

The frontend was designed using Java 17, JavaFX 17.0.2, FXML, CSS, and a Gradle multi-module project consisting of `shared-client`, `user-client`, and `admin-client`.

The approved frontend design covered 28 interface screens: 21 user screens and 7 administrator screens. The implementation used observable in-memory data and mock services to demonstrate application workflows. Real database persistence, backend networking, production authentication, and external AI integration were outside the original interactive frontend scope.

The following records identify specific instances of AI assistance and distinguish documented outcomes from subsequent changes that still require validation.

## B.2. AI assistance during frontend development

### Record 1 — Frontend architecture and implementation planning

**Date:** November 14–15, 2026  
**Access hour:** [Insert verified time]

**Original prompt:** The initial implementation prompt is not preserved in the supplied excerpts. The approved design and task documents are available as evidence of the resulting implementation specification.

**Purpose:** Organize the supplied interface designs into two interactive JavaFX desktop applications with maintainable navigation, controllers, services, and mock data.

**AI-assisted work recorded:**
- Preparation of a frontend architecture using focused service interfaces, JavaFX controllers, and observable application state.
- Organization of the implementation into 15 tasks and 8 execution batches.
- Definition of authentication, messaging, friends, groups, profile, reporting, and administrator workflows.
- Identification of UI controls requiring implementation, validation, or explicit disabling.
- Planning of automated tests, FXML smoke tests, and acceptance checks.

**Evidence:** `2026-11-14-interactive-javafx-frontends-design.md`, `2026-11-14-interactive-javafx-frontends.md`, and `2026-11-14-interactive-javafx-execution-batches.md`.

**Student contribution and validation:** [Explain which architectural decisions your group reviewed, which requirements were checked against the original design, and what changes were made independently.]

### Record 2 — Interactive frontend implementation

**Date:** November 14–15, 2026  
**Access hour:** [Insert verified time]

**Original prompt evidence:** The implementation plan and execution history record the approved tasks, but the complete original natural-language prompts for every task are not included in the supplied files.

**Purpose:** Convert the static FXML prototypes into functioning JavaFX demonstration applications.

**AI-assisted work recorded:**
- Creation and modification of JavaFX application shells, controllers, navigation, and FXML views.
- Development of in-memory authentication, chat, friendship, group, profile, and moderation services.
- Implementation of observable data updates, form validation, sorting, searching, dialogs, and confirmation interactions.
- Preparation and execution of service-level and controller-level tests.

**Recorded results:** The execution ledger documents successful completion of the eight batches. Its final November 15 entry reports 153 passing automated tests after final-review corrections.

**Evidence:** Implementation plan, batch execution ledger, and frontend source code.

**Student contribution and validation:** [State which screens and workflows your group personally exercised, how you compared the interfaces with the supplied references, and which AI-produced changes you accepted or revised.]

### Record 3 — Investigation and correction of JavaFX graphical defects

**Date:** Recorded in the exported frontend conversation; exact time to be verified.

**Original user prompts:**
- "There is some bug and stytle issue as the third panel don't look like the screen shot much, find out why."
- "Do not use work flow, is it a simple fix ?"
- "Do it then"

**Purpose:** Identify and correct visual differences between the supplied reference designs and the running JavaFX frontend.

**AI-assisted investigation identified:**
- The Profile screen had been simplified compared with the original two-card FXML layout.
- Some standard JavaFX controls used default light-theme styling, creating unreadable combinations in the dark interface.
- Outgoing and incoming messages were not distinguished correctly by alignment.
- Some message and group-member components displayed numeric identifiers instead of account names.

**AI-assisted modifications recorded:**
- Restoring the Profile layout and its main visual sections.
- Extending CSS styling for JavaFX lists, selection controls, popup cells, and buttons.
- Adjusting chat-message alignment based on the signed-in user.
- Resolving display names through account information rather than showing raw identifiers.
- Adding focused regression checks and running the automated test suite.

**Recorded results:** The exported conversation reports commit `5ca53e9`, 154 passing tests, and successful JavaFX startup after these corrections. These results apply to that recorded code revision, not automatically to later modified project packages.

**Evidence:** `# Chuyển giao diện docs vào admin client.md`, especially the visual-defect diagnosis and the final implementation response.

**Student contribution and validation:** The student supplied a visual bug report, identified a mismatch with the reference screenshots, and requested targeted corrections. [Add the result of your own post-fix visual inspection.]

### Record 4 — Version 1 requirement review and interface expansion

**Date:** November 18, 2026 and subsequent conversation  
**Access hour:** [Insert verified time]

**Original user prompts:**
- "Can you handle a 150,783 kb zip project to find certain graphic bugs and implement missing function ?"
- "How hard are these fix/implementation will be ?"
- "Could you do it?"

**Purpose:** Compare the existing frontend with the Version 1 requirement review and address missing interface components.

**AI-assisted work recorded:**

Administrator interface:
- Additional user-profile information and account-management dialogs.
- System-wide login history.
- Registration and activity statistics, filtering, and monthly charts.
- Friend-count statistics and moderation filters.
- Adjustments targeting dropdown readability and corrupted text characters.

User interface:
- Additional profile information fields.
- Online/offline indicators and online-only filtering.
- Friend search, unfriend confirmation, and friend-request filtering.
- User search and group-creation interface improvements.
- Selection of initial members in the group-creation dialog.

**Recorded deliverables:** The `RELAY_V1_UI_IMPLEMENTED.zip` package and `RELAY_V1_IMPLEMENTATION_REPORT.md`. A proposed relational database schema was also supplied as a separate design artifact.

**Validation limitations:** The implementation report records syntax parsing of 141 Java files and XML parsing of 36 FXML files, but not a successful complete Gradle build or visual runtime verification for the modified package.

**Student contribution and validation:** The student provided the requirements review and original project files, ran Gradle on a local Windows environment, and supplied compilation errors. The later build logs reported 19 unresolved-method errors. Accordingly, successful integration of all Version 1 modifications must not be claimed without further verification.

### Record 5 — Build troubleshooting and Java compatibility

**Date:** Subsequent to the Version 1 changes, within the recorded conversation period  
**Access hour:** [Insert verified time]

**Original user prompts/errors:**
- Gradle compiler output showing 19 missing-method errors in the administrator module.
- "Unsupported class file major version 71"
- "Anyway to switch to 17 with out switching env variable ?"

**Purpose:** Investigate failures preventing the modified JavaFX applications from compiling or launching.

**AI-assisted explanations and recommendations:**
- Investigating missing shared-service methods and model accessors referenced by administrator controllers.
- Distinguishing Java compilation errors from Gradle configuration-cache errors.
- Identifying a possible mismatch between the JDK running Gradle and the project's Java 17 requirements.
- Recommending explicit Gradle JDK configuration through `org.gradle.java.home`, without changing the system-wide `JAVA_HOME` variable.
- Suggesting a rebuild and retesting after correcting the environment and source dependencies.

**Recorded outcome:** Diagnostic guidance was provided. The supplied evidence does not establish that all subsequent compilation problems were resolved.

**Student contribution and validation:** The student executed the build commands, collected the diagnostic output, and requested alternative JDK configuration. [Insert the successful build result or describe the final unresolved errors, as applicable.]

## B.3. Validation and academic responsibility

The AI-generated suggestions and code modifications should be treated as implementation assistance rather than automatically accepted final results.

The available evidence distinguishes three verification levels:

1. Earlier frontend revisions were reported to have passed the automated test suite and JavaFX startup checks.
2. The later Version 1 implementation package passed static Java and FXML parsing checks but was not verified through a complete build in the AI execution environment.
3. Local compilation logs subsequently exposed unresolved integration and Java/Gradle compatibility problems.

**Additional independent verification by the student:** [Document the commands actually rerun, test counts, results, manual screen inspections, corrections made without AI, and explanations the group can give of the submitted code.]

## B.4. Evidence references

- E1 — AI Usage Guidelines, sections 2, 4, 5 and 6.
- E2 — Approved JavaFX frontend design document, November 14, 2026.
- E3 — JavaFX implementation plan and eight-batch execution history.
- E4 — Exported frontend debugging and implementation conversation.
- E5 — Version 1 interface review checklist, November 18, 2026.
- E6 — Version 1 implementation report and modified source archive.
- E7 — Shared ChatGPT conversation and current troubleshooting conversation.
- E8 — [Attach actual screenshots of AI prompts, generated outputs, local test results, and any independently verified interface changes.]

**Declaration:** AI was used for the specific implementation, review, debugging, and troubleshooting activities documented above. The final submitted work and its validation are the responsibility of the student group. This declaration should be revised to reflect precisely what the group independently completed and verified.

Sources: 
- https://chatgpt.com/share/6b006bd7-3c24-83ec-bb5f-8af44b6b9d8d
- https://chatgpt.com/share/6b006408-d3f4-83ec-87a6-63304d6c390c
- docs/superpowers/*

