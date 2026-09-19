package com.example.link2sdclone.groups

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * إعادة كتابة كاملة لنظام تخزين المجموعات.
 * كل مجموعة تُخزَّن الآن كـ JSONObject:
 *   { "packages": [...], "pinned": bool, "createdAt": long, "modifiedAt": long }
 * بدلاً من JSONArray وحدها كما كان سابقاً. عند القراءة، أي مجموعة لا تزال
 * بالصيغة القديمة (JSONArray) تُرحَّل تلقائياً وتُحفظ فوراً بالصيغة الجديدة
 * بدون فقدان أي بيانات موجودة.
 */
object GroupsManager {

    enum class GroupSort { NAME, CREATED, MODIFIED, COUNT }

    private const val PREFS = "app_groups_prefs"
    private const val KEY_GROUPS = "groups_json"
    private const val KEY_SORT = "groups_sort_mode"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readAll(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY_GROUPS, null) ?: return JSONObject()
        val obj = try { JSONObject(raw) } catch (e: Exception) { return JSONObject() }

        var migrated = false
        val keys = obj.keys().asSequence().toList()
        for (key in keys) {
            val value = obj.opt(key)
            if (value is JSONArray) {
                val now = System.currentTimeMillis()
                val entry = JSONObject().apply {
                    put("packages", value)
                    put("pinned", false)
                    put("createdAt", now)
                    put("modifiedAt", now)
                }
                obj.put(key, entry)
                migrated = true
            }
        }
        if (migrated) writeAll(context, obj)
        return obj
    }

    private fun writeAll(context: Context, obj: JSONObject) {
        prefs(context).edit().putString(KEY_GROUPS, obj.toString()).apply()
    }

    private fun updateEntry(context: Context, name: String, mutator: (JSONObject) -> Unit) {
        val obj = readAll(context)
        val entry = obj.optJSONObject(name) ?: JSONObject().apply {
            put("packages", JSONArray())
            put("pinned", false)
            val now = System.currentTimeMillis()
            put("createdAt", now)
            put("modifiedAt", now)
        }
        mutator(entry)
        obj.put(name, entry)
        writeAll(context, obj)
    }

    fun getSortMode(context: Context): GroupSort {
        val raw = prefs(context).getString(KEY_SORT, GroupSort.NAME.name)
        return try { GroupSort.valueOf(raw ?: GroupSort.NAME.name) } catch (e: Exception) { GroupSort.NAME }
    }

    fun setSortMode(context: Context, sort: GroupSort) {
        prefs(context).edit().putString(KEY_SORT, sort.name).apply()
    }

    /** المجموعات المثبّتة تظهر أولاً دائماً، بعدها الباقي، وكلاهما مرتّب
     *  حسب [sort]. */
    fun listGroupNames(context: Context, sort: GroupSort = getSortMode(context)): List<String> {
        val obj = readAll(context)
        val names = obj.keys().asSequence().toList()
        val comparator: Comparator<String> = when (sort) {
            GroupSort.NAME -> compareBy { it.lowercase() }
            GroupSort.CREATED -> compareByDescending { getCreatedAt(context, it) }
            GroupSort.MODIFIED -> compareByDescending { getModifiedAt(context, it) }
            GroupSort.COUNT -> compareByDescending { getPackages(context, it).size }
        }
        val sorted = names.sortedWith(comparator)
        val pinned = sorted.filter { isPinned(context, it) }
        val others = sorted.filterNot { isPinned(context, it) }
        return pinned + others
    }

    fun getPackages(context: Context, groupName: String): Set<String> {
        val entry = readAll(context).optJSONObject(groupName) ?: return emptySet()
        val arr = entry.optJSONArray("packages") ?: JSONArray()
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    fun setPackages(context: Context, groupName: String, packages: Set<String>) {
        updateEntry(context, groupName) { entry ->
            entry.put("packages", JSONArray(packages.toList()))
            entry.put("modifiedAt", System.currentTimeMillis())
        }
    }

    fun removePackage(context: Context, groupName: String, packageName: String) {
        val current = getPackages(context, groupName).toMutableSet()
        if (current.remove(packageName)) {
            setPackages(context, groupName, current)
        }
    }

    fun isPinned(context: Context, groupName: String): Boolean =
        readAll(context).optJSONObject(groupName)?.optBoolean("pinned", false) ?: false

    fun setPinned(context: Context, groupName: String, pinned: Boolean) {
        updateEntry(context, groupName) { it.put("pinned", pinned) }
    }

    fun getCreatedAt(context: Context, groupName: String): Long =
        readAll(context).optJSONObject(groupName)?.optLong("createdAt", 0L) ?: 0L

    fun getModifiedAt(context: Context, groupName: String): Long =
        readAll(context).optJSONObject(groupName)?.optLong("modifiedAt", 0L) ?: 0L

    /** Returns false if a group with this exact name already exists. */
    fun createGroup(context: Context, name: String): Boolean {
        val obj = readAll(context)
        if (obj.has(name)) return false
        val now = System.currentTimeMillis()
        obj.put(name, JSONObject().apply {
            put("packages", JSONArray())
            put("pinned", false)
            put("createdAt", now)
            put("modifiedAt", now)
        })
        writeAll(context, obj)
        return true
    }

    /** Returns false if [oldName] doesn't exist or [newName] is already taken. */
    fun renameGroup(context: Context, oldName: String, newName: String): Boolean {
        val obj = readAll(context)
        if (!obj.has(oldName) || obj.has(newName) || newName.isBlank()) return false
        val entry = obj.get(oldName)
        obj.remove(oldName)
        obj.put(newName, entry)
        writeAll(context, obj)
        return true
    }

    fun deleteGroup(context: Context, name: String) {
        val obj = readAll(context)
        obj.remove(name)
        writeAll(context, obj)
    }

    /** True only if every installed package in the group is currently
     *  frozen (disabled). Empty group counts as false (nothing to unfreeze). */
    fun areAllFrozen(context: Context, groupName: String): Boolean {
        val pkgs = getPackages(context, groupName)
        if (pkgs.isEmpty()) return false
        val pm = context.packageManager
        return pkgs.all { pkg ->
            try {
                !pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
            } catch (e: Exception) {
                false
            }
        }
    }

    /** (عدد المجمّد, العدد الكلي) لعرضها في صف المجموعة. */
    fun frozenCount(context: Context, groupName: String): Pair<Int, Int> {
        val pkgs = getPackages(context, groupName)
        val pm = context.packageManager
        val frozen = pkgs.count { pkg ->
            try {
                !pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
            } catch (e: Exception) {
                false
            }
        }
        return frozen to pkgs.size
    }
}
