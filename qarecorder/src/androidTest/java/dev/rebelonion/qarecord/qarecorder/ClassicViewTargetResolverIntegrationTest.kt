package dev.rebelonion.qarecord.qarecorder

import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton as NativeRadioButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import dev.rebelonion.qarecord.qarecorder.model.TargetSource
import dev.rebelonion.qarecord.qarecorder.model.UiRole
import dev.rebelonion.qarecord.qarecorder.resolve.ClassicViewTargetResolver
import org.junit.Assert.assertEquals
import org.junit.Test

internal class ClassicViewTargetResolverIntegrationTest : ResolverIntegrationTestBase() {
    @Test
    fun classicResolverFindsNativeButtonByText() {
        composeRule.runOnUiThread {
            composeRule.activity.setContentView(
                LinearLayout(composeRule.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Button(context).apply {
                        text = "Apply Promo"
                    })
                },
            )
        }

        val target = classicTargetAtFirst<Button>()

        assertEquals("Apply Promo", target?.label)
        assertEquals(UiRole.Button, target?.role)
        assertEquals(TargetSource.AndroidView, target?.source)
    }

    @Test
    fun classicResolverReportsFocusedEditTextSnapshot() {
        composeRule.runOnUiThread {
            composeRule.activity.setContentView(
                LinearLayout(composeRule.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(EditText(context).apply {
                        hint = "Promo code"
                        setText("SAVE10")
                        requestFocus()
                    })
                },
            )
        }

        val snapshot = composeRule.runOnUiThread {
            ClassicViewTargetResolver().resolveFocusedInput(composeRule.activity)
        }

        assertEquals("Promo code", snapshot?.target?.label)
        assertEquals(UiRole.TextField, snapshot?.target?.role)
        assertEquals("SAVE10", snapshot?.text)
    }

    @Test
    fun classicResolverReportsNativeCheckboxState() {
        composeRule.runOnUiThread {
            composeRule.activity.setContentView(
                LinearLayout(composeRule.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(CheckBox(context).apply {
                        text = "Save address"
                        isChecked = true
                    })
                },
            )
        }

        val target = classicTargetAtFirst<CheckBox>()

        assertEquals("Save address", target?.label)
        assertEquals(UiRole.Checkbox, target?.role)
        assertEquals(true, target?.checked)
    }

    @Test
    fun classicResolverFindsClickableTextViewByText() {
        composeRule.runOnUiThread {
            composeRule.activity.setContentView(
                LinearLayout(composeRule.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = "Terms and conditions"
                        isClickable = true
                    })
                },
            )
        }

        val target = classicTargetAtFirst<TextView>()

        assertEquals("Terms and conditions", target?.label)
        assertEquals(UiRole.Button, target?.role)
    }

    @Test
    fun classicResolverFindsImageButtonByContentDescription() {
        composeRule.runOnUiThread {
            composeRule.activity.setContentView(
                LinearLayout(composeRule.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(ImageButton(context).apply {
                        contentDescription = "Open camera"
                        layoutParams = LinearLayout.LayoutParams(96, 96)
                    })
                },
            )
        }

        val target = classicTargetAtFirst<ImageButton>()

        assertEquals("Open camera", target?.label)
        assertEquals(UiRole.Image, target?.role)
    }

    @Test
    fun classicResolverReportsNativeRadioButtonState() {
        composeRule.runOnUiThread {
            composeRule.activity.setContentView(
                LinearLayout(composeRule.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(NativeRadioButton(context).apply {
                        text = "ASAP"
                        isChecked = true
                    })
                },
            )
        }

        val target = classicTargetAtFirst<NativeRadioButton>()

        assertEquals("ASAP", target?.label)
        assertEquals(UiRole.RadioButton, target?.role)
        assertEquals(true, target?.checked)
    }

    @Test
    fun classicResolverReportsNativeSwitchState() {
        composeRule.runOnUiThread {
            composeRule.activity.setContentView(
                LinearLayout(composeRule.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Switch(context).apply {
                        text = "Contactless delivery"
                        isChecked = false
                    })
                },
            )
        }

        val target = classicTargetAtFirst<Switch>()

        assertEquals("Contactless delivery", target?.label)
        assertEquals(UiRole.Switch, target?.role)
        assertEquals(false, target?.checked)
    }

    @Test
    fun classicResolverReportsSeekBarValueAndRange() {
        composeRule.runOnUiThread {
            composeRule.activity.setContentView(
                LinearLayout(composeRule.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(SeekBar(context).apply {
                        contentDescription = "Tip percentage"
                        min = 0
                        max = 30
                        progress = 18
                    })
                },
            )
        }

        val target = classicTargetAtFirst<SeekBar>()

        assertEquals("Tip percentage", target?.label)
        assertEquals(UiRole.Slider, target?.role)
        assertEquals(18f, target?.value?.current)
        assertEquals(0f, target?.value?.min)
        assertEquals(30f, target?.value?.max)
    }

    @Test
    fun classicResolverUsesSpinnerSelectedItemAsFallbackLabel() {
        composeRule.runOnUiThread {
            composeRule.activity.setContentView(
                LinearLayout(composeRule.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Spinner(context).apply {
                        adapter = ArrayAdapter(
                            context,
                            android.R.layout.simple_spinner_item,
                            listOf("Standard delivery", "Priority delivery"),
                        )
                        setSelection(1)
                    })
                },
            )
        }

        val target = classicTargetAtFirst<Spinner>()

        assertEquals("Priority delivery", target?.label)
        assertEquals(UiRole.Button, target?.role)
    }

    @Test
    fun classicResolverTreatsAdapterRowsAsMenuItems() {
        composeRule.runOnUiThread {
            composeRule.activity.setContentView(
                ListView(composeRule.activity).apply {
                    adapter = ArrayAdapter(
                        context,
                        android.R.layout.simple_list_item_1,
                        listOf("Standard delivery", "Priority delivery"),
                    )
                },
            )
        }

        val target = classicTargetAtFirst<TextView>()

        assertEquals("Standard delivery", target?.label)
        assertEquals(UiRole.MenuItem, target?.role)
    }

    @Test
    fun classicResolverStillLabelsDisabledButton() {
        composeRule.runOnUiThread {
            composeRule.activity.setContentView(
                LinearLayout(composeRule.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Button(context).apply {
                        text = "Submit order"
                        isEnabled = false
                    })
                },
            )
        }

        val target = classicTargetAtFirst<Button>()

        assertEquals("Submit order", target?.label)
        assertEquals(UiRole.Button, target?.role)
    }

    @Test
    fun classicResolverUsesTopmostVisibleViewForOverlappingTargets() {
        lateinit var top: Button
        composeRule.runOnUiThread {
            composeRule.activity.setContentView(
                FrameLayout(composeRule.activity).apply {
                    addView(Button(context).apply {
                        text = "Bottom action"
                        layoutParams = FrameLayout.LayoutParams(240, 120)
                    })
                    top = Button(context).apply {
                        text = "Top action"
                        layoutParams = FrameLayout.LayoutParams(240, 120)
                    }
                    addView(top)
                },
            )
        }

        val target = classicTargetAt(top)

        assertEquals("Top action", target?.label)
        assertEquals(UiRole.Button, target?.role)
    }
}
