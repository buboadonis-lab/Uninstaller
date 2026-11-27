# Uninstaller

A lightweight, native Android package browser for reviewing installed apps. It has no third-party runtime libraries, analytics, ads, or network code.

## What it does

- Lists installed packages with name, package ID, app type, search, and a system-app filter.
- Opens Android's app-info screen and the standard uninstall confirmation for ordinary user apps. Android's package manager performs supported app-owned data cleanup.
- On a rooted device, offers **Remove for user** for system packages. After a second confirmation, it runs `su -c "pm uninstall --user 0 <package>"`. This removes that package for the primary Android user, not from the read-only/factory system image; root access is requested only when you take this action.
- Offers local usage-based review suggestions for launchable, non-system apps with no or old usage. Suggestions need the user to grant Android's special **Usage access** permission and are advisory only. The app never removes anything automatically.
- Blocks uninstall actions for a small set of core Android/Samsung packages and the currently active home app.

## Important limits and safety

Android does not expose a general Linux-style package dependency resolver. Apps are independently installed packages, and an apparent "sub-app" may be a shared service, a feature another app relies on, or part of the firmware. Uninstaller deliberately does **not** recursively remove related packages, use `rm`, erase shared storage, or modify the system partition. The Android package manager handles only the selected package and whatever supported cleanup it owns. Review each package carefully; removing OEM software can break device features, updates, calling, or setup. A system package removed for user 0 may usually be restored with `cmd package install-existing <package>` from a rooted shell, but this is not guaranteed for every OEM build.

The app's suggestions are based on usage timestamps and install age—not a claim that an app is objectively useless. Apps with background roles may not appear as launchable candidates. Usage data stays on-device.

## Build

Requirements: JDK 17, Android SDK platform 35, and Gradle 8.9.

```sh
gradle :app:assembleDebug
gradle :app:assembleRelease
```

The release variant uses the standard Android debug signing key so the public build can be installed without a private signing secret. This is suitable for an open-source test distribution, not a production signing strategy. Builds signed with different ephemeral CI debug keys won't update one another; uninstall an older build first if Android reports a signature mismatch.

## GitHub Releases

`.github/workflows/release.yml` builds `app-release.apk` on a `v*` tag and publishes it to GitHub Releases. You can also run **Build and publish APK** with GitHub Actions' `workflow_dispatch`; that creates a prerelease tagged `v1.0.0-build.<run number>`. The workflow also uploads the APK as a 30-day Actions artifact. Review the build and test it on the target device before distributing broadly.

Minimum Android version: 6.0 (API 23). Target SDK: 34. The rooted system-removal path has not been validated against every Samsung firmware or root manager.
