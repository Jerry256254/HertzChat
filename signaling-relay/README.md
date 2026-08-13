# Hertz Chat signaling relay

Hertz Chat is peer-to-peer and end-to-end encrypted: chat messages, voice
notes, photos and videos never touch a server. Establishing a *direct*
connection between two phones over the internet still needs one small,
blind rendezvous step (this is inherent to how NAT/WebRTC works, not a
Hertz Chat design choice) - this server is that step, nothing else.

What it does:
- Lets a client announce "I'm online as `<contactId>`" (a pseudonymous key
  fingerprint, not an account).
- Lets clients see who else is currently online.
- Forwards small, opaque JSON envelopes (WebRTC SDP offers/answers, ICE
  candidates, friend-request handshakes) between two online clients.

What it never does:
- Store anything to disk. There is no database.
- See or log message content, media, or contact lists - it only forwards
  the connection-setup envelope, not the encrypted chat traffic itself,
  which flows directly between the two phones (or over a TURN relay if a
  direct path isn't possible, which likewise only ever sees encrypted bytes).
- Persist who talked to whom after the process restarts (all state is
  in-memory and wiped on disconnect).

## Running it

```bash
npm install
npm start
# or:
docker build -t hertzchat-relay .
docker run -p 8765:8765 hertzchat-relay
```

Point the app at your own instance in Settings → Network, or run one for
your friend group. Anyone can self-host this - it holds no secrets.
