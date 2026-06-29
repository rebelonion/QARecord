package dev.rebelonion.qarecord

import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton as NativeRadioButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch as NativeSwitch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.rebelonion.qarecord.qarecorder.core.QaStepRecorder
import dev.rebelonion.qarecord.ui.theme.QARecordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QARecordTheme {
                Scaffold { innerPadding ->
                    RecorderTestScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderTestScreen(modifier: Modifier = Modifier) {
    var address by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var leaveAtDoor by remember { mutableStateOf(false) }
    var contactless by remember { mutableStateOf(true) }
    var selectedSpeed by remember { mutableStateOf("ASAP") }
    var tip by remember { mutableFloatStateOf(18f) }
    var showDialog by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var showDropdown by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val speeds = listOf("ASAP", "30 minutes", "Schedule later")

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Confirm delivery") },
            text = { Text("Use this dialog to test recording controls above the current screen.") },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Delivery sheet", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = { showSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose courier")
                }
                Button(
                    onClick = { showSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Close sheet")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "QA Recorder Test Checkout",
            style = MaterialTheme.typography.headlineSmall,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Compose controls", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Address field" },
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Delivery instructions") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Delivery instructions field" },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {},
                        modifier = Modifier.semantics { contentDescription = "Begin order" },
                    ) {
                        Text("Begin Order")
                    }
                    Button(
                        onClick = {},
                        modifier = Modifier.semantics { contentDescription = "Continue checkout" },
                    ) {
                        Text("Continue")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Leave at door")
                    Switch(
                        checked = leaveAtDoor,
                        onCheckedChange = { leaveAtDoor = it },
                        modifier = Modifier.semantics { contentDescription = "Leave at door" },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Contactless delivery")
                    Checkbox(
                        checked = contactless,
                        onCheckedChange = { contactless = it },
                        modifier = Modifier.semantics { contentDescription = "Contactless delivery" },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE8F1FF))
                        .clickable(onClick = {})
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Clickable Box CTA")
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3D6))
                        .clickable(onClick = {})
                        .semantics { role = Role.Button }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Role Button Box")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEAF7EA))
                        .clickable(onClick = {})
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Pro plan")
                    Text("$29")
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1EAF7))
                        .clickable(onClick = {})
                        .clearAndSetSemantics {
                            contentDescription = "Order total summary"
                            role = Role.Button
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text("Total")
                    Text("$42")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Top layer surfaces", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { showDialog = true }) {
                        Text("Open Dialog")
                    }
                    Button(onClick = { showSheet = true }) {
                        Text("Open Sheet")
                    }
                }
                Box {
                    Button(onClick = { showDropdown = true }) {
                        Text("Open Menu")
                    }
                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Standard delivery") },
                            onClick = { showDropdown = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Priority delivery") },
                            onClick = { showDropdown = false },
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Options", style = MaterialTheme.typography.titleMedium)
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(speed)
                        RadioButton(
                            selected = selectedSpeed == speed,
                            onClick = { selectedSpeed = speed },
                            modifier = Modifier.semantics { contentDescription = speed },
                        )
                    }
                }
                Text("Tip: ${tip.toInt()}%")
                Slider(
                    value = tip,
                    onValueChange = { tip = it },
                    valueRange = 0f..30f,
                    modifier = Modifier.semantics { contentDescription = "Tip percentage" },
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Classic Android Views", style = MaterialTheme.typography.titleMedium)
                NativeViewTestControls()
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("WebView content", style = MaterialTheme.typography.titleMedium)
                WebViewTestContent()
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Scrollable order list", style = MaterialTheme.typography.titleMedium)
                LazyColumn(
                    modifier = Modifier.height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items((1..12).map { "Item $it" }) { item ->
                        Text(
                            text = "$item - customize",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = {})
                                .semantics { contentDescription = "$item customize row" }
                                .padding(vertical = 8.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Place order" },
        ) {
            Text("Place Order")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun NativeViewTestControls() {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 0)

                addView(EditText(context).apply {
                    hint = "Promo code"
                    contentDescription = "Promo code"
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                })

                addView(CheckBox(context).apply {
                    text = "Save address"
                    contentDescription = "Save address"
                })

                addView(NativeSwitch(context).apply {
                    text = "Native contactless delivery"
                    contentDescription = "Native contactless delivery"
                    isChecked = true
                })

                addView(NativeRadioButton(context).apply {
                    text = "Native ASAP"
                    contentDescription = "Native ASAP"
                    isChecked = true
                })

                addView(SeekBar(context).apply {
                    contentDescription = "Native tip percentage"
                    min = 0
                    max = 30
                    progress = 12
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                })

                addView(Spinner(context).apply {
                    adapter = ArrayAdapter(
                        context,
                        android.R.layout.simple_spinner_item,
                        listOf("Standard delivery", "Priority delivery", "Schedule later"),
                    )
                    setSelection(1)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                })

                addView(Button(context).apply {
                    text = "Apply Promo"
                    contentDescription = "Apply promo"
                })

                addView(Button(context).apply {
                    text = "Submit Order Disabled"
                    contentDescription = "Submit order disabled"
                    isEnabled = false
                })

                addView(FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        140,
                    )
                    addView(Button(context).apply {
                        text = "Bottom Overlap"
                        contentDescription = "Bottom overlap"
                        layoutParams = FrameLayout.LayoutParams(320, 120)
                    })
                    addView(Button(context).apply {
                        text = "Top Overlap"
                        contentDescription = "Top overlap"
                        layoutParams = FrameLayout.LayoutParams(320, 120)
                    })
                })

                addView(ComposeView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    setContent {
                        QARecordTheme {
                            Button(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Compose Inside Classic")
                            }
                        }
                    }
                })
            }
        },
    )
}

