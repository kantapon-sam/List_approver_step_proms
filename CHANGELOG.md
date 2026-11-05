# Releases

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
