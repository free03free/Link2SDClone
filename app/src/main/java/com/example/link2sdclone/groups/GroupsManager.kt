package com.example.link2sdclone.groups

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores named groups of package names as { "GroupName": ["pkg1","pkg2"] }.
 * No Room needed -- this is small, infrequently-written data, a perfect fit
 * for a single SharedPreferences string.
 */
object GroupsManager {
    private const val PREFS = "app_groups_prefs"
    private const val KEY_GROUPS = "groups_json"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readAll(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY_GROUPS, null) ?: return JSONObject()
        return try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
    }

    private fun writeAll(context: Context, obj: JSONObject) {
        prefs(context).edit().putString(KEY_GROUPS, obj.toString()).apply()
    }

    fun listGroupNames(context: Context): List<String> {
        val obj = readAll(context)
        return obj.keys().asSequence().sorted().toList()
    }

    fun getPackages(context: Context, groupName: String): Set<String> {
        val arr = readAll(context).optJSONArray(groupName) ?: return emptySet()
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    /** Returns false if a group with this exact name already exists. */
    fun createGroup(context: Context, name: String): Boolean {
        val obj = readAll(context)
        if (obj.has(name)) return false
        obj.put(name, JSONArray())
        writeAll(context, obj)
        return true
    }

    fun deleteGroup(context: Context, name: String) {
        val obj = readAll(context)
        obj.remove(name)
        writeAll(context, obj)
    }

    fun setPackages(context: Context, groupName: String, packages: Set<String>) {
        val obj = readAll(context)
        obj.put(groupName, JSONArray(packages.toList()))
        writeAll(context, obj)
    }
}
