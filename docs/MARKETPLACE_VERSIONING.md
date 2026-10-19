# OneClickAPI — Marketplace Versioning Rules

This document should be used before every new release to ensure that the version is updated correctly in `plugin.xml`. Since the plugin is **paid**, the `<product-descriptor>` block is active, and JetBrains Marketplace performs strict validation on it.

## Main Rule

The **last digit** of `release-version` represents the MINOR version. All preceding digits represent the MAJOR version. The `<version>` value must start with the exact same MAJOR.MINOR combination.

```text
release-version = "20260917"
                     └───┬──┘└┘
                      MAJOR  MINOR (last digit)

version = "2026091.7.<PATCH>"
            └───┬──┘ └┬┘
             MAJOR   MINOR (must match the last digit of release-version)
```

## Two Types of Updates

### 1. Regular Fix / Patch Release

This is the most common scenario. Do **not** change `release-date` or `release-version`; simply increment the PATCH number at the end of `version`.

```xml
<version>2026091.7.1</version>
...
<product-descriptor code="PAUTOAPIGENERAT" release-date="20260917" release-version="20260917"/>
```

Then:

```xml
<version>2026091.7.2</version>
...
<product-descriptor code="PAUTOAPIGENERAT" release-date="20260917" release-version="20260917"/>
```

### 2. New Major Release

When you need to change `release-version`, remember that it can **never be lower than the previous successful release**. Marketplace will reject it otherwise.

Since our versioning scheme is date-based, simply use today's date in `YYYYMMDD` format. This automatically guarantees a higher value.

```text
Today's date: 2026-09-18

release-date    = "20260918"
release-version = "20260918"
```

Then build the `version` value by inserting a dot before the last digit:

```xml
<version>2026091.8.1</version>
...
<product-descriptor code="PAUTOAPIGENERAT" release-date="20260918" release-version="20260918"/>
```

The PATCH number can start from `1` again because this is the first release of the new MINOR version.

## Quick Calculation Formula

1. Take today's date in `YYYYMMDD` format.
2. Use it for both `release-date` and `release-version`.
3. For `version`, insert a dot before the last digit of the date. This becomes the MAJOR.MINOR portion.
4. Append `.1` (or the next PATCH number).

| release-version | version prefix |
| --------------- | -------------- |
| `20260917`      | `2026091.7`    |
| `20260918`      | `2026091.8`    |
| `20261005`      | `2026100.5`    |
| `20270102`      | `2027010.2`    |

## Pre-Upload Checklist

* [ ] `release-version` is greater than the previous successful release (never decrease it).
* [ ] The beginning of `version` exactly matches the `release-version` value with a dot inserted before the last digit.
* [ ] `release-date` and `release-version` are identical (always true in our scheme).
* [ ] `until-build` in `idea-version` covers current IntelliJ versions (e.g. `262.*`). Review it periodically when JetBrains releases a new major build.
* [ ] The `version` value in `build.gradle.kts` is synchronized with the value in `plugin.xml`. Ideally, manage it from a single source using Gradle's `pluginConfiguration.version` DSL rather than maintaining it manually in multiple places.

## Why Does This Rule Exist?

The `<product-descriptor>` block is used only for **paid/freemium plugins** and integrates with JetBrains' licensing and payment system.

The `release-version` effectively defines the version boundary used by the licensing system to determine which plugin versions a purchased license is valid for. Because of this, Marketplace does not allow the value to decrease, as doing so could break license validation and entitlement checks.

If the plugin is ever converted back to a completely **free** plugin, remove (or comment out) the entire `<product-descriptor>` block. In that case, none of the `release-version` restrictions apply, and `version` can follow any format you choose.
