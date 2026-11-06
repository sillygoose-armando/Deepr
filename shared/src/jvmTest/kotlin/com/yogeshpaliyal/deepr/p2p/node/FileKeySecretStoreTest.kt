package com.yogeshpaliyal.deepr.p2p.node

import com.yogeshpaliyal.deepr.p2p.jvm.JvmP2pCrypto
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FileKeySecretStoreTest {

    private val crypto = JvmP2pCrypto()

    @Test
    fun roundtrip() {
        val tmp = File.createTempFile("deepr-test", ".key")
        tmp.deleteOnExit()
        tmp.delete() // FileKeySecretStore will create it
        val store = FileKeySecretStore(tmp, crypto)
        val plain = crypto.randomBytes(64)
        val sealed = store.seal(plain)
        val opened = store.open(sealed)
        assertNotNull(opened)
        assertEquals(plain.toList(), opened.toList())
    }

    @Test
    fun secondInstanceOnSameFileOpensSameSealed() {
        val tmp = File.createTempFile("deepr-test", ".key")
        tmp.deleteOnExit()
        tmp.delete()
        val store1 = FileKeySecretStore(tmp, crypto)
        val plain = crypto.randomBytes(32)
        val sealed = store1.seal(plain)

        val store2 = FileKeySecretStore(tmp, crypto)
        val opened = store2.open(sealed)
        assertNotNull(opened)
        assertEquals(plain.toList(), opened.toList())
    }

    @Test
    fun differentFileReturnsNull() {
        val tmp1 = File.createTempFile("deepr-test1", ".key")
        tmp1.deleteOnExit()
        tmp1.delete()
        val tmp2 = File.createTempFile("deepr-test2", ".key")
        tmp2.deleteOnExit()
        tmp2.delete()

        val store1 = FileKeySecretStore(tmp1, crypto)
        val sealed = store1.seal(crypto.randomBytes(32))

        val store2 = FileKeySecretStore(tmp2, crypto)
        assertNull(store2.open(sealed))
    }

    @Test
    fun posixFileModeIs0600() {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("win") || os.contains("mac") == false) {
            // Only test on Linux where POSIX permissions are meaningful
            val tmp = File.createTempFile("deepr-test", ".key")
            tmp.deleteOnExit()
            tmp.delete()
            val store = FileKeySecretStore(tmp, crypto)
            assertNotNull(store)
            val perms = Files.getPosixFilePermissions(tmp.toPath())
            val expected = java.util.EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
            )
            assertEquals(expected, perms, "file must be rw-------")
        }
    }

    @Test
    fun corruptKeyFileIsReplacedNotCrashing() {
        val tmp = File.createTempFile("deepr-test", ".key")
        tmp.deleteOnExit()
        tmp.writeBytes(ByteArray(5)) // wrong size
        val store = FileKeySecretStore(tmp, crypto)
        assertEquals(32, tmp.readBytes().size)
        val plain = crypto.randomBytes(16)
        assertEquals(plain.toList(), store.open(store.seal(plain))?.toList())
    }
}
