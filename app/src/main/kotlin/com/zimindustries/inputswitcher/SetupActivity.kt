package com.zimindustries.inputswitcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

/**
 * The app's main icon. Three test buttons, "Add widget to home screen", "Add shortcuts to home
 * screen", transport selection, and a status line with the last switch result.
 */
class SetupActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var transportText: TextView
    private lateinit var sourceChangeText: TextView
    private lateinit var portEdit: EditText

    private val statusListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshStatus() }

    /**
     * Optional: the firmware fires this whenever the source changes. A manifest receiver would not
     * get it (implicit runtime broadcast), so we only listen while this screen is open and show the
     * extras, which is handy for figuring out the payload.
     */
    private val sourceChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val extras = intent.extras
            // Payload types are undocumented, so dump whatever is there generically.
            @Suppress("DEPRECATION")
            val dump = extras?.keySet()?.joinToString(", ") { k -> "$k=${extras.get(k)}" }.orEmpty()
            Log.i(SourceSwitcher.TAG, "${intent.action}: $dump")
            sourceChangeText.text = getString(R.string.status_source_change, dump.ifEmpty { "(no extras)" })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        statusText = findViewById(R.id.text_status)
        transportText = findViewById(R.id.text_transport)
        sourceChangeText = findViewById(R.id.text_source_change)
        portEdit = findViewById(R.id.edit_ip_port)

        bindSourceButton(R.id.setup_button_conference_hub, Source.CONFERENCE_HUB)
        bindSourceButton(R.id.setup_button_front_usbc, Source.FRONT_USBC)
        bindSourceButton(R.id.setup_button_front_hdmi, Source.FRONT_HDMI)

        findViewById<Button>(R.id.button_add_widget).setOnClickListener { addWidget() }
        findViewById<Button>(R.id.button_add_shortcuts).setOnClickListener { addShortcuts() }

        setUpTransportControls()
    }

    override fun onResume() {
        super.onResume()
        SwitchStatus.prefs(this).registerOnSharedPreferenceChangeListener(statusListener)
        registerSourceChangeReceiver()
        refreshStatus()
    }

    override fun onPause() {
        super.onPause()
        SwitchStatus.prefs(this).unregisterOnSharedPreferenceChangeListener(statusListener)
        runCatching { unregisterReceiver(sourceChangeReceiver) }
    }

    private fun bindSourceButton(id: Int, source: Source) {
        findViewById<Button>(id).apply {
            text = source.label
            setOnClickListener { SourceSwitcher.switch(this@SetupActivity, source) }
        }
    }

    private fun setUpTransportControls() {
        val group = findViewById<RadioGroup>(R.id.radio_transport)
        val direct = findViewById<RadioButton>(R.id.radio_transport_direct)
        val ip = findViewById<RadioButton>(R.id.radio_transport_ip)

        when (Settings.transportKind(this)) {
            TransportKind.DIRECT -> direct.isChecked = true
            TransportKind.IP -> ip.isChecked = true
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val kind = if (checkedId == R.id.radio_transport_ip) TransportKind.IP else TransportKind.DIRECT
            Settings.setTransportKind(this, kind)
            refreshStatus()
        }

        val port = Settings.ipPort(this)
        if (port > 0) portEdit.setText(port.toString())
        portEdit.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) savePort() }
        portEdit.setOnEditorActionListener { _, _, _ -> savePort(); false }
    }

    private fun savePort() {
        val port = portEdit.text.toString().trim().toIntOrNull() ?: 0
        Settings.setIpPort(this, port.coerceIn(0, 65535))
        refreshStatus()
    }

    private fun refreshStatus() {
        val transport = SourceSwitcher.transport(this)
        val detail = if (transport === IpControlTransport) {
            val port = Settings.ipPort(this)
            if (port > 0) " (127.0.0.1:$port)" else " (port not set)"
        } else ""
        transportText.text = getString(R.string.status_transport, transport.displayName + detail)
        statusText.text = getString(R.string.status_last, SwitchStatus.last(this) ?: getString(R.string.status_none))
    }

    private fun registerSourceChangeReceiver() {
        val filter = IntentFilter(ACTION_CVTE_SOURCE_CHANGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sourceChangeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(sourceChangeReceiver, filter)
        }
    }

    private fun addWidget() {
        val ok = InputSwitcherWidget.requestPin(this)
        if (!ok) Toast.makeText(this, R.string.toast_pin_widget_unsupported, Toast.LENGTH_LONG).show()
    }

    private fun addShortcuts() {
        val manager = getSystemService(ShortcutManager::class.java)
        if (manager == null || !manager.isRequestPinShortcutSupported) {
            Toast.makeText(this, R.string.toast_pin_shortcut_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        // Launcher3 stacks one confirmation sheet per request; the user confirms them in turn.
        for (source in Source.entries) {
            val intent = Intent(this, SwitchActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(Source.EXTRA_SOURCE_ID, source.id)
            val info = ShortcutInfo.Builder(this, "source_${source.id}")
                .setShortLabel(source.label)
                .setLongLabel(source.label)
                .setIcon(Icon.createWithResource(this, source.launcherIconRes))
                .setIntent(intent)
                .build()
            manager.requestPinShortcut(info, null)
        }
    }

    private companion object {
        const val ACTION_CVTE_SOURCE_CHANGE = "com.cvte.touchmenu.sourcechange"
    }
}
