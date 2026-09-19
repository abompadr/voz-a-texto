package com.vozatexto.app.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileSaver {
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun save(context: Context, text: String): String {
        val filename = "transcripcion_${dateFormat.format(Date())}.txt"
        File(context.filesDir, filename).writeText(text)
        return filename
    }

    fun listFiles(context: Context): List<File> =
        context.filesDir.listFiles { f -> f.name.startsWith("transcripcion_") && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
