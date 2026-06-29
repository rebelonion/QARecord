package dev.rebelonion.qarecord.qarecorder

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.rebelonion.qarecord.qarecorder.capture.PopupSemanticsReader
import dev.rebelonion.qarecord.qarecorder.capture.WindowRootRegistry
import dev.rebelonion.qarecord.qarecorder.model.RecordedTarget
import dev.rebelonion.qarecord.qarecorder.resolve.ClassicViewTargetResolver
import dev.rebelonion.qarecord.qarecorder.resolve.ComposeSemanticsTargetResolver
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule

internal abstract class ResolverIntegrationTestBase {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    protected fun composeTargetAtText(text: String): RecordedTarget? =
        composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.center.let { center ->
            composeRule.runOnUiThread {
                val point = rawPointForSemanticsCenter(center)
                ComposeSemanticsTargetResolver().resolveTap(
                    composeRule.activity,
                    point.x,
                    point.y,
                )
            }
        }

    protected fun composeTargetAtTag(tag: String): RecordedTarget? =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center.let { center ->
            composeRule.runOnUiThread {
                val point = rawPointForSemanticsCenter(center)
                ComposeSemanticsTargetResolver().resolveTap(
                    composeRule.activity,
                    point.x,
                    point.y,
                )
            }
        }

    protected fun composeTargetAtDescription(description: String): RecordedTarget? =
        composeRule.onNodeWithContentDescription(description, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center.let { center ->
                composeRule.runOnUiThread {
                    val point = rawPointForSemanticsCenter(center)
                    ComposeSemanticsTargetResolver().resolveTap(
                        composeRule.activity,
                        point.x,
                        point.y,
                    )
                }
            }

    protected inline fun <reified T : View> classicTargetAtFirst(): RecordedTarget? =
        composeRule.runOnUiThread {
            val view = composeRule.activity.window.decorView.findFirst(T::class.java)
            assertNotNull(view)
            val bounds = android.graphics.Rect()
            assertTrue(view!!.getGlobalVisibleRect(bounds))
            ClassicViewTargetResolver().resolveTap(
                composeRule.activity,
                bounds.exactCenterX(),
                bounds.exactCenterY(),
            )
        }

    protected fun classicTargetAt(view: View): RecordedTarget? =
        composeRule.runOnUiThread {
            val bounds = android.graphics.Rect()
            assertTrue(view.getGlobalVisibleRect(bounds))
            ClassicViewTargetResolver().resolveTap(
                composeRule.activity,
                bounds.exactCenterX(),
                bounds.exactCenterY(),
            )
        }

    protected fun composeTargetInAnyWindowRoot(label: String): RecordedTarget? =
        waitForWindowRootContaining(label).let { root ->
            composeRule.runOnUiThread {
                val candidate = PopupSemanticsReader.candidates(root)
                    .first { it.target.label == label && it.hasClick }
                ComposeSemanticsTargetResolver().resolveTapInRoot(
                    root = root,
                    rawX = candidate.bounds.exactCenterX(),
                    rawY = candidate.bounds.exactCenterY(),
                )
            }
        }

    protected inline fun <reified T : View> classicTargetInAnyWindowRoot(label: String): RecordedTarget? =
        waitForNativeWindowRootContaining(label).let { root ->
            composeRule.runOnUiThread {
                val view = root.findTextViewWithLabel(label) as? T
                assertNotNull(view)
                val location = IntArray(2)
                view!!.getLocationOnScreen(location)
                ClassicViewTargetResolver().resolveTapInRoot(
                    root = root,
                    rawX = location[0] + view.width / 2f,
                    rawY = location[1] + view.height / 2f,
                )
            }
        }

    protected fun waitForWindowRootContaining(
        label: String,
        rootPredicate: (View) -> Boolean = { true },
    ): View {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.runOnUiThread {
                WindowRootRegistry.entries().any { entry ->
                    rootPredicate(entry.view) &&
                        PopupSemanticsReader.candidates(entry.view).any { it.target.label == label }
                }
            }
        }
        return composeRule.runOnUiThread {
            WindowRootRegistry.entries().first { entry ->
                rootPredicate(entry.view) &&
                    PopupSemanticsReader.candidates(entry.view).any { it.target.label == label }
            }.view
        }
    }

    private fun waitForNativeWindowRootContaining(label: String): View {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.runOnUiThread {
                WindowRootRegistry.entries().any { entry ->
                    entry.view.containsTextViewLabel(label)
                }
            }
        }
        return composeRule.runOnUiThread {
            WindowRootRegistry.entries().first { entry ->
                entry.view.containsTextViewLabel(label)
            }.view
        }
    }

    private fun rawPointForSemanticsCenter(centerInRoot: Offset): Offset {
        val composeHost = composeRule.activity.window.decorView.findComposeHost()
        val location = IntArray(2)
        composeHost.getLocationOnScreen(location)
        return Offset(location[0] + centerInRoot.x, location[1] + centerInRoot.y)
    }
}

internal fun View.findComposeHost(): View {
    if (javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return this
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            val found = getChildAt(index).runCatching { findComposeHost() }.getOrNull()
            if (found != null) return found
        }
    }
    error("AndroidComposeView was not found")
}

internal fun <T : View> View.findFirst(type: Class<T>): T? {
    if (type.isInstance(this)) return type.cast(this)
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            val found = getChildAt(index).findFirst(type)
            if (found != null) return found
        }
    }
    return null
}

internal fun View.containsTextViewLabel(label: String): Boolean {
    if (this is TextView && isShown && text?.toString() == label) return true
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            if (getChildAt(index).containsTextViewLabel(label)) return true
        }
    }
    return false
}

internal fun View.findTextViewWithLabel(label: String): TextView? {
    if (this is TextView && isShown && text?.toString() == label) return this
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            val found = getChildAt(index).findTextViewWithLabel(label)
            if (found != null) return found
        }
    }
    return null
}
