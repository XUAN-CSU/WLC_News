package com.wlc.news

import android.content.Context
import android.content.SharedPreferences

/**
 * Server profile. Everything here is editable from the Settings screen.
 */
object Prefs {
    private const val FILE = "wlc_news_prefs"
    const val KEY_HOST = "sftp_host"
    const val KEY_PORT = "sftp_port"
    const val KEY_USER = "sftp_user"
    const val KEY_PASS = "sftp_pass"
    const val KEY_BASE_PATH = "sftp_base_path"
    const val KEY_LAST_DATE = "last_date"

    const val DEFAULT_HOST = "8.137.111.186"
    const val DEFAULT_PORT = "22"
    const val DEFAULT_USER = "root"
    const val DEFAULT_BASE_PATH = "/root/Auto_Download_From_NPR/data/voice"

    fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun host(context: Context) = sp(context).getString(KEY_HOST, DEFAULT_HOST)!!
    fun port(context: Context) =
        sp(context).getString(KEY_PORT, DEFAULT_PORT)!!.toIntOrNull() ?: 22
    fun user(context: Context) = sp(context).getString(KEY_USER, DEFAULT_USER)!!
    fun password(context: Context) = sp(context).getString(KEY_PASS, "")!!

    fun hasPassword(context: Context): Boolean =
        sp(context).getString(KEY_PASS, "")!!.isNotEmpty()

    fun setPassword(context: Context, password: String) {
        sp(context).edit().putString(KEY_PASS, password).apply()
    }
    fun basePath(context: Context) =
        sp(context).getString(KEY_BASE_PATH, DEFAULT_BASE_PATH)!!.trimEnd('/')

    fun save(
        context: Context,
        host: String,
        port: String,
        user: String,
        password: String,
        basePath: String,
    ) {
        sp(context).edit()
            .putString(KEY_HOST, host.trim())
            .putString(KEY_PORT, port.trim())
            .putString(KEY_USER, user.trim())
            .putString(KEY_PASS, password)
            .putString(KEY_BASE_PATH, basePath.trim())
            .apply()
    }

    fun lastDate(context: Context): String? =
        sp(context).getString(KEY_LAST_DATE, null)

    fun setLastDate(context: Context, date: String) {
        sp(context).edit().putString(KEY_LAST_DATE, date).apply()
    }

    fun configured(context: Context): Boolean =
        host(context).isNotBlank() && user(context).isNotBlank() &&
                basePath(context).isNotBlank()
}
