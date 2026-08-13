package cz.kuclab.hertzchat.crypto

import cz.kuclab.hertzchat.data.db.IdentityDao
import cz.kuclab.hertzchat.data.db.IdentityEntity
import cz.kuclab.hertzchat.data.db.KyberPreKeyDao
import cz.kuclab.hertzchat.data.db.KyberPreKeyEntity
import cz.kuclab.hertzchat.data.db.PreKeyDao
import cz.kuclab.hertzchat.data.db.PreKeyEntity
import cz.kuclab.hertzchat.data.db.SessionDao
import cz.kuclab.hertzchat.data.db.SessionEntity
import cz.kuclab.hertzchat.data.db.SignedPreKeyDao
import cz.kuclab.hertzchat.data.db.SignedPreKeyEntity
import java.util.UUID
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.groups.state.SenderKeyStore
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore

/**
 * Trust-on-first-use identity policy: the first identity key ever seen for a
 * given contact address is pinned. Any later message claiming a *different*
 * identity key for the same address is rejected until the user explicitly
 * re-verifies (safety-number reset) - this is what protects against a
 * man-in-the-middle silently swapping in their own key on the P2P link.
 */
class RoomSignalProtocolStore(
    private val localIdentity: IdentityKeyPair,
    private val localRegistrationId: Int,
    private val sessionDao: SessionDao,
    private val identityDao: IdentityDao,
    private val preKeyDao: PreKeyDao,
    private val signedPreKeyDao: SignedPreKeyDao,
    private val kyberPreKeyDao: KyberPreKeyDao,
) : SignalProtocolStore {

    private val senderKeys = mutableMapOf<String, SenderKeyRecord>()

    private fun key(address: SignalProtocolAddress) = "${address.name}:${address.deviceId}"

    // --- IdentityKeyStore ---

    override fun getIdentityKeyPair(): IdentityKeyPair = localIdentity

    override fun getLocalRegistrationId(): Int = localRegistrationId

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
    ): IdentityKeyStore.IdentityChange {
        val existing = identityDao.find(key(address))
        val changed = existing != null && !existing.identityKey.contentEquals(identityKey.serialize())
        identityDao.upsert(
            IdentityEntity(
                address = key(address),
                identityKey = identityKey.serialize(),
                firstSeenAt = existing?.firstSeenAt ?: System.currentTimeMillis(),
            ),
        )
        return if (changed) IdentityKeyStore.IdentityChange.REPLACED_EXISTING else IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean {
        val existing = identityDao.find(key(address)) ?: return true // trust on first use
        return existing.identityKey.contentEquals(identityKey.serialize())
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val existing = identityDao.find(key(address)) ?: return null
        return IdentityKey(existing.identityKey)
    }

    // --- PreKeyStore ---

    override fun loadPreKey(id: Int): PreKeyRecord {
        val entity = preKeyDao.find(id) ?: throw InvalidKeyIdException("No such one-time prekey: $id")
        return PreKeyRecord(entity.record)
    }

    override fun storePreKey(id: Int, record: PreKeyRecord) {
        preKeyDao.upsert(PreKeyEntity(id, record.serialize()))
    }

    override fun containsPreKey(id: Int): Boolean = preKeyDao.find(id) != null

    override fun removePreKey(id: Int) = preKeyDao.delete(id)

    // --- SignedPreKeyStore ---

    override fun loadSignedPreKey(id: Int): SignedPreKeyRecord {
        val entity = signedPreKeyDao.find(id) ?: throw InvalidKeyIdException("No such signed prekey: $id")
        return SignedPreKeyRecord(entity.record)
    }

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> =
        signedPreKeyDao.findAll().map { SignedPreKeyRecord(it.record) }.toMutableList()

    override fun storeSignedPreKey(id: Int, record: SignedPreKeyRecord) {
        signedPreKeyDao.upsert(SignedPreKeyEntity(id, record.serialize()))
    }

    override fun containsSignedPreKey(id: Int): Boolean = signedPreKeyDao.find(id) != null

    override fun removeSignedPreKey(id: Int) = signedPreKeyDao.delete(id)

    // --- KyberPreKeyStore ---

    override fun loadKyberPreKey(id: Int): KyberPreKeyRecord {
        val entity = kyberPreKeyDao.find(id) ?: throw InvalidKeyIdException("No such kyber prekey: $id")
        return KyberPreKeyRecord(entity.record)
    }

    override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> =
        kyberPreKeyDao.findAll().map { KyberPreKeyRecord(it.record) }.toMutableList()

    override fun storeKyberPreKey(id: Int, record: KyberPreKeyRecord) {
        kyberPreKeyDao.upsert(KyberPreKeyEntity(id, record.serialize()))
    }

    override fun containsKyberPreKey(id: Int): Boolean = kyberPreKeyDao.find(id) != null

    override fun markKyberPreKeyUsed(
        id: Int,
        deviceIdUnused: Int,
        baseKeyUnused: org.signal.libsignal.protocol.ecc.ECPublicKey,
    ) {
        kyberPreKeyDao.markUsed(id)
    }

    // --- SessionStore ---

    override fun loadSession(address: SignalProtocolAddress): SessionRecord? {
        val entity = sessionDao.find(key(address)) ?: return SessionRecord()
        return SessionRecord(entity.record)
    }

    override fun loadExistingSessions(
        addresses: MutableList<SignalProtocolAddress>,
    ): MutableList<SessionRecord> =
        addresses.mapNotNull { addr -> sessionDao.find(key(addr))?.let { SessionRecord(it.record) } }.toMutableList()

    override fun getSubDeviceSessions(name: String): MutableList<Int> =
        sessionDao.findAllForName(name).map { it.address.substringAfterLast(":").toInt() }.toMutableList()

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        sessionDao.upsert(SessionEntity(key(address), record.serialize()))
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean = sessionDao.find(key(address)) != null

    override fun deleteSession(address: SignalProtocolAddress) = sessionDao.delete(key(address))

    override fun deleteAllSessions(name: String) = sessionDao.deleteAllForName(name)

    // --- SenderKeyStore (unused for 1:1 P2P chat, kept in-memory to satisfy the interface) ---

    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
        senderKeys["${key(sender)}:$distributionId"] = record
    }

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? =
        senderKeys["${key(sender)}:$distributionId"]
}
