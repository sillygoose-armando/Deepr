# Task P3 — pairing crypto, envelope, invite codec (delegated)

Read `docs/p2p-sync/PLAN.md` sections "Pairing" and "Transport envelope" first. Work ONLY in the files listed below. Do not touch any other file. Do not run git commands.

## Build & test
`cd tools/sync-harness && ../../gradlew -p . test -q` (JDK 17, no Android SDK). All existing tests must keep passing.
The harness compiles `shared/src/commonMain/kotlin/com/yogeshpaliyal/deepr/p2p/**` and `shared/src/jvmShared/kotlin/com/yogeshpaliyal/deepr/p2p/**`. Tests go in `tools/sync-harness/src/test/kotlin/`.

## Hard rules
- Code in `shared/src/commonMain/...` must be **pure Kotlin multiplatform**: NO `java.*`, `javax.*` or `android.*` imports. Use `kotlin.io.encoding.Base64` (UrlSafe, padding ABSENT, `@OptIn(ExperimentalEncodingApi::class)`) and kotlinx.serialization.
- All crypto primitives live behind the interface below. The JVM implementation (`javax.crypto` / `java.security` only, no new dependencies) is the only place they are implemented.
- MAC/tag comparisons must be constant-time (write `constantTimeEquals` in common code).
- Errors from untrusted input throw `PairingException` (new, in Pairing.kt) or `SyncProtocolException` (exists). Never crash with other exception types on garbage input: catch and wrap.

## Files to create
### 1. `shared/src/commonMain/kotlin/com/yogeshpaliyal/deepr/p2p/core/P2pCrypto.kt`
```kotlin
class EcdhKeyPair(val publicKey: ByteArray /* 65-byte uncompressed X9.62 P-256 point */, val privateKey: ByteArray /* 32-byte big-endian scalar */)
interface P2pCrypto {
    fun randomBytes(size: Int): ByteArray
    fun generateEcdhKeyPair(): EcdhKeyPair
    /** 32-byte shared secret (x coordinate). Must reject public keys not on the P-256 curve. */
    fun ecdh(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
    /** Returns ciphertext || 16-byte tag. nonce is 12 bytes. */
    fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray
    /** Throws on authentication failure. */
    fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray): ByteArray
}
fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean
```

### 2. `shared/src/jvmShared/kotlin/com/yogeshpaliyal/deepr/p2p/jvm/JvmP2pCrypto.kt`
`class JvmP2pCrypto : P2pCrypto` using `SecureRandom`, `KeyPairGenerator("EC")` + `ECGenParameterSpec("secp256r1")`, `KeyAgreement("ECDH")`, `Mac("HmacSHA256")`, `Cipher("AES/GCM/NoPadding")` with `GCMParameterSpec(128, nonce)`. HKDF is implemented per RFC 5869 on top of HMAC. Private keys convert to and from the raw 32-byte scalar via `ECPrivateKeySpec`, public keys via `ECPublicKeySpec`. Curve params come from `AlgorithmParameters.getInstance("EC")` initialised with `ECGenParameterSpec("secp256r1")`. Before use, **validate peer points are on the curve** (y² = x³ + ax + b mod p, coordinates < p, length 65, prefix 0x04).

