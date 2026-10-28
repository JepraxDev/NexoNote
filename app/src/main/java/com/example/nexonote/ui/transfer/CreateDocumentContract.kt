package com.example.nexonote.ui.transfer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

data class CreateDocumentInput(val fileName: String, val mimeType: String)

/**
 * `ActivityResultContracts.CreateDocument` fixes its MIME type when the contract is created, but
 * the export screen changes formats at runtime. This contract receives the MIME type and the
 * suggested file name as input instead.
 */
class CreateDocumentContract : ActivityResultContract<CreateDocumentInput, Uri?>() {
    override fun createIntent(context: Context, input: CreateDocumentInput): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input.mimeType
            putExtra(Intent.EXTRA_TITLE, input.fileName)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
