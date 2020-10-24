package edu.ucsd.sbrg.miriam;

import static java.text.MessageFormat.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.zbit.util.ResourceManager;
import edu.ucsd.sbrg.bigg.BiGGId;
import edu.ucsd.sbrg.miriam.models.Miriam;

public class Registry {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(Registry.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");

  /*
   * Static initializer for Miriam, read registry once and convert to compact representation
   */
  static {
    Miriam miriam = RegistryProvider.getInstance().getMiriam();
    // convert namespaces to CompactEntries, holding only the necessary information for ModelPolisher
    Entries.initFromList(miriam.getNamespaces().values().parallelStream().map(CompactEntry::fromNamespace).collect(Collectors.toList()));
    // Close unneeded resources
    RegistryProvider.close();
  }

  public static Optional<String> checkResourceUrl(String resource) {
    // remove trailing whitespaces
    resource = resource.stripTrailing();
    if (resource.startsWith("https://identifiers.org/omim")) {
      // omim is present in BiGGDB, but is not valid, skip
      return Optional.empty();
    }
    // no longer supported by identifiers.org, but should still resolve, keep and fix missing id prefix
    if (resource.contains("ncbigi")) {
      String[] split = resource.split("/");
      String id = split[split.length - 1];
      if (!id.startsWith("GI:")) {
        if (id.startsWith("gi:")) {
          resource = replace(resource, id, id.replaceAll("gi:", "GI:"));
        } else {
          resource = replace(resource, id, "GI:" + id);
        }
      }
      return Optional.of(resource);
    }
    Pattern identifiersURL = Pattern.compile("(?:https?://)?identifiers.org/(?:(?<prefix>.*?)/)?(?<id>.*)");
    Matcher urlMatcher = identifiersURL.matcher(resource);
    String prefix = "";
    String identifier = "";
    if (urlMatcher.matches()) {
      prefix = urlMatcher.group("prefix");
      prefix = prefix == null ? "" : prefix;
      identifier = urlMatcher.group("id");
    }
    // handle case mismatch in provider code
    if (prefix.matches("[A-Z]+")) {
      prefix = prefix.toLowerCase();
    }
    Entries entries = Entries.getInstance();
    String query = identifier;
    // Get provider by checking for uniquely matching Regex
    if (prefix.isEmpty()) {
      String collection = entries.getCollection(query);
      if (collection != null) {
        prefix = entries.getPrefixForCollection(collection);
      }
    }
    // handle cases where provider and collection name have been mixed up
    if (entries.getCollectionForPrefix(prefix).isEmpty() && !entries.getPrefixForCollection(prefix).isEmpty()) {
      prefix = entries.getPrefixForCollection(prefix);
    } else if (entries.getCollectionForPrefix(prefix).isEmpty()) {
      logger.severe(format(MESSAGES.getString("UNCAUGHT_URI"), resource));
      return Optional.of(resource);
    }
    String collection = entries.getCollectionForPrefix(prefix);
    String regexp = entries.getPattern(collection);
    Boolean correct = checkPattern(identifier, regexp);
    resource = createURI(prefix, identifier);
    String report_resource = resource;
    if (!correct) {
      logger.info(format(MESSAGES.getString("PATTERN_MISMATCH_INFO"), identifier, regexp, collection));
      resource = fixResource(resource, identifier);
    }
    if (resource == null) {
      logger.warning(format(MESSAGES.getString("CORRECTION_FAILED_DROP"), report_resource, collection));
      return Optional.empty();
    } else {
      logger.finer(format("Added resource {0}", resource));
      return Optional.of(resource);
    }
  }


  public static String createURI(String prefix, BiGGId id) {
    return createURI(prefix, id.getAbbreviation());
  }


  public static String createURI(String prefix, Object id) {
    return createURI(prefix, id.toString());
  }


  public static String createURI(String prefix, String id) {
    return "https://identifiers.org/" + prefix + "/" + id;
  }


  public static String createShortURI(Object id) {
    return "https://identifiers.org/" + id.toString();
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
      logger.severe(format("Changed identifier {0} to {1}", identifier, identifier.substring(1)));
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
        format("Ids starting with 'WP_' seem to belong to 'ncbiprotein', not 'refseq'. Changed accordingly for {0}",
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
    if (checkPattern(identifier, Entries.getInstance().getPattern("Reactome"))) {
      return createURI("reactome", identifier);
    } else {
      return null;
    }
  }


  /**
   * Extracts provider and id from identifiers.org url
   *
   * @param url vallid identifiers.org URL
   * @return provider code and id
   */
  public static List<String> getPartsFromIdentifiersURI(String url) {
    List<String> parts = new ArrayList<>();
    Matcher identifiersURL = Pattern.compile("(https?://)?(www\\.)?identifiers\\.org/(?<prefix>.*?)/(?<id>.*)").matcher(url);
    if (identifiersURL.matches()) {
      String prefix = identifiersURL.group("prefix");
      String id = identifiersURL.group("id");
      if (prefix != null) {
        parts.add(prefix);
      }
      if (id != null) {
        parts.add(id);
      }
    }
    return parts;
  }



  public static Boolean checkPattern(String id, String pattern) {
    return Pattern.compile(pattern).matcher(id).matches();
  }
}
