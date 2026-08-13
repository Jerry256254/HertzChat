package cz.kuclab.hertzchat.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cz.kuclab.hertzchat.data.db.AppDatabase
import cz.kuclab.hertzchat.data.db.KyberPreKeyEntity
import cz.kuclab.hertzchat.data.db.PreKeyEntity
import cz.kuclab.hertzchat.data.db.SignedPreKeyEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

private const val PREFS_NAME = "hertzchat_identity_prefs"
private const val KEY_IDENTITY_KEYPAIR = "identity_keypair"
private const val KEY_REGISTRATION_ID = "registration_id"
private const val KEY_NICKNAME = "nickname"
private const val KEY_I2P_PRIVATE_KEY = "i2p_private_key"
private const val KEY_I2P_DESTINATION = "i2p_destination"

private const val ONE_TIME_PREKEY_COUNT = 100
private const val ONE_TIME_PREKEY_LOW_WATERMARK = 20

/**
 * Owns the device-bound cryptographic identity: one long-term Ed/X25519
 * identity key pair, generated once on this device and never sent anywhere
 * in the clear. There is no phone number, e-mail or central account behind
 * it - the identity key itself *is* the account.
 */
@Singleton
class IdentityKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val hasIdentity: Boolean
        get() = prefs.contains(KEY_IDENTITY_KEYPAIR)

    var nickname: String
        get() = prefs.getString(KEY_NICKNAME, null) ?: generateAnonymousNickname()
        set(value) = prefs.edit().putString(KEY_NICKNAME, value).apply()

    private var cachedIdentity: IdentityKeyPair? = null

    fun identityKeyPair(): IdentityKeyPair {
        cachedIdentity?.let { return it }
        val stored = prefs.getString(KEY_IDENTITY_KEYPAIR, null)
        val pair = if (stored != null) {
            IdentityKeyPair(Base64.decode(stored, Base64.NO_WRAP))
        } else {
            IdentityKeyPair.generate().also {
                prefs.edit().putString(KEY_IDENTITY_KEYPAIR, Base64.encodeToString(it.serialize(), Base64.NO_WRAP)).apply()
            }
        }
        cachedIdentity = pair
        return pair
    }

    fun registrationId(): Int {
        if (prefs.contains(KEY_REGISTRATION_ID)) return prefs.getInt(KEY_REGISTRATION_ID, 0)
        val id = Random.nextInt(1, 16384)
        prefs.edit().putInt(KEY_REGISTRATION_ID, id).apply()
        return id
    }

    /** Stable, shareable contact ID derived from the public identity key - this is what you show a friend so they can find/add you. */
    fun contactId(): String = contactIdFor(identityKeyPair().publicKey.serialize())

    /** Opaque I2P destination keypair blob - empty until [I2pTransport][cz.kuclab.hertzchat.network.p2p.I2pTransport] first opens our destination, after which it's persisted so the address stays stable across restarts. */
    var i2pPrivateKey: String
        get() = prefs.getString(KEY_I2P_PRIVATE_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_I2P_PRIVATE_KEY, value).apply()

    var i2pDestination: String
        get() = prefs.getString(KEY_I2P_DESTINATION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_I2P_DESTINATION, value).apply()

    fun contactIdFor(identityKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(identityKeyBytes)
        return Base64.encodeToString(digest.copyOfRange(0, 16), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Creates the identity (if it doesn't exist yet) and provisions the initial
     * batch of one-time prekeys, the signed prekey and the post-quantum Kyber
     * prekey that a peer needs to run X3DH against us the first time we're offline.
     */
    fun ensureIdentityAndPreKeys(chosenNickname: String?) {
        identityKeyPair()
        registrationId()
        nickname = chosenNickname?.takeIf { it.isNotBlank() } ?: nickname

        topUpOneTimePreKeysIfNeeded()
        ensureSignedPreKey()
        ensureKyberPreKey()
    }

    private fun topUpOneTimePreKeysIfNeeded() {
        val dao = database.preKeyDao()
        if (dao.count() >= ONE_TIME_PREKEY_LOW_WATERMARK) return
        var nextId = (dao.maxId() ?: 0) + 1
        repeat(ONE_TIME_PREKEY_COUNT) {
            val keyPair = ECKeyPair.generate()
            dao.upsert(PreKeyEntity(nextId, PreKeyRecord(nextId, keyPair).serialize()))
            nextId++
        }
    }

    private fun ensureSignedPreKey() {
        val dao = database.signedPreKeyDao()
        if (dao.findAll().isNotEmpty()) return
        val identity = identityKeyPair()
        val keyPair = ECKeyPair.generate()
        val signature = identity.privateKey.calculateSignature(keyPair.publicKey.serialize())
        val id = 1
        dao.upsert(SignedPreKeyEntity(id, SignedPreKeyRecord(id, System.currentTimeMillis(), keyPair, signature).serialize()))
    }

    private fun ensureKyberPreKey() {
        val dao = database.kyberPreKeyDao()
        if (dao.findAll().isNotEmpty()) return
        val identity = identityKeyPair()
        val keyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val signature = identity.privateKey.calculateSignature(keyPair.publicKey.serialize())
        val id = 1
        dao.upsert(KyberPreKeyEntity(id, KyberPreKeyRecord(id, System.currentTimeMillis(), keyPair, signature).serialize()))
    }

    /** Builds the bundle that gets published over the signaling channel while we're online so a new contact can start a session with us. */
    fun currentPreKeyBundle(deviceId: Int = 1): PreKeyBundle {
        val preKeyDao = database.preKeyDao()
        val signedPreKey = database.signedPreKeyDao().findAll().first()
        val kyberPreKey = database.kyberPreKeyDao().findAll().first { true }
        val oneTime = preKeyDao.maxId()?.let { preKeyDao.find(it) }
        val signedRecord = SignedPreKeyRecord(signedPreKey.record)
        val kyberRecord = KyberPreKeyRecord(kyberPreKey.record)
        val oneTimeRecord = oneTime?.let { PreKeyRecord(it.record) }

        return PreKeyBundle(
            registrationId(),
            deviceId,
            oneTimeRecord?.id ?: PreKeyBundle.NULL_PRE_KEY_ID,
            oneTimeRecord?.keyPair?.publicKey,
            signedRecord.id,
            signedRecord.keyPair.publicKey,
            signedRecord.signature,
            identityKeyPair().publicKey,
            kyberRecord.id,
            kyberRecord.keyPair.publicKey,
            kyberRecord.signature,
        )
    }

    /**
     * Everything needed for a *new* device to take over this identity so
     * existing contacts keep finding/reaching the same contactId. Message
     * history and media are intentionally not included - they only ever
     * live on the device that received them (no cloud, nothing to migrate
     * except the cryptographic identity itself); a QR code also physically
     * cannot carry gigabytes of chat history.
     */
    fun exportIdentityJson(): String {
        val payload = buildString {
            append('{')
            append("\"identityKeyPair\":\"").append(Base64.encodeToString(identityKeyPair().serialize(), Base64.NO_WRAP)).append("\",")
            append("\"registrationId\":").append(registrationId()).append(',')
            append("\"nickname\":\"").append(nickname.replace("\"", "")).append("\",")
            append("\"i2pPrivateKey\":\"").append(i2pPrivateKey).append('"')
            append('}')
        }
        return payload
    }

    /**
     * Overwrites the local identity with one exported from another device,
     * including the I2P destination key - without that, contacts would keep
     * trying to reach the old device's destination and never find the
     * new one. Only ever call this from a dedicated migration flow the user
     * explicitly confirmed.
     */
    fun importIdentityJson(json: String) {
        val obj = org.json.JSONObject(json)
        val keyPairBytes = Base64.decode(obj.getString("identityKeyPair"), Base64.NO_WRAP)
        cachedIdentity = null
        prefs.edit()
            .putString(KEY_IDENTITY_KEYPAIR, Base64.encodeToString(keyPairBytes, Base64.NO_WRAP))
            .putInt(KEY_REGISTRATION_ID, obj.getInt("registrationId"))
            .putString(KEY_NICKNAME, obj.getString("nickname"))
            .putString(KEY_I2P_PRIVATE_KEY, obj.optString("i2pPrivateKey", ""))
            .remove(KEY_I2P_DESTINATION) // republished on next start; keep the key, drop the cached address until I2P confirms it
            .apply()
    }

    private fun generateAnonymousNickname(): String {
        val adjectives = listOf("Rychlý", "Tichý", "Modrý", "Stínový", "Divoký", "Volný", "Jasný", "Chladný")
        val animals = listOf("Sokol", "Vlk", "Rys", "Jestřáb", "Liška", "Orel", "Tygr", "Delfín")
        val n = SecureRandom().nextInt(10000)
        return "${adjectives.random()}${animals.random()}$n"
    }
}
