# List Approver Step PROMs

A **Java 8** desktop application that reads every request visible in PROMs Pending Approval, opens every Activity/Milestone step, and exports its Approval Line to CSV.

## Download and start

1. Open the [latest release](https://github.com/kantapon-sam/List_approver_step_proms/releases/latest) and download the asset ending in **-windows.zip**.
2. Extract the whole ZIP to a writable folder. Keep `lib` beside `List_approver_step_proms.jar`.
3. Double-click `List_approver_step_proms.jar` with Java 8. No CMD launcher is needed.
4. Click **Read all approvals**. Chrome opens for sign-in.
5. Sign in, including any OTP/CAPTCHA. The application reads all request pages, then all steps and all approval pages automatically.
6. Click **Open output folder** to find the report. Progress shows the request-page count, total requests, and current request/step. **Stop** saves a clearly marked partial report.

**No Ref No, Project ID or Steps entry is required for the desktop workflow.** Multiple projects are handled automatically as their requests are read from the website.

## Login and local files

The Windows ZIP contains actual filenames with no user data:

| File | Initial contents | Purpose |
| --- | --- | --- |
| `Login.txt` | Blank User and Password entries | Optional sign-in prefill |
| `input.csv` | `Ref No,Project ID` header only | Optional command-line lookup |
| `proms.properties` | Empty | Optional settings; built-in defaults apply |

To enable prefill, edit `Login.txt` locally:

```text
User,
Password,
```

Enter your values after each comma, or leave both blank and sign in manually in Chrome. Only User and Password are read. There are no Search or Description fields. Password commas and spaces are preserved. The program does not automatically press Sign in.

Real credentials, user input, settings, reports and logs are excluded from Git and release assets. Packaging uses the verified blank files in `defaults/`, never working files beside a local JAR. Keep your populated Login.txt private.

## Scope and results

The default list is `https://proms.truecorp.co.th/proms/pendingapprove`. **Only requests visible to the signed-in account in that list are included.** Completed requests or projects outside that list may be absent. This is not a complete inventory of all PROMs projects.

The application reads navigation and detail tables; it does not approve, reject or submit requests. It clears the list search, collects all pages, deduplicates exact reference numbers, and opens each request's Activity/Milestone and Approval Line pages.

Reports use UTF-8 with a BOM for Excel, one row per approver per step. Columns include Ref No, Project ID, Project Name, Request Type, Requester, Step, Description, Approval Order, Approver, Status, Approved Date, Result, Message, Source URL and Extracted At. The leading Input columns identify the source-list row and its IDs in automatic mode, or the CSV query in command-line mode.

Approved Date is preserved exactly as displayed, including blank values. Names and other website data retain their original language. Approval Order is the displayed row order, not an inferred authorization level. Extracted At uses Asia/Bangkok. Source URL identifies the list page because details may share the same URL. Text that Excel could interpret as a formula is escaped. Existing report files are never overwritten.

| Result | Meaning |
| --- | --- |
| `OK` | Approval data was read successfully |
| `NO_APPROVAL_ROWS` | Approval Line exists but is empty |
| `NO_STEPS` | Activity/Milestone exists but has no steps |
| `NOT_FOUND_IN_SCOPE` | An optional CSV ID has no exact match in this list |
| `STEP_NOT_FOUND` | A legacy CSV step filter has no matching step |
| `READ_ERROR` | A request or step could not be read; see Message |
| `RUN_ERROR` / `CANCELLED` | The run stopped early; the report may be partial |

If the list is empty, the application logs that no requests are visible and writes a header-only report. Unexpected table headings or incomplete pagination produce an error rather than guessed columns.

Live extraction has not yet been verified against the current PROMs website. Automated browser tests use a local synthetic portal to check all-request discovery, pagination, step navigation, approval extraction and read-only behavior.

## Updates

The application checks the public GitHub release feed on startup and every 30 minutes. Click **Check for updates** to check immediately. A newer version displays **Update and restart**; finish or stop the current export before updating.

The updater verifies the downloaded ZIP's size, SHA-256 and Java 8 bytecode, then uses a separate Java 8 process to wait for shutdown, back up application files, install and restart the JAR. If installation fails, it attempts rollback and keeps recovery information under `.updates`. No CMD launcher or GitHub token is needed by users.

Updates preserve Login.txt, input.csv, proms.properties, reports and ChromeDriver. Missing local configuration files are created with blank defaults. Exact, untouched copies of the old v1.0.0 example templates are removed on startup; edited or locked files are preserved.

The canonical ZIP without `-windows` is for the updater and contains only application files, keeping compatibility with v1.0.0. New users should download **-windows.zip**, which also contains the blank real-name configuration files. Both packages contain the same application version.

## Optional command-line input

The normal desktop workflow does not read input.csv. For a restricted command-line lookup, its only required columns are:

```csv
Ref No,Project ID
```

Enter either Ref No or Project ID on each row. Multiple IDs go on separate rows. If both are present, both must match. A Project ID expands to every matching request in the configured list. All steps are read automatically; there is no Steps column in the supplied input file. Keep IDs as text in Excel to preserve leading zeroes. Legacy three-column CSV files remain readable for compatibility.

```text
java -jar List_approver_step_proms.jar --all
java -jar List_approver_step_proms.jar --input input.csv
java -jar List_approver_step_proms.jar --check-input --input input.csv
java -jar List_approver_step_proms.jar --check-login
java -jar List_approver_step_proms.jar --check-update
java -jar List_approver_step_proms.jar --version
```

Use `--output path/report.csv` to choose an output file or `--config path/proms.properties` for settings. `--all` and `--input` are mutually exclusive. Exit codes: 0 complete, 1 failed, 2 unresolved records, 130 stopped. An untouched header-only input.csv needs at least one ID before using optional CSV mode.

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

The build downloads pinned Ant/dependencies with SHA-256 verification. Offline tests cover CSV handling, exact matching, automatic extraction, partial errors, default-file preservation, ChromeDriver updates and updater handoff/rollback/restart. To include the headless local browser fixture, add `-FixtureDriver 'path\to\chromedriver.exe'` to Build.ps1. Tests do not connect to PROMs.

For a new release, update version.properties and CHANGELOG, validate, then commit and push to main. GitHub Actions builds/tests with Java 8 and publishes the two ZIPs, update.properties and SHA256SUMS.txt. Assets are uploaded to a draft before it becomes latest. Published releases are never overwritten; bump the version for each change. Verify the remote commit, assets and live update feed before announcing availability.

Authorized maintainers can also run scripts/Publish-Release.ps1 after a clean committed build. It uses GitHub CLI or the normal Git credential helper in memory; credentials are never written to source or release files.
