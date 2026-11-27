# UI reference restoration implementation plan

**Goal:** Restore User and Admin styling and placement from `docs/UI_design/Screenshot/User` and `Admin`, preserving interactive behavior and live fixture data.

**Architecture:** Keep the existing JavaFX controllers, FXML IDs, navigation and services. Restore layout containers in FXML and fix CSS specificity. Render dynamic rows as aligned graphic cells rather than concatenated text.

**Tech stack:** Java 17, JavaFX 17, Gradle, JUnit 5.

## Constraints and review focus

- Preserve all existing actions, confirmation bars and disabled-control explanations.
- Keep reference imagery and user-provided `current` images unchanged.
- Preserve current window profiles, extra functional controls, and actual data counts.
- Verify long rows, narrow detail columns, profile scrolling, and composer placement.
- Work in the supplied checkout; no branch/commit changes are needed for these repairs.

## Tasks

- [x] Reproduce missing page insets, sidebar CSS overrides, chat rail and metric sizing with layout checks. Run existing suite as baseline.
- [x] Restore shared styling and User page cards, header hierarchy, metrics, chat columns, dialog spacing, and aligned dynamic rows.
- [x] Restore Admin branding, dashboard chart and proportional cards, directory columns, and detail-page card placement.
- [x] Run all workflow and layout tests. Render all routes to PNGs, inspect against references, correct clipping, and document verification.

Validation: all 163 tests passed with screenshot capture enabled; 30 PNGs rendered. Independent review caught a chart range refresh regression, reproduced by a failing test and fixed with an observable trend listener plus disposal cleanup. Reference and supplied current screenshots were preserved.
