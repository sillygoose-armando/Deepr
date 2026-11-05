package com.yogeshpaliyal.deepr.util

object LanguageUtil {
    // Language code constants
    const val SYSTEM_DEFAULT = ""
    const val ENGLISH = "en"
    const val HINDI = "hi"
    const val SPANISH = "es"
    const val FRENCH = "fr"
    const val GERMAN = "de"
    const val URDU = "ur"

    // Available languages
    data class Language(
        val code: String,
        val name: String,
        val nativeName: String,
    )

    val availableLanguages =
        listOf(
            Language(SYSTEM_DEFAULT, "", ""), // Will be replaced in UI with localized strings
            Language(ENGLISH, "English", "English"),
            Language(HINDI, "Hindi", "हिंदी"),
            Language(SPANISH, "Spanish", "Español"),
            Language(FRENCH, "French", "Français"),
            Language(GERMAN, "German", "Deutsch"),
            Language(URDU, "Urdu", "اردو"),
        )

    /**
     * Gets the display name for a language code
     */
    fun getLanguageDisplayName(languageCode: String): String = availableLanguages.find { it.code == languageCode }?.name ?: ""

    /**
     * Gets the native name for a language code
     */
    fun getLanguageNativeName(languageCode: String): String = availableLanguages.find { it.code == languageCode }?.nativeName ?: ""
}