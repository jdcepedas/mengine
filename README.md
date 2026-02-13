# Matching Engine (ME)

High-performance matching engine for trading assets (event-driven, containerized).

## Modules

- **mengine-shared** – Order, Trade, DTOs, JSON codecs for Aeron
- **mengine-core** – Matching Engine Core: Aeron subscriber → Input Disruptor (Matcher + Journaler) → Output Disruptor (DB writer + Notification), Query API
- **gateway** – HTTP API: POST /orders → Aeron; GET /orderbook, /orders, /trades → ME Core + DB

## Requirements

- Java 21
- Gradle 8.5+ (wrapper included)
- Optional: Docker, PostgreSQL (for trade persistence)

## Build

```bash
./gradlew build
./gradlew :mengine-core:installDist
./gradlew :gateway:installDist
```

## Run locally (two processes)

**Order matters:** Gateway must start first and own the Media Driver. ME Core must connect to that same directory.

**1. Start Gateway** (runs Media Driver, HTTP on 8080):

```bash
./gradlew :gateway:run
```

In the console you must see:
- `[Gateway] Media Driver LAUNCHED at: /some/path/...`
- `[Gateway] >>> Start ME Core with: export ME_AERON_DIR=/some/path/...`
- `[Gateway] Aeron client connecting to directory: /some/path/...`

Copy the **exact** path from that output.

**2. In a second terminal**, start ME Core using that path. You must set `ME_AERON_DIR` so ME Core connects to the same Media Driver as the Gateway: either **export** it in the shell, or set it in your IDE (e.g. IntelliJ: Run → Edit Configurations → select mengine-core → Environment variables: `ME_AERON_DIR=/paste/the/exact/path/from/Gateway`).

```bash
export ME_AERON_DIR=/paste/the/exact/path/from/Gateway
./gradlew :mengine-core:run
```

In ME Core’s console you must see:
- `[ME Core] Subscription created: ... aeronDir=/same/path`
- `[ME Core] Connecting to EXISTING driver (Gateway must have started first)`
- After a few seconds: `[ME Core] Subscription has image(s): imageCount=1 (publication connected - ready for orders)`

**3. Only after** you see `imageCount=1` in ME Core, send POST /orders to the Gateway. If you see `[ME Core] Waiting for publication (imageCount=0)` every 5s, the directory does not match or Gateway was not started first – stop ME Core, start Gateway first, then ME Core with the path Gateway printed.

## Run with Docker

```bash
./gradlew :mengine-core:installDist :gateway:installDist
docker-compose build
docker-compose up
```

- Gateway: http://localhost:8080  
- ME Core Query API: http://localhost:8081  
- PostgreSQL: localhost:5432 (user/pass: mengine/mengine)

## REST API (Gateway)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /orders | Submit order (body: symbol, type, price, quantity) |
| GET | /orders/{id} | Order status (from ME Core) |
| GET | /orderbook/{symbol} | Order book snapshot (from ME Core) |
| GET | /trades/{symbol} | Recent trades (from DB) |

### Order request (POST /orders)

```json
{
  "symbol": "AAPL",
  "type": "BUY",
  "price": 100.50,
  "quantity": 10
}
```

## Configuration

**ME Core** – env / `mengine.properties`:

- `ME_QUERY_PORT` (default 8081)
- `ME_AERON_CHANNEL` (default `aeron:ipc`)
- `ME_AERON_STREAM_ID` (default 10)
- `ME_AERON_DIR` – media driver directory (for shared driver with Gateway)
- `ME_JOURNAL_DIR` – order journal directory (default `journal`)
- `ME_DB_URL`, `ME_DB_USER`, `ME_DB_PASSWORD` – trades DB (empty = in-memory)
- `ME_BUFFER_SIZE`, `ME_MATCHING_TIMEOUT_MS`, `ME_STANDARD_DELAY_MS`

**Gateway** – env / `gateway.properties`:

- `GW_HTTP_PORT` (default 8080)
- `GW_AERON_CHANNEL`, `GW_AERON_STREAM_ID`
- `GW_AERON_DIR` – connect to existing driver (no launch)
- `GW_ME_CORE_URL` (default http://localhost:8081)
- `GW_DB_URL`, `GW_DB_USER`, `GW_DB_PASSWORD` – for GET /trades

## Architecture

```
Gateway (HTTP) → Aeron (orders) → ME Core
  ↓                    ↓
  GET /orderbook,      Aeron poller → Input Disruptor
  GET /orders    ←         ↓
  (ME Core)           Matcher + Journaler (orders to disk)
  GET /trades              ↓
  (DB)               Output Disruptor → DB writer + Notification
```
