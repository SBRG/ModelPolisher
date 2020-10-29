package edu.ucsd.sbrg.miriam;

import static java.text.MessageFormat.format;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.sbml.jsbml.Annotation;
import org.sbml.jsbml.CVTerm;

import de.zbit.util.ResourceManager;
import edu.ucsd.sbrg.bigg.BiGGId;
import edu.ucsd.sbrg.miriam.models.Miriam;
import us.hebi.matlab.mat.types.Char;

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
    Entries.initFromList(
      miriam.getNamespaces().values().parallelStream().map(CompactEntry::fromNamespace).collect(Collectors.toList()));
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
      return Optional.of(handleNCBIGI(resource));
    }
    if (resource.contains("reactome")) {
      resource = fixReactomeId(resource);
    }
    Pattern identifiersURL = Pattern.compile("(?:https?://)?identifiers.org/(?:(?<provider>.*?)/)?(?<id>.*)");
    Matcher urlMatcher = identifiersURL.matcher(resource);
    if (urlMatcher.matches()) {
      resource = handleIdentifiersURL(resource, urlMatcher);
    } else {
      resource = handleAlternativeURL(resource);
    }
    if (resource != null) {
      return Optional.of(resource);
    } else {
      return Optional.empty();
    }
  }


  /**
   * @param resource
   *        annotation URL
   * @return resource with corrected NCBI GI id
   */
  private static String handleNCBIGI(String resource) {
    String[] split = resource.split("/");
    String id = split[split.length - 1];
    if (!id.startsWith("GI:")) {
      if (id.startsWith("gi:")) {
        resource = replace(resource, id, id.replaceAll("gi:", "GI:"));
      } else {
        resource = replace(resource, id, "GI:" + id);
      }
    }
    return resource;
  }


  /**
   * @param resource
   *        annotation url
   * @return resource with corrected reactome id
   */
  private static String fixReactomeId(String resource) {
    String identifier = "";
    if (resource.contains("R-ALL-REACT_")) {
      identifier = identifier.split("R-ALL-REACT_")[1];
      if (Character.isDigit(identifier.charAt(0))) {
        identifier = "R-ALL-" + identifier;
      }
      return resource.replaceAll("R-ALL-REACT_.*$", identifier);
    }
    return resource;
  }


  /**
   * Create and check identifiers.org URLs
   *
   * @param resource
   *        identifiers.org URL to be checked
   * @param urlMatcher
   *        {@link Matcher} used to verify valid identifiers.org URL used to extract provider and and id
   * @return Valid identifiers.org URL, uncorrected URL if collection is unknown or {@code null}, if id can not be
   *         corrected
   */
  public static String handleIdentifiersURL(String resource, Matcher urlMatcher) {
    String provider = urlMatcher.group("provider");
    provider = provider == null ? "" : provider;
    String identifier = urlMatcher.group("id");
    // handle case mismatch in provider code
    if (provider.matches("[A-Z]+")) {
      provider = provider.toLowerCase();
    }
    Entries entries = Entries.getInstance();
    // Get provider by checking for uniquely matching Regex
    if (provider.isEmpty()) {
      Node collection = entries.getCollection(resource);
      if (collection != null) {
        provider = entries.getProviderForCollection(collection.getName());
      }
    }
    // handle cases where provider and collection name have been mixed up
    if (entries.getCollectionForProvider(provider).isEmpty() && !entries.getProviderForCollection(provider).isEmpty()) {
      provider = entries.getProviderForCollection(provider);
    } else if (entries.getCollectionForProvider(provider).isEmpty()) {
      logger.severe(format(MESSAGES.getString("UNCAUGHT_URI"), resource));
      return resource;
    }
    resource = createURI(provider, identifier);
    String collection = entries.getCollectionForProvider(provider);
    String regexp = entries.getPattern(collection);
    boolean correct = checkPattern(identifier, regexp);
    String report_resource = resource;
    if (!correct) {
      logger.info(format(MESSAGES.getString("PATTERN_MISMATCH_INFO"), identifier, regexp, collection));
      resource = fixResource(resource, identifier);
    }
    if (resource == null) {
      logger.warning(format(MESSAGES.getString("CORRECTION_FAILED_DROP"), report_resource, collection));
    } else {
      logger.finer(format("Added resource {0}", resource));
    }
    return resource;
  }


  /**
   * Retrieve identifiers.org URL from non identifiers.org URL, if possible
   *
   * @param resource
   *        non identifiers.org URL
   * @return identifiers.org URL, if retrieval was possible, else original resource
   */
  public static String handleAlternativeURL(String resource) {
    Entries entries = Entries.getInstance();
    Node collection = entries.getCollection(resource);
    if (collection == null) {
      // can't retrieve a corresponding identifiers.org URL in this case, so return the original URL instead
      // Some fixing might be possible, however this is not implemented for now, as non identifiers URL identifier
      // extraction without knowing the proper collection is not trivial
      return resource;
    }
    Optional<String> identifier = collection.extractId(resource);
    if (identifier.isPresent()) {
      String provider = entries.getProviderForCollection(collection.getName());
      resource = createURI(provider, identifier);
    }
    return resource;
  }


  public static String createURI(String provider, BiGGId id) {
    return createURI(provider, id.getAbbreviation());
  }


  public static String createURI(String provider, Object id) {
    return createURI(provider, id.toString());
  }


  public static String createURI(String provider, String id) {
    return "https://identifiers.org/" + provider + "/" + id;
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
    if(identifier.isEmpty()){
      // this is invalid
      return null;
    }
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
    // identifiers with case mismatch exist, correct them
    char first = identifier.charAt(0);
    identifier = Character.toUpperCase(first) + identifier.substring(1);
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
   * Run all object annotations to possibly fix IDs and/or obtain additional identifiers.org URL
   * 
   * @param annotation
   *        {@link Annotation} present on an SBML entity to process
   */
  public static void processResources(Annotation annotation) {
    for (CVTerm term : annotation.getListOfCVTerms()) {
      Set<String> resources = new HashSet<>();
      for (String resource : term.getResources()) {
        Optional<String> checkedResource = Registry.checkResourceUrl(resource);
        if (checkedResource.isEmpty()) {
          // could not verify, keep annotation for now
          resources.add(resource);
        } else {
          String newResource = checkedResource.get();
          if (newResource.equals(resource)) {
            // no changes
            resources.add(resource);
          } else if (newResource.contains("identifiers.org") && !resource.contains("identifiers.org")) {
            // identifiers.org URL was obtained
            resources.add(resource);
            resources.add(newResource);
          } else {
            // some errors were corrected
            resources.add(newResource);
          }
        }
      }
      // remove old resources
      for (int i = 0; i < term.getResourceCount(); i++) {
        term.removeResource(i);
      }
      // add fixed/additional resources
      term.addResources(resources.stream().sorted().toArray(String[]::new));
    }
  }


  /**
   * Extracts provider and id from identifiers.org url
   *
   * @param url
   *        vallid identifiers.org URL
   * @return provider code and id
   */
  public static List<String> getPartsFromIdentifiersURI(String url) {
    List<String> parts = new ArrayList<>();
    Matcher identifiersURL =
      Pattern.compile("(https?://)?(www\\.)?identifiers\\.org/(?<prefix>.*?)/(?<id>.*)").matcher(url);
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
