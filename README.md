# QA Recorder

QA Recorder is an Android library for turning a tester's manual app session into readable QA steps. It is intended for debug and QA builds where someone explores a real app, records what they do, then copies Markdown steps into a ticket, bug report, or test note.

Setup is intentionally simple: initialize it once from your `Application`, and QA Recorder observes Compose and classic View components without any manual wrapping.

It is not a replay engine and does not try to generate automation scripts. The output is meant to be human-readable repro or exploratory-test documentation that needs little cleanup.

## What It Records

QA Recorder observes interactions across common Android UI surfaces:

* Jetpack Compose controls through semantics.
* Classic Android Views.
* Compose/View interop in both directions.
* Instrumented WebViews through DOM events.
* Dialogs, bottom sheets, dropdowns, popups, and other transient window roots when reflection is available.

Current step types include:

* Tap
* Long press
* Toggle on/off
* Radio and menu selection
* Slider/value changes
* Text entry
* Vertical scroll direction
* Back presses and transient-window dismissal

The recorder tries to keep steps action-specific. For example, a switch should become `Turn on "Leave at door"` instead of a generic tap, and a slider should become `Set "Tip percentage" to 18%` when the final value is available.

## Output

Recorded sessions are copied as Markdown:

```text
## Steps

1. Tap "Begin Order"
2. Enter "123 Test St" into "Address"
3. Turn on "Leave at door"
4. Select "ASAP"
5. Set "Tip percentage" to 18%
6. Scroll down
7. Press Back
```

Each recorded target can include a label, role, source, bounds, checked state, or numeric value depending on what the platform exposes. Labels come from visible text, hints, content descriptions, Compose semantics, DOM labels, placeholders, selected options, or fallback identifiers.

## In-App Workflow

When installed, QA Recorder adds a small floating overlay to resumed activities. The overlay can be dragged and expanded or collapsed.

Overlay actions:

* `Start` begins a fresh recording session.
* `Stop` ends recording while keeping the captured steps.
* `Copy` places the Markdown output on the clipboard.
* `Clear` removes the current session.

Touches on the recorder overlay are ignored so the controls do not pollute the captured steps.

## Setup

Add JitPack to `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Then add the dependency:

```kotlin
implementation("com.github.rebelonion:QARecord:Tag")
```

Replace `Tag` with a release tag.

Install it from `Application.onCreate()` and gate it out of release builds:

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.QA_RECORDER_ENABLED) {
            QaStepRecorder.install(this)
        }
    }
}
```

The sample app does this with a `QA_RECORDER_ENABLED` build config flag that is `true` for debug and `false` for release.

Text entry records literal text by default. To redact entered text:

```kotlin
QaStepRecorder.install(
    app = this,
    config = QaRecorderConfig(recordLiteralTextEntry = false),
)
```

## WebViews

Classic View fallback can identify a WebView as a touched View, but DOM-level labels and form values require instrumentation before content loads:

```kotlin
QaStepRecorder.instrument(webView, existingWebViewClient)
```

The WebView bridge records common DOM `click`, `change`, `input`, and `focusin` events. It can infer labels from `aria-labelledby`, `aria-label`, associated labels, wrapping labels, visible text, placeholders, and selected option text.

If the WebView already has a `WebViewClient`, pass it to `instrument`; the recorder delegates to it after injecting its bridge.

WebView support has a few practical limits:

* DOM-level recording only works for WebViews that are explicitly instrumented.
* Instrumentation should happen before loading content; setting a different `WebViewClient` afterward can bypass the recorder injection.
* Pages that block JavaScript or bridge injection will fall back to less specific View-level recording.
* Heavily custom DOM controls are only as readable as their accessible labels, visible text, or form metadata.
* Single-page apps are supported best when their controls dispatch normal DOM events; custom event systems may need extra validation.

## Supported Surfaces

The sample app includes Compose controls, embedded native Views, clickable modifier cases, a dialog, a bottom sheet, a dropdown menu, and an instrumented WebView.

Automated coverage currently exercises:

* Classic View buttons, text, image buttons, edit fields, checkboxes, radio buttons, switches, seek bars, spinners, adapter rows, and overlapping targets.
* Compose buttons, clickable containers, content descriptions, combined child labels, `clearAndSetSemantics`, passive containers, checkbox/switch state, radio buttons, sliders, test-tag fallback labels, and focused text fields.
* Native controls inside `AndroidView`.
* Compose controls inside classic View hierarchies.
* Nested View/Compose interop chains.
* Resolver selection for smaller meaningful targets over larger host containers.
* Compose and platform dialogs, Compose dropdown popup semantics, and Compose modal bottom sheets.
* WebView DOM mapping and real instrumented WebView target resolution.

## Behavior Notes

QA Recorder suppresses obvious scroll and drag gestures so they do not become false taps. Vertical scrolls are recorded as direction-only steps.

For Compose stateful controls, the recorder captures state after touch dispatch so toggle and slider output reflects the final UI state. It also corrects some edge taps on large switch and checkbox touch targets by comparing state before and after the gesture.

Transient windows are observed through reflected window-root access. If reflection fails on a device or OS version, the recorder should degrade without crashing the host app, but some popup/dialog detail may be unavailable.

## License

MIT
