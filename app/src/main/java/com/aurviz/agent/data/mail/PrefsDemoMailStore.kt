package com.aurviz.agent.data.mail

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Keeps demo drafts and sent mail in app-private storage so they survive a restart. */
class PrefsDemoMailStore(context: Context) : DemoMailStore {
    private val prefs = context.getSharedPreferences("aurviz_demo_mail", Context.MODE_PRIVATE)

    override fun load(): List<DemoEmail> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map {
            val o = array.getJSONObject(it)
            DemoEmail(
                id = o.getString("id"), from = o.getString("from"), to = o.getString("to"),
                subject = o.getString("subject"), daysAgo = o.optInt("daysAgo", 0),
                body = o.getString("body"), folder = o.getString("folder")
            )
        }
    }.getOrDefault(emptyList())

    override fun save(emails: List<DemoEmail>) {
        val array = JSONArray()
        emails.forEach {
            array.put(
                JSONObject().put("id", it.id).put("from", it.from).put("to", it.to).put("subject", it.subject)
                    .put("daysAgo", it.daysAgo).put("body", it.body).put("folder", it.folder)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object { const val KEY = "messages" }
}
