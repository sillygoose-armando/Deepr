package com.yogeshpaliyal.deepr.p2p.node

import com.yogeshpaliyal.deepr.p2p.core.P2pCrypto
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/**
 * Reads a 32-byte AES-256 key from [file], or creates it on first use.
 * The file is created with owner-only permissions (0600 on POSIX; on Windows the OS-level user
 * profile ACL provides equivalent protection).
 */
class FileKeySecretStore(file: File, crypto: P2pCrypto) : AesKeySecretStore(crypto, loadOrCreateKey(file, crypto)) {

    companion object {
        /** 32-byte key length for AES-256. */
        private const val KEY_LEN = 32

        private fun loadOrCreateKey(file: File, crypto: P2pCrypto): ByteArray {
            if (file.exists() && file.isFile() && file.canRead()) {
                val bytes = file.readBytes()
                if (bytes.size == KEY_LEN) return bytes
            }
            val key = crypto.randomBytes(KEY_LEN)
            writeKeyFile(file, key)
            return key
        }

        private fun writeKeyFile(file: File, key: ByteArray) {
            val parent = file.parentFile
            if (parent != null) parent.mkdirs()
            // A truncated/corrupt key file can't decrypt anything anyway; replace it (createFile needs a fresh path).
            Files.deleteIfExists(file.toPath())
            // On POSIX set 0600 before writing; on Windows the user profile ACL applies.
            try {
                Files.createFile(
                    file.toPath(),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
                )
            } catch (e: UnsupportedOperationException) {
                // Windows does not support POSIX permissions; the user's profile ACL is equivalent.
                file.createNewFile()
            }
            file.writeBytes(key)
        }
    }
}