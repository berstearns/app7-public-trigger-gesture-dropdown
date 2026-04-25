package pl.czak.learnlauncher.data

import android.content.Context
import com.google.gson.Gson
import pl.czak.learnlauncher.data.repository.TokenRegionRepository
import pl.czak.learnlauncher.domain.model.ConversationBox
import pl.czak.learnlauncher.domain.model.RegionType
import pl.czak.learnlauncher.domain.model.TokenRegionsManifest

class AndroidTokenRegionRepository(private val context: Context) : TokenRegionRepository {

    private val gson = Gson()
    private val tokenCache = mutableMapOf<String, Map<Int, List<ConversationBox>>>()

    init {
        loadManifest()
    }

    private fun loadManifest() {
        try {
            val json = context.assets.open("token_regions.json").bufferedReader().use { it.readText() }
            val manifest = gson.fromJson(json, TokenRegionsManifest::class.java)

            manifest?.tokenRegions?.forEach { entry ->
                val bubbleMap = tokenCache.getOrPut(entry.imageId) { mutableMapOf() }.toMutableMap()
                bubbleMap[entry.bubbleIndex] = entry.tokens.map { token ->
                    ConversationBox(
                        x = token.x, y = token.y,
                        width = token.width, height = token.height,
                        regionType = RegionType.TOKEN,
                        parentBubbleIndex = entry.bubbleIndex,
                        tokenIndex = token.tokenIndex,
                        text = token.text
                    )
                }
                tokenCache[entry.imageId] = bubbleMap
            }
        } catch (_: Exception) { }
    }

    override fun getTokensForBubble(imageId: String, bubbleIndex: Int): List<ConversationBox> =
        tokenCache[imageId]?.get(bubbleIndex) ?: emptyList()

    override fun hasTokens(imageId: String, bubbleIndex: Int): Boolean =
        tokenCache[imageId]?.containsKey(bubbleIndex) == true
}
