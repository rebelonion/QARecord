package dev.rebelonion.qarecord.qarecorder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.rebelonion.qarecord.qarecorder.model.TargetSource
import dev.rebelonion.qarecord.qarecorder.model.UiRole
import dev.rebelonion.qarecord.qarecorder.resolve.ComposeSemanticsTargetResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class ComposeTargetResolverIntegrationTest : ResolverIntegrationTestBase() {
    @Test
    fun composeResolverFindsMaterialButtonByVisibleText() {
        composeRule.setContent {
            MaterialButton(onClick = {}) {
                Text("Begin Order")
            }
        }

        val target = composeTargetAtText("Begin Order")

        assertEquals("Begin Order", target?.label)
        assertEquals(UiRole.Button, target?.role)
        assertEquals(TargetSource.ComposeSemantics, target?.source)
    }

    @Test
    fun composeResolverFindsClickableContainerByContentDescription() {
        composeRule.setContent {
            Text(
                text = "",
                modifier = Modifier
                    .testTag("icon-only-action")
                    .clickable(onClick = {})
                    .semantics {
                        contentDescription = "Search orders"
                        role = Role.Button
                    }
                    .padding(48.dp),
            )
        }

        val target = composeTargetAtTag("icon-only-action")

        assertEquals("Search orders", target?.label)
        assertEquals(UiRole.Button, target?.role)
        assertEquals(TargetSource.ComposeSemantics, target?.source)
    }

    @Test
    fun composeResolverCombinesMultipleChildLabelsForClickableContainer() {
        composeRule.setContent {
            Row(
                modifier = Modifier
                    .testTag("plan-card")
                    .clickable(onClick = {})
                    .padding(24.dp),
            ) {
                Text("Pro plan")
                Text("$29")
            }
        }

        val target = composeTargetAtTag("plan-card")

        assertEquals("Pro plan $29", target?.label)
        assertEquals(UiRole.Button, target?.role)
    }

    @Test
    fun composeResolverUsesClearAndSetSemanticsLabel() {
        composeRule.setContent {
            Column(
                modifier = Modifier
                    .testTag("summary-card")
                    .clickable(onClick = {})
                    .clearAndSetSemantics {
                        contentDescription = "Order total summary"
                        role = Role.Button
                    }
                    .padding(24.dp),
            ) {
                Text("Total")
                Text("$42")
            }
        }

        val target = composeTargetAtTag("summary-card")

        assertEquals("Order total summary", target?.label)
        assertEquals(UiRole.Button, target?.role)
    }

    @Test
    fun composeResolverIgnoresPassiveContainerWithManyChildLabels() {
        composeRule.setContent {
            Column(
                modifier = Modifier
                    .testTag("passive-section")
                    .padding(24.dp),
            ) {
                Text("Compose controls")
                Text("Address")
                Text("Delivery instructions")
                Text("Begin Order")
                Text("Continue")
            }
        }

        val target = composeTargetAtTag("passive-section")

        assertNull(target)
    }

    @Test
    fun composeResolverReportsStatefulCheckboxTargets() {
        composeRule.setContent {
            var checked by remember { mutableStateOf(true) }
            Checkbox(
                checked = checked,
                onCheckedChange = { checked = it },
                modifier = Modifier.semantics { contentDescription = "Contactless delivery" },
            )
        }

        composeRule.waitForIdle()
        val targets = composeRule.runOnUiThread {
            ComposeSemanticsTargetResolver()
                .resolveStatefulTargetsInRoot(composeRule.activity.window.decorView)
        }

        val checkbox = targets.singleOrNull { it.label == "Contactless delivery" }
        assertNotNull(checkbox)
        assertEquals(UiRole.Checkbox, checkbox?.role)
        assertEquals(true, checkbox?.checked)
    }

    @Test
    fun composeResolverReportsSwitchState() {
        composeRule.setContent {
            MaterialSwitch(
                checked = true,
                onCheckedChange = {},
                modifier = Modifier.semantics { contentDescription = "Leave at door" },
            )
        }

        val target = composeTargetAtDescription("Leave at door")

        assertEquals("Leave at door", target?.label)
        assertEquals(UiRole.Switch, target?.role)
        assertEquals(true, target?.checked)
    }

    @Test
    fun composeResolverReportsRadioSelection() {
        composeRule.setContent {
            RadioButton(
                selected = true,
                onClick = {},
                modifier = Modifier.semantics { contentDescription = "ASAP" },
            )
        }

        val target = composeTargetAtDescription("ASAP")

        assertEquals("ASAP", target?.label)
        assertEquals(UiRole.RadioButton, target?.role)
        assertEquals(true, target?.checked)
    }

    @Test
    fun composeResolverReportsSliderValueAndRange() {
        composeRule.setContent {
            Slider(
                value = 18f,
                onValueChange = {},
                valueRange = 0f..30f,
                modifier = Modifier
                    .width(240.dp)
                    .semantics { contentDescription = "Tip percentage" },
            )
        }

        val target = composeTargetAtDescription("Tip percentage")

        assertEquals("Tip percentage", target?.label)
        assertEquals(UiRole.Slider, target?.role)
        assertEquals(18f, target?.value?.current)
        assertEquals(0f, target?.value?.min)
        assertEquals(30f, target?.value?.max)
    }

    @Test
    fun composeResolverUsesTestTagAsFallbackLabel() {
        composeRule.setContent {
            Text(
                text = "",
                modifier = Modifier
                    .testTag("unlabeled-compose-action")
                    .clickable(onClick = {})
                    .size(96.dp),
            )
        }

        val target = composeTargetAtTag("unlabeled-compose-action")

        assertEquals("unlabeled-compose-action", target?.label)
        assertEquals(UiRole.Button, target?.role)
    }

    @Test
    fun composeResolverReportsFocusedTextFieldSnapshot() {
        composeRule.setContent {
            var text by remember { mutableStateOf("123 Main") }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Address") },
                modifier = Modifier.testTag("address-field"),
            )
        }
        composeRule.onNodeWithTag("address-field").performClick()

        val snapshot = composeRule.runOnUiThread {
            ComposeSemanticsTargetResolver().resolveFocusedInput(composeRule.activity)
        }

        assertEquals("Address", snapshot?.target?.label)
        assertEquals(UiRole.TextField, snapshot?.target?.role)
        assertEquals("123 Main", snapshot?.text)
    }
}
