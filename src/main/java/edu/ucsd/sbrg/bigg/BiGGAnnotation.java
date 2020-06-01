package edu.ucsd.sbrg.bigg;

import de.zbit.util.ResourceManager;
import de.zbit.util.progressbar.AbstractProgressBar;
import de.zbit.util.progressbar.ProgressBar;
import edu.ucsd.sbrg.bigg.annotation.CompartmentAnnotation;
import edu.ucsd.sbrg.bigg.annotation.GeneProductAnnotation;
import edu.ucsd.sbrg.bigg.annotation.ModelAnnotation;
import edu.ucsd.sbrg.bigg.annotation.ReactionAnnotation;
import edu.ucsd.sbrg.bigg.annotation.SpeciesAnnotation;
import edu.ucsd.sbrg.db.BiGGDB;
import edu.ucsd.sbrg.db.BiGGDBContract;
import edu.ucsd.sbrg.db.QueryOnce;
import edu.ucsd.sbrg.miriam.Registry;
import org.sbml.jsbml.CVTerm;
import org.sbml.jsbml.CVTerm.Qualifier;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBase;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCModelPlugin;
import org.sbml.jsbml.ext.fbc.GeneProduct;
import org.sbml.jsbml.util.Pair;

import javax.swing.tree.TreeNode;
import javax.xml.stream.XMLStreamException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import static java.text.MessageFormat.format;

/**
 * @author Thomas Zajac
 *         This code runs only, if ANNOTATE_WITH_BIGG is true
 */
public class BiGGAnnotation {

  /**
   * A {@link Logger} for this class.
   */
  static final transient Logger logger = Logger.getLogger(BiGGAnnotation.class.getName());
  /**
   * Localization support.
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   * Progressbar for the whole annotation process
   */
  private AbstractProgressBar progress;
  /**
   * Variable tracking gene product count for progress bar, as it changes dynamically during processing
   */
  private int initialGeneProducts;

  public BiGGAnnotation() {
  }


  /**
   * Adds annotations from BiGG Knowledgebase for the model contained in the {@link SBMLDocument}
   * 
   * @param doc:
   *        {@link SBMLDocument} to be annotated with data from BiGG Knowledgebase
   * @return Annotated SBMLDocument
   */
  public SBMLDocument annotate(SBMLDocument doc) {
    if (!doc.isSetModel()) {
      logger.info(MESSAGES.getString("NO_MODEL_FOUND"));
      return doc;
    }
    Model model = doc.getModel();
    // add fake count so it never reaches 100 before gene products are processed, as new gene products are added
    // dynamically
    int count = model.getCompartmentCount() + model.getSpeciesCount() + model.getReactionCount() + 50;
    if (model.isSetPlugin(FBCConstants.shortLabel)) {
      FBCModelPlugin fbcModelPlug = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
      initialGeneProducts = fbcModelPlug.getGeneProductCount();
      count += initialGeneProducts;
    }
    progress = new ProgressBar(count);
    Map<String, String> replacements = processReplacements(model);
    ModelAnnotation modelAnnotation = new ModelAnnotation(model);
    modelAnnotation.annotate();
    annotatePublications(model);
    annotateListOfCompartments(model);
    annotateListOfSpecies(model);
    annotateListOfReactions(model);
    annotateListOfGeneProducts(model);
    try {
      appendNotes(doc, replacements);
    } catch (IOException | XMLStreamException exc) {
      logger.warning(MESSAGES.getString("FAILED_WRITE_NOTES"));
    }
    // Recursively sort and group all annotations in the SBMLDocument.
    mergeMIRIAMannotations(doc);
    if (progress != null) {
      progress.finished();
    }
    return doc;
  }


