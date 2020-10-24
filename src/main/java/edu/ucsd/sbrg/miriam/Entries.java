package edu.ucsd.sbrg.miriam;

import static java.text.MessageFormat.format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.zbit.util.ResourceManager;

public class Entries {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(Entries.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  private static final Map<String, String> COLLECTION_FOR_PREFIX = new HashMap<>();
  private static final Map<String, String> PATTERN_FOR_COLLECTION = new HashMap<>();
  private static final Map<String, String> PREFIX_FOR_COLLECTION = new HashMap<>();
  private static Entries instance;
  private final Root root;

  static class Root {

    private final List<Namespace> children;

    /**
     * Container for reduced representation tree structure.
     * Each children corresponds to a MIRIAM namespace
     *
     * @param entries
     *        entries to convert into children {@link Namespace}s
     */
    Root(List<CompactEntry> entries) {
      children = new ArrayList<>();
      for (CompactEntry entry : entries) {
        String collectionName = entry.getName();
        String pattern = entry.getPattern();
        String prefix = entry.getPrefix();
        COLLECTION_FOR_PREFIX.put(prefix, collectionName);
        PATTERN_FOR_COLLECTION.put(collectionName, pattern);
        PREFIX_FOR_COLLECTION.put(collectionName, prefix);
        Namespace child = new Namespace(this, entry);
        children.add(child);
      }
    }
  }

  private Entries(List<CompactEntry> entries) {
    root = new Root(entries);
  }


  /**
   * Builds namespace tree from reduced representation entries and returns an instance to query respective MIRIAM
   * namespaces/resources
   *
   * @param entries
   *        List of {@link CompactEntry} objects representing MIRIAM namespaces reduced to attributes and fields used in
   *        ModelPolisher
   */
  public static void initFromList(List<CompactEntry> entries) {
    if (instance != null) {
      throw new IllegalStateException("Entries have already been initialized!");
    }
    instance = new Entries(entries);
  }


  /**
   * @return List of {@link Namespace} objects representing all MIRIAM namespaces
   */
  public List<Namespace> get() {
    return instance.root.children;
  }


  public String getCollection(String url) {
    List<Node> matches = getMatchForUrl(url);
    if (matches.size() != 1) {
      return null;
    }
    return matches.get(0).getName();
  }


  public String getCollectionForPrefix(String prefix) {
    return COLLECTION_FOR_PREFIX.getOrDefault(prefix, "");
  }


  /**
   * @return {@link Entries} class instance to query namespaces and associated resources
   */
  public static Entries getInstance() {
    if (instance == null) {
      throw new IllegalStateException("Entries have not been initialized yet.");
    }
    return instance;
  }


  /**
   * Tries to match a query URL uniquely to retrieve the correct MIRIAM collection for additional annotation
   *
   * @param query
   *        URL to match against identifiers.org namespace and resource URLPatterns
   * @return Empty optional if either no match is found or the match is not unique (a message is logged in the second
   *         case), else the Optional contains the match
   */
  public List<Node> getMatchForUrl(String query) {
    // strip protocol from query URL for better matching
    if (query.startsWith("http://") || query.startsWith("https://")) {
      query = query.replaceAll("^https?://", "");
    }
    List<Node> matches = new ArrayList<>();
    for (Node namespace : root.children) {
      if (namespace.isMatch(query)) {
        matches.add(namespace);
      }
      for (Node resource : ((Namespace) namespace).getLeaves()) {
        if (resource.isMatch(query)) {
          matches.add(resource);
        }
      }
    }
    if (matches.size() > 1) {
      logger.info(format("Could not resolve MIRIAM collection for URL {0} uniquely", query));
    }
    return matches;
  }


  public String getPrefixForCollection(String collection) {
    return PREFIX_FOR_COLLECTION.getOrDefault(collection, "");
  }


  public String getPattern(String collection) {
    return PATTERN_FOR_COLLECTION.getOrDefault(collection, "");
  }


  /**
   * {@link String#replaceAll(String, String)} unescapes second string, i.e. it is not usable to insert a regex into a
   * string
   * This static method replaces the id placeholder tag with the correct regex for matching
   *
   * @param url
   *        URL Pattern for MIRIAM namespace or child thereof
   * @param pattern
   *        Identifier RegEx for matching
   * @return url with id placeholder tag replaced with pattern
   */
  static String replaceIdTag(String url, String pattern) {
    Pattern id = Pattern.compile("\\{?\\{\\$id}}?");
    Matcher matcher = id.matcher(url);
    if (!matcher.find()) {
      return Pattern.quote(url);
    }
    String[] parts = url.split(id.pattern());
    String result = Pattern.quote(parts[0]) + pattern;
    if (parts.length == 2) {
      result += Pattern.quote(parts[1]);
    }
    return result;
  }


  /**
   * {@link String#replaceAll(String, String)} unescapes second string, i.e. it is not usable to insert a regex into a
   * string
   * This static method replaces the id placeholder tag with the correct regex for matching also dealing with namespace
   * prefixes in the RegEx pattern
   *
   * @param url
   *        URL Pattern for MIRIAM namespace or child thereof
   * @param pattern
   *        Identifier RegEx for matching
   * @return url with id placeholder tag replaced with pattern
   */
  static String replaceIdTagWithPrefix(String url, String pattern, String prefix) {
    Pattern id = Pattern.compile(prefix + "\\{?\\{\\$id}}?");
    Matcher matcher = id.matcher(url);
    if (!matcher.find()) {
      return replaceIdTag(url, pattern);
    }
    String[] parts = url.split(id.pattern());
    String result = Pattern.quote(parts[0]) + pattern;
    if (parts.length == 2) {
      result += Pattern.quote(parts[1]);
    }
    return result;
  }


  /**
   * @return number of namespaces
   */
  public int size() {
    return root.children.size();
  }
}
