package com.yogeshpaliyal.deepr.data

import com.yogeshpaliyal.deepr.preference.PreferenceRepository
import com.yogeshpaliyal.deepr.util.isValidDeeplink
import com.yogeshpaliyal.deepr.util.normalizeLink
import kotlinx.coroutines.flow.first

sealed interface SilentSaveResult {
    data object Saved : SilentSaveResult
    data object AlreadyExists : SilentSaveResult
    data object InvalidLink : SilentSaveResult
    data class Failed(val error: Throwable) : SilentSaveResult
}

class SilentSaver(
    private val linkRepository: LinkRepository,
    private val networkRepository: NetworkRepository,
    private val preferenceRepository: PreferenceRepository,
) {
    suspend fun save(sharedText: String, title: String?): SilentSaveResult {
        val normalizedLink = normalizeLink(sharedText)
        if (!isValidDeeplink(normalizedLink)) {
            return SilentSaveResult.InvalidLink
        }

        return try {
            val profileId = preferenceRepository.getSilentSaveProfileId.first()

            // Check if link already exists
            val existingLink = linkRepository.getDeeprByLink(normalizedLink)
            if (existingLink != null) {
                return SilentSaveResult.AlreadyExists
            }

            var finalTitle = title ?: ""
            var finalThumbnail = ""

            // If title or thumbnail is blank, try to fetch it
            if (finalTitle.isBlank() || finalThumbnail.isBlank()) {
                networkRepository.getLinkInfo(normalizedLink).onSuccess { linkInfo ->
                    if (finalTitle.isBlank()) {
                        finalTitle = linkInfo.title ?: ""
                    }
                    if (finalThumbnail.isBlank()) {
                        finalThumbnail = linkInfo.image ?: ""
                    }
                }
            }

            linkRepository.insertDeepr(
                link = normalizedLink,
                name = finalTitle,
                openedCount = 0,
                notes = "",
                thumbnail = finalThumbnail,
                profileId = profileId,
            )

            SilentSaveResult.Saved
        } catch (e: Exception) {
            SilentSaveResult.Failed(e)
        }
    }
}
