package dev.astoris.ursa.core.storage

import dev.astoris.ursa.data.model.SavedStatusPage
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusPageOrderingTest {

    @Test
    fun move_reordersAndNormalizesSparseOrderValues() {
        val pages = listOf(page("a", 8), page("b", 2), page("c", 30))

        val moved = StatusPageOrdering.move(pages, "c", -1)

        assertEquals(listOf("b", "c", "a"), moved.map { it.id })
        assertEquals(listOf(0, 1, 2), moved.map { it.order })
    }

    @Test
    fun move_stopsAtListBoundaries() {
        val pages = listOf(page("a", 0), page("b", 1))

        assertEquals(listOf("a", "b"), StatusPageOrdering.move(pages, "a", -1).map { it.id })
        assertEquals(listOf("a", "b"), StatusPageOrdering.move(pages, "b", 1).map { it.id })
    }

    private fun page(id: String, order: Int) = SavedStatusPage(
        id = id,
        name = id.uppercase(),
        url = "https://$id.example.com",
        slug = "status",
        order = order,
    )
}
