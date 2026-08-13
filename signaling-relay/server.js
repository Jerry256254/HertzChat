'use strict';

/**
 * Hertz Chat signaling relay.
 *
 * This process holds no database and writes no logs of message content. Its
 * only job is: (1) let a client announce "I'm online as <contactId>", (2)
 * let clients list who else is currently online, and (3) blindly forward
 * opaque JSON envelopes (WebRTC offers/answers/ICE candidates, friend
 * requests) between two online clients by contactId. It never decrypts,
 * inspects or persists the payload it relays - actual chat messages and
 * media never pass through this server at all, only the initial handshake
 * needed to open a direct peer-to-peer connection.
 */

const { WebSocketServer } = require('ws');

const PORT = process.env.PORT ? parseInt(process.env.PORT, 10) : 8765;
const MAX_NICKNAME_LEN = 64;
const MAX_PAYLOAD_BYTES = 64 * 1024; // SDP/ICE envelopes are small; refuse anything larger

const wss = new WebSocketServer({ port: PORT, maxPayload: MAX_PAYLOAD_BYTES });

/** contactId -> { socket, nickname } */
const online = new Map();

function send(socket, message) {
  if (socket.readyState === socket.OPEN) {
    socket.send(JSON.stringify(message));
  }
}

function broadcastPresenceChange() {
  const snapshot = onlineList();
  for (const { socket } of online.values()) {
    send(socket, { type: 'presence_update', online: snapshot });
  }
}

function onlineList() {
  return Array.from(online.entries()).map(([contactId, v]) => ({
    contactId,
    nickname: v.nickname,
  }));
}

wss.on('connection', (socket) => {
  let myContactId = null;

  socket.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString('utf8'));
    } catch {
      return; // ignore malformed frames
    }
    if (typeof msg !== 'object' || msg === null || typeof msg.type !== 'string') return;

    switch (msg.type) {
      case 'hello': {
        const contactId = String(msg.contactId || '').slice(0, 128);
        const nickname = String(msg.nickname || '').slice(0, MAX_NICKNAME_LEN);
        if (!contactId) return;
        myContactId = contactId;
        online.set(contactId, { socket, nickname });
        send(socket, { type: 'hello_ack', online: onlineList() });
        broadcastPresenceChange();
        break;
      }

      case 'list_online': {
        send(socket, { type: 'presence_update', online: onlineList() });
        break;
      }

      // Generic blind relay: friend requests, friend accept/reject, SDP
      // offer/answer, ICE candidates - the server never interprets `payload`.
      case 'relay': {
        const to = String(msg.to || '');
        const target = online.get(to);
        if (!target || !myContactId) return;
        send(target.socket, {
          type: 'relay',
          from: myContactId,
          payload: msg.payload,
        });
        break;
      }

      default:
        break;
    }
  });

  socket.on('close', () => {
    if (myContactId && online.get(myContactId)?.socket === socket) {
      online.delete(myContactId);
      broadcastPresenceChange();
    }
  });
});

console.log(`Hertz Chat signaling relay listening on ws://0.0.0.0:${PORT}`);
