package edu.ucsd.sbrg.miriam;

import static java.text.MessageFormat.format;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import de.zbit.util.ResourceManager;
import edu.ucsd.sbrg.miriam.models.Miriam;

public class RegistryTest {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(RegistryTest.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  Miriam miriam = RegistryProvider.getInstance().getMiriam();

  @Test
  public void patternTest() {
    // Assert that every MIRIAM resource can be matched
    Entries.initFromList(miriam.getNamespaces().values().parallelStream().map(CompactEntry::fromNamespace).collect(Collectors.toList()));
    Entries entries = Entries.getInstance();
    for (Namespace node : entries.get()) {
      // Test enclosing namespace
      String sampleId = node.getSampleId();
      String query = node.resolveID(sampleId);
      // ark sample id does not match pattern, skip validation
      if (!query.equals("https://identifiers.org/ark/(ark\\:)/12345/fk1234")) {
        assertTrue(node.isMatch(query),
          format("Query \"{0}\" did not match namespace pattern: {1}", query, node.getURLWithPattern()));
        assertTrue(node.extractId(query).isPresent(), format("Could not extract id for namespace query: {0}", query));
        String extractedId = node.extractId(query).get();
        // Workaround for wrong regex
        if (!(extractedId.equals("HGVPM623") || extractedId.equals("P00266"))) {
          assertTrue(node.matchesPattern(extractedId),
            format("Mismatch between extracted namespace id and pattern. Expected: {0} Was: {1}", node.getPattern(),
              extractedId));
        }
      }
      for (Resource leaf : node.getLeaves()) {
        // Test resource id resolution and pattern matching
        CompactResource resource = leaf.getResource();
        String id = resource.getSampleId();
        query = leaf.resolveID(id);
        List<Node> results = entries.getMatchForUrl(query);
        assertTrue(results.size() > 0, format("Could not get a match for query: {0}", query));
        boolean matched = false;
        for (Node result : results) {
          matched |= result.isMatch(query);
        }
        assertTrue(matched, format("Query \"{0}\" did not match resource Pattern.", query));
        assertTrue(leaf.extractId(query).isPresent(), format("Could not extract id for resource query: {0}", query));
        String extractedId = leaf.extractId(query).get();
        // workaround for wrong regex
        if (!(extractedId.equals("HGVPM623") || extractedId.equals("P00266"))) {
          assertTrue(leaf.matchesPattern(extractedId),
            format("Mismatch between extracted resource id and pattern. Expected: {0} Was: {1}", leaf.getPattern(),
              extractedId));
        }
      }
    }
  }
}
