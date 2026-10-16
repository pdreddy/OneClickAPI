# OneClickAPI — Release & Versioning

OneClickAPI is a free plugin, so no `<product-descriptor>` is used and no license check runs.

1. Bump `version` in `build.gradle.kts` and `src/main/resources/META-INF/plugin.xml` (use the same value, e.g. `1.0.1`). The Marketplace rejects a version that was already uploaded.
2. Commit, then push a tag: `git tag v1.0.1 && git push origin v1.0.1`.
3. The GitHub Actions workflow builds, signs and publishes the plugin (needs the secrets `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`).
