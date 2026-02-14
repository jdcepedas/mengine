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
- `ME_LATENCY_LOG_ENABLED` – enable matching latency log (default `false`)
- `ME_LATENCY_LOG_PATH` – path for latency CSV file (default `{ME_JOURNAL_DIR}/matching_latency.log`)

**Gateway** – env / `gateway.properties`:

- `GW_HTTP_PORT` (default 8080)
- `GW_AERON_CHANNEL`, `GW_AERON_STREAM_ID`
- `GW_AERON_DIR` – connect to existing driver (no launch)
- `GW_ME_CORE_URL` (default http://localhost:8081)
- `GW_DB_URL`, `GW_DB_USER`, `GW_DB_PASSWORD` – for GET /trades

## Matching latency measurement

ME Core can log per-order matching latency to a CSV file for analysis. The feature is **off by default** so the hot path is unaffected until you enable it.

### Enabling the latency log

1. **Set the enable flag** (environment or config file):
   - **Environment:** `export ME_LATENCY_LOG_ENABLED=true`
   - **Properties file** (`mengine.properties` or `config/mengine.properties`): `me.latency.log.enabled=true`

2. **Optional – log file path** (default is `{ME_JOURNAL_DIR}/matching_latency.log`, e.g. `journal/matching_latency.log`):
   - **Environment:** `export ME_LATENCY_LOG_PATH=/var/log/me/matching_latency.log`
   - **Properties:** `me.latency.log.path=/var/log/me/matching_latency.log`

3. **Restart ME Core** so it picks up the config. The latency writer thread starts with the input disruptor and creates the file (and parent directories) on first write.

### How it works

- **Hot path:** After each `match(order)` call, the disruptor handler builds a small record (order id, symbol, type, matched/partial, trade count, latency in nanoseconds, timestamp) and **offers** it to a bounded queue. No I/O and no blocking on the matching thread; if the queue is full, the record is dropped.
- **Background writer:** A single daemon thread drains the queue and appends one CSV line per order to the log file. Writes are buffered and flushed every 100 records (and on shutdown) to limit syscalls.
- **Shutdown:** When ME Core exits, the recorder is stopped: the writer drains remaining records, flushes, and closes the file.

### Log format

The file is CSV with a header line, then one row per order:

```text
orderId,symbol,type,matched,partial,tradeCount,latencyNs,timestampMs
O1,AAPL,BUY,true,false,1,1240625,1739123456789
O2,AAPL,SELL,false,true,1,24250,1739123456795
```

- **orderId** – order identifier  
- **symbol** – instrument  
- **type** – `BUY` or `SELL`  
- **matched** – order fully filled  
- **partial** – order partially filled  
- **tradeCount** – number of trades produced by this order  
- **latencyNs** – matching duration in nanoseconds (from entry to exit of `match()`)  
- **timestampMs** – time when the record was taken (milliseconds since epoch)

You can open the file in a spreadsheet or use scripts to compute percentiles (e.g. p50, p99) and correlate with order flow.

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
