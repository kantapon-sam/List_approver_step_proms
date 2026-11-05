# Releases

## 1.0.3

- Open Chrome for manual sign-in, then continue extraction in a verified background browser session. Transfer authentication in memory only.
- Preserve the login browser identity and replace the new browser's logged-out cookies before verifying the background session.
- Close automation browsers when the run finishes, fails or is stopped. Keep the desktop window open with the results and log.
- Redesign the English desktop window with input/step progress, status counters, a six-column results table and a separate Run log tab.
- Add Open CSV and restore the latest saved report on startup. Limit the table preview to 1,000 rows while saving and counting the complete report.
- Export only CSV, with filenames timestamped to seconds and no millisecond suffix. Show extraction logs in the application instead of creating .csv.log files. Create the CSV after the background session is ready.
- Preserve all existing input, login, settings and reports. Update one installation folder in place; no separate version folder is needed.
- Verify authenticated background handoff, failed-session cleanup, cancellation, report summaries and existing extraction/updater behavior with Java 8 tests.
- Live read-only validation completed both user-provided inputs in background mode, including approved, waiting and empty approval lines. The automation browsers closed and the results window remained open. No credentials or live report data are included in this release.

## 1.0.2

Download **List_approver_step_proms-1.0.2-windows.zip** for a new installation, or use **Update and restart** in the existing application.

- Read the Ref No / Project ID rows from input.csv and search each ID directly. Remove the unfiltered scan of all Pending Approval pages.
- A Ref No found on the first filtered page is processed immediately without clicking request pagination. Project ID lookups include every matching filtered page.
- Read every Activity/Milestone step automatically; the supplied input still has only Ref No and Project ID columns.
- Open each Ref No once and finish all its steps before moving to the next request; close step dialogs and navigate step pages without searching the same Ref again.
- Export exactly six columns: Ref No, Project ID, Step, Approver, Status, Approved Date. Use TK07/TK08 labels for TK steps and keep empty-table notices and errors in a companion log.
- Support grouped approvers whose sequence cells span multiple table rows, as found in the live TK10 approval table.
- Include all approval statuses and preserve blank dates for unapproved records. Keep steps with empty Approval Lines as rows with blank approver/status/date fields.
- Show the selected CSV and progress for each input ID in the English desktop window.
- Preserve existing Login.txt, input.csv, proms.properties and reports during updates. Blank templates are used only for missing files and new installations.
- Verify two input references, exclusion of a similar unrelated reference, zero request-page clicks for direct matches, and complete step/approval pagination using the local browser fixture.
- Live read-only validation with user-provided inputs confirmed approved records, waiting approvals and empty approval lines without extraction errors. No credentials or live report data are included in the release.

## 1.0.1

For a new installation, download **List_approver_step_proms-1.0.1-windows.zip**, extract it and double-click the JAR with Java 8. Existing users can click the update button in the application.

- Read all approvals automatically: every visible Pending Approval request, every page and every Activity/Milestone step. No input CSV or step entry is required in the desktop window.
- Show request-page progress, total request count and current request/step. Stop keeps a clearly marked partial CSV.
- Use English throughout the application, update dialogs and documentation.
- Distribute blank files with their real names: Login.txt, input.csv and proms.properties. The optional input CSV has only Ref No and Project ID; no Steps column or sample IDs.
- Keep existing user files. Retire untouched v1.0.0 example templates when the application opens.
- Keep a separate compatible updater archive for existing v1.0.0 installations.

The Windows ZIP includes blank user files. The other ZIP is used by the built-in updater. Neither contains real credentials. Automated workflow validation uses a local browser fixture; live PROMs extraction still requires verification.

## 1.0.0

First public release, resetting the earlier local prototype version numbers.

- Java 8 desktop application launched by double-clicking the JAR.
- CSV lookup by Ref No or Project ID; selected or all Activity/Milestone steps.
- Approval Line export with Approver, Status and Approved Date.
- Login.txt uses only User and Password; no credentials in source or release packages.
- Automatic GitHub release checks at startup and every 30 minutes; visible update button.
- Verified update download, separate Java 8 installer, backup/rollback and restart.
- User login, input, settings, output and ChromeDriver remain outside update ownership.

PROMs live extraction has not yet been verified against request 26070892. Local fixture tests cover the supported DOM workflow.
