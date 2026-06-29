package dev.rebelonion.qarecord.qarecorder.core

data class QaRecorderConfig(
    /**
     * Logs reflected window-root discovery, popup candidates, and popup touch diagnostics.
     * Keep this off by default; it is noisy and intended for recorder development only.
     */
    val debugWindowRoots: Boolean = false,

    /**
     * Observes non-Activity window roots such as dialogs, bottom sheets, dropdowns, and popups.
     * Disable this only if reflection causes compatibility problems in a host app.
     */
    val captureTransientWindowRoots: Boolean = true,

    /**
     * Attempts to infer selected Compose popup/dropdown items after the popup window disappears.
     * This depends on cached popup semantics and reflected ViewRootImpl touch coordinates.
     */
    val inferPopupSelections: Boolean = true,

    /**
     * Records the actual text entered into focused fields.
     * Set to false to emit a redacted placeholder instead of QA-entered text.
     */
    val recordLiteralTextEntry: Boolean = true,
)
