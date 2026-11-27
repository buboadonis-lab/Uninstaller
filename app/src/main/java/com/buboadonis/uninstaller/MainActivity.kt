package com.buboadonis.uninstaller

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

private data class InstalledApp(
    val packageInfo: PackageInfo,
    val label: String,
    val packageName: String,
    val system: Boolean,
    val updated: Boolean
)

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val apps = mutableListOf<InstalledApp>()
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private var page = Page.APPS
    private var showSystem = false

    private enum class Page { APPS, SUGGESTIONS }

    private val ink = Color.rgb(28, 43, 40)
    private val muted = Color.rgb(102, 119, 114)
    private val green = Color.rgb(23, 107, 91)
    private val paper = Color.rgb(245, 247, 246)
    private val line = Color.rgb(226, 233, 230)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = paper
        window.navigationBarColor = paper
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        buildShell()
        refreshApps()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) render()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
            setPadding(dp(20), dp(18), dp(20), dp(8))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = column().apply {
            addView(text("APP CARE", 11, green, true))
            addView(text("Uninstaller", 27, ink, true))
        }
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val refreshButton = smallButton("↻")
        refreshButton.contentDescription = "Refresh installed apps"
        refreshButton.setOnClickListener { refreshApps() }
        header.addView(refreshButton)
        root.addView(header)

        val intro = text("A clear view of what’s installed.", 14, muted, false).apply {
            setPadding(0, dp(4), 0, dp(14))
        }
        root.addView(intro)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(12))
        }
        val allButton = navButton("Installed apps")
        val suggestionButton = navButton("Review suggestions")
        allButton.tag = Page.APPS
        suggestionButton.tag = Page.SUGGESTIONS
        allButton.setOnClickListener { page = Page.APPS; render() }
        suggestionButton.setOnClickListener { page = Page.SUGGESTIONS; render() }
        nav.addView(allButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        nav.addView(suggestionButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(6) })
        root.addView(nav)

        search = EditText(this).apply {
            hint = "Search name or package"
            textSize = 15f
            setSingleLine(true)
            setPadding(dp(15), 0, dp(15), 0)
            background = rounded(Color.WHITE, line, 14)
            setTextColor(ink)
            setHintTextColor(muted)
            addTextChangedListener(SimpleTextWatcher { renderListOnly() })
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(12) })

        val scroller = ScrollView(this).apply { clipToPadding = false }
        content = column()
        scroller.addView(content)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun refreshApps() {
        Toast.makeText(this, "Reading installed apps…", Toast.LENGTH_SHORT).show()
        worker.execute {
            val pm = packageManager
            val found = try {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_META_DATA).mapNotNull { info ->
                    val appInfo = info.applicationInfo ?: return@mapNotNull null
                    val pkg = info.packageName ?: return@mapNotNull null
                    val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    val updated = appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    InstalledApp(info, pm.getApplicationLabel(appInfo).toString(), pkg, isSystem, updated)
                }.sortedBy { it.label.lowercase() }
            } catch (_: Exception) { emptyList() }
            runOnUiThread {
                apps.clear()
                apps.addAll(found)
                render()
            }
        }
    }

    private fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        val filtered = matchingApps()
        if (page == Page.APPS) {
            val userApps = apps.count { !it.system }
            val systemApps = apps.count { it.system }
            val stats = rowCard()
            stats.addView(statBlock("${apps.size}", "INSTALLED"), LinearLayout.LayoutParams(0, -2, 1f))
            stats.addView(statBlock("$userApps", "USER APPS"), LinearLayout.LayoutParams(0, -2, 1f))
            stats.addView(statBlock("$systemApps", "SYSTEM"), LinearLayout.LayoutParams(0, -2, 1f))
            content.addView(stats)
            val tools = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(8))
            }
            tools.addView(text("Installed packages", 18, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
            val systemToggle = smallButton(if (showSystem) "Hide system" else "Show system")
            systemToggle.setOnClickListener { showSystem = !showSystem; render() }
            tools.addView(systemToggle)
            content.addView(tools)
            content.addView(text("${filtered.size} shown · package manager handles app-owned data during uninstall", 12, muted, false).apply {
                setPadding(0, 0, 0, dp(10))
            })
        } else {
            content.addView(text("Worth a second look", 21, ink, true))
            content.addView(text("Suggestions are based on usage history—not a verdict. Nothing is removed automatically.", 13, muted, false).apply {
                setPadding(0, dp(5), 0, dp(12))
            })
            if (!hasUsageAccess()) {
                val permissionCard = rowCard().apply { orientation = LinearLayout.VERTICAL }
                permissionCard.addView(text("Allow usage access to find apps you haven’t opened recently.", 14, ink, true))
                permissionCard.addView(text("Usage history stays on this device and is only used to rank this list.", 12, muted, false).apply {
                    setPadding(0, dp(6), 0, dp(10))
                })
                val grant = actionButton("Open usage access settings") { openUsageSettings() }
                permissionCard.addView(grant)
                content.addView(permissionCard)
                return
            }
            val suggestions = suggestions()
            content.addView(text("${suggestions.size} apps with little or no recent use", 13, muted, false).apply {
                setPadding(0, dp(4), 0, dp(10))
            })
            if (suggestions.isEmpty()) {
                content.addView(emptyCard("No clear candidates right now", "Keep using your phone as usual. Apps with recent usage are left off this list."))
                return
            }
            suggestions.filter { matchesQuery(it) }.forEach { content.addView(appCard(it, suggestion = true)) }
            if (suggestions.none { matchesQuery(it) }) content.addView(emptyCard("No matches", "Try another app name or package."))
            return
        }

        if (filtered.isEmpty()) {
            content.addView(emptyCard("No apps found", "Try a different search or change the system-app filter."))
        } else {
            filtered.forEach { content.addView(appCard(it)) }
        }
    }

    private fun renderListOnly() = render()

    private fun matchingApps(): List<InstalledApp> = apps.filter { (showSystem || !it.system) && matchesQuery(it) }

    private fun matchesQuery(app: InstalledApp): Boolean {
        val q = search.text?.toString()?.trim()?.lowercase().orEmpty()
        return q.isEmpty() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q)
    }

    private fun appCard(app: InstalledApp, suggestion: Boolean = false): View {
        val card = rowCard().apply {
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener { showDetails(app) }
        }
        val icon = ImageView(this).apply {
            val size = dp(46)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(12) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            try { setImageDrawable(packageManager.getApplicationIcon(app.packageName)) }
            catch (_: Exception) { setImageResource(android.R.drawable.sym_def_app_icon) }
        }
        card.addView(icon)
        val labels = column()
        labels.addView(text(app.label, 15, ink, true, maxLines = 1))
        labels.addView(text(app.packageName, 11, muted, false, maxLines = 1))
        val type = when {
            suggestion -> "REVIEW"
            app.updated -> "UPDATED SYSTEM"
            app.system -> "SYSTEM"
            else -> "USER APP"
        }
        labels.addView(text(type, 9, if (suggestion) green else muted, true).apply { setPadding(0, dp(4), 0, 0) })
        card.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(text("›", 26, muted, false).apply { setPadding(dp(8), 0, 0, 0) })
        return card
    }

    private fun showDetails(app: InstalledApp) {
        val body = column().apply { setPadding(dp(22), dp(5), dp(22), 0) }
        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(58)).apply { bottomMargin = dp(8) }
            try { setImageDrawable(packageManager.getApplicationIcon(app.packageName)) } catch (_: Exception) {}
        }
        body.addView(icon)
        body.addView(text(app.label, 20, ink, true))
        body.addView(text(app.packageName, 12, muted, false).apply { setPadding(0, dp(3), 0, dp(12)) })
        body.addView(text(if (app.system) "Pre-installed / system package" else "Installed user app", 13, ink, true))
        body.addView(text("Version ${app.packageInfo.versionName ?: "unknown"} · ${if (app.updated) "system app with an update" else if (app.system) "included with device software" else "installed by a user"}", 12, muted, false).apply {
            setPadding(0, dp(5), 0, dp(10))
        })
        body.addView(text("Android apps are separate packages. This tool will not guess at dependencies or delete shared system files. Android’s package manager performs supported app-data cleanup.", 12, muted, false))
        val protected = app.packageName == packageName || isProtected(app.packageName)
        if (protected) body.addView(text("Uninstall is disabled for this essential package.", 12, green, true).apply { setPadding(0, dp(8), 0, 0) })
        val dialog = AlertDialog.Builder(this)
            .setView(body)
            .setNegativeButton("Close", null)
            .setNeutralButton("App info") { _, _ -> openAppInfo(app.packageName) }
            .setPositiveButton(if (app.system) "Remove for user" else "Uninstall") { _, _ -> confirmUninstall(app) }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !protected
    }

    private fun confirmUninstall(app: InstalledApp) {
        if (app.packageName == packageName || isProtected(app.packageName)) {
            Toast.makeText(this, "This package is protected", Toast.LENGTH_LONG).show()
            return
        }
        val title = if (app.system) "Remove ${app.label} for this user?" else "Uninstall ${app.label}?"
        val message = if (app.system) {
            "This uses Android’s package manager to remove the package for user 0. The factory image is not erased. It can affect Samsung or Android features that depend on this package. A root manager is required. No related packages or shared files will be deleted."
        } else {
            "Android will uninstall this app and clean its package-owned private data. Other apps and shared files are not automatically removed."
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (app.system) "Continue" else "Open uninstall prompt") { _, _ ->
                if (app.system) removeSystemForUser(app) else launchUninstallPrompt(app)
            }.show()
    }

    private fun launchUninstallPrompt(app: InstalledApp) {
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
        } catch (_: Exception) {
            Toast.makeText(this, "Android could not open the uninstall prompt", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeSystemForUser(app: InstalledApp) {
        worker.execute {
            val result = runCatching {
                require(app.packageName.matches(Regex("[A-Za-z0-9_.]+")))
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm uninstall --user 0 ${app.packageName}"))
                val out = process.inputStream.bufferedReader().readText()
                val err = process.errorStream.bufferedReader().readText()
                val code = process.waitFor()
                Triple(code, out.trim(), err.trim())
            }
            runOnUiThread {
                val success = result.getOrNull()?.let { it.first == 0 && it.second.contains("Success", true) } == true
                val message = result.fold(
                    { "${it.second.ifBlank { it.third.ifBlank { "Exit code ${it.first}" } }}" },
                    { "Could not run package manager: ${it.message ?: "unknown error"}" }
                )
                AlertDialog.Builder(this)
                    .setTitle(if (success) "Package removed for user" else "Could not remove package")
                    .setMessage(if (success) "${app.label} is no longer installed for the current user. The factory copy may remain and can usually be restored with Android’s install-existing command. Shared dependencies and files were left untouched." else message)
                    .setPositiveButton("OK") { _, _ -> refreshApps() }
                    .show()
            }
        }
    }

    private fun suggestions(): List<InstalledApp> {
        val usage = getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val start = now - 90L * 24 * 60 * 60 * 1000
        val latest = runCatching {
            usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
                .groupBy { it.packageName }
                .mapValues { (_, entries) -> entries.maxOfOrNull { it.lastTimeUsed } ?: 0L }
        }.getOrDefault(emptyMap())
        return apps.asSequence()
            .filter { !it.system && it.packageName != packageName && !isProtected(it.packageName) }
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .filter { app ->
                val last = latest[app.packageName] ?: 0L
                val installed = app.packageInfo.firstInstallTime
                val reference = if (last > 0) last else installed
                reference < now - 60L * 24 * 60 * 60 * 1000
            }
            .sortedBy { latest[it.packageName]?.takeIf { t -> t > 0 } ?: it.packageInfo.firstInstallTime }
            .toList()
    }

    private fun hasUsageAccess(): Boolean {
        return try {
            val ops = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    private fun openUsageSettings() {
        try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        catch (_: Exception) { Toast.makeText(this, "Open Settings and find Usage access", Toast.LENGTH_LONG).show() }
    }

    private fun openAppInfo(pkg: String) {
        try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))) }
        catch (_: Exception) { Toast.makeText(this, "Could not open app settings", Toast.LENGTH_SHORT).show() }
    }

    private fun isProtected(pkg: String): Boolean {
        val exact = setOf(
            "android", "com.android.systemui", "com.android.settings", "com.android.phone",
            "com.android.server.telecom", "com.android.providers.settings", "com.android.providers.media",
            "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.android.providers.downloads", "com.android.providers.contacts", "com.android.providers.telephony",
            "com.android.dialer", "com.android.emergency", "com.google.android.gms",
            "com.samsung.android.securitylogagent", "com.samsung.android.knox.containercore",
            "com.sec.android.app.launcher", "com.samsung.android.app.telephonyui"
        )
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val activeHomePackages = packageManager.queryIntentActivities(homeIntent, 0).map { it.activityInfo.packageName }
        return pkg == packageName || pkg in exact || pkg in activeHomePackages || pkg.startsWith("com.android.providers.")
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(green, green, 12)
        setOnClickListener { action() }
    }

    private fun navButton(label: String): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(ink)
        background = rounded(Color.WHITE, line, 14)
    }

    private fun smallButton(label: String): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        minHeight = dp(38)
        minimumHeight = dp(38)
        setPadding(dp(10), 0, dp(10), 0)
        setTextColor(green)
        background = rounded(Color.WHITE, line, 12)
    }

    private fun rowCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = rounded(Color.WHITE, line, 16)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
    }

    private fun statBlock(value: String, label: String): LinearLayout = column().apply {
        addView(text(value, 21, ink, true))
        addView(text(label, 9, muted, true).apply { setPadding(0, dp(2), 0, 0) })
    }

    private fun emptyCard(title: String, description: String) = rowCard().apply {
        orientation = LinearLayout.VERTICAL
        addView(text(title, 15, ink, true))
        addView(text(description, 12, muted, false).apply { setPadding(0, dp(5), 0, 0) })
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun text(value: String, size: Int, color: Int, bold: Boolean, maxLines: Int = Int.MAX_VALUE) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        this.maxLines = maxLines
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        setCornerRadius(dp(radius).toFloat())
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

private class SimpleTextWatcher(private val changed: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}
