package dev.rebelonion.qarecord.qarecorder

import android.view.View
import android.widget.Button
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import dev.rebelonion.qarecord.qarecorder.capture.PopupSemanticsReader
import dev.rebelonion.qarecord.qarecorder.model.TargetSource
import dev.rebelonion.qarecord.qarecorder.model.UiRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class TransientWindowTargetResolverIntegrationTest : ResolverIntegrationTestBase() {
    @Test
    fun composeAlertDialogActionResolvesFromTransientWindowRoot() {
        composeRule.setContent {
            MaterialAlertDialog(
                onDismissRequest = {},
                title = { Text("Confirm delivery") },
                text = { Text("Dialog body") },
                confirmButton = {
                    TextButton(onClick = {}) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {}) {
                        Text("Cancel")
                    }
                },
            )
        }

        val target = composeTargetInAnyWindowRoot("Confirm")

        assertEquals("Confirm", target?.label)
        assertEquals(UiRole.Button, target?.role)
        assertEquals(TargetSource.ComposeSemantics, target?.source)
    }

    @Test
    fun composeDropdownMenuItemsAreVisibleInPopupSemantics() {
        composeRule.setContent {
            DropdownMenu(
                expanded = true,
                onDismissRequest = {},
            ) {
                DropdownMenuItem(
                    text = { Text("Standard delivery") },
                    onClick = {},
                )
                DropdownMenuItem(
                    text = { Text("Priority delivery") },
                    onClick = {},
                )
            }
        }

        val popupRoot = waitForWindowRootContaining("Priority delivery") { root: View ->
            root.isComposePopupLayout()
        }
        val candidates = composeRule.runOnUiThread {
            PopupSemanticsReader.candidates(popupRoot)
        }

        val priority = candidates.singleOrNull { it.target.label == "Priority delivery" }
        assertNotNull(priority)
        assertTrue(priority?.hasClick == true)
        assertEquals(UiRole.MenuItem, priority?.target?.role)
        assertEquals(TargetSource.ComposeSemantics, priority?.target?.source)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun composeModalBottomSheetActionResolvesFromWindowRoots() {
        composeRule.setContent {
            ModalBottomSheet(
                onDismissRequest = {},
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                MaterialButton(onClick = {}) {
                    Text("Choose courier")
                }
            }
        }

        val target = composeTargetInAnyWindowRoot("Choose courier")

        assertEquals("Choose courier", target?.label)
        assertEquals(UiRole.Button, target?.role)
        assertEquals(TargetSource.ComposeSemantics, target?.source)
    }

    @Test
    fun platformAlertDialogButtonResolvesFromTransientWindowRoot() {
        var dialog: android.app.AlertDialog? = null
        composeRule.runOnUiThread {
            dialog = android.app.AlertDialog.Builder(composeRule.activity)
                .setTitle("Delete order")
                .setMessage("Use platform dialog")
                .setPositiveButton("Delete", null)
                .setNegativeButton("Cancel", null)
                .show()
        }

        try {
            val target = classicTargetInAnyWindowRoot<Button>("Delete")

            assertEquals("Delete", target?.label)
            assertEquals(UiRole.Button, target?.role)
            assertEquals(TargetSource.AndroidView, target?.source)
        } finally {
            composeRule.runOnUiThread {
                dialog?.dismiss()
            }
        }
    }
}

private fun View.isComposePopupLayout(): Boolean {
    return javaClass.name == "androidx.compose.ui.window.PopupLayout"
}
