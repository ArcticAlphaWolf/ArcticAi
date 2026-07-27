package com.arcticalphawolf.arcticrf.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Dumps a list of JSON objects to a cache file and hands it to the system share sheet. */
object Exporter {

    fun exportAndShare(context: Context, fileNameStem: String, jsonObjects: List<JSONObject>) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${fileNameStem}_${System.currentTimeMillis()}.json")
        file.writeText(JSONArray(jsonObjects).toString(2))

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export $fileNameStem").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
