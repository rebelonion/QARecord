package dev.rebelonion.qarecord.qarecorder

import android.widget.Button
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import dev.rebelonion.qarecord.qarecorder.model.TargetSource
import dev.rebelonion.qarecord.qarecorder.model.UiRole
import org.junit.Assert.assertEquals
import org.junit.Test

internal class InteropTargetResolverIntegrationTest : ResolverIntegrationTestBase() {
    @Test
    fun classicResolverFindsNativeButtonInsideAndroidViewInterop() {
        composeRule.setContent {
            AndroidView(
                factory = { context ->
                    Button(context).apply {
                        text = "Native Interop Action"
                    }
                },
            )
        }

        val target = classicTargetAtFirst<Button>()

        assertEquals("Native Interop Action", target?.label)
        assertEquals(UiRole.Button, target?.role)
        assertEquals(TargetSource.AndroidView, target?.source)
    }

    @Test
    fun composeResolverFindsComposeButtonInsideClassicViewHierarchy() {
        composeRule.runOnUiThread {
            composeRule.activity.setContentView(
                LinearLayout(composeRule.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(ComposeView(context).apply {
                        setContent {
                            MaterialButton(onClick = {}) {
                                Text("Compose Interop Action")
                            }
                        }
                    })
                },
            )
        }
        composeRule.waitForIdle()

        val target = composeTargetAtText("Compose Interop Action")

        assertEquals("Compose Interop Action", target?.label)
        assertEquals(UiRole.Button, target?.role)
        assertEquals(TargetSource.ComposeSemantics, target?.source)
    }

    @Test
    fun composeResolverFindsComposeButtonNestedInsideAndroidViewOnComposeScreen() {
        composeRule.setContent {
            Column {
                Text("Classic Android Views")
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { context ->
                        LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(ComposeView(context).apply {
                                setContent {
                                    MaterialButton(
                                        onClick = {},
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Compose Inside Classic")
                                    }
                                }
                            })
                        }
                    },
                )
            }
        }
        composeRule.waitForIdle()

        val target = composeTargetAtText("Compose Inside Classic")

        assertEquals("Compose Inside Classic", target?.label)
        assertEquals(UiRole.Button, target?.role)
        assertEquals(TargetSource.ComposeSemantics, target?.source)
    }

    @Test
    fun classicResolverFindsNativeButtonInComposeViewAndroidViewComposeViewAndroidViewChain() {
        composeRule.setContent {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        contentDescription = "Misleading native container"
                        addView(ComposeView(context).apply {
                            setContent {
                                AndroidView(
                                    modifier = Modifier.fillMaxWidth(),
                                    factory = { nestedContext ->
                                        Button(nestedContext).apply {
                                            text = "Deep Native Action"
                                        }
                                    },
                                )
                            }
                        })
                    }
                },
            )
        }
        composeRule.waitForIdle()

        val target = classicTargetAtFirst<Button>()

        assertEquals("Deep Native Action", target?.label)
        assertEquals(UiRole.Button, target?.role)
        assertEquals(TargetSource.AndroidView, target?.source)
    }

    @Test
    fun composeResolverFindsComposeButtonInViewComposeViewAndroidViewComposeViewChain() {
        composeRule.runOnUiThread {
            composeRule.activity.setContentView(
                LinearLayout(composeRule.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    contentDescription = "Misleading root view"
                    addView(ComposeView(context).apply {
                        setContent {
                            AndroidView(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { nestedContext ->
                                    LinearLayout(nestedContext).apply {
                                        orientation = LinearLayout.VERTICAL
                                        contentDescription = "Misleading nested view"
                                        addView(ComposeView(context).apply {
                                            setContent {
                                                MaterialButton(
                                                    onClick = {},
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    Text("Deep Compose Action")
                                                }
                                            }
                                        })
                                    }
                                },
                            )
                        }
                    })
                },
            )
        }
        composeRule.waitForIdle()

        val target = composeTargetAtText("Deep Compose Action")

        assertEquals("Deep Compose Action", target?.label)
        assertEquals(UiRole.Button, target?.role)
        assertEquals(TargetSource.ComposeSemantics, target?.source)
    }
}