  /**
   * Replace placeholders in {@link Parameters#documentTitlePattern()} and ModelNotes
   *
   * @param model:
   *        {@link Model} contained within {@link SBMLDocument} passed to {@link #annotate(SBMLDocument)}
   */
  private Map<String, String> processReplacements(Model model) {
    String id = model.getId();
    // Empty organism name should be ok, if it is not a BiGG model
    String organism = BiGGDB.getOrganism(id).orElse("");
    Parameters parameters = Parameters.get();
    String name = parameters.documentTitlePattern();
    name = name.replace("[biggId]", id);
    name = name.replace("[organism]", organism);
    Map<String, String> replacements = new HashMap<>();
    replacements.put("${organism}", organism);
    replacements.put("${title}", name);
    replacements.put("${bigg_id}", id);
    replacements.put("${year}", Integer.toString(Calendar.getInstance().get(Calendar.YEAR)));
    replacements.put("${bigg.timestamp}", BiGGDB.getBiGGVersion().map(date -> format("{0,date}", date)).orElse(""));
    replacements.put("${species_table}", "");
    if (!model.isSetName()) {
      model.setName(organism);
    }
    return replacements;
  }


  /**
   * Replaces generic placeholders in notes files and appends both note types
   *
   * @param doc:
   *        {@link SBMLDocument} to add notes to
   * @throws IOException:
   *         propagated from {@link SBMLDocument#appendNotes(String)} or {@link Model#appendNotes(String)}
   * @throws XMLStreamException:
   *         propagated from {@link SBMLDocument#appendNotes(String)} or {@link Model#appendNotes(String)}
   */
  private void appendNotes(SBMLDocument doc, Map<String, String> replacements) throws IOException, XMLStreamException {
    Parameters parameters = Parameters.get();
    String modelNotesFile = "ModelNotes.html";
    String documentNotesFile = "SBMLDocumentNotes.html";
    if ((parameters.noModelNotes() != null) && parameters.noModelNotes()) {
      modelNotesFile = null;
      documentNotesFile = null;
    } else {
      if (parameters.modelNotesFile() != null) {
        File modelNotes = parameters.modelNotesFile();
        modelNotesFile = modelNotes != null ? modelNotes.getAbsolutePath() : null;
      }
      if (parameters.documentNotesFile() != null) {
        File documentNotes = parameters.documentNotesFile();
        documentNotesFile = documentNotes != null ? documentNotes.getAbsolutePath() : null;
      }
    }
    if (replacements.containsKey("${title}") && (documentNotesFile != null)) {
      doc.appendNotes(parseNotes(documentNotesFile, replacements));
    }
    if (modelNotesFile != null) {
      doc.getModel().appendNotes(parseNotes(modelNotesFile, replacements));
    }
  }


  /**
   * Recursively goes through all annotations in the given {@link SBase} and
   * alphabetically sort annotations after grouping them by {@link org.sbml.jsbml.CVTerm.Qualifier}.
   *
   * @param sbase:
   *        {@link SBase} to start the merging process at, corresponding to an instance of {@link SBMLDocument} here,
   *        though also used to pass current {@link SBase} during recursion
   */
  private void mergeMIRIAMannotations(SBase sbase) {
    if (sbase.isSetAnnotation()) {
      SortedMap<Qualifier, SortedSet<String>> miriam = new TreeMap<>();
      boolean doMerge = hashMIRIAMuris(sbase, miriam);
      if (doMerge) {
        sbase.getAnnotation().unsetCVTerms();
        for (Entry<Qualifier, SortedSet<String>> entry : miriam.entrySet()) {
          logger.info(format(MESSAGES.getString("MERGING_MIRIAM_RESOURCES"), entry.getKey(),
            sbase.getClass().getSimpleName(), sbase.getId()));
          sbase.addCVTerm(new CVTerm(entry.getKey(), entry.getValue().toArray(new String[0])));
        }
      }
    }
    for (int i = 0; i < sbase.getChildCount(); i++) {
      TreeNode node = sbase.getChildAt(i);
      if (node instanceof SBase) {
        mergeMIRIAMannotations((SBase) node);
      }
    }
  }


