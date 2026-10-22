# Blue-white redesign — design QA

final result: passed

Date: 2026-09-26

## Visual target and scope

The user selected the first displayed blue-white concept and authorized further refinement. Eight existing pages inherit the consolidated design: login, overview, tasks, contributions, contribution form, contribution detail, teacher review, personal report. Existing API behavior and role restrictions are retained.

- Original selected concept: D:/codex/generated_images/01a0ccd0-6855-7a82-b4eb-2b0665c7f668/exec-6c765a26-b184-4412-b402-18438924a6f8.png
- Refined source visual truth: D:/codex/generated_images/01a0ccd0-6855-7a82-b4eb-2b0665c7f668/exec-9df5a3e2-02d3-4a4a-a284-d41e29f080ac.png
- Implementation: http://127.0.0.1:18080/
- Screenshot: D:/codex/visualizations/2026/09/23/01a0ccd0-6855-7a82-b4eb-2b0665c7f668/blue-final-overview.png
- Full-view combined comparison: D:/codex/visualizations/2026/09/23/01a0ccd0-6855-7a82-b4eb-2b0665c7f668/blue-final-comparison.png
- Focused combined project comparison: D:/codex/visualizations/2026/09/23/01a0ccd0-6855-7a82-b4eb-2b0665c7f668/blue-final-comparison-detail.png
- Source and implementation dimensions: both 1487 x 1058 pixels; CSS viewport 1487 x 1058, deviceScaleFactor 1. No density resampling in the comparison artifact. Chat may scale the displayed comparison down.
- State: student Zhang San overview, one project, three tasks, zero pending, one approved contribution. Current date matches the mock. Dates and content are supplied by the real application.

## Comparison history

1. First rendered comparison found P2 undersized secondary text/navigation, a short statistics strip and a small project photograph relative to the selected visual. Increased navigation to 17 px, body to 16 px, dashboard title to 34 px, project title to 26 px, statistics labels to 16 px, statistics values to 36 px. Set strip minimum height to 132 px and project photo to 230 x 180 px. Re-captured after the fixes.
2. Second full comparison and final focused comparison show the same blue-white hierarchy, continuous statistics strip, project-left/workflow-right composition, task chips and recent contribution table. No outstanding P0/P1/P2 usability or responsive defects were observed at tested sizes. The final artifacts above document the post-fix state.

## Required fidelity surfaces

- Typography: real Microsoft YaHei / MicrosoftYaHei-Bold verified through Chromium platform-font inspection. Core body 15–16 px, navigation 17 px; minor metadata 13–14 px. Deliberately uses available native Chinese sans-serif instead of attempting to reproduce rasterized generated letterforms. Clear hierarchy and no global scaling.
- Layout rhythm: white sidebar 224 px, header 64 px, main padding 32 px, maximum content width 1360 px. Dashboard columns 2.15:1, with a 300 px minimum workflow column. Cards use 12 px radii and faint shadows. Wide displays constrain text width; tablet and mobile stack major regions.
- Colors: cobalt #345AE8, cool page #F5F7FB, white surfaces, navy text #182238, borders #E5E9F0. Green/orange are reserved for semantic states. Login retains the user-requested outer card and soft shadow.
- Assets: retained the actual existing campus photograph and existing product book/navigation icons. No replacement raster assets were needed. The real photo perspective and outlined book mark differ slightly from ImageGen; these are intentional source-asset reuse, not missing placeholders. New decorative mock avatars were not introduced.
- Copy: authentic project, contribution and member content is retained. The new student overview action opens the actual contribution form; the teacher action still opens project creation. Existing breadcrumb context and demo disclaimer remain. The generated mock's extra member chip is omitted because membership is already displayed in project facts.

## Functional and responsive checks

Browser plugin unavailable in the session; verification used existing Playwright with Microsoft Edge headless. No dependencies were installed.

- Vite 5173 and Java 18080 serve the revised UI.
- Login at 1440, 768, 390: outer frame, soft shadow, no document overflow, demo account filling and successful login.
- Overview at 1487, 1920, 1024, 390: no document overflow, correct project and workflow layout, working submission entry.
- Other routes checked across student/teacher roles at 1366, 768, 390 (27 role/route/viewport states). Unique selected navigation item, correct breadcrumbs, no loading/error overlay or app console errors.
- Project and contribution filters, contribution detail navigation, task-create modal open/cancel, project modal open/Escape, CSV export, mobile navigation and print CSS passed.
- Contribution form: switch link/file/existing-material modes; add, duplicate and remove an existing evidence reference in local form state. No contribution was submitted.
- Review empty state checked against real server data. Populated review checked using a browser-only intercepted fixture based on an existing contribution: radio choices, approval/return enable conditions, comments and desktop/mobile layout. No review was submitted and no database records were changed.
- Build: npm frontend build and Maven package succeeded. Backend tests were skipped because this change concerns presentation; no backend code was modified.

## Remaining limits / P3 polish

This is an implemented refinement, not a pixel-identical raster trace. Existing icons, real photo crop and native fonts have minor visual differences from the generated concept. Data tables scroll within their container on narrow screens so all columns remain accessible. Verification covers Edge; Safari and Firefox were not exercised. Actual upload/submission/review writes were not re-run during this design task.

## Implementation checklist

- [x] Consolidate accumulated CSS and apply the shared system to all existing screens.
- [x] Refine overview composition and add the real student action.
- [x] Preserve login frame/shadow, responsive forms, status states and print layout.
- [x] Compare full and focused reference/implementation images, correct identified typography and proportion issues.
- [x] Build and verify the Java-served final version.
