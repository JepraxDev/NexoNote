package com.example.nexonote.navigation

import android.net.Uri

object TransferRoutes {
    const val EXPORT = "transfer/export"
    const val IMPORT = "transfer/import"
    const val BACKUP = "transfer/backup"
    const val RESTORE = "transfer/restore?uri={uri}"
    const val RESTORE_URI = "uri"

    fun restore(uri: String): String = "transfer/restore?uri=${Uri.encode(uri)}"
}
