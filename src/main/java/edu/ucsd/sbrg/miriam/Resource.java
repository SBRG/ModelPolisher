package edu.ucsd.sbrg.miriam;

import static java.text.MessageFormat.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.zbit.util.ResourceManager;

/**
 * Corresponds to a child of a MIRIAM namespace
 */
class Resource implements Node {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(Resource.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  private final Namespace parent;
  private final CompactResource resource;
  private final String pattern;
  private List<Resource> siblings;

  /**
   * Container holding a namespace resource
   *
   * @param parent
   *        namespace this resource belongs to
   * @param resource
   *        actual resource
   */
  Resource(Namespace parent, CompactResource resource) {
    this.parent = parent;
    this.resource = resource;
    pattern = parent.getPattern();
  }



  public boolean matchesPattern(String query) {
    return Pattern.compile(pattern).matcher(query).matches();
  }


  @Override
  public Optional<String> extractId(String query) {
    String target = getURLWithPattern();
    if (query.startsWith("http://") || query.startsWith("https://")) {
      query = query.replaceAll("^https?://", "");
    }
    Matcher matcher = Pattern.compile(target).matcher(query);
    if (matcher.find()) {
      String group = matcher.group("id");
      if (parent.isNamespaceEmbeddedInLui() && !group.contains(":") && !group.contains("goref-")) {
        group = group.replaceFirst("[_=]", ":");
      } else if(group.startsWith("goref-")){
        group = group.replaceAll("goref-", "GO_REF:");
      }
      return Optional.of(group);
    }
    return Optional.empty();
  }

  public String getName(){
    return parent.getName();
  }

  public String getPattern() {
    return pattern;
  }


  public CompactResource getResource() {
    return resource;
  }

  public List<Resource> getSiblings() {
    if (siblings == null) {
      siblings = new ArrayList<>();
      for (Resource resource : parent.getLeaves()) {
        if (!this.equals(resource)) {
          siblings.add(resource);
        }
      }
    }
    return siblings;
  }

  @Override
  public String getURLWithPattern() {
    String urlPattern = stripProtocol(resource.getUrlPattern());
    boolean namespaceInId = parent.isNamespaceEmbeddedInLui();
    String idPattern = pattern.replaceAll("\\^|\\$", "");
    if (idPattern.equals("HGVP\\d+")) {
      // fix for wrong gwascentral phenotype RegEx
      idPattern = "HGVPM\\d+";
    } else if (idPattern.equals("G|P|U|C|S\\d{5}")) {
      idPattern = "(G|P|U|C|S)\\d{5}";
    }
    // Wrap in capture group for id extraction
    idPattern = "(?<id>" + idPattern + ")";
    // create pattern for id if namespace is present in id, e.g. CHEBI:
    boolean replaced = false;
    if (namespaceInId) {
      Pattern prefixPattern = Pattern.compile("\\(?[\\w\\\\]+?[:]\\)?");
      Matcher idPatternMatcher = prefixPattern.matcher(idPattern);
      Matcher urlPatternMatcher = prefixPattern.matcher(urlPattern);
      String prefix = "";
      if (idPatternMatcher.find()) {
        prefix = idPatternMatcher.group();
        if (!urlPatternMatcher.find()) {
          // Actual pattern in resource URL is different, match correct version of prefix-id separator
          Matcher separatorMatcher =
            Pattern.compile(prefix.substring(0, prefix.length() - 1) + "[_=]").matcher(urlPattern);
          if (separatorMatcher.find()) {
            prefix = separatorMatcher.group();
          } else if (urlPattern.contains("gorefs/goref-")) {
            prefix = "goref-";
          } else {
            throw new IllegalStateException(format("No match found for {0}", urlPattern));
          }
          replaced = true;
        }
      }
      // Should not happen, in this case the regex is likely wrong
      if (prefix.isBlank()) {
        throw new IllegalStateException("Failed to extract namespace from id");
      }
      // adjust RegEx for resource ids
      if (replaced) {
        if (urlPattern.contains("gorefs/goref-")) {
          idPattern = idPattern.replaceAll("GO_REF:", "goref-");
        } else {
          String tmpPrefix = prefix.substring(0, prefix.length() - 1) + ":";
          idPattern = idPattern.replaceAll(tmpPrefix, prefix);
        }
      }
      // Replace prefix:id with correctly escaped pattern
      urlPattern = Entries.replaceIdTagWithPrefix(urlPattern, idPattern, prefix);
    } else {
      // Replace id with pattern
      urlPattern = Entries.replaceIdTag(urlPattern, idPattern);
    }
    return urlPattern;
  }


  /**
   * Replaces id placeholder tag in URL string with actual id
   *
   * @param id
   *        id to insert
   * @return Annotation ready URL string
   */
  @Override
  public String resolveID(String id) {
    String url = resource.getUrlPattern().replaceAll("\\{\\$id}", id);
    if (!isMatch(url)) {
      logger.warning(
        format("Provided id \"{0}\" did not match required pattern \"{1}\" for the given collection", id, pattern));
    }
    return url;
  }


  private String stripProtocol(String query) {
    if (query.startsWith("http://") || query.startsWith("https://")) {
      Matcher protocolMatcher = Pattern.compile("^https?://").matcher(query);
      if (protocolMatcher.find()) {
        query = query.replaceAll(protocolMatcher.pattern().pattern(), "");
      }
    }
    return query;
  }


  @Override
  public boolean isDeprecated() {
    return resource.isDeprecated();
  }


  /**
   * @param query
   *        MIRIAM compatible URL
   * @return {@code true} if non-protocol part of URL matches resource URL and the id is matched by the regex
   */
  @Override
  public boolean isMatch(String query) {
    String target = getURLWithPattern();
    if (query.startsWith("http://") || query.startsWith("https://")) {
      query = query.replaceAll("^https?://", "");
    }
    Matcher matcher = Pattern.compile(target).matcher(query);
    return matcher.matches();
  }
}
