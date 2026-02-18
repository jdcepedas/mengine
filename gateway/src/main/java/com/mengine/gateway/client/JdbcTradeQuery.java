package com.mengine.gateway.client;

import com.mengine.model.Trade;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcTradeQuery implements TradeQuery {

    private final String url;
    private final String user;
    private final String password;

    public JdbcTradeQuery(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @Override
    public List<Trade> findRecentBySymbol(String symbol, int limit) {
        String sql = "SELECT id, symbol, buy_order_id, sell_order_id, price, quantity, timestamp_ns FROM trades WHERE symbol = ? ORDER BY timestamp_ns DESC LIMIT ?";
        List<Trade> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, user, password);
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
            return List.of();
        }
        return result;
    }
}