  /**
   * @param sbase:
   *        Current {@link SBase} to merge annotations for
   * @param miriam:
   *        Current annotations for the given {@link SBase}
   * @return Returns {@code true}, if there are different {@link CVTerm} instances with the same qualifier that need to
   *         be
   *         merged
   */
  private boolean hashMIRIAMuris(SBase sbase, SortedMap<Qualifier, SortedSet<String>> miriam) {
    boolean doMerge = false;
    for (int i = 0; i < sbase.getCVTermCount(); i++) {
      CVTerm term = sbase.getCVTerm(i);
      Qualifier qualifier = term.getQualifier();
      if (miriam.containsKey(qualifier)) {
        doMerge = true;
      } else {
        if (sbase instanceof Model) {
          if (!qualifier.isModelQualifier()) {
            logger.info(format(MESSAGES.getString("CORRECTING_INVALID_QUALIFIERS"),
              qualifier.getElementNameEquivalent(), sbase.getId()));
            qualifier = Qualifier.getModelQualifierFor(qualifier.getElementNameEquivalent());
          }
        } else if (!qualifier.isBiologicalQualifier()) {
          logger.info(format(MESSAGES.getString("CORRECTING_INVALID_MODEL_QUALIFIER"),
            qualifier.getElementNameEquivalent(), sbase.getClass().getSimpleName(), sbase.getId()));
          qualifier = Qualifier.getBiologicalQualifierFor(qualifier.getElementNameEquivalent());
        }
        miriam.put(qualifier, new TreeSet<>());
      }
      miriam.get(qualifier).addAll(term.getResources());
    }
    return doMerge;
  }


  /**
   * Add publication annotation for given {@link Model}.
   * Only works for models contained in BiGG
   *
   * @param model:
   *        {@link Model} contained within {@link SBMLDocument} passed to {@link #annotate(SBMLDocument)}
   */
  private void annotatePublications(Model model) {
    progress.DisplayBar("Annotating Publications (1/5)  ");
    String id = model.getId();
    if (!QueryOnce.isModel(id)) {
      return;
    }
    List<Pair<String, String>> publications = BiGGDB.getPublications(id);
    int numPublications;
    if ((numPublications = publications.size()) > 0) {
      String[] resources = new String[numPublications];
      int i = 0;
      for (Pair<String, String> publication : publications) {
        resources[i++] = Registry.createURI(publication.getKey(), publication.getValue());
      }
      model.addCVTerm(new CVTerm(CVTerm.Qualifier.BQM_IS_DESCRIBED_BY, resources));
    }
  }


  /**
   * Delegates annotation processing for all compartments contained in the {@link Model}
   *
   * @param model:
   *        {@link Model} contained within {@link SBMLDocument} passed to {@link #annotate(SBMLDocument)}
   */
  private void annotateListOfCompartments(Model model) {
    for (int i = 0; i < model.getCompartmentCount(); i++) {
      progress.DisplayBar("Annotating Compartments (2/5)  ");
      CompartmentAnnotation compartmentAnnotation = new CompartmentAnnotation(model.getCompartment(i));
      compartmentAnnotation.annotate();
    }
  }


  /**
   * Delegates annoation processing for all chemical species contained in the {@link Model}
   *
   * @param model:
   *        {@link Model} contained within {@link SBMLDocument} passed to {@link #annotate(SBMLDocument)}
   */
  private void annotateListOfSpecies(Model model) {
    for (int i = 0; i < model.getSpeciesCount(); i++) {
      progress.DisplayBar("Annotating Species (3/5)  ");
      SpeciesAnnotation speciesAnnotation = new SpeciesAnnotation(model.getSpecies(i));
      speciesAnnotation.annotate();
    }
  }


  /**
   * Tries to get a BiGG ID specification conform id from BiGG knowledgebase for a given {@link Species},
   * {@link Reaction} or {@link GeneProduct} from the annotations present
   *
   * @param resources:
   *        Annotations for the given object, should be a list of URIs
   * @param type:
   *        Either {@link BiGGDBContract.Constants#TYPE_SPECIES}, {@link BiGGDBContract.Constants#TYPE_REACTION} or
   *        {@link BiGGDBContract.Constants#TYPE_GENE_PRODUCT}
   * @return {@link Optional<String>} if an id could be retrieved, else {@link Optional#empty()}
   */
  public static Optional<String> getBiGGIdFromResources(List<String> resources, String type) {
    for (String resource : resources) {
      Optional<String> id = Registry.checkResourceUrl(resource).map(Registry::getPartsFromCanonicalURI)
                                    .flatMap(parts -> getBiggIdFromParts(parts, type));
      if (id.isPresent()) {
        return id;
      }
    }
    return Optional.empty();
  }


