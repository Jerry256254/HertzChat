// Hertz Relay - a minimal relay for Hertz Chat with one hard rule:
//
//   NOTHING IS STORED. NOTHING IS LOGGED.
//
// - Only Nostr *ephemeral* events (kinds 20000-29999) are accepted, and only
//   forwarded to currently-subscribed sockets. There is no database, no file
//   write, no message log anywhere in this file.
// - No IP address, pubkey, timestamp or event content is ever printed, written
//   or kept beyond the RAM needed to route this second's traffic. The only
//   stdout lines are the startup banner and hourly aggregate counters.
// - Per-IP connection caps exist purely as in-memory flood protection (never
//   persisted, never logged); set RATE_LIMIT=0 to disable them entirely.
//
// Run:  npm install && node relay.js   (PORT=8080 RATE_LIMIT=1)
// Put it behind any TLS terminator (Caddy, nginx) for wss://.

import { createServer } from 'node:http';
import { WebSocketServer } from 'ws';
import { createHash } from 'node:crypto';
import { schnorr } from '@noble/curves/secp256k1.js';

const PORT = Number(process.env.PORT ?? 8080);
const RATE_LIMIT = (process.env.RATE_LIMIT ?? '1') !== '0';
const MAX_MESSAGE_BYTES = 128 * 1024;
const MAX_SUBS_PER_CONN = 10;
const MAX_CONNS_PER_IP = 20;
const MAX_FILTER_KEYS = 12;

const KIND_MIN = 20000;
const KIND_MAX = 29999;

// Aggregate-only counters for the hourly stdout line. No per-client data.
let forwardedTotal = 0;
let rejectedTotal = 0;
let connectionsTotal = 0;

// Transient flood-protection state (RAM only, never logged or persisted).
const connsPerIp = new Map();

const server = createServer((req, res) => {
  // NIP-11 relay information document.
  if (req.headers.accept === 'application/nostr+json') {
    res.writeHead(200, { 'Content-Type': 'application/nostr+json' });
    res.end(JSON.stringify({
      name: 'hertz-relay',
      description: 'Zero-storage ephemeral relay for Hertz Chat. Nothing is stored, nothing is logged.',
      supported_nips: [1, 11, 16],
      limitation: { max_message_length: MAX_MESSAGE_BYTES, max_subscriptions: MAX_SUBS_PER_CONN },
      retention: [],
    }));
    return;
  }
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('hertz-relay: speak Nostr over this websocket.\n');
});

const wss = new WebSocketServer({ server, maxPayload: MAX_MESSAGE_BYTES });

function canonicalEventId(ev) {
  const serialized = JSON.stringify([0, ev.pubkey, ev.created_at, ev.kind, ev.tags, ev.content]);
  return createHash('sha256').update(serialized).digest('hex');
}

function validEvent(ev) {
  try {
    if (!ev || typeof ev !== 'object') return false;
    if (typeof ev.id !== 'string' || typeof ev.pubkey !== 'string') return false;
    if (!Number.isInteger(ev.created_at) || !Number.isInteger(ev.kind)) return false;
    if (!Array.isArray(ev.tags) || typeof ev.content !== 'string' || typeof ev.sig !== 'string') return false;
    if (ev.kind < KIND_MIN || ev.kind > KIND_MAX) return false; // ephemeral only
    if (ev.id.toLowerCase() !== canonicalEventId(ev)) return false;
    return schnorr.verify(ev.sig, ev.id, ev.pubkey);
  } catch {
    return false;
  }
}

function filterMatches(filter, ev) {
  if (typeof filter !== 'object' || filter === null) return false;
  if (Object.keys(filter).length > MAX_FILTER_KEYS) return false;
  if (filter.ids && !filter.ids.some((id) => typeof id === 'string' && ev.id.startsWith(id.toLowerCase()))) return false;
  if (filter.kinds && !filter.kinds.includes(ev.kind)) return false;
  if (filter.authors && !filter.authors.some((a) => typeof a === 'string' && ev.pubkey.startsWith(a.toLowerCase()))) return false;
  if (Number.isInteger(filter.since) && ev.created_at < filter.since) return false;
  if (Number.isInteger(filter.until) && ev.created_at > filter.until) return false;
  for (const [key, values] of Object.entries(filter)) {
    if (!key.startsWith('#') || !Array.isArray(values)) continue;
    const tag = key.slice(1);
    const hit = ev.tags.some((t) => Array.isArray(t) && t[0] === tag && values.includes(t[1]));
    if (!hit) return false;
  }
  return true;
}