### 3. `shared/src/commonMain/kotlin/com/yogeshpaliyal/deepr/p2p/core/Pairing.kt`
- `@Serializable data class Invite(v: Int = 1, d: String /*inviter deviceId*/, n: String /*inviter name*/, h: List<String> /*hosts*/, p: Int /*port*/, t: String /*b64url 32-byte token*/, k: String /*b64url inviter ephemeral public key*/, s: String /*shareId*/, pn: String /*profile name*/, exp: Long /*epoch seconds*/)`.
- `fun encodeInvite(invite): String` = `"deepr-pair:1:" + base64url(json)`. `fun decodeInvite(code: String, nowEpochSec: Long): Invite` trims whitespace, checks the prefix and v == 1, rejects a code over 4 KB, rejects expiry (`exp < now`), and validates field lengths (ids: 32 lowercase hex; token: 32 bytes; key: 65 bytes; names ≤ 100 chars; ≤ 8 hosts; port in 1..65535). It throws `PairingException`.
- `@Serializable data class JoinRequest(d: String, n: String, k: String /*b64url joiner pub*/, mac: String /*b64url*/)` and `@Serializable data class JoinResponse(mac: String)`.
- Transcript bytes (UTF-8): `"deepr-p2p-v1|" + inviterId + "|" + joinerId + "|" + b64(inviterPub) + "|" + b64(joinerPub) + "|" + shareId`.
- `joinMac = HMAC(token, "join|" + transcript)`; `pairKey = HKDF(ikm = ecdh, salt = token, info = "deepr-p2p-v1|" + inviterId + "|" + joinerId, 32)`; `confirmMac = HMAC(pairKey, "confirm|" + transcript)`; `sas = (first 4 bytes of HKDF(pairKey, salt = empty, info = "sas", 4) as unsigned big-endian int) % 1_000_000`, formatted as 6 digits with leading zeros.
- `class PairingInviter(crypto, deviceId, deviceName)`: `fun createInvite(hosts, port, shareId, profileName, nowEpochSec, ttlSec = 600): Invite`; keeps the ephemeral private key and token in memory, and `fun accept(join: JoinRequest): PairingResult` — **single use**: a second call throws; it also throws if the joiner MAC is wrong (constant-time). `PairingResult(peerId, peerName, pairKey: ByteArray, sas: String, response: JoinResponse)`.
- `class PairingJoiner(crypto, deviceId, deviceName, invite)`: `val request: JoinRequest` and `fun finish(resp: JoinResponse): PairingResult` (verifies confirmMac; the result's response field may hold resp).
- Both sides derive the same pairKey and SAS.

### 4. `shared/src/commonMain/kotlin/com/yogeshpaliyal/deepr/p2p/core/Envelope.kt`
- `@Serializable data class Envelope(peer: String, nonce: String /*b64url 12 bytes*/, ct: String /*b64url*/)`.
- `fun seal(crypto, key, senderId, path, body: String, nowMs: Long): Envelope`. The inner plaintext is the JSON `{"ts": nowMs, "body": body}`; `aad = "v1|" + path + "|" + senderId`; nonce = 12 random bytes.
- `class ReplayGuard(windowMs = 600_000, maxEntries = 10_000)`: `fun checkAndRemember(senderId, nonce: String, ts: Long, nowMs: Long)` rejects `|now - ts| > 300_000` and a nonce already seen from that sender within the window. It evicts old entries; bounded memory. Thread-safety: callers are suspend functions — guard with a `kotlinx.coroutines.sync.Mutex` and a `suspend fun`.
- `suspend fun open(crypto, key, envelope, expectedSender, path, guard, nowMs): String` returns body or throws `SyncProtocolException` (bad base64, wrong sender, auth failure, replay, stale).

## Tests to add (`tools/sync-harness/src/test/kotlin/`)
- `CryptoTest.kt`: HKDF RFC 5869 test case 1 (exact OKM); HMAC-SHA256 RFC 4231 test case 2; AES-GCM roundtrip, and tampered ct/aad/nonce each fail; ECDH both sides agree; an off-curve public key is rejected.
- `PairingTest.kt`: happy path (same key, same SAS, 6 digits); wrong token in the joiner MAC → rejected; tampered joiner public key → rejected; second `accept` → rejected; confirm MAC tampered → joiner rejects; invite encode/decode roundtrip; expired invite, wrong prefix, garbage base64 and oversized code → `PairingException`.
- `EnvelopeTest.kt`: roundtrip; replay rejected; stale timestamp rejected; wrong path (aad) rejected; wrong expected sender rejected.

## Done when
All harness tests pass (`tests=... failures="0" errors="0"` in `tools/sync-harness/build/test-results/test/*.xml`). Then print a short summary of files created and anything you could not do.
