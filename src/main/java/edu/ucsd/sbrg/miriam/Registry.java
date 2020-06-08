package edu.ucsd.sbrg.miriam;

import de.zbit.util.ResourceManager;
import edu.ucsd.sbrg.bigg.BiGGId;
import edu.ucsd.sbrg.miriam.models.Miriam;
import org.sbml.jsbml.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.text.MessageFormat.format;

public class Registry {

  /**
   * A {@link Logger} for this class.
   */
  static final transient Logger logger = Logger.getLogger(Registry.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   *
   */
  private static final List<CompactEntry> entries;
  /**
   * Contains mapping from resource provider URL to collection name for URLs ending with the id
   */
  private static final Map<String, String> ALTERNATIVE_URL_PATTERNS = new HashMap<>();
  /**
   * Contains mapping for identifiers.org URI /w & /wo provider information
   */
  private static final Map<String, String> COLLECTION_FOR_URI = new HashMap<>();
  /**
   * Mapping for collection to its pattern
   */
  private static final Map<String, String> COLLECTION_FOR_PATTERN = new HashMap<>();
  /**
   * Mapping provider code to collection name
   */
  private static final Map<String, String> COLLECTION_FOR_PREFIX = new HashMap<>();
  /**
   * Mapping for pattern to its respective collection
   */
  private static final Map<String, String> PATTERN_FOR_COLLECTION = new HashMap<>();
  /**
   * Mapping collection name to provider code
   */
  private static final Map<String, String> PREFIX_FOR_COLLECTION = new HashMap<>();
  /*
   * Static initializer for Miriam, read registry once and convert to compact representation
   */
  static {
    Miriam miriam = RegistryProvider.getInstance().getMiriam();
    // convert namespaces to CompactEntries, holding only the necessary information for ModelPolisher
    entries =
      miriam.getNamespaces().values().parallelStream().map(CompactEntry::fromNamespace).collect(Collectors.toList());
    // Free unneeded resources for GC
    RegistryProvider.close();
    // Create helper structures for fast access
    for (CompactEntry entry : entries) {
      String collectionName = entry.getName();
      String pattern = entry.getPattern();
      String prefix = entry.getPrefix();
      COLLECTION_FOR_PATTERN.put(pattern, collectionName);
      COLLECTION_FOR_PREFIX.put(prefix, collectionName);
      COLLECTION_FOR_URI.put(createProviderURI(prefix), collectionName);
      PATTERN_FOR_COLLECTION.put(collectionName, pattern);
      PREFIX_FOR_COLLECTION.put(collectionName, prefix);
      for (CompactResource resource : entry.getResources()) {
        String provider = resource.getProviderCode();
        // Split pattern on {$id} and replace {$id} with actual pattern of namespace for urlPattern matching and id
        // extraction later on
        String urlPattern = removeIdParts(resource.getUrlPattern());
        String[] patternParts = urlPattern.split("\\{\\$id}");
        // Convert pattern to named capture group
        String patternCaptureGroup = "(?<id>" + pattern.replaceAll("\\^|\\$", "") + ")";
        // Part after {$id} is only present for some URLs
        String first;
        String second;
        if (patternParts.length > 1) {
          second = patternParts[1];
          first = patternParts[0];
          if (second.equals("\"")) {
            first = first + second;
          }
        } else {
          first = patternParts[0];
          second = "";
        }
        first = Pattern.quote(first);
        second = Pattern.quote(second);
        String matcherPattern = format("{0}{1}{2}", first, patternCaptureGroup, second);
        ALTERNATIVE_URL_PATTERNS.put(matcherPattern, collectionName);
        COLLECTION_FOR_URI.put(createProviderURI(provider), collectionName);
      }
    }
  }

  /**
   * NamespaceEmbeddedInLui does not work to correctly establish what is a part of an id in a non-identifiers URL
   * Thus this function strips the namespace prefix for URLs where it would be duplicated
   *
   * @param urlPattern
   * @return
   */
  private static String removeIdParts(String urlPattern) {
    for (String prefix : Arrays.asList("affymetrix", "BAO:", "EFO:", "FB:", "ecnumber:", "homologene:", "IDO:",
      "interaction_id:", "interpro:", "kisao:", "Locus:", "locusname:", "ncbigene:", "ndc:", "orphanet:", "RGD:",
      "sabioreactionid", "sgd:", "SGD:", "Taxon:", "taxonomy:", "WB:", "WP:", "ZFIN:")) {
      if (urlPattern.contains(prefix)) {
        return urlPattern;
      }
    }
    Matcher idAfterEquals = Pattern.compile("(?<=/).*=(?<remove>\"?\\w+?:)\\{\\$id}\"?").matcher(urlPattern);
    Matcher idAfterSlash = Pattern.compile("/#?(?<remove>\\w+?:)\\{\\$id}").matcher(urlPattern);
    if (idAfterEquals.find()) {
      String remove = idAfterEquals.group("remove");
      urlPattern = urlPattern.replaceAll(remove, "");
    } else if (idAfterSlash.find()) {
      String remove = idAfterSlash.group("remove");
      urlPattern = urlPattern.replaceAll(remove, "");
    }
    return urlPattern;
  }


  /**
   * @param providerCode
   * @param id
   * @return
   */
  public static String createURI(String providerCode, BiGGId id) {
    return createURI(providerCode, id.getAbbreviation());
  }


  /**
   * @param id
   * @return
   */
  public static String createShortURI(Object id) {
    return "https://identifiers.org/" + id.toString();
  }


  /**
   * @param providerCode
   * @param id
   * @return
   */
  public static String createURI(String providerCode, Object id) {
    return createProviderURI(providerCode) + id.toString();
  }


  /**
   * @param providerCode
   * @return
   */
  public static String createProviderURI(String providerCode) {
    return "https://identifiers.org/" + providerCode + "/";
  }


  public static String getPrefixForCollection(String collection) {
    return PREFIX_FOR_COLLECTION.getOrDefault(collection, "");
  }


  public static String getCollectionForPrefix(String prefix) {
    return COLLECTION_FOR_PREFIX.getOrDefault(prefix, "");
  }


  /**
   * Checks resource URIs and logs those not matching the specified pattern
   * Used to check URIs obtained from BiGGDB
   * A resource URI that does not match the pattern will be logged and not added
   * to the model
   *
   * @param resource:
   *        resource URI to be added as annotation
   * @return corrected resource URI
   */
  public static Optional<String> checkResourceUrl(String resource) {
    // TODO: temporary fix, http vs https should be irrelevant, handle urlPattern differently for proper handling
    if (resource.startsWith("http://www.reactome.org")) {
      resource = resource.replaceAll("http://www.reactome.org", "https://www.reactome.org");
    }
    // no longer supported by identifiers.org, but should still resolve, keep and fix missing id prefix
    if (resource.contains("ncbigi")) {
      String[] split = resource.split("/");
      int len = split.length;
      String id = split[len - 1];
      if (!id.startsWith("GI:")) {
        if (id.startsWith("gi:")) {
          resource = Registry.replace(resource, id, id.replaceAll("gi:", "GI:"));
        } else {
          resource = Registry.replace(resource, id, "GI:" + id);
        }
      }
      return Optional.of(resource);
    }
    if (resource.startsWith("https://identifiers.org/omim")) {
      // omim is present in BiGGDB, but is not valid, skip
      return Optional.empty();
    }
    /*
     * Either [namespace prefix]:[accession] or [provider code]/[namespace prefix]:[accession] second option is
     * currently not strict - older URIs with only accession are supported, if provider code is given
     */
    Pattern identifiersURL = Pattern.compile("(?:https?://)?identifiers.org/(?:(?<provider>.*?)/)?(?<id>.*)");
    Matcher urlMatcher = identifiersURL.matcher(resource);
    String provider = "";
    String identifier = "";
    if (urlMatcher.matches()) {
      provider = Optional.ofNullable(urlMatcher.group("provider")).orElse("");
      identifier = urlMatcher.group("id");
    } else {
      Pair<String, String> parts = extractPartsFromNonCanonical(resource);
      if (!parts.getKey().isEmpty()) {
        provider = parts.getKey();
        identifier = parts.getValue();
      }
    }
    // remove trailing whitespace from id
    identifier = identifier.stripTrailing();
    // handle case mismatch in provider code
    if (provider.matches("[A-Z]+")) {
      provider = provider.toLowerCase();
    }
    String query = identifier;
    // Get provider by checking for uniquely matching Regex
    if (provider.isEmpty()) {
      List<String> collections =
        COLLECTION_FOR_PATTERN.keySet().stream().filter(s -> Pattern.compile(s).matcher(query).matches())
                              .collect(Collectors.toList());
      if (collections.size() == 1) {
        String collection = collections.get(0);
        provider = PREFIX_FOR_COLLECTION.get(collection);
      }
    }
    // handle cases where provider and collection name have been mixed up
    if (!COLLECTION_FOR_PREFIX.containsKey(provider) && PREFIX_FOR_COLLECTION.containsKey(provider)) {
      provider = PREFIX_FOR_COLLECTION.get(provider);
    } else if (!COLLECTION_FOR_PREFIX.containsKey(provider)) {
      logger.severe(format(MESSAGES.getString("UNCAUGHT_URI"), resource));
      return Optional.of(resource);
    }
    String collection = COLLECTION_FOR_PREFIX.get(provider);
    String regexp = getPattern(collection);
    Boolean correct = checkPattern(identifier, regexp);
    resource = createURI(provider, identifier);
    String report_resource = resource;
    if (!correct) {
      logger.info(format(MESSAGES.getString("PATTERN_MISMATCH_INFO"), identifier, regexp, collection));
      resource = fixResource(resource, identifier);
    }
    if (resource == null) {
      logger.warning(format(MESSAGES.getString("CORRECTION_FAILED_DROP"), report_resource, collection));
      return Optional.empty();
    } else {
      logger.fine(format("Added resource {0}", resource));
      return Optional.of(resource);
    }
  }


  /**
   * @param resource
   * @return
   */
  private static Pair<String, String> extractPartsFromNonCanonical(String resource) {
    String identifier = "";
    String provider = "";
    for (Map.Entry<String, String> entry : ALTERNATIVE_URL_PATTERNS.entrySet()) {
      Matcher matcher = Pattern.compile(entry.getKey()).matcher(resource);
      if (matcher.matches()) {
        identifier = matcher.group("id");
        provider = PREFIX_FOR_COLLECTION.get(entry.getValue());
        break;
      }
    }
    return Pair.of(provider, identifier);
  }


  /**
   * Get pattern from collection name
   *
   * @param collection
   * @return
   */
  public static String getPattern(String collection) {
    return PATTERN_FOR_COLLECTION.getOrDefault(collection, "");
  }


  /**
   * @param id:
   *        Id part of annotation URL
   * @param pattern:
   *        Pattern for matching collection
   * @return
   */
  public static Boolean checkPattern(String id, String pattern) {
    return Pattern.compile(pattern).matcher(id).matches();
  }


  /**
   * @param resource
   * @param identifier
   * @return
   */
  private static String fixResource(String resource, String identifier) {
    if (resource.contains("chebi")) {
      resource = fixChEBI(resource, identifier);
    } else if (resource.contains("ec-code")) {
      resource = fixECCode(resource, identifier);
    } else if (resource.contains("go") && !resource.contains("goa")) {
      resource = fixGO(resource, identifier);
    } else if (resource.contains("hmdb") && identifier.startsWith("/")) {
      resource = replace(resource, identifier, identifier.substring(1));
      logger.severe(format("Changed identifier '{0}' to '{1}'", identifier, identifier.substring(1)));
    } else if (resource.contains("kegg")) {
      // We can correct the kegg collection
      resource = fixKEGGCollection(resource, identifier);
    } else if (resource.contains("reactome")) {
      String resource_old = resource;
      resource = fixReactome(resource, identifier);
      logger.info(format(MESSAGES.getString("CHANGED_REACTOME"), resource_old, resource));
    } else if (resource.contains("refseq") && resource.contains("WP_")) {
      resource = replace(resource, "refseq", "ncbiprotein");
      logger.info(
        format("Ids starting with 'WP_' seem to belong to 'ncbiprotein', not 'refseq'. Changed accordingly for '{0}'",
          resource));
    } else if (resource.contains("rhea") && resource.contains("#")) {
      // remove last part, it's invalid either way, even though it gets resolved due to misinterpretation as non
      // existing anchor
      resource = resource.split("#")[0];
      logger.info(format(MESSAGES.getString("CHANGED_RHEA"), resource));
    } else {
      resource = null;
    }
    return resource;
  }


  /**
   * @param resource
   * @param identifier
   * @return
   */
  private static String fixChEBI(String resource, String identifier) {
    if (Pattern.compile("\\d+").matcher(identifier).matches()) {
      logger.info(MESSAGES.getString("ADD_PREFIX_CHEBI"));
      resource = replace(resource, identifier, "CHEBI:" + identifier);
    }
    return resource;
  }


  /**
   * @param resource
   * @param pattern
   * @param replacement
   * @return
   */
  public static String replace(String resource, String pattern, String replacement) {
    return resource.replaceAll(pattern, replacement);
  }


  /**
   * @param resource
   * @param identifier
   * @return
   */
  private static String fixECCode(String resource, String identifier) {
    int missingDots = identifier.length() - identifier.replace(".", "").length();
    if (missingDots < 1) {
      logger.warning(format(MESSAGES.getString("EC_CHANGE_FAILED"), identifier));
      return null;
    }
    return replace(resource, identifier, identifier + ".-".repeat(Math.max(0, 3 - missingDots)));
  }


  /**
   * @param resource
   * @param identifier
   * @return
   */
  private static String fixGO(String resource, String identifier) {
    if (!identifier.toLowerCase().startsWith("go:")) {
      logger.info(MESSAGES.getString("ADD_PREFIX_GO"));
      return replace(resource, identifier, "GO:" + identifier);
    }
    return resource;
  }


  /**
   * @param resource
   * @param identifier
   * @return
   */
  private static String fixKEGGCollection(String resource, String identifier) {
    if (identifier.startsWith("D")) {
      logger.info(MESSAGES.getString("CHANGE_KEGG_DRUG"));
      resource = replace(resource, "kegg.compound", "kegg.drug");
    } else if (identifier.startsWith("G")) {
      logger.info(MESSAGES.getString("CHANGE_KEGG_GLYCAN"));
      resource = replace(resource, "kegg.compound", "kegg.glycan");
    }
    return resource;
  }


  /**
   * @param resource
   * @param identifier
   * @return
   */
  private static String fixReactome(String resource, String identifier) {
    if (identifier.startsWith("R-ALL-REACT_")) {
      identifier = identifier.split("_")[1];
    }
    if (Character.isDigit(identifier.charAt(0))) {
      identifier = "R-ALL-" + identifier;
    }
    if (checkPattern(identifier, PATTERN_FOR_COLLECTION.get("Reactome"))) {
      return createURI("reactome", identifier);
    } else {
      return null;
    }
  }


  /**
   * @param resource
   * @return
   */
  public static List<String> getPartsFromCanonicalURI(String resource) {
    Pattern identifiersURL = Pattern.compile("(?:https?://)?identifiers.org/(?:(?<provider>.*?)/)?(?<id>.*)");
    Matcher matcher = identifiersURL.matcher(resource);
    List<String> parts = new ArrayList<>(2);
    if (matcher.matches()) {
      parts.add(matcher.group("provider"));
      parts.add(matcher.group("id"));
    }
    return parts;
  }


  /**
   * Get collection from identifiers.org URI
   *
   * @param resource:
   *        identifiers.org URI
   * @return
   */
  public static String getDataCollectionPartFromURI(String resource) {
    if (resource.contains("identifiers.org")) {
      return resource.split("/")[3];
    } else {
      return resource.split("/")[2];
    }
  }
}