  /**
   * Tries to get id from BiGG Knowledgebase based on annotation prefix and id for specific species, reaction or gene
   * product
   *
   * @param parts:
   *        Parts retrieved from the identifiers.org URI - prefix and id
   * @param type:
   *        Either {@link BiGGDBContract.Constants#TYPE_SPECIES}, {@link BiGGDBContract.Constants#TYPE_REACTION} or
   *        {@link BiGGDBContract.Constants#TYPE_GENE_PRODUCT}
   * @return {@link Optional<String>} containing the id, if one could be retrieved, else {@link Optional#empty()}
   */
  private static Optional<String> getBiggIdFromParts(List<String> parts, String type) {
    String prefix = parts.get(0);
    String synonymId = parts.get(1);
    if (QueryOnce.isDataSource(prefix)) {
      Optional<String> id = BiGGDB.getBiggIdFromSynonym(prefix, synonymId, type);
      if (id.isPresent()) {
        return id;
      }
    }
    return Optional.empty();
  }


  /**
   * Delegates annotation of reactions
   *
   * @param model:
   *        {@link Model} contained within {@link SBMLDocument} passed to {@link #annotate(SBMLDocument)}
   */
  private void annotateListOfReactions(Model model) {
    for (int i = 0; i < model.getReactionCount(); i++) {
      progress.DisplayBar("Annotating Reactions (4/5)  ");
      ReactionAnnotation reactionAnnotation = new ReactionAnnotation(model.getReaction(i));
      reactionAnnotation.annotate();
    }
  }


  /**
   * Delegates annotation of gene products
   *
   * @param model:
   *        {@link Model} contained within {@link SBMLDocument} passed to {@link #annotate(SBMLDocument)}
   */
  private void annotateListOfGeneProducts(Model model) {
    if (model.isSetPlugin(FBCConstants.shortLabel)) {
      FBCModelPlugin fbcModelPlugin = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
      // update progress bar for added geneProducts, i.e. change dummy count to correct one
      int changed = fbcModelPlugin.getNumGeneProducts() - initialGeneProducts;
      if (changed > 0) {
        long current = progress.getCallNumber();
        // substract fake count
        progress.setNumberOfTotalCalls(progress.getNumberOfTotalCalls() + changed - 50);
        progress.setCallNr(current);
      }
      for (GeneProduct geneProduct : fbcModelPlugin.getListOfGeneProducts()) {
        progress.DisplayBar("Annotating Gene Products (5/5)  ");
        GeneProductAnnotation geneProductAnnotation = new GeneProductAnnotation(geneProduct);
        geneProductAnnotation.annotate();
      }
    }
  }


  /**
   * @param location:
   *        relative path to the resource from this class.
   * @param replacements:
   *        map of actual values for placeholder tokens in the notes
   * @return Constants.URL_PREFIX + " like '%%identifiers.org%%'"
   * @throws IOException:
   *         propagated from {@link FileInputStream()}
   */
  private String parseNotes(String location, Map<String, String> replacements) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (InputStream is = getClass().getResourceAsStream(location);
        InputStreamReader isReader = new InputStreamReader((is != null) ? is : new FileInputStream(new File(location)));
        BufferedReader br = new BufferedReader(isReader)) {
      String line;
      boolean start = false;
      while (br.ready() && ((line = br.readLine()) != null)) {
        if (line.matches("\\s*<body.*")) {
          start = true;
        }
        if (!start) {
          continue;
        }
        if (line.matches(".*\\$\\{.*}.*")) {
          for (String key : replacements.keySet()) {
            line = line.replace(key, replacements.get(key));
          }
        }
        sb.append(line);
        sb.append('\n');
        if (line.matches("\\s*</body.*")) {
          break;
        }
      }
    }
    return sb.toString();
  }
}
