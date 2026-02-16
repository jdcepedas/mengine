package com.mengine.core.persistence;

import com.mengine.model.Trade;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of TradeRepository (PostgreSQL) using a DataSource (e.g. HikariCP).
 */
public class JdbcTradeRepository implements TradeRepository {

    private final DataSource dataSource;

    public JdbcTradeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        initSchema();
    }

    private void initSchema() {
        String sql = """
            CREATE TABLE IF NOT EXISTS trades (
                id VARCHAR(64) PRIMARY KEY,
                symbol VARCHAR(32) NOT NULL,
                buy_order_id VARCHAR(64) NOT NULL,
                sell_order_id VARCHAR(64) NOT NULL,
                price DECIMAL(36,18) NOT NULL,
                quantity DECIMAL(36,18) NOT NULL,
                timestamp_ns BIGINT NOT NULL
            )
            """;
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            // Two ME Cores starting concurrently can both run CREATE TABLE; one creates table+type,
            // the other hits duplicate key on pg_type (typname=trades). Treat as success.
            String state = e.getSQLState();
            if ("23505".equals(state) || "42P07".equals(state)) {
                return; // unique_violation (type already exists) or duplicate_table
            }
            throw new RuntimeException("Failed to init trades table", e);
        }
    }

    @Override
    public void save(Trade trade) {
        saveBatch(List.of(trade));
    }

    @Override
    public void saveBatch(List<Trade> trades) {
        if (trades.isEmpty()) return;
        String sql = "INSERT INTO trades (id, symbol, buy_order_id, sell_order_id, price, quantity, timestamp_ns) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Trade t : trades) {
                ps.setString(1, t.getId());
                ps.setString(2, t.getSymbol());
                ps.setString(3, t.getBuyOrderId());
                ps.setString(4, t.getSellOrderId());
                ps.setBigDecimal(5, t.getPrice());
                ps.setBigDecimal(6, t.getQuantity());
                ps.setLong(7, t.getTimestampNs());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save trades", e);
        }
    }

    @Override
    public List<Trade> findRecentBySymbol(String symbol, int limit) {
        String sql = "SELECT id, symbol, buy_order_id, sell_order_id, price, quantity, timestamp_ns FROM trades WHERE symbol = ? ORDER BY timestamp_ns DESC LIMIT ?";
        List<Trade> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(Trade.builder()
                            .id(rs.getString(1))
                            .symbol(rs.getString(2))
                            .buyOrderId(rs.getString(3))
                            .sellOrderId(rs.getString(4))
                            .price(rs.getBigDecimal(5))
                            .quantity(rs.getBigDecimal(6))
                            .timestampNs(rs.getLong(7))
                            .build());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find trades", e);
        }
        return result;
    }
}