function subMatches(filters, ev) {
  return Array.isArray(filters) && filters.some((f) => filterMatches(f, ev));
}

// Socket -> its subscriptions. The ONLY per-client structure, RAM-only.
const clientSubs = new WeakMap();

wss.on('connection', (ws, req) => {
  const ip = req.socket.remoteAddress ?? 'unknown';
  if (RATE_LIMIT) {
    const count = (connsPerIp.get(ip) ?? 0) + 1;
    connsPerIp.set(ip, count);
    if (count > MAX_CONNS_PER_IP) {
      ws.close(1013, 'too many connections');
      return;
    }
    ws.on('close', () => {
      const left = (connsPerIp.get(ip) ?? 1) - 1;
      if (left <= 0) connsPerIp.delete(ip);
      else connsPerIp.set(ip, left);
    });
  }
  connectionsTotal++;
  const subs = new Map(); // subId -> filters (RAM only, dropped on disconnect)
  let alive = true;
  ws.on('pong', () => { alive = true; });
  const heartbeat = setInterval(() => {
    if (!alive) { clearInterval(heartbeat); ws.terminate(); return; }
    alive = false;
    ws.ping();
  }, 30000);

  ws.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString('utf8'));
    } catch {
      return;
    }
    if (!Array.isArray(msg) || typeof msg[0] !== 'string') return;
    const [verb] = msg;

    if (verb === 'EVENT') {
      const ev = msg[1];
      const ok = validEvent(ev);
      if (!ok) {
        rejectedTotal++;
        safeSend(ws, JSON.stringify(['OK', ev?.id ?? '', false, 'invalid: ephemeral signed events only']));
        return;
      }
      safeSend(ws, JSON.stringify(['OK', ev.id, true, '']));
      // Fan out to matching subscriptions. No storage, no log, no copy kept.
      for (const client of wss.clients) {
        if (client.readyState !== 1) continue;
        const theirSubs = clientSubs.get(client);
        if (!theirSubs) continue;
        for (const [subId, filters] of theirSubs) {
          if (subMatches(filters, ev)) {
            forwardedTotal++;
            safeSend(client, JSON.stringify(['EVENT', subId, ev]));
          }
        }
      }
      return;
    }

    if (verb === 'REQ') {
      const subId = msg[1];
      const filters = msg.slice(2);
      if (typeof subId !== 'string' || subId.length === 0 || subId.length > 128) return;
      if (!Array.isArray(filters) || filters.length === 0) return;
      if (subs.size >= MAX_SUBS_PER_CONN && !subs.has(subId)) {
        safeSend(ws, JSON.stringify(['CLOSED', subId, 'too many subscriptions']));
        return;
      }
      subs.set(subId, filters);
      clientSubs.set(ws, subs);
      // Ephemeral relays hold no history, so EOSE follows immediately.
      safeSend(ws, JSON.stringify(['EOSE', subId]));
      return;
    }

    if (verb === 'CLOSE') {
      subs.delete(msg[1]);
      return;
    }

    if (verb === 'AUTH' || verb === 'COUNT') {
      safeSend(ws, JSON.stringify(['NOTICE', 'unsupported']));
      return;
    }
  });

  ws.on('close', () => {
    clearInterval(heartbeat);
    clientSubs.delete(ws);
  });
  ws.on('error', () => {});
});

function safeSend(ws, text) {
  if (ws.readyState === 1) {
    try { ws.send(text); } catch { /* gone */ }
  }
}

server.listen(PORT, () => {
  console.log(`hertz-relay listening on :${PORT} (ephemeral only, zero storage)`);
});

// Hourly aggregate counters only. No IPs, no keys, no contents, ever.
setInterval(() => {
  console.log(`hertz-relay stats: connections=${connectionsTotal} forwarded=${forwardedTotal} rejected=${rejectedTotal}`);
}, 3600_000);
