# Matching Engine (ME)

A high-performance matching engine for trading assets, similar to a stock exchange. Acts as an intermediary between sellers and buyers with an in-memory order book serving as legal guarantee.

## Features

- **Order Management**: Validates and processes sell offers and buy requests
- **In-Memory Order Book**: Lock-free data structures for each asset symbol
- **Matching Engine**: Price-time priority matching with < 200ms latency
- **Reactive Buffer**: LMAX Disruptor for burst absorption (65,536 capacity)
- **Notification Service**: Premium (real-time) and Standard (delayed) tiers
- **Analytics Module**: Trade volume, price statistics, fill rates

## Requirements

- Java 21
- Gradle 8.5+ (wrapper included)

## Build & Run

```bash
./gradlew build
./gradlew run
```

Server starts on port 8080 (configurable via `ME_SERVER_PORT`).

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /orders | Submit order (BUY/SELL) |
| GET | /orders/{id} | Get order status |
| GET | /orderbook/{symbol} | Get order book state |
| GET | /trades/{symbol} | Get recent trades |
| GET | /analytics/{symbol} | Get analytics report |
| POST | /subscribe | Subscribe to events |

### Order Request (POST /orders)

```json
{
  "symbol": "AAPL",
  "type": "BUY",
  "price": 100.50,
  "quantity": 10
}
```

## Configuration

Environment variables (override defaults):

- `ME_BUFFER_SIZE` - Ring buffer size (default: 65536)
- `ME_MATCHING_TIMEOUT_MS` - Match timeout (default: 200)
- `ME_STANDARD_DELAY_MS` - Standard tier delay (default: 1000)
- `ME_SERVER_PORT` - HTTP port (default: 8080)
- `ME_MATCHING_THREADS` - Matching threads (default: 4)
- `ME_TRADE_STORE_SIZE` - Max trades per symbol (default: 1000)

Config file: `mengine.properties` or `~/.mengine.properties`

## Load Testing

Run with JMeter or the included load test:

```bash
./gradlew run &   # Start server
./gradlew test --tests "com.mengine.LoadTest"  # Run load tests (remove @Disabled first)
```

Target SLAs:
- Ingestion: < 0.5s (sell), < 0.3s (buy)
- Matching: < 200ms
- Throughput: 1,300 orders/min baseline, 5,000 matches/min peak

## Architecture

```
Client → Reactive Buffer (Disruptor) → Order Processor
                                            ↓
                              Matching Engine ↔ Order Book
                                            ↓
                              Notification Service → Subscribers
                                            ↓
                              Analytics Module
```
