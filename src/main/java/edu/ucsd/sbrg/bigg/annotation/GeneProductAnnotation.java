package edu.ucsd.sbrg.bigg.annotation;

import de.zbit.util.ResourceManager;
import edu.ucsd.sbrg.bigg.BiGGId;
import edu.ucsd.sbrg.db.BiGGDB;
import edu.ucsd.sbrg.miriam.Registry;
import edu.ucsd.sbrg.util.SBMLUtils;
import org.sbml.jsbml.CVTerm;
import org.sbml.jsbml.CVTerm.Qualifier;
import org.sbml.jsbml.ext.fbc.GeneProduct;

import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static edu.ucsd.sbrg.bigg.annotation.BiGGAnnotation.getBiGGIdFromResources;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.TYPE_GENE_PRODUCT;
import static java.text.MessageFormat.format;

public class GeneProductAnnotation extends CVTermAnnotation {

  /**
   * A {@link Logger} for this class.
   */
  static final transient Logger logger = Logger.getLogger(GeneProductAnnotation.class.getName());
  /**
   * Localization support.
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   * Instance of gene product to annotate
   */
  private final GeneProduct geneProduct;

  public GeneProductAnnotation(GeneProduct geneProduct) {
    this.geneProduct = geneProduct;
  }


  /**
   * Adds annotation for a gene product
   */
  @Override
  public void annotate() {
    Optional<BiGGId> biggId = checkId();
    // TODO: don't pass optional around, handle this differently
    Optional<String> label = getLabel(biggId);
    if (label.isEmpty()) {
      return;
    }
    // fix geneProductReference in Association not updated
    SBMLUtils.updateGeneProductReference(geneProduct);
    biggId.ifPresent(id -> {
      addAnnotations(id);
      if (geneProduct.getCVTermCount() > 0) {
        geneProduct.setMetaId(id.toBiGGId());
      }
    });
    setGPLabelName(label.get());
  }


  /**
   * Checks if {@link GeneProduct#getId()} returns a correct {@link BiGGId} and tries to retrieve a corresponding
   * {@link BiGGId} based on annotations present.
   *
   * @return String representation of {@link BiGGId}
   */
  @Override
  public Optional<BiGGId> checkId() {
    String id = geneProduct.getId();
    boolean isBiGGid = id.matches("^(G_)?([a-zA-Z][a-zA-Z0-9_]+)(?:_([a-z][a-z0-9]?))?(?:_([A-Z][A-Z0-9]?))?$");
    if (!isBiGGid) {
      // Flatten all resources for all CVTerms into a list
      List<String> resources = geneProduct.getAnnotation().getListOfCVTerms().stream()
                                          .filter(cvTerm -> cvTerm.getQualifier() == Qualifier.BQB_IS)
                                          .flatMap(term -> term.getResources().stream()).collect(Collectors.toList());
      if (!resources.isEmpty()) {
        // update id if we found something
        id = getBiGGIdFromResources(resources, TYPE_GENE_PRODUCT).orElse(id);
      }
    }
    return BiGGId.createGeneId(id);
  }


  /**
   * @param biggId
   * @return
   */
  public Optional<String> getLabel(Optional<BiGGId> biggId) {
    if (geneProduct.isSetLabel() && !geneProduct.getLabel().equalsIgnoreCase("None")) {
      return Optional.of(geneProduct.getLabel());
    } else if (geneProduct.isSetId()) {
      return biggId.map(BiGGId::toBiGGId);
    } else {
      return Optional.of("");
    }
  }


  /**
   * Set gene product label and, if possible, update or set the gene product name to the one obtained from BiGG by use
   * of the label, which was either already set or corresponds to a {@link BiGGId} created from
   * {@link GeneProduct#getId()}
   *
   * @param label
   */
  public void setGPLabelName(String label) {
    // we successfully found information by using the id, so this needs to be the label
    if (geneProduct.getLabel().equalsIgnoreCase("None")) {
      geneProduct.setLabel(label);
    }
    BiGGDB.getGeneName(label).ifPresent(geneName -> {
      if (geneName.isEmpty()) {
        logger.fine(format(MESSAGES.getString("NO_GENE_FOR_LABEL"), geneProduct.getName()));
      } else if (geneProduct.isSetName() && !geneProduct.getName().equals(geneName)) {
        logger.warning(format(MESSAGES.getString("UPDATE_GP_NAME"), geneProduct.getName(), geneName));
      }
      geneProduct.setName(geneName);
    });
  }


  /**
   * Add annotations for gene product based on {@link BiGGId}
   *
   * @param biggId:
   *        {@link BiGGId} from species id
   */
  @Override
  public void addAnnotations(BiGGId biggId) {
    CVTerm termIs = new CVTerm(Qualifier.BQB_IS);
    CVTerm termEncodedBy = new CVTerm(Qualifier.BQB_IS_ENCODED_BY);
    // label is stored without "G_" prefix in BiGG
    BiGGDB.getGeneIds(biggId.getAbbreviation()).forEach(
      resource -> Registry.checkResourceUrl(resource).map(Registry::getPartsFromCanonicalURI)
                          .filter(parts -> parts.size() > 0).map(parts -> parts.get(0)).ifPresent(collection -> {
                            switch (collection) {
                            case "interpro":
                            case "pdb":
                            case "uniprot":
                              termIs.addResource(resource);
                              break;
                            default:
                              termEncodedBy.addResource(resource);
                            }
                          }));
    if (termIs.getResourceCount() > 0) {
      geneProduct.addCVTerm(termIs);
    }
    if (termEncodedBy.getResourceCount() > 0) {
      geneProduct.addCVTerm(termEncodedBy);
    }
  }
}
