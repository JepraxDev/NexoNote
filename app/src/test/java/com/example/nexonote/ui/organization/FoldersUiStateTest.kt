package com.example.nexonote.ui.organization

import com.example.nexonote.domain.model.ContentType
import com.example.nexonote.domain.model.OrganizationItem
import org.junit.Assert.*
import org.junit.Test

class FoldersUiStateTest {
    private fun item(id: Long, folderId: Long?, type: ContentType = ContentType.NOTE) = OrganizationItem(
        type = type,
        id = id,
        title = "Elemento $id",
        folderId = folderId,
    )

    @Test fun itemsAreGroupedByTheirFolderAndRootItemsAreExcluded() {
        val state = FoldersUiState(
            loading = false,
            items = listOf(
                item(1, 10),
                item(2, 10, ContentType.TASK),
                item(3, 20, ContentType.TABLE),
                item(4, null, ContentType.EVENT),
            ),
        )

        assertEquals(setOf(10L, 20L), state.itemsByFolder.keys)
        assertEquals(listOf(1L, 2L), state.itemsByFolder.getValue(10).map { it.id })
        assertEquals(listOf(3L), state.itemsByFolder.getValue(20).map { it.id })
    }

    @Test fun anEmptyOrganizationHasNoFolderContents() {
        assertTrue(FoldersUiState(loading = false).itemsByFolder.isEmpty())
    }
}
