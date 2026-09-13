package com.example.applauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

data class InstalledApp(
    val label: String,
    val packageName: String
)

class AppResolver(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager
    private var cachedApps: List<InstalledApp>? = null

    /**
     * Loads or retrieves cached installed launchable applications.
     */
    fun getInstalledApps(forceRefresh: Boolean = false): List<InstalledApp> {
        if (!forceRefresh && cachedApps != null) {
            return cachedApps!!
        }

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
        val apps = resolveInfos.mapNotNull { resolveInfo ->
            val label = resolveInfo.loadLabel(packageManager)?.toString()?.trim()
            val packageName = resolveInfo.activityInfo?.packageName
            if (!label.isNullOrBlank() && !packageName.isNullOrBlank()) {
                InstalledApp(label = label, packageName = packageName)
            } else {
                null
            }
        }.distinctBy { it.packageName }

        cachedApps = apps
        Log.d(TAG, "Loaded ${apps.size} launchable applications.")
        return apps
    }

    /**
     * Resolves an app query string into the best matching installed package.
     */
    fun findApp(query: String): InstalledApp? {
        val apps = getInstalledApps()
        val cleanQuery = query.trim().lowercase()

        // 1. Check direct system package aliases
        val systemPackage = SYSTEM_ALIASES[cleanQuery]
        if (systemPackage != null) {
            val match = apps.firstOrNull { it.packageName.equals(systemPackage, ignoreCase = true) }
            if (match != null) return match
        }

        // 2. Exact match on label
        val exactMatch = apps.firstOrNull { it.label.equals(cleanQuery, ignoreCase = true) }
        if (exactMatch != null) return exactMatch

        // 3. Normalized alphanumeric match
        val normalizedQuery = cleanQuery.replace(Regex("[^a-z0-9]"), "")
        val normalizedMatch = apps.firstOrNull {
            it.label.replace(Regex("[^a-zA-Z0-9]"), "").equals(normalizedQuery, ignoreCase = true)
        }
        if (normalizedMatch != null) return normalizedMatch

        // 4. Label contains query
        val containsMatch = apps.firstOrNull { it.label.contains(cleanQuery, ignoreCase = true) }
        if (containsMatch != null) return containsMatch

        // 5. Query contains label (e.g. "google chrome browser" contains "chrome")
        val reverseContains = apps.filter { it.label.length >= 3 }
            .firstOrNull { cleanQuery.contains(it.label, ignoreCase = true) }
        if (reverseContains != null) return reverseContains

        // 6. Package name contains query
        val packageMatch = apps.firstOrNull { it.packageName.contains(cleanQuery, ignoreCase = true) }
        if (packageMatch != null) return packageMatch

        // 7. Fuzzy Levenshtein distance match
        var bestFuzzy: InstalledApp? = null
        var bestSimilarity = 0.0

        for (app in apps) {
            val similarity = calculateSimilarity(cleanQuery, app.label.lowercase())
            if (similarity > 0.65 && similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestFuzzy = app
            }
        }

        return bestFuzzy
    }

    /**
     * Launches the resolved application.
     * Returns true if successfully launched, false otherwise.
     */
    fun launchApp(appNameOrPackage: String): Boolean {
        val app = findApp(appNameOrPackage)
        val packageName = app?.packageName ?: appNameOrPackage

        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Log.d(TAG, "Successfully launched package: $packageName")
                true
            } else {
                Log.w(TAG, "No launch intent found for package: $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package: $packageName", e)
            false
        }
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        val longer = if (s1.length >= s2.length) s1 else s2
        val shorter = if (s1.length < s2.length) s1 else s2
        if (longer.isEmpty()) return 1.0
        val editDistance = levenshteinDistance(longer, shorter)
        return (longer.length - editDistance).toDouble() / longer.length.toDouble()
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        for (j in costs.indices) costs[j] = j
        for (i in 1..s1.length) {
            costs[0] = i
            var nw = i - 1
            for (j in 1..s2.length) {
                val cj = Math.min(
                    1 + Math.min(costs[j], costs[j - 1]),
                    if (s1[i - 1] == s2[j - 1]) nw else nw + 1
                )
                nw = costs[j]
                costs[j] = cj
            }
        }
        return costs[s2.length]
    }

    companion object {
        private const val TAG = "AppResolver"

        private val SYSTEM_ALIASES = mapOf(
            "settings" to "com.android.settings",
            "camera" to "com.android.camera",
            "calculator" to "com.google.android.calculator",
            "chrome" to "com.android.chrome",
            "youtube" to "com.google.android.youtube",
            "maps" to "com.google.android.apps.maps",
            "photos" to "com.google.android.apps.photos",
            "clock" to "com.google.android.deskclock",
            "contacts" to "com.google.android.contacts",
            "messages" to "com.google.android.apps.messaging",
            "phone" to "com.google.android.dialer",
            "play store" to "com.android.vending"
        )
    }
}
