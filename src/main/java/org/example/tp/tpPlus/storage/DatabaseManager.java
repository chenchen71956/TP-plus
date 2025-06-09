package org.example.tp.tpPlus.storage;

import org.example.tp.tpPlus.config.ConfigManager;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DatabaseManager {
    private static Connection connection;
    private static final String DB_FILE = "tpplus.db";
    
    public static void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            File dbFile = ConfigManager.getConfigFile(DB_FILE);
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTables();
            migrateToFloatCoordinates();
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println("初始化数据库失败: " + e.getMessage());
        }
    }
    
    private static void createTables() {
        try (Statement stmt = connection.createStatement()) {
            // 创建homes表
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS homes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "player_uuid TEXT NOT NULL, " +
                "home_name TEXT NOT NULL, " +
                "x REAL NOT NULL, " +
                "y REAL NOT NULL, " +
                "z REAL NOT NULL, " +
                "dimension TEXT NOT NULL, " +
                "UNIQUE(player_uuid, home_name)" +
                ")"
            );
        } catch (SQLException e) {
            System.err.println("创建表失败: " + e.getMessage());
        }
    }
    
    private static void migrateToFloatCoordinates() {
        try (Statement stmt = connection.createStatement()) {
            // 检查是否需要进行迁移（检查列类型是否已经是REAL）
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(homes)");
            boolean needMigration = false;
            while (rs.next()) {
                String columnName = rs.getString("name");
                String columnType = rs.getString("type");
                if ((columnName.equals("x") || columnName.equals("y") || columnName.equals("z")) 
                    && !columnType.equals("REAL")) {
                    needMigration = true;
                    break;
                }
            }
            
            if (needMigration) {
                System.out.println("正在迁移数据库到浮点坐标...");
                // 创建临时表
                stmt.execute(
                    "CREATE TABLE homes_temp (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "player_uuid TEXT NOT NULL, " +
                    "home_name TEXT NOT NULL, " +
                    "x REAL NOT NULL, " +
                    "y REAL NOT NULL, " +
                    "z REAL NOT NULL, " +
                    "dimension TEXT NOT NULL, " +
                    "UNIQUE(player_uuid, home_name)" +
                    ")"
                );
                
                // 复制数据
                stmt.execute(
                    "INSERT INTO homes_temp (player_uuid, home_name, x, y, z, dimension) " +
                    "SELECT player_uuid, home_name, x, y, z, dimension FROM homes"
                );
                
                // 删除旧表
                stmt.execute("DROP TABLE homes");
                
                // 重命名临时表
                stmt.execute("ALTER TABLE homes_temp RENAME TO homes");
                
                System.out.println("数据库迁移完成！");
            }
        } catch (SQLException e) {
            System.err.println("迁移数据库失败: " + e.getMessage());
        }
    }
    
    public static void saveHome(UUID playerUuid, String homeName, double x, double y, double z, String dimension) {
        String sql = "INSERT OR REPLACE INTO homes (player_uuid, home_name, x, y, z, dimension) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, homeName);
            pstmt.setDouble(3, x);
            pstmt.setDouble(4, y);
            pstmt.setDouble(5, z);
            pstmt.setString(6, dimension);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("保存家失败: " + e.getMessage());
        }
    }
    
    public static Map<String, HomeLocation> getHomes(UUID playerUuid) {
        Map<String, HomeLocation> homes = new HashMap<>();
        String sql = "SELECT home_name, x, y, z, dimension FROM homes WHERE player_uuid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                String homeName = rs.getString("home_name");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                String dimension = rs.getString("dimension");
                
                homes.put(homeName, new HomeLocation(x, y, z, dimension));
            }
        } catch (SQLException e) {
            System.err.println("获取家失败: " + e.getMessage());
        }
        
        return homes;
    }
    
    public static boolean deleteHome(UUID playerUuid, String homeName) {
        String sql = "DELETE FROM homes WHERE player_uuid = ? AND home_name = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, homeName);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("删除家失败: " + e.getMessage());
            return false;
        }
    }
    
    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("关闭数据库连接失败: " + e.getMessage());
        }
    }
    
    // 数据类
    public static class HomeLocation {
        private final double x;
        private final double y;
        private final double z;
        private final String dimension;
        
        public HomeLocation(double x, double y, double z, String dimension) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
        }
        
        public double getX() {
            return x;
        }
        
        public double getY() {
            return y;
        }
        
        public double getZ() {
            return z;
        }
        
        public String getDimension() {
            return dimension;
        }
    }
} 