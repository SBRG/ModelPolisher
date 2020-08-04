package edu.ucsd.sbrg.db;

import de.zbit.util.prefs.KeyProvider;
import de.zbit.util.prefs.Option;

/**
 * @author Andreas Dr&auml;ger
 */
public interface BiGGDBOptions extends KeyProvider {

  /**
   * 
   */
  @SuppressWarnings("unchecked")
  Option<String> HOST = new Option<>("BiGG_HOST", String.class, "Host name", "biggdb");
  /**
   *
   */
  @SuppressWarnings("unchecked")
  Option<Integer> PORT = new Option<>("BiGG_PORT", Integer.class, "Port", 1310);
  /**
   *
   */
  @SuppressWarnings("unchecked")
  Option<String> USER = new Option<>("BiGG_USER", String.class, "User name", "postgres");
  /**
   *
   */
  @SuppressWarnings("unchecked")
  Option<String> PASSWD = new Option<>("BiGG_PASSWD", String.class, "Password", "postgres");
  /**
   *
   */
  @SuppressWarnings("unchecked")
  Option<String> DBNAME = new Option<>("BiGG_DBNAME", String.class, "The name of the database to use.", "bigg");
}
