package edu.ucsd.sbrg.bigg;

import static edu.ucsd.sbrg.bigg.ModelPolisher.mpMessageBundle;
import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.BIGG_METABOLITE;
import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.BIGG_REACTION;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.TYPE_GENE_PRODUCT;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.TYPE_REACTION;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.TYPE_SPECIES;
import static java.text.MessageFormat.format;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.tree.TreeNode;
import javax.xml.stream.XMLStreamException;

import org.sbml.jsbml.CVTerm;
import org.sbml.jsbml.CVTerm.Qualifier;
import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBO;
import org.sbml.jsbml.SBase;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCModelPlugin;
import org.sbml.jsbml.ext.fbc.FBCSpeciesPlugin;
import org.sbml.jsbml.ext.fbc.GeneProduct;
import org.sbml.jsbml.ext.groups.Group;
import org.sbml.jsbml.ext.groups.GroupsConstants;
import org.sbml.jsbml.ext.groups.GroupsModelPlugin;
import org.sbml.jsbml.util.Pair;

import de.zbit.util.ResourceManager;
import de.zbit.util.Utils;
import de.zbit.util.progressbar.AbstractProgressBar;
import de.zbit.util.progressbar.ProgressBar;
import edu.ucsd.sbrg.db.AnnotateDB;
import edu.ucsd.sbrg.db.BiGGDB;
import edu.ucsd.sbrg.db.QueryOnce;
import edu.ucsd.sbrg.miriam.Registry;
import edu.ucsd.sbrg.util.GPRParser;
import edu.ucsd.sbrg.util.SBMLUtils;

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
  private static final transient ResourceBundle baseBundle =
    ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   * Default model notes.
   */
  private String modelNotes = "ModelNotes.html";
  /**
   * Mapping for generic placeholder entries in notes files to actual values
   */
  protected Map<String, String> replacements;
  /**
   * Default document notes
   */
  private String documentNotesFile = "SBMLDocumentNotes.html";
  /**
   *
   */
  private AbstractProgressBar progress;
  /**
   *
   */
  private int initialGeneProducts;

  /**
   */
  public BiGGAnnotation() {
  }


  /**
   * @param doc
   * @return
   */
  public SBMLDocument annotate(SBMLDocument doc) {
    if (!doc.isSetModel()) {
      logger.info(baseBundle.getString("NO_MODEL_FOUND"));
      return doc;
    }
    Model model = doc.getModel();
    replacements = new HashMap<>();
    // add fake count so it never reaches 100 before gene products are processed
    int count = model.getCompartmentCount() + model.getSpeciesCount() + model.getReactionCount() + 50;
    if (model.isSetPlugin(FBCConstants.shortLabel)) {
      FBCModelPlugin fbcModelPlug = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
      initialGeneProducts = fbcModelPlug.getGeneProductCount();
      count += initialGeneProducts;
    }
    progress = new ProgressBar(count);
    annotate(model);
    try {
      appendNotes(doc);
    } catch (IOException | XMLStreamException exc) {
      logger.warning(baseBundle.getString("FAILED_WRITE_NOTES"));
    }
    // Recursively sort and group all annotations in the SBMLDocument.
    mergeMIRIAMannotations(doc);
    if (progress != null) {
      progress.finished();
    }
    return doc;
  }


  /**
   * Replaces generic placeholders in notes files and appends both note types
   *
   * @param doc
   * @throws IOException
   * @throws XMLStreamException
   */
  private void appendNotes(SBMLDocument doc) throws IOException, XMLStreamException {
    if (replacements.containsKey("${title}") && (documentNotesFile != null)) {
      doc.appendNotes(parseNotes(documentNotesFile, replacements));
    }
    if (modelNotes != null) {
      doc.getModel().appendNotes(parseNotes(modelNotes, replacements));
    }
  }


  /**
   * Recursively goes through all annotations in the given {@link SBase} and
   * alphabetically sort annotations after grouping them by {@link org.sbml.jsbml.CVTerm.Qualifier}.
   *
   * @param sbase
   */
  private void mergeMIRIAMannotations(SBase sbase) {
    if (sbase.isSetAnnotation()) {
      SortedMap<Qualifier, SortedSet<String>> miriam = new TreeMap<>();
      boolean doMerge = hashMIRIAMuris(sbase, miriam);
      if (doMerge) {
        sbase.getAnnotation().unsetCVTerms();
        for (Entry<Qualifier, SortedSet<String>> entry : miriam.entrySet()) {
          logger.info(format(baseBundle.getString("MERGING_MIRIAM_RESOURCES"), entry.getKey(),
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
   * @param sbase
   * @param miriam
   * @return
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
            logger.info(format(baseBundle.getString("CORRECTING_INVALID_QUALIFIERS"),
              qualifier.getElementNameEquivalent(), sbase.getId()));
            qualifier = Qualifier.getModelQualifierFor(qualifier.getElementNameEquivalent());
          }
        } else if (!qualifier.isBiologicalQualifier()) {
          logger.info(format(baseBundle.getString("CORRECTING_INVALID_MODEL_QUALIFIER"),
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
   * @param model
   */
  private void annotate(Model model) {
    BiGGDB.getTaxonId(model.getId()).ifPresent(
      taxonId -> model.addCVTerm(new CVTerm(CVTerm.Qualifier.BQB_HAS_TAXON, Registry.createURI("taxonomy", taxonId))));
    BiGGDB.getOrganism(model.getId()).ifPresent(organism -> processReplacements(model, organism));
    if (QueryOnce.isModel(model.getId())) {
      addBiGGModelAnnotations(model);
    }
    if (!model.isSetMetaId() && (model.getCVTermCount() > 0)) {
      model.setMetaId(model.getId());
    }
    annotatePublications(model);
    annotateListOfCompartments(model);
    annotateListOfSpecies(model);
    annotateListOfReactions(model);
    annotateListOfGeneProducts(model);
  }


  /**
   * @param model
   */
  private void addBiGGModelAnnotations(Model model) {
    model.addCVTerm(new CVTerm(CVTerm.Qualifier.BQM_IS, Registry.createURI("bigg.model", model.getId())));
    String accession = BiGGDB.getGenomeAccesion(model.getId());
    Matcher refseqMatcher =
      Pattern.compile("^(((AC|AP|NC|NG|NM|NP|NR|NT|NW|XM|XP|XR|YP|ZP)_\\d+)|(NZ\\_[A-Z]{2,4}\\d+))(\\.\\d+)?$")
             .matcher(accession);
    CVTerm term = new CVTerm(Qualifier.BQB_IS_VERSION_OF);
    if (refseqMatcher.matches()) {
      term.addResource(Registry.createShortURI("refseq:" + accession));
    } else {
      if (Parameters.get().includeAnyURI()) {
        Matcher genomeAssemblyMatcher = Pattern.compile("^GC[AF]_[0-9]{9}\\.[0-9]+$").matcher(accession);
        if (genomeAssemblyMatcher.matches()) {
          // resolution issues with https://identifiers.org/insdc.gca, resolve non MIRIAM way (see Issue #96)
          term.addResource("https://www.ncbi.nlm.nih.gov/assembly/" + accession);
        } else {
          term.addResource("https://www.ncbi.nlm.nih.gov/nuccore/" + accession);
        }
      }
    }
    if (term.getResourceCount() > 0) {
      model.addCVTerm(term);
    }
  }


  /**
   * @param model
   * @param organism
   */
  private void processReplacements(Model model, String organism) {
    Parameters parameters = Parameters.get();
    String name = parameters.documentTitlePattern();
    name = name.replace("[biggId]", model.getId());
    name = name.replace("[organism]", organism);
    replacements.put("${title}", name);
    replacements.put("${organism}", organism);
    replacements.put("${bigg_id}", model.getId());
    replacements.put("${year}", Integer.toString(Calendar.getInstance().get(Calendar.YEAR)));
    replacements.put("${bigg.timestamp}", BiGGDB.getBiGGVersion().map(date -> format("{0,date}", date)).orElse(""));
    replacements.put("${species_table}", "");
    if (!model.isSetName()) {
      model.setName(organism);
    }
  }


  /**
   * @param model
   */
  private void annotatePublications(Model model) {
    progress.DisplayBar("Annotating Publications (1/5)  ");
    List<Pair<String, String>> publications = BiGGDB.getPublications(model.getId());
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
   * @param model
   */
  private void annotateListOfCompartments(Model model) {
    for (int i = 0; i < model.getCompartmentCount(); i++) {
      progress.DisplayBar("Annotating Compartments (2/5)  ");
      annotateCompartment(model.getCompartment(i));
    }
  }


  /**
   * @param compartment
   */
  private void annotateCompartment(Compartment compartment) {
    BiGGId biggId = new BiGGId(compartment.getId());
    if (QueryOnce.isCompartment(biggId.getAbbreviation())) {
      compartment.addCVTerm(new CVTerm(CVTerm.Qualifier.BQB_IS, Registry.createURI("bigg.compartment", biggId)));
      compartment.setSBOTerm(SBO.getCompartment()); // physical compartment
      if (!compartment.isSetName() || compartment.getName().equals("default")) {
        BiGGDB.getCompartmentName(biggId).ifPresent(compartment::setName);
      }
    }
  }


  /**
   * @param model
   */
  private void annotateListOfSpecies(Model model) {
    for (int i = 0; i < model.getSpeciesCount(); i++) {
      progress.DisplayBar("Annotating Species (3/5)  ");
      annotateSpecies(model.getSpecies(i));
    }
  }


  /**
   * @param species
   */
  private void annotateSpecies(Species species) {
    // This biggId corresponds to BiGGId calculated from getSpeciesBiGGIdFromUriList method, if not present as
    // species.id
    checkId(species).ifPresent(biggId -> {
      setSpeciesName(species, biggId);
      setSBOTermFromComponentType(species, biggId);
      setCVTermResources(species, biggId);
      FBCSetFormulaCharge(species, biggId);
    });
  }


  /**
   * @param species
   * @return
   */
  private Optional<BiGGId> checkId(Species species) {
    // TODO: compartments are not handled correctly -- is this at all possible to get right?
    Optional<BiGGId> metaboliteId = BiGGId.createMetaboliteId(species.getId());
    Optional<String> id = metaboliteId.flatMap(biggId -> {
      // extracting BiGGId if not present for species
      boolean isBiGGid = QueryOnce.isMetabolite(biggId.getAbbreviation());
      List<String> resources = new ArrayList<>();
      if (!isBiGGid) {
        // Flatten all resources for all CVTerms into a list
        resources = species.getAnnotation().getListOfCVTerms().stream()
                           .filter(cvTerm -> cvTerm.getQualifier() == Qualifier.BQB_IS)
                           .flatMap(term -> term.getResources().stream()).collect(Collectors.toList());
      }
      // update id if we found something
      return getBiGGIdFromResources(resources, TYPE_SPECIES);
    });
    return id.map(BiGGId::createMetaboliteId).orElse(metaboliteId);
  }


  /**
   * @param resources
   */
  private Optional<String> getBiGGIdFromResources(List<String> resources, String type) {
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
   * @param parts
   * @param type
   * @return
   */
  private Optional<String> getBiggIdFromParts(List<String> parts, String type) {
    String dataSource = parts.get(0);
    String synonymId = parts.get(1);
    if (QueryOnce.isDataSource(dataSource)) {
      Optional<String> id = BiGGDB.getBiggIdFromSynonym(dataSource, synonymId, type);
      if (id.isPresent()) {
        return id;
      }
    }
    return Optional.empty();
  }


  /**
   * @param species
   * @param biggId
   */
  private void setSpeciesName(Species species, BiGGId biggId) {
    if (!species.isSetName()
      || species.getName().equals(format("{0}_{1}", biggId.getAbbreviation(), biggId.getCompartmentCode()))) {
      BiGGDB.getComponentName(biggId).map(SBMLPolisher::polishName).ifPresent(species::setName);
    }
  }


  /**
   * @param species
   * @param biggId
   */
  private void setSBOTermFromComponentType(Species species, BiGGId biggId) {
    BiGGDB.getComponentType(biggId).ifPresent(type -> {
      switch (type) {
      case "metabolite":
        species.setSBOTerm(SBO.getSimpleMolecule());
        break;
      case "protein":
        species.setSBOTerm(SBO.getProtein());
        break;
      default:
        Parameters parameters = Parameters.get();
        if (parameters.omitGenericTerms()) {
          species.setSBOTerm(SBO.getMaterialEntity());
        }
        break;
      }
    });
  }


  /**
   * @param species
   * @param biggId
   */
  private void setCVTermResources(Species species, BiGGId biggId) {
    // Set of annotations calculated from BiGGDB and AnnotateDB
    CVTerm cvTerm = null;
    for (CVTerm term : species.getAnnotation().getListOfCVTerms()) {
      if (term.getQualifier() == Qualifier.BQB_IS) {
        cvTerm = term;
        species.removeCVTerm(term);
        break;
      }
    }
    if (cvTerm == null) {
      cvTerm = new CVTerm(Qualifier.BQB_IS);
    }
    Set<String> annotations = new HashSet<>();
    boolean isBiGGMetabolite = QueryOnce.isMetabolite(biggId.getAbbreviation());
    // using BiGG Database
    if (isBiGGMetabolite) {
      annotations.add(Registry.createURI("bigg.metabolite", biggId));
    }
    Parameters parameters = Parameters.get();
    Set<String> linkOut = BiGGDB.getResources(biggId, parameters.includeAnyURI(), false);
    // convert to set to remove possible duplicates; TreeSet respects order
    annotations.addAll(linkOut);
    // using AnnotateDB
    if (parameters.addADBAnnotations() && AnnotateDB.inUse() && isBiGGMetabolite) {
      // TODO: sabiork.reaction and strange IDs are returned, needs rework
      Set<String> adb_annotations = AnnotateDB.getAnnotations(BIGG_METABOLITE, biggId.toBiGGId());
      annotations.addAll(adb_annotations);
    }
    // don't add resources that are already present
    Set<String> existingAnnotations =
      cvTerm.getResources().stream()
            .map(resource -> resource.replaceAll("http://identifiers.org", "https://identifiers.org"))
            .collect(Collectors.toSet());
    annotations.removeAll(existingAnnotations);
    // adding annotations to cvTerm
    List<String> sortedAnnotations = new ArrayList<>(annotations);
    Collections.sort(sortedAnnotations);
    for (String annotation : sortedAnnotations) {
      cvTerm.addResource(annotation);
    }
    if (cvTerm.getResourceCount() > 0) {
      species.addCVTerm(cvTerm);
    }
    if ((species.getCVTermCount() > 0) && !species.isSetMetaId()) {
      species.setMetaId(species.getId());
    }
  }


  /**
   * @param species
   * @param biggId
   */
  @SuppressWarnings("deprecation")
  private void FBCSetFormulaCharge(Species species, BiGGId biggId) {
    String modelId = species.getModel().getId();
    String compartmentCode = biggId.getCompartmentCode();
    FBCSpeciesPlugin fbcSpecPlug = (FBCSpeciesPlugin) species.getPlugin(FBCConstants.shortLabel);
    boolean isBiGGModel = QueryOnce.isModel(modelId);
    boolean compartmentNonEmpty = compartmentCode != null && !compartmentCode.equals("");
    if (!fbcSpecPlug.isSetChemicalFormula()) {
      Optional<String> chemicalFormula = Optional.empty();
      if (isBiGGModel) {
        chemicalFormula = BiGGDB.getChemicalFormula(biggId.getAbbreviation(), species.getModel().getId());
      }
      if ((!isBiGGModel || chemicalFormula.isEmpty()) && compartmentNonEmpty) {
        chemicalFormula = BiGGDB.getChemicalFormulaByCompartment(biggId.getAbbreviation(), compartmentCode);
      }
      chemicalFormula.ifPresent(formula -> {
        try {
          fbcSpecPlug.setChemicalFormula(formula);
        } catch (IllegalArgumentException exc) {
          logger.severe(format(mpMessageBundle.getString("CHEM_FORMULA_INVALID"), Utils.getMessage(exc)));
        }
      });
    }
    Optional<Integer> chargeFromBiGG = Optional.empty();
    if (isBiGGModel) {
      chargeFromBiGG = BiGGDB.getCharge(biggId.getAbbreviation(), species.getModel().getId());
    } else if (compartmentNonEmpty) {
      chargeFromBiGG = BiGGDB.getChargeByCompartment(biggId.getAbbreviation(), biggId.getCompartmentCode());
    }
    if (species.isSetCharge()) {
      chargeFromBiGG.filter(charge -> charge != species.getCharge()).ifPresent(charge -> logger.warning(
        format(mpMessageBundle.getString("CHARGE_CONTRADICTION"), charge, species.getCharge(), species.getId())));
      species.unsetCharge();
    }
    chargeFromBiGG.filter(charge -> charge != 0).ifPresent(fbcSpecPlug::setCharge);
  }


  /**
   * @param model
   */
  private void annotateListOfReactions(Model model) {
    for (int i = 0; i < model.getReactionCount(); i++) {
      progress.DisplayBar("Annotating Reactions (4/5)  ");
      annotateReaction(model.getReaction(i));
    }
  }


  /**
   * @param reaction
   */
  private void annotateReaction(Reaction reaction) {
    checkId(reaction).ifPresent(biggId -> {
      String abbreviation = biggId.getAbbreviation();
      Parameters parameters = Parameters.get();
      if (!reaction.isSetSBOTerm()) {
        if (BiGGDB.isPseudoreaction(abbreviation)) {
          reaction.setSBOTerm(631);
        } else if (!parameters.omitGenericTerms()) {
          reaction.setSBOTerm(375); // generic process
        }
      }
      if ((reaction.getCVTermCount() > 0) && !reaction.isSetMetaId()) {
        reaction.setMetaId(biggId.toBiGGId());
      }
      // This biggId corresponds to BiGGId calculated from getSpeciesBiGGIdFromUriList method, if not present as
      // reaction.id
      BiGGDB.getReactionName(abbreviation).filter(name -> !name.equals(reaction.getName()))
            .map(SBMLPolisher::polishName).ifPresent(reaction::setName);
      List<String> geneReactionRules = BiGGDB.getGeneReactionRule(abbreviation, reaction.getModel().getId());
      for (String geneRactionRule : geneReactionRules) {
        GPRParser.parseGPR(reaction, geneRactionRule, parameters.omitGenericTerms());
      }
      parseSubsystems(reaction, biggId);
      setCVTermResources(reaction, biggId);
    });
  }


  /**
   * @param reaction
   * @return
   */
  private Optional<BiGGId> checkId(Reaction reaction) {
    String id = reaction.getId();
    // extracting BiGGId if not present for species
    boolean isBiGGid = id.matches("^(R_)?([a-zA-Z][a-zA-Z0-9_]+)(?:_([a-z][a-z0-9]?))?(?:_([A-Z][A-Z0-9]?))?$")
      && QueryOnce.isReaction(id);
    if (!isBiGGid) {
      // Flatten all resources for all CVTerms into a list
      List<String> resources =
        reaction.getAnnotation().getListOfCVTerms().stream().filter(cvTerm -> cvTerm.getQualifier() == Qualifier.BQB_IS)
                .flatMap(term -> term.getResources().stream()).collect(Collectors.toList());
      if (!resources.isEmpty()) {
        // update id if we found something
        id = getBiGGIdFromResources(resources, TYPE_REACTION).orElse(id);
      }
    }
    return BiGGId.createReactionId(id);
  }


  /**
   * @param reaction
   * @param biggId
   */
  private void parseSubsystems(Reaction reaction, BiGGId biggId) {
    Model model = reaction.getModel();
    boolean isBiGGModel = QueryOnce.isModel(model.getId());
    List<String> subsystems;
    if (isBiGGModel) {
      subsystems = BiGGDB.getSubsystems(model.getId(), biggId.getAbbreviation());
    } else {
      logger.warning(
        "Retrieving subsystem information for model with id not present in BiGG. Please validate obtained results");
      subsystems = BiGGDB.getSubsystemsForReaction(biggId.getAbbreviation());
    }
    if (subsystems.size() < 1) {
      return;
    } else {
      // filter out duplicates only differing in case - relevant for #getSubsystemsForReaction results
      subsystems = subsystems.stream().map(String::toLowerCase).distinct().collect(Collectors.toList());
      // Code already allows for multiple results from one query. If we have no BiGG model id, this might lead to
      // ambiguous, incorrect results
      if (!isBiGGModel && subsystems.size() > 1) {
        return;
      }
    }
    String groupKey = "GROUP_FOR_NAME";
    if (model.getUserObject(groupKey) == null) {
      model.putUserObject(groupKey, new HashMap<String, Group>());
    }
    @SuppressWarnings("unchecked")
    Map<String, Group> groupForName = (Map<String, Group>) model.getUserObject(groupKey);
    for (String subsystem : subsystems) {
      Group group;
      if (groupForName.containsKey(subsystem)) {
        group = groupForName.get(subsystem);
      } else {
        GroupsModelPlugin groupsModelPlugin = (GroupsModelPlugin) model.getPlugin(GroupsConstants.shortLabel);
        group = groupsModelPlugin.createGroup("g" + (groupsModelPlugin.getGroupCount() + 1));
        group.setName(subsystem);
        group.setKind(Group.Kind.partonomy);
        group.setSBOTerm(633); // subsystem
        groupForName.put(subsystem, group);
      }
      SBMLUtils.createSubsystemLink(reaction, group.createMember());
    }
  }


  /**
   * @param reaction
   * @param biggId
   */
  private void setCVTermResources(Reaction reaction, BiGGId biggId) {
    // Set of annotations calculated from BiGGDB and AnnotateDB
    CVTerm cvTerm = null;
    for (CVTerm term : reaction.getAnnotation().getListOfCVTerms()) {
      if (term.getQualifier() == Qualifier.BQB_IS) {
        cvTerm = term;
        reaction.removeCVTerm(term);
        break;
      }
    }
    if (cvTerm == null) {
      cvTerm = new CVTerm(Qualifier.BQB_IS);
    }
    Set<String> annotations = new HashSet<>();
    boolean isBiGGReaction = QueryOnce.isReaction(biggId.getAbbreviation());
    // using BiGG Database
    if (isBiGGReaction) {
      annotations.add(Registry.createURI("bigg.reaction", biggId));
    }
    Parameters parameters = Parameters.get();
    Set<String> linkOut = BiGGDB.getResources(biggId, parameters.includeAnyURI(), true);
    annotations.addAll(linkOut);
    // using AnnotateDB
    if (parameters.addADBAnnotations() && AnnotateDB.inUse() && isBiGGReaction) {
      // TODO: probably similar problems as in the species case -- needs rework
      Set<String> adb_annotations = AnnotateDB.getAnnotations(BIGG_REACTION, biggId.toBiGGId());
      annotations.addAll(adb_annotations);
    }
    // add only annotations not already present in model
    Set<String> existingAnnotations =
      cvTerm.getResources().stream()
            .map(resource -> resource.replaceAll("http://identifiers.org", "https://identifiers.org"))
            .collect(Collectors.toSet());
    annotations.removeAll(existingAnnotations);
    // adding annotations to cvTerm
    List<String> sortedAnnotations = new ArrayList<>(annotations);
    Collections.sort(sortedAnnotations);
    for (String annotation : sortedAnnotations) {
      cvTerm.addResource(annotation);
    }
    if (cvTerm.getResourceCount() > 0) {
      reaction.addCVTerm(cvTerm);
    }
    if ((reaction.getCVTermCount() > 0) && !reaction.isSetMetaId()) {
      reaction.setMetaId(reaction.getId());
    }
  }


  /**
   * @param model
   */
  private void annotateListOfGeneProducts(Model model) {
    if (model.isSetPlugin(FBCConstants.shortLabel)) {
      FBCModelPlugin fbcModelPlugin = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
      // update progress bar for added geneProducts
      int changed = fbcModelPlugin.getNumGeneProducts() - initialGeneProducts;
      if (changed > 0) {
        long current = progress.getCallNumber();
        // substract fake count
        progress.setNumberOfTotalCalls(progress.getNumberOfTotalCalls() + changed - 50);
        progress.setCallNr(current);
      }
      for (GeneProduct geneProduct : fbcModelPlugin.getListOfGeneProducts()) {
        progress.DisplayBar("Annotating Gene Products (5/5)  ");
        annotateGeneProduct(geneProduct);
      }
    }
  }


  /**
   * @param geneProduct
   */
  private void annotateGeneProduct(GeneProduct geneProduct) {
    Optional<String> label = Optional.empty();
    Optional<BiGGId> biggId = checkId(geneProduct);
    if (geneProduct.isSetLabel() && !geneProduct.getLabel().equalsIgnoreCase("None")) {
      label = Optional.of(geneProduct.getLabel());
    } else if (geneProduct.isSetId()) {
      label = biggId.map(BiGGId::toBiGGId);
    }
    if (label.isEmpty()) {
      return;
    }
    // fix geneProductReference in Association not updated
    SBMLUtils.updateGeneProductReference(geneProduct);
    biggId.ifPresent(id -> {
      setCVTermResources(geneProduct, id);
      if (geneProduct.getCVTermCount() > 0) {
        geneProduct.setMetaId(id.toBiGGId());
      }
    });
    setGPLabelName(geneProduct, label.get());
  }


  /**
   * @param geneProduct
   * @return
   */
  private Optional<BiGGId> checkId(GeneProduct geneProduct) {
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
   * @param geneProduct
   * @param biggId
   */
  private void setCVTermResources(GeneProduct geneProduct, BiGGId biggId) {
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


  /**
   * @param geneProduct
   * @param label
   */
  private void setGPLabelName(GeneProduct geneProduct, String label) {
    // we successfully found information by using the id, so this needs to be the label
    if (geneProduct.getLabel().equalsIgnoreCase("None")) {
      geneProduct.setLabel(label);
    }
    BiGGDB.getGeneName(label).ifPresent(geneName -> {
      if (geneName.isEmpty()) {
        logger.fine(format(mpMessageBundle.getString("NO_GENE_FOR_LABEL"), geneProduct.getName()));
      } else if (geneProduct.isSetName() && !geneProduct.getName().equals(geneName)) {
        logger.warning(format(mpMessageBundle.getString("UPDATE_GP_NAME"), geneProduct.getName(), geneName));
      }
      geneProduct.setName(geneName);
    });
  }


  /**
   * @param location
   *        relative path to the resource from this class.
   * @param replacements
   * @return Constants.URL_PREFIX + " like '%%identifiers.org%%'"
   * @throws IOException
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


  /**
   * @param modelNotes
   *        the modelNotes to set
   */
  public void setModelNotesFile(File modelNotes) {
    this.modelNotes = modelNotes != null ? modelNotes.getAbsolutePath() : null;
  }


  /**
   * @param documentNotesFile
   */
  public void setDocumentNotesFile(File documentNotesFile) {
    this.documentNotesFile = documentNotesFile != null ? documentNotesFile.getAbsolutePath() : null;
  }
}
