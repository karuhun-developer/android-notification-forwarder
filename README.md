# Notification Interceptor / Android Notification Forwarder

An Android app that forwards notifications to HTTP endpoints based on app packages and routing rules. It includes a queue stored in Room, WorkManager scheduling, event and delivery identifiers, optional retries, and delivery history.

The listener forwards notification content to the endpoints you configure. Your receiver can process the payload and integrate it with other services.

## Demo

![Android Notification Forwarder demo](demo.gif)

## Webhook Result Example

![Example of a notification forwarded to a webhook](image.png)

## Features

- Route notifications by app package, endpoint, HTTP method, and custom headers.
- Save a delivery snapshot before sending the HTTP request: its payload, endpoint, headers, and retry settings remain fixed.
- Schedule work with network constraints and backoff, recover pending deliveries when the app opens or the listener connects, and run periodic recovery.
- Keep identifiers stable across replays and retries, claim deliveries atomically, use leases, and verify the ownership token before completing a delivery.
- Automatic retries are disabled by default and can be enabled per rule for future notifications.
- View delivery states, attempt counts, HTTP responses or errors, and manually retry FAILED deliveries. Older history is displayed separately.
- Catch up on notifications that are still active when the listener reconnects, and request listener rebinding.
- Migrate Room v1/v2 to v3 while preserving rules and history, without a destructive fallback.
- Use [GitHub Actions](.github/workflows/android-release.yml) to test, lint, build, and publish a GitHub Release when a version tag is pushed.

## Build and Install

Use JDK 21, Android SDK Platform 36.1, Build Tools 36.0.0, and the repository's Gradle wrapper. The compile SDK is 36.1, the target SDK is 36, and the minimum Android API level is 24. Targeting Java 11 bytecode does not mean JDK 11 is sufficient to build the app. Local validation used JDK 21; results and environment limitations are documented in the [reliability report](docs/reliability.md).

```bash
git clone https://github.com/karuhun-developer/android-notification-forwarder.git
cd android-notification-forwarder
bash gradlew :app:assembleDebug
bash gradlew :app:installDebug
```

Set `ANDROID_HOME` to your local SDK path or configure `sdk.dir` in `local.properties`. The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. `assembleRelease` produces an unsigned APK because a production keystore has not been configured.

## Listener and Rule Setup

1. Open the app and grant **Notification access** in Settings. Permission being granted and the listener service being connected are separate conditions; check the app's status indicators.
2. Select the package names of the apps whose notifications you want to forward.
3. Create a rule with your endpoint URL, HTTP method, and any required custom headers.
4. To send a dummy notification, add the package `com.karuhundeveloper.notification`. On Android 13+, grant POST_NOTIFICATIONS permission, then tap **Send Dummy**. This permission is for dummy notifications and does not replace Notification access.
5. Open the rule's History to check deliveries and endpoint responses. HTTP 2xx only indicates that the endpoint accepted the request.

Example custom headers for an endpoint that requires a bearer token:

```json
{ "Authorization": "Bearer <api-token>" }
```

## Request Contract

POST, PUT, and PATCH use a JSON body with the four existing fields:

```json
{
  "app_name": "Example App",
  "notification_title": "<original title>",
  "notification_text": "<original text>",
  "timestamp_ms": 1791939600000
}
```

GET sends the same fields as query parameters; DELETE sends no body. Custom headers are included with every request. Queued deliveries add these headers:

| Header | Value |
| --- | --- |
| `X-Notification-Event-Id` | SHA-256 of the installation UUID, notification key, post time, title, and text |
| `X-Delivery-Id` | UUID for one delivery to one rule |
| `X-Notification-Package` | Android package of the source app |

Internal headers are applied after custom headers so they cannot be overwritten. A unique event/rule pair prevents replays of identical notifications from creating another delivery. Changes to the title, text, or post time can create a new event.

`timestamp_ms` is the first capture time stored for the delivery. The Android post time is stored locally but is not sent. An older notification that is still active when the listener reconnects may receive a new capture timestamp; receivers should treat it as the capture time recorded by this app.

## Queue and Retries

| State | Meaning |
| --- | --- |
| QUEUED | Saved, waiting for scheduled work or network access |
| SENDING | Owned by a worker through a lease |
| RETRYING | Waiting for the next attempt |
| DELIVERED | The endpoint returned HTTP 2xx |
| FAILED | Requires inspection or a manual retry |
| CANCELLED | The rule was deleted; delivery was stopped |

Automatic retries are **disabled by default**, including for migrated rules. When enabled, network errors and HTTP 408/429/5xx responses can be retried, with up to 10 attempts per cycle. Configuration errors, redirects, and permanent HTTP 4xx errors stop retries. WorkManager uses exponential backoff starting at 30 seconds; Android scheduling may delay execution further.

The built-in client disables redirects and implicit connection retries, and prevents automatic follow-up requests for HTTP 503 responses with `Retry-After: 0`. Each attempt is governed by the delivery policy. The lease lasts 120 seconds, and the total call timeout is 45 seconds. Any injected custom client must preserve the same behavior.

An interrupted request may already have reached the receiver. Enable retries only after the receiver stores events before acknowledging them and deduplicates delivery identifiers. A manual retry requeues a FAILED delivery with the same ID and body, resets the attempt count, and preserves the endpoint and settings snapshot. Stale UI clicks do not cancel work that is already queued.

If a worker is interrupted during SENDING, an expired lease is recovered for a retry only when retries are enabled and attempts remain. Otherwise, the delivery becomes FAILED with an unknown outcome. Check the receiver before retrying manually. Deleting a rule stops its queued deliveries but cannot recall requests the server has already received.

## Data and Recovery Limits

The queue survives restarts after the Room transaction commits successfully. Process death before the commit, notifications that are never posted, notifications that disappear while the listener is disconnected, force-stops, and OEM restrictions can still cause missed captures. Catch-up only reads notifications that are still active.

The listener does not run until the device is unlocked after a reboot. Periodic WorkManager recovery has a minimum interval of 15 minutes, and its schedule is not exact. Device QA has not yet covered reboot, reconnect, Doze/OEM behavior, visual appearance, or large fonts. Exactly-once delivery is not guaranteed.

Clear history only removes older history entries. Deliveries are retained for deduplication; safe purging has not been implemented. The database and installation identity are excluded from backups, cloud restoration, and device transfer. Rules, history, and queued deliveries are not restored from backups; reinstalling the app or clearing its data removes them. Headers and tokens are stored in the app's local database.

## Tests and Releases

```bash
bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

The test suite uses Room, Robolectric, the WorkManager test API, and MockWebServer. The full suite passed on GitHub Actions for `v1.2.1`: 24 tests with no failures, errors, or skipped tests; lint reported 0 errors and 37 warnings. The [reliability report](docs/reliability.md) records hosted validation evidence and local results, including emulation limitations on an ARM host.

Tags such as `v1.0.0` or `v1.1.0-beta.1` trigger the workflow. Tests and lint must pass before APKs and checksums are published. See the [CI and release guide](docs/ci-release.md) for triggers, artifacts, signing, and instructions for running the workflow.

[Release v1.2.1](https://github.com/karuhun-developer/android-notification-forwarder/releases/tag/v1.2.1) provides a debug APK for testing, an unsigned release APK, and SHA-256 checksums. Pushing this tag successfully triggered the automated build and publication.

## License

```
MIT License — Copyright (c) 2026 karuhundeveloper

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is furnished
to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```
