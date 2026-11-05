# Repository instructions

- Use JDK/Java 8 only for compilation, tests, the application and its update helper. Do not generate CMD launchers; users open the JAR.
- Version authority is `version.properties`. The first public release is 1.0.0, independent of earlier local prototype versions.
- Never commit or upload real User/Password, GitHub tokens, cookies, live Login.txt/input.csv/proms.properties, runtime output, logs, driver executables or local evidence. Only sanitized files under defaults/ may be tracked and packaged with these real filenames: blank User/Password, CSV header only, and empty settings.
- Preserve user Login.txt, input.csv, proms.properties and output during installation/update. Release packages use the allowlist in scripts/Package-Release.ps1.
- The windows ZIP includes blank real-name defaults for new users. The canonical updater ZIP excludes user files to remain compatible with v1.0.0. Never package working runtime files from dist.
- All application messages and documentation must be English. Preserve text extracted from PROMs as supplied by the website.
- Build/test with scripts/Build.ps1 using JDK 8. The updater's download, archive validation, rollback, process handoff and restart tests must pass before publishing changes to it.
- For an authorized release, update version.properties/CHANGELOG, validate, fetch first, commit only task source/tests/docs/config, and use normal fast-forward-safe pushes to main. The release workflow publishes on version changes. Never force-push or overwrite a published release.
- Verify the remote commit, the published release assets and the live update feed before claiming that a release is available to users.
