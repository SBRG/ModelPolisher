
package edu.ucsd.sbrg.miriam;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Corresponds to a MIRIAM namespace entry, reduced to data needed by ModelPolisher
 */
class Namespace implements Node {

  private final Entries.Root parent;
  private final CompactEntry entry;
  private final List<Resource> children;

  Namespace(Entries.Root parent, CompactEntry entry) {
    this.parent = parent;
    this.entry = entry;
    children = new ArrayList<>();
    for (CompactResource resource : entry.getResources()) {
      // children correspond to namespace resources
      Resource child = new Resource(this, resource);
      children.add(child);
    }
  }


  /**
   * @return List of resources for a namespace
   */
  public List<Resource> getLeaves() {
    return children;
  }

  public String getName(){
    return entry.getName();
  }

  public String getPattern() {
    return entry.getPattern();
  }


  public String getPrefix() {
    return entry.getPrefix();
  }


  public String getSampleId() {
    return entry.getSampleId();
  }


  public boolean isNamespaceEmbeddedInLui() {
    return entry.isNamespaceEmbeddedInLui();
  }


  public boolean matchesPattern(String query) {
    return Pattern.compile(getPattern()).matcher(query).matches();
  }


  @Override
  public Optional<String> extractId(String query) {
    String target = getURLWithPattern();
    if (query.startsWith("http://") || query.startsWith("https://")) {
      query = query.replaceAll("^https?://", "");
    }
    Matcher matcher = Pattern.compile(target).matcher(query);
    if (matcher.find()) {
      return Optional.of(matcher.group("id"));
    }
    return Optional.empty();
  }


  @Override
  public String getURLWithPattern() {
    String idPattern = getPattern().replaceAll("\\^|\\$", "");
    if (idPattern.equals("HGVP\\d+")) {
      // fix for wrong gwascentral phenotype RegEx
      idPattern = "HGVPM\\d+";
    } else if (idPattern.equals("G|P|U|C|S\\d{5}")) {
      idPattern = "(G|P|U|C|S)\\d{5}";
    }
    idPattern = "(?<id>" + idPattern + ")";
    return "identifiers.org/" + getPrefix() + "/" + idPattern;
  }


  @Override
  public String resolveID(String id) {
    if (isNamespaceEmbeddedInLui()) {
      Pattern prefixPattern = Pattern.compile("\\(?[\\w\\\\]+?[:]\\)?");
      Matcher prefixMatcher = prefixPattern.matcher(getPattern().replaceAll("\\^|\\$", ""));
      if (prefixMatcher.find()) {
        String pattern = prefixMatcher.group();
        // Quickfix for escaped colon in CCO and possibly others
        if (pattern.endsWith("\\:")) {
          pattern = pattern.substring(0, pattern.length() - 2) + ":";
        }
        return "https://identifiers.org/" + getPrefix() + "/" + pattern + id;
      }
      throw new IllegalStateException("Could not extract prefix, this should not happen.");
    } else {
      return "https://identifiers.org/" + getPrefix() + "/" + id;
    }
  }


  @Override
  public boolean isDeprecated() {
    return entry.isDeprecated();
  }


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
