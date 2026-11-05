# List Approver Step PROMs

A **Java 8** desktop application that searches PROMs Pending Approval by the Ref No / Project ID rows in input.csv, opens every Activity/Milestone step, and exports its Approval Line to CSV.

## Download and start

1. Open the [latest release](https://github.com/kantapon-sam/List_approver_step_proms/releases/latest) and download the asset ending in **-windows.zip**.
2. Extract the whole ZIP to a writable folder. Keep `lib` beside `List_approver_step_proms.jar`.
3. Double-click `List_approver_step_proms.jar` with Java 8. No CMD launcher is needed.
4. Fill in input.csv with Ref No or Project ID, one ID per row, and save it. Click **Choose CSV** if the file is in another folder.
5. Click **Run** and sign in to Chrome, including any OTP/CAPTCHA. After sign-in, the program verifies a separate background session and closes the login browser. Each input ID is searched directly. A Ref No found on the first filtered page is processed immediately; Project IDs include all matching filtered result pages. Every step and approval page is read automatically.
6. Follow the input position and per-Ref work status in the application window. Once a Project ID search finds its matching requests, all those Refs appear as Pending, then Running, Completed or Completed with issues. The bar counts processed Refs, for example **Processed 4 / 10 Refs (40%)**. The background browser closes when the run ends; this window stays open. Click **Open CSV** for approval details. **Stop** keeps partial output and the remaining work status.

Keep one installation folder and update it in place using **Update and restart**. There is no need to extract another version folder or keep release ZIPs beside the installed application. Updates preserve your input, login, settings and reports.

The window shows work status only, with Input, Project ID, Ref No, Run status and Current step columns. Counts distinguish completed work, work with issues and remaining Refs. Approval rows, approver names and approval-status counters are not displayed. While running, the progress bar is scoped to the current input's discovered Refs; after the run it summarizes all discovered Refs. Later input totals are known after their searches, and previous inputs stay listed. Cancelled or unstarted work is marked Stopped or Not processed instead of being reported as complete. On startup, **Open CSV** links to the most recent report without loading its contents.

Approval pages are read and written incrementally. Every CSV row is flushed to the file before the next page is requested, so the complete report is not accumulated in Java memory. The window retains only compact Ref/work-state metadata and the most recent 120,000 characters of run messages. Navigation uses small page fingerprints instead of retaining copies of earlier pages. No companion log file is created.

If a connection or page read fails, **Run status** shows the error and affected input/Ref/Step. The application can continue with later items when possible and finishes as **Completed with issues**; a run-level failure shows **Run failed**. Already-written CSV rows remain available, but the report may be partial. If failure occurs before extraction starts, no CSV is created. After browser cleanup, one dialog reports a failed or incomplete run and restores the window if minimized. It does not interrupt each failed step. Successful runs and user-requested stops do not show a completion dialog. Invalid input or local settings are reported in a dialog before the run begins.

**There is no Steps column to fill in.** Enter multiple Ref No / Project ID values on separate rows. The program searches only those IDs and reads their steps from the website; it does not scan the full unfiltered request list.

## Login and local files

The Windows ZIP contains actual filenames with no user data:

| File | Initial contents | Purpose |
| --- | --- | --- |
| `Login.txt` | Blank User and Password entries | Optional sign-in prefill |
| `input.csv` | `Ref No,Project ID` header only | IDs to search in the desktop or command-line workflow |
| `proms.properties` | Empty | Optional settings; built-in defaults apply |

To enable prefill, edit `Login.txt` locally:

```text
User,
Password,
```

Enter your values after each comma, or leave both blank and sign in manually in Chrome. Only User and Password are read. There are no Search or Description fields. Password commas and spaces are preserved. The program does not automatically press Sign in.

The login browser and the background browser are separate automation sessions. Cookies and browser storage are transferred in memory and verified before the login window closes; they are not written to application configuration, reports or logs. The program closes its own browsers after completion, failure or cancellation. Your normal Chrome windows are separate. If the background session cannot be verified, the run shows an error without creating a CSV.

Real credentials, user input, settings, reports and logs are excluded from Git and release assets. Packaging uses the verified blank files in `defaults/`, never working files beside a local JAR. Keep your populated Login.txt private.

## Scope and results

The default list is `https://proms.truecorp.co.th/proms/pendingapprove`. **Only requests visible to the signed-in account in that list are included.** Completed requests or projects outside that list may be absent. This is not a complete inventory of all PROMs projects.

The application reads navigation and detail tables; it does not approve, reject or submit requests. It searches each input ID, matches IDs exactly, deduplicates reference numbers within a query, and opens each matching request's Activity/Milestone and Approval Line pages. Request pagination is limited to filtered searches: all pages for Project IDs, or fallback pages if an exact Ref No is not on the first page. Empty searches are rejected.

Reports use UTF-8 with a BOM for Excel and exactly six columns: **Ref No, Project ID, Step, Approver, Status, Approved Date**. Step identifies TK07, TK08, etc.; other step codes retain their original name. Each approval record from every step is included. Progress and diagnostic messages appear in **Run status** (or the console for command-line runs); no companion log file is created. The application opens each Ref No once and finishes all of its steps before moving to the next request.

Approved Date is preserved exactly as displayed, including blank values. Names and status text retain their original language. Text that Excel could interpret as a formula is escaped. Report names use `approval_steps_YYYYMMDD_HHMMSS.csv`, without milliseconds. CSV creation begins after the background session is ready. Existing files are never overwritten.

The log records these diagnostics. The CSV includes every approval status, including pending or waiting records. A step with an empty Approval Line is included with blank Approver, Status and Approved Date values; the application does not invent a status:

| Log status | Meaning |
| --- | --- |
| `OK` | Approval data was read successfully |
| `NO_APPROVAL_ROWS` | Approval Line exists but is empty |
| `NO_STEPS` | Activity/Milestone exists but has no steps |
| `NOT_FOUND_IN_SCOPE` | A requested CSV ID has no exact match in this list |
| `STEP_NOT_FOUND` | A legacy CSV step filter has no matching step |
| `READ_ERROR` | A request or step could not be read; see Message |
| `RUN_ERROR` / `CANCELLED` | The run stopped early; the report may be partial |

If a search has no matching requests, Run status shows NOT_FOUND_IN_SCOPE for that input row. Unexpected table headings or incomplete pagination produce a visible error rather than guessed columns. A cancelled or failed run can leave a partial CSV; check Run status and the completion message before closing the application.

Automated browser tests use a local synthetic portal to check two direct input references without request-page clicks, filtered Project ID pagination, step navigation, approval extraction and read-only behavior. Version 1.0.3 was also verified read-only with two user-provided live inputs in background mode, including approved records, waiting approvals and empty approval lines, without extraction errors. The automation browsers closed after completion while the results window stayed open. Other projects and page layouts may differ; Run status reports any unsupported table structure.

## Updates

The application checks the public GitHub release feed on startup and every 30 minutes. Click **Check for updates** to check immediately. A newer version displays **Update and restart**; finish or stop the current export before updating.

The updater verifies the downloaded ZIP's size, SHA-256 and Java 8 bytecode, then uses a separate Java 8 process to wait for shutdown, back up application files, install and restart the JAR. If installation fails, it attempts rollback and keeps recovery information under `.updates`. No CMD launcher or GitHub token is needed by users.

Updates preserve Login.txt, input.csv, proms.properties, reports and ChromeDriver. Missing local configuration files are created with blank defaults. Exact, untouched copies of the old v1.0.0 example templates are removed on startup; edited or locked files are preserved.

The canonical ZIP without `-windows` is for the updater and contains only application files, keeping compatibility with v1.0.0. New users should download **-windows.zip**, which also contains the blank real-name configuration files. Both packages contain the same application version.

## Input format and command line

The desktop and command-line workflows both read input.csv. The supplied columns are:

```csv
Ref No,Project ID
```

Enter either Ref No or Project ID on each row. Multiple IDs go on separate rows. If both are present, both must match. A Project ID expands to every matching request in the configured list. All steps are read automatically; there is no Steps column in the supplied input file. Keep IDs as text in Excel to preserve leading zeroes. Legacy three-column CSV files remain readable for compatibility.

```text
java -jar List_approver_step_proms.jar --input input.csv
java -jar List_approver_step_proms.jar --check-input --input input.csv
java -jar List_approver_step_proms.jar --check-login
java -jar List_approver_step_proms.jar --check-update
java -jar List_approver_step_proms.jar --version
```

Use `--output path/report.csv` to choose an output file or `--config path/proms.properties` for settings. The old `--all` mode is removed to prevent unfiltered scans. Exit codes: 0 complete, 1 failed, 2 unresolved records, 130 stopped. An untouched header-only input.csv needs at least one ID before running.

## Optional settings

An empty proms.properties uses these defaults. Add only the keys you need to override:

```properties
list.url=https://proms.truecorp.co.th/proms/pendingapprove
search.css=#txtSearch
timeout.seconds=45
login.seconds=300
quiet.millis=1800
max.pages=2000
```

Another PROMs list URL must expose the same headings and detail navigation; other Project/Work Order pages have not been verified. ChromeDriver is checked automatically against the local Chrome version and downloaded from the official source when needed.

## Build and release

Use JDK 8 only, including for tests and the update helper. Other Java versions are rejected.

```powershell
./scripts/Build.ps1 -JavaHome 'C:\Program Files\OpenJDK\jdk-8.0.292.10-hotspot'
./scripts/Package-Release.ps1
```

The build downloads pinned Ant/dependencies with SHA-256 verification. Offline tests cover CSV handling, exact matching, automatic extraction, partial errors, default-file preservation, ChromeDriver updates and updater handoff/rollback/restart. To include the headless local browser fixtures, add `-FixtureDriver 'path\to\chromedriver.exe'` to Build.ps1. These also verify authenticated session transfer, browser cleanup and cancellation using synthetic cookies and storage. Tests do not connect to PROMs.

For a new release, update version.properties and CHANGELOG, validate, then commit and push to main. GitHub Actions builds/tests with Java 8 and publishes the two ZIPs, update.properties and SHA256SUMS.txt. Assets are uploaded to a draft before it becomes latest. Published releases are never overwritten; bump the version for each change. Verify the remote commit, assets and live update feed before announcing availability.

Authorized maintainers can also run scripts/Publish-Release.ps1 after a clean committed build. It uses GitHub CLI or the normal Git credential helper in memory; credentials are never written to source or release files.
