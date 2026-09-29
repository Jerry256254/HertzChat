package cz.kuclab.hertzchat.p2p

import cz.kuclab.hertzchat.data.db.ContactDao
import cz.kuclab.hertzchat.data.repository.SettingsRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** At most one wake-up ping per contact per window - a dead peer must not turn into a ping flood. */
private const val PING_MIN_INTERVAL_MS = 5 * 60_000L

/**
 * The Google-free wake-up layer: pings contacts whose direct P2P keeps
 * failing, and polls our own topic for pings others sent us. Both sides go
 * through plain ntfy with empty bodies - the pings carry zero content, and
 * topics are random ids exchanged only over established P2P channels.
 *
 * Direction P2pChatService -> PushPinger only (never the reverse), so there
 * is no injection cycle: the service asks for pings, the worker asks for
 * polls, neither gets called back.
 */
@Singleton
class PushPinger @Inject constructor(
    private val contactDao: ContactDao,
    private val settingsRepository: SettingsRepository,
) {
    private val lastPingAt = ConcurrentHashMap<String, Long>()

    /**
     * Wakes [contactId] when we have mail for them that direct P2P can't
     * deliver. No-op (false) when push is off, the peer never shared a
     * topic (old version), or we pinged them within the rate window.
     * Never throws - a failed ping must never break the retry loop.
     */
    suspend fun maybePing(contactId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsRepository.settings.first()
            if (!settings.pushWakeEnabled) return@runCatching false
            val contact = contactDao.find(contactId) ?: return@runCatching false
            val topic = contact.pushTopic ?: return@runCatching false
            if (contact.blocked) return@runCatching false
            val now = System.currentTimeMillis()
            if (now - (lastPingAt[contactId] ?: 0L) < PING_MIN_INTERVAL_MS) return@runCatching false
            lastPingAt[contactId] = now
            NtfyPing.publish(settings.ntfyServerUrl, topic)
        }.getOrDefault(false)
    }

    /**
     * Checks our own topic for pings. True means "someone has mail for us" -
     * the caller (the periodic worker) starts the service so the queued
     * messages can flow. Advances the cursor on every successful poll, so
     * each ping wakes us at most once. Never throws.
     */
    suspend fun pollForPings(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsRepository.settings.first()
            if (!settings.pushWakeEnabled) return@runCatching false
            val topic = settingsRepository.pushTopic()
            val since = settingsRepository.pushLastSeenId()
            val latest = NtfyPing.pollLatestId(settings.ntfyServerUrl, topic, since) ?: return@runCatching false
            settingsRepository.setPushLastSeenId(latest)
            // First poll ever only plants the cursor - cached history is not a wake-up.
            since != null && latest != since
        }.getOrDefault(false)
    }
}
