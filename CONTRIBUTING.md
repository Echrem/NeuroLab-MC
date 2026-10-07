# Contributing

Thanks for helping improve NeuroLab. This is an experimental research tool; distinguish measured behavior from hypotheses and game-world approximations in issues and pull requests.

## Before opening a pull request

1. Use JDK 21.
2. Run `./gradlew clean test build`.
   Also run `./gradlew runGameTestServer` and `python3 -m unittest discover -s tools -p 'test_*.py' -v`.
3. State Minecraft and Forge versions, and whether you tested in a live client or server.
4. Add or update tests for changes to graph parsing, simulation, telemetry, or decoding where practical.
5. Keep comments, identifiers, user-facing text, and documentation in English.
6. Preserve dataset attribution. Review `DATA_PROVENANCE.md` before changing, replacing, or redistributing the connectome asset.

## Publishing a release

For each release, update `mod_version` in `gradle.properties`, `CHANGELOG.md`, and versioned documentation, then commit and push the change to `main`. Create a matching version tag and push it. GitHub Actions checks the version, runs Java, research-tool, and Forge integration tests, and publishes only the installable JAR. Sources remain available in GitHub's source archives. Do not overwrite an existing release asset with different bytes: publish a new version.

If GitHub Actions cannot start, run the same checks locally on the exact release commit, inspect the JAR metadata, calculate its SHA-256, and upload the verified installable JAR manually. Record which checks actually ran in the release notes. A successful compile is not a client play test.

## Scope and scientific claims

Prefer small, independently testable components. Keep Minecraft entity/world access on the game thread; background simulation tasks should exchange immutable numeric snapshots. Document assumptions, parameter sources, and limitations. Do not describe behavior as biologically validated unless it has been validated against an appropriate reference.

## Bug reports

Include the mod version, Minecraft version, loader/API versions, relevant log excerpt, reproduction steps, and whether the issue occurs in single-player or on a dedicated server. Remove private server addresses, account names, and other sensitive details from logs before posting.
