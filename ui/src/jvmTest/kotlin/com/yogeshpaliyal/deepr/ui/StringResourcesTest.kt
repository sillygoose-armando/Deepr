package com.yogeshpaliyal.deepr.ui

import com.yogeshpaliyal.deepr.ui.resources.Res
import com.yogeshpaliyal.deepr.ui.resources.allStringResources
import com.yogeshpaliyal.deepr.ui.resources.delete_link_confirmation_with_name
import com.yogeshpaliyal.deepr.ui.resources.local_server_instructions
import com.yogeshpaliyal.deepr.ui.resources.silent_save_profile_description
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

class StringResourcesTest {

    companion object {
        // On this headless test JVM the default system environment crashes while skiko loads
        // its native theme library. Override it with a fixed default-locale environment.
        // The ResourceEnvironment constructor and the environment hook are internal to the
        // Compose resources library, so this is done reflectively at JVM level.
        init {
            val resourcesPkg = "org.jetbrains.compose.resources"
            val langClass = Class.forName("$resourcesPkg.LanguageQualifier")
            val regionClass = Class.forName("$resourcesPkg.RegionQualifier")
            val themeClass = Class.forName("$resourcesPkg.ThemeQualifier")
            val densityClass = Class.forName("$resourcesPkg.DensityQualifier")
            val lang = langClass.getConstructor(String::class.java).newInstance("en")
            val region = regionClass.getConstructor(String::class.java).newInstance("")
            val theme = themeClass.enumConstants.first { (it as Enum<*>).name == "LIGHT" }
            val density = densityClass.enumConstants.first { (it as Enum<*>).name == "MDPI" }
            val environment = Class.forName("$resourcesPkg.ResourceEnvironment")
                .getConstructor(langClass, regionClass, themeClass, densityClass)
                .newInstance(lang, region, theme, density)
            // The hook field holds a KFunction, but its only consumer casts it to Function0.
            // A proxy satisfies both; the member methods just return inert defaults.
            val hookField = Class.forName("$resourcesPkg.ResourceEnvironmentKt")
                .getDeclaredField("getResourceEnvironment")
            hookField.isAccessible = true
            var identity: Any? = null
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                StringResourcesTest::class.java.classLoader,
                arrayOf(Class.forName("kotlin.jvm.functions.Function0"), Class.forName("kotlin.reflect.KFunction")),
            ) { _, method, args ->
                when (method.name) {
                    "invoke", "call", "callBy" -> environment
                    "equals" -> identity === args?.firstOrNull()
                    "hashCode" -> environment.hashCode()
                    "toString" -> "StringResourcesTest default-locale environment"
                    "getAnnotations", "annotations", "annotationsByType", "typeParameters", "parameters", "valueParameters" -> emptyList<Any>()
                    "isAbstract", "isFinal", "isOpen", "isSuspend", "isInline", "isOperator",
                    "isInfix", "isExternal", "isExpect", "isActual",
                    -> false
                    "getName", "name" -> "getResourceEnvironment"
                    "getReturnType", "returnType" -> environment.javaClass
                    else -> when (method.returnType) {
                        java.lang.Boolean::class.javaPrimitiveType -> false
                        Integer::class.javaPrimitiveType, java.lang.Long::class.javaPrimitiveType -> 0
                        else -> null
                    }
                }
            }
            identity = proxy
            hookField.set(null, proxy)
        }
    }

    private fun parseStringNames(file: File): List<String> {
        assertTrue(file.exists(), "strings.xml not found at ${file.absolutePath}")
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(file)
        val strings = doc.getElementsByTagName("string")
        return (0 until strings.length).map { i ->
            strings.item(i).attributes.getNamedItem("name").nodeValue
        }
    }

    // The test runs with its working dir set to the ui module dir, so walk up to the repo root.
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "app/src/main/res/values/strings.xml").exists()) {
            dir = dir.parentFile
        }
        return dir ?: error("Could not locate repo root containing app/src/main/res")
    }

    @Test
    fun deleteLinkConfirmationWithArgSubstitutesAndUnescapesApostrophes() = runBlocking {
        val text = getString(Res.string.delete_link_confirmation_with_name, "x")
        assertEquals("Are you sure you want to delete 'x'?", text)
    }

    @Test
    fun silentSaveProfileDescriptionHasPlainApostrophes() = runBlocking {
        val text = getString(Res.string.silent_save_profile_description)
        assertEquals("Links saved via 'Save Silently' will go to this profile", text)
    }

    @Test
    fun newlineEscapesDecodeAsRealNewlines() = runBlocking {
        val text = getString(Res.string.local_server_instructions)
        assertTrue(text.contains("2. Make sure both devices are on the same Wi-Fi network"))
        assertTrue('\n' in text, "expected \n escapes to decode as real newlines, got: $text")
        assertTrue("\\n" !in text, "literal backslash-n must not remain, got: $text")
    }

    @Test
    fun everyAndroidDefaultStringExistsInRes() {
        val androidNames =
            parseStringNames(File(repoRoot(), "app/src/main/res/values/strings.xml")).toSet()
        val composeNames = Res.allStringResources.keys
        val missing = androidNames - composeNames
        assertTrue(missing.isEmpty(), "Missing from Res.allStringResources: $missing")
        assertTrue("ui_smoke" in composeNames, "pre-existing ui_smoke resource must stay")
    }

    @Test
    fun localeFilesCoverTheSameNamesAsAndroid() {
        val composeResDir = File(repoRoot(), "ui/src/commonMain/composeResources")
        val languages = listOf("de", "es", "et", "fr", "hi", "it", "ja", "pl", "ru", "ur")
        for (lang in languages) {
            val androidNames =
                parseStringNames(File(repoRoot(), "app/src/main/res/values-$lang/strings.xml")).toSet()
            val composeNames =
                parseStringNames(File(composeResDir, "values-$lang/strings.xml")).toSet()
            assertEquals(androidNames, composeNames, "locale $lang name mismatch")
        }
    }
}