@Composable
private fun WebViewTestContent() {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                QaStepRecorder.instrument(this)
                loadDataWithBaseURL(
                    "https://qa-record.local/",
                    testCheckoutHtml,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}

private val testCheckoutHtml = """
    <!doctype html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1" />
      <style>
        body {
          margin: 0;
          padding: 16px;
          font: 16px sans-serif;
          color: #1b1b1f;
          background: #ffffff;
        }
        h2 {
          margin: 0 0 12px;
          font-size: 20px;
        }
        label {
          display: block;
          margin: 14px 0 6px;
          font-weight: 600;
        }
        input, select, button, a.action {
          box-sizing: border-box;
          width: 100%;
          min-height: 44px;
          border-radius: 6px;
          font: inherit;
        }
        input, select {
          border: 1px solid #77777d;
          padding: 8px 10px;
        }
        button, a.action {
          display: flex;
          align-items: center;
          justify-content: center;
          margin-top: 14px;
          border: 0;
          background: #185abc;
          color: white;
          text-decoration: none;
          font-weight: 700;
        }
        .row {
          display: flex;
          align-items: center;
          gap: 10px;
          margin-top: 14px;
        }
        .row input {
          width: auto;
          min-height: auto;
        }
        #status {
          min-height: 24px;
          margin-top: 12px;
          color: #2e6b2e;
          font-weight: 600;
        }
      </style>
    </head>
    <body>
      <h2>Embedded Web Checkout</h2>

      <label for="email">Email</label>
      <input id="email" aria-label="Email address" placeholder="name@example.com" />

      <label for="delivery">Delivery window</label>
      <select id="delivery" aria-label="Delivery window">
        <option>Today, 5-6 PM</option>
        <option>Tomorrow, 9-10 AM</option>
        <option>Schedule later</option>
      </select>

      <label class="row">
        <input id="sms" type="checkbox" aria-label="Send SMS updates" />
        <span>Send SMS updates</span>
      </label>

      <button id="apply" type="button">Apply Web Coupon</button>
      <a class="action" href="#details" aria-label="View web details">View Details Link</a>
      <div id="status" aria-live="polite"></div>

      <script>
        document.getElementById('apply').addEventListener('click', function () {
          document.getElementById('status').textContent = 'Web coupon applied';
        });
      </script>
    </body>
    </html>
""".trimIndent()

@Preview(showBackground = true)
@Composable
fun RecorderTestScreenPreview() {
    QARecordTheme {
        RecorderTestScreen()
    }
}
