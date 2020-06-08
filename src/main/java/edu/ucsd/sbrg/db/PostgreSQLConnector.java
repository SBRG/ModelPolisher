package edu.ucsd.sbrg.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sbml.jsbml.util.StringTools;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

import static java.text.MessageFormat.format;

/**
 * Created by mephenor on 05.05.17.
 */
class PostgreSQLConnector {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(PostgreSQLConnector.class.getName());
  /**
   * 
   */
  private final HikariDataSource dataSource;

  /**
   * @return
   * @throws SQLException
   */
  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }


  /**
   * 
   */
  public void close() {
    dataSource.close();
  }



  /**
   * @param host
   * @param port
   * @param user
   * @param password
   * @param dbName
   */
  PostgreSQLConnector(String host, int port, String user, String password, String dbName) {
    password = password == null ? "" : password;
    Properties properties = new Properties();
    properties.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");
    properties.setProperty("dataSource.user", user);
    properties.setProperty("dataSource.password", password);
    properties.setProperty("dataSource.databaseName", dbName);
    properties.setProperty("dataSource.serverName", host);
    HikariConfig config = new HikariConfig(properties);
    config.setMaximumPoolSize(16);
    config.setReadOnly(true);
    dataSource = new HikariDataSource(config);
    logger.fine(format("{0}@{1}:{2}, password={3}", user, host, port, StringTools.fill(password.length(), '*')));
  }
}
