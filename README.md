# Project Current - Chat Server

Project Current is a fast, asynchronous chat server built with **Java 17+** and **Netty**. At its heart, there’s a single-threaded logic core, so the server runs quickly and stays easy to maintain.

You get multi-room chat, direct messages, and a full friend system — sending requests, accepting, rejecting, the whole thing.

---

## Core Features

* **Netty Framework:** Handles all WebSocket communication without blocking.
* **Rooms & DMs:** Create or join group chats, send private messages 1-on-1.
* **Friend System:** Add, accept, reject, and remove friends right from chat.
* **Thread-Safe by Design:** The single-threaded model keeps state changes clean and avoids race conditions — no complicated locks needed.

---

## How It Works: The Single-Threaded Logic Core

Network I/O and core logic stay separate, which keeps state handling simple.

Everything important — handling commands, tracking users, changing state — runs on a **dedicated thread** (`ResolverService`).

Here’s how a new command flows through:

1. **Gateway (Netty):** The `ChatGatewayHandler`, running on a Netty I/O thread, catches a WebSocket message.
2. **Queue:** It wraps this message in a `ClientCommand` and drops it into a central `BlockingQueue`.
3. **Resolver (Logic Thread):** The `ResolverService` thread grabs commands from the queue, one at a time, parses, runs the logic, updates state — joining a room, sending a message, whatever’s needed. Because this thread handles commands sequentially, actions always happen in order.
4. **Broadcast (Virtual Threads):** When it’s time to send something out to clients, the resolver hands off the network write to a **virtual thread pool** (`BroadcastService`). That way, the main logic thread never gets stuck waiting on network I/O and can keep moving through new commands.

---

## API

### Client-to-Server (Commands)

Clients send plain-text commands, each one space-delimited.

**Examples:**
* `/create_room <room_name>`
* `/join_room <room_id>`
* `/dm <username> <message>`
* `/add_friend <username>`
* `/accept_friend <username>`
* `/list users`
* `/user_info <username>`

### Server-to-Client (JSON)

The server answers with type-safe JSON. All replies use a sealed `ServerPayload` interface, which makes client-side parsing simple.

Every message includes a `type` property (like `CHAT`, `DM`, `SYSTEM`, or `ERROR`) so clients know what they’re dealing with.

---

## Running

### Dependencies
* **Java 17+** (needed for virtual threads)
* **Netty** (`io.netty:netty-all`)
* **Jackson** (`com.fasterxml.jackson.core:jackson-databind`)

### Launch
1. Build the project using Maven or Gradle.
2. Run the `ServerMain` class. That’s it.