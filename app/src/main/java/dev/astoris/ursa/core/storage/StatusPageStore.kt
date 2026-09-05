package dev.astoris.ursa.core.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.astoris.ursa.data.model.SavedStatusPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.statusPageDataStore by preferencesDataStore(name = "ursa_status_pages")

/** Encrypted, local-first storage for an unlimited number of public status pages. */
class StatusPageStore(context: Context) {

    private val appContext = context.applicationContext
    private val crypto = Crypto(appContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val pagesKey = stringPreferencesKey("pages")

    val pages: Flow<List<SavedStatusPage>> =
        appContext.statusPageDataStore.data.map { preferences -> decode(preferences) }

    suspend fun upsert(page: SavedStatusPage) {
        appContext.statusPageDataStore.edit { preferences ->
            val current = decode(preferences)
            val existing = current.firstOrNull { it.id == page.id }
            val nextOrder = existing?.order ?: ((current.maxOfOrNull { it.order } ?: -1) + 1)
            val next = current.filterNot { it.id == page.id } + page.copy(order = nextOrder)
            preferences[pagesKey] = encode(StatusPageOrdering.normalized(next))
        }
    }

    suspend fun remove(id: String) {
        appContext.statusPageDataStore.edit { preferences ->
            val next = decode(preferences).filterNot { it.id == id }
            preferences[pagesKey] = encode(StatusPageOrdering.normalized(next))
        }
    }

    suspend fun toggleFavorite(id: String) {
        appContext.statusPageDataStore.edit { preferences ->
            val next = decode(preferences).map { page ->
                if (page.id == id) page.copy(favorite = !page.favorite) else page
            }
            preferences[pagesKey] = encode(next)
        }
    }

    suspend fun move(id: String, direction: Int) {
        appContext.statusPageDataStore.edit { preferences ->
            preferences[pagesKey] = encode(StatusPageOrdering.move(decode(preferences), id, direction))
        }
    }

    private fun encode(pages: List<SavedStatusPage>): String =
        crypto.encrypt(json.encodeToString(pages))

    private fun decode(preferences: Preferences): List<SavedStatusPage> {
        val cipher = preferences[pagesKey] ?: return emptyList()
        val plain = crypto.decrypt(cipher) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SavedStatusPage>>(plain) }
            .getOrDefault(emptyList())
            .filter { it.id.isNotBlank() && it.name.isNotBlank() && it.url.isNotBlank() && it.slug.isNotBlank() }
            .let(StatusPageOrdering::normalized)
    }
}

internal object StatusPageOrdering {
    fun normalized(pages: List<SavedStatusPage>): List<SavedStatusPage> =
        pages.sortedWith(compareBy<SavedStatusPage> { it.order }.thenBy { it.name.lowercase() })
            .mapIndexed { index, page -> page.copy(order = index) }

    fun move(pages: List<SavedStatusPage>, id: String, direction: Int): List<SavedStatusPage> {
        val ordered = normalized(pages).toMutableList()
        val from = ordered.indexOfFirst { it.id == id }
        if (from < 0) return ordered
        val to = (from + direction.sign()).coerceIn(0, ordered.lastIndex)
        if (from != to) {
            val page = ordered.removeAt(from)
            ordered.add(to, page)
        }
        return ordered.mapIndexed { index, page -> page.copy(order = index) }
    }

    private fun Int.sign(): Int = when {
        this < 0 -> -1
        this > 0 -> 1
        else -> 0
    }
}
