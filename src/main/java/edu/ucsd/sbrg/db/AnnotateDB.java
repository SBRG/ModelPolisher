package edu.ucsd.sbrg.db;

import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.BIGG_METABOLITE;
import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.BIGG_REACTION;
import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.METABOLITE_PREFIX;
import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.REACTION_PREFIX;
import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.Column.NAMESPACE;
import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.Column.SOURCE_NAMESPACE;
import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.Column.SOURCE_TERM;
import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.Column.TARGET_NAMESPACE;
import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.Column.TARGET_TERM;
import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.Column.URLPATTERN;
import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.Table.ADB_COLLECTION;
import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.Table.MAPPING_VIEW;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import de.zbit.util.Utils;

/**
 * @author Kaustubh Trivedi
 */
public class AnnotateDB {

  private static final Logger logger = Logger.getLogger(AnnotateDB.class.getName());
  private static PostgreSQLConnector connector;

  /**
   * Don't allow instantiation
   */
  private AnnotateDB() {
  }


  /**
   * Initialize a SQL connection
   * 
   * @param host
   * @param port
   * @param user
   * @param passwd
   * @param name
   */
  public static void init(String host, String port, String user, String passwd, String name) {
    connector = new PostgreSQLConnector(host, Integer.parseInt(port), user, passwd, name);
  }


  /**
   * 
   */
  public static void close() {
    connector.close();
  }


  /**
   * @return
   */
  public static boolean inUse() {
    return connector != null;
  }


  /**
   * @param type
   * @param biggId
   * @return
   */
  public static Set<String> getAnnotations(String type, String biggId) {
    TreeSet<String> annotations = new TreeSet<>();
    if (!type.equals(BIGG_METABOLITE) && !type.equals(BIGG_REACTION)) {
      return annotations;
    }
    if (type.equals(BIGG_METABOLITE) && biggId.startsWith(METABOLITE_PREFIX)) {
      biggId = biggId.substring(2);
    } else if (type.equals(BIGG_METABOLITE) && biggId.startsWith(REACTION_PREFIX)) {
      biggId = biggId.substring(2);
    }
    if (biggId.endsWith("_")) {
      biggId = biggId.substring(0, biggId.length() - 2);
    }
    String query = "SELECT m." + TARGET_TERM + ", ac." + URLPATTERN + " FROM " + MAPPING_VIEW + " m, " + ADB_COLLECTION
      + " ac WHERE m." + SOURCE_NAMESPACE + " = ? AND m." + SOURCE_TERM + " = ? AND ac." + NAMESPACE + " = m."
      + TARGET_NAMESPACE;
    try {
      Connection connection = connector.getConnection();
      PreparedStatement pStatement = connection.prepareStatement(query);
      pStatement.setString(1, type);
      pStatement.setString(2, biggId);
      ResultSet resultSet = pStatement.executeQuery();
      while (resultSet.next()) {
        String uri = resultSet.getString(URLPATTERN);
        String id = resultSet.getString(TARGET_TERM);
        uri = uri.replace("{$id}", id);
        annotations.add(uri);
      }
      pStatement.close();
      connection.close();
    } catch (SQLException exc) {
      logger.warning(Utils.getMessage(exc));
    }
    return annotations;
  }
}
