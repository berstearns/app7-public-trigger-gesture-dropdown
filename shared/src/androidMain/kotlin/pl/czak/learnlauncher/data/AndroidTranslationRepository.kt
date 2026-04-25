package pl.czak.learnlauncher.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.czak.learnlauncher.data.db.AppDatabase
import pl.czak.learnlauncher.data.db.entity.RegionTranslationEntity
import pl.czak.learnlauncher.data.model.RegionTranslation
import pl.czak.learnlauncher.data.repository.TranslationRepository

class AndroidTranslationRepository(
    private val context: Context,
    private val db: AppDatabase
) : TranslationRepository {

    private val translationCache = mutableMapOf<String, RegionTranslation>()

    init {
        loadDummyTranslations()
    }

    private fun loadDummyTranslations() {
        try {
            val json = context.assets.open("_dummy_translations.json").bufferedReader().use { it.readText() }
            val manifest = Gson().fromJson(json, TranslationsManifest::class.java)
            manifest?.translations?.forEach { t ->
                val cacheKey = "${t.imageId}_${t.bubbleIndex}"
                translationCache[cacheKey] = RegionTranslation(
                    id = cacheKey, imageId = t.imageId, bubbleIndex = t.bubbleIndex,
                    originalText = t.originalText, meaningTranslation = t.meaningTranslation,
                    literalTranslation = t.literalTranslation,
                    sourceLanguage = t.sourceLanguage, targetLanguage = t.targetLanguage
                )
            }
        } catch (_: Exception) { }
    }

    override suspend fun getTranslation(imageId: String, bubbleIndex: Int): RegionTranslation? {
        val cacheKey = "${imageId}_${bubbleIndex}"
        translationCache[cacheKey]?.let { return it }
        return withContext(Dispatchers.IO) {
            db.regionTranslationDao().getByImageAndBubble(imageId, bubbleIndex)?.let { entity ->
                entity.toModel().also { translationCache[cacheKey] = it }
            }
        }
    }

    override fun hasTranslation(imageId: String, bubbleIndex: Int): Boolean =
        translationCache.containsKey("${imageId}_${bubbleIndex}")

    override suspend fun getTranslationsForImage(imageId: String): List<RegionTranslation> =
        withContext(Dispatchers.IO) {
            db.regionTranslationDao().getForImage(imageId).map { it.toModel() }
        }

    override suspend fun getAll(): List<RegionTranslation> =
        withContext(Dispatchers.IO) {
            db.regionTranslationDao().getAll().map { it.toModel() }
        }

    override suspend fun saveTranslation(translation: RegionTranslation) {
        withContext(Dispatchers.IO) {
            db.regionTranslationDao().insert(translation.toEntity())
            translationCache["${translation.imageId}_${translation.bubbleIndex}"] = translation
        }
    }

    private fun RegionTranslationEntity.toModel() = RegionTranslation(
        id = id, imageId = imageId, bubbleIndex = bubbleIndex,
        originalText = originalText, meaningTranslation = meaningTranslation,
        literalTranslation = literalTranslation,
        sourceLanguage = sourceLanguage, targetLanguage = targetLanguage
    )

    private fun RegionTranslation.toEntity() = RegionTranslationEntity(
        id = id, imageId = imageId, bubbleIndex = bubbleIndex,
        originalText = originalText, meaningTranslation = meaningTranslation,
        literalTranslation = literalTranslation,
        sourceLanguage = sourceLanguage, targetLanguage = targetLanguage
    )
}

private data class TranslationsManifest(val translations: List<TranslationData>)
private data class TranslationData(
    val imageId: String, val bubbleIndex: Int,
    val originalText: String, val meaningTranslation: String, val literalTranslation: String,
    val sourceLanguage: String = "ja", val targetLanguage: String = "en"
)
