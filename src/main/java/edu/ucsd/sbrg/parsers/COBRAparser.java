package edu.ucsd.sbrg.parsers;

import static edu.ucsd.sbrg.bigg.ModelPolisher.MESSAGES;
import static java.text.MessageFormat.format;
import static org.sbml.jsbml.util.Pair.pairOf;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import org.sbml.jsbml.CVTerm;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBase;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.Unit;
import org.sbml.jsbml.UnitDefinition;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCModelPlugin;
import org.sbml.jsbml.ext.fbc.FBCReactionPlugin;
import org.sbml.jsbml.ext.fbc.FBCSpeciesPlugin;
import org.sbml.jsbml.ext.fbc.FluxObjective;
import org.sbml.jsbml.ext.fbc.GeneProduct;
import org.sbml.jsbml.ext.fbc.Objective;
import org.sbml.jsbml.ext.groups.Group;
import org.sbml.jsbml.ext.groups.GroupsConstants;
import org.sbml.jsbml.ext.groups.GroupsModelPlugin;
import org.sbml.jsbml.util.ModelBuilder;

import de.zbit.sbml.util.SBMLtools;
import de.zbit.util.Utils;
import edu.ucsd.sbrg.bigg.BiGGId;
import edu.ucsd.sbrg.miriam.Registry;
import edu.ucsd.sbrg.util.GPRParser;
import edu.ucsd.sbrg.util.SBMLUtils;
import edu.ucsd.sbrg.util.UpdateListener;
import us.hebi.matlab.mat.format.Mat5;
import us.hebi.matlab.mat.format.Mat5File;
import us.hebi.matlab.mat.types.Array;
import us.hebi.matlab.mat.types.Cell;
import us.hebi.matlab.mat.types.Char;
import us.hebi.matlab.mat.types.MatFile.Entry;
import us.hebi.matlab.mat.types.MatlabType;
import us.hebi.matlab.mat.types.Matrix;
import us.hebi.matlab.mat.types.Struct;

/**
 * @author Andreas Dr&auml;ger
 */
public class COBRAparser {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(COBRAparser.class.getName());
  private static final String DELIM = " ,;\t\n\r\f";
  /**
   *
   */
  private MatlabFields mlField;
  /**
   *
   */
  private boolean omitGenericTerms;

  /**
   *
   */
  private COBRAparser() {
    super();
    setOmitGenericTerms(false);
  }


  /**
   * @return the omitGenericTerms
   */
  public boolean getOmitGenericTerms() {
    return omitGenericTerms;
  }


  /**
   * @param omitGenericTerms
   *        the omitGenericTerms to set
   */
  public void setOmitGenericTerms(boolean omitGenericTerms) {
    this.omitGenericTerms = omitGenericTerms;
  }


  /**
   * @param matFile
   * @param omitGenericTerms
   * @return
   * @throws IOException
   */
  public static SBMLDocument read(File matFile, boolean omitGenericTerms) throws IOException {
    COBRAparser parser = new COBRAparser();
    parser.setOmitGenericTerms(omitGenericTerms);
    return parser.parse(matFile);
  }


  /**
   * @param matFile
   * @return
   * @throws IOException
   */
  // returns List<SBMLDocument> after parsing
  private SBMLDocument parse(File matFile) throws IOException {
    Mat5File mat5File = Mat5.readFromFile(matFile);
    SBMLDocument doc = parseModel(getModel(mat5File));
    mat5File.close();
    return doc;
  }


  /**
   * @param mat5File
   * @return
   */
  // returns Map of arrayName, Array
  private Map<String, Array> getModel(Mat5File mat5File) {
    Map<String, Array> content = new HashMap<>();
    for (Entry entry : mat5File.getEntries()) {
      content.put(entry.getName(), entry.getValue());
    }
    Map<String, Array> nameModel = new HashMap<>(1);
    Iterator<String> keys = content.keySet().iterator();
    while (keys.hasNext()) {
      // only fetch and process first model in file, assume other models present are equal to it
      String name = keys.next();
      Array array = content.get(name);
      if ((array.getNumElements() == 1) && array.getType() == MatlabType.Structure) {
        nameModel.put(name, array);
        break;
      }
    }
    if (content.keySet().size() > 1) {
      logger.warning(format(MESSAGES.getString("MORE_MODELS_COBRA_FILE"), content.keySet().size()));
    }
    return nameModel;
  }


  /**
   * @param namedModel
   * @return
   */
  // input = HashMap<arrayName,Array>
  private SBMLDocument parseModel(Map<String, Array> namedModel) {
    if (namedModel.size() != 1) {
      return null;
    }
    Map.Entry<String, Array> entry = namedModel.entrySet().iterator().next();
    String name = entry.getKey();
    Array model = entry.getValue();
    ModelBuilder builder = new ModelBuilder(3, 1);
    builder.buildModel(SBMLtools.toSId(name), null);
    SBMLDocument doc = builder.getSBMLDocument();
    doc.addTreeNodeChangeListener(new UpdateListener());
    parseModel(builder, name, model);
    return doc;
  }


  /**
   * @param builder
   * @param array
   * @return
   */
  // input = builder, arrayName, array
  private void parseModel(ModelBuilder builder, String name, Array array) {
    Struct struct = (Struct) array;
    // Check that the given data structure only contains allowable entries
    Struct correctedStruct = Mat5.newStruct(struct.getDimensions());
    List<String> field_names = struct.getFieldNames();
    for (String field_name : field_names) {
      Array field = MatlabFields.getStructField(struct, field_name);
      checkModelField(correctedStruct, name, field, field_name);
    }
    Model model = parseModel(correctedStruct, builder);
    parseGPRsAndSubsystems(model);
    FBCModelPlugin fbc = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
    Objective obj = fbc.createObjective("obj");
    obj.setType(Objective.Type.MAXIMIZE);
    fbc.getListOfObjectives().setActiveObjective(obj.getId());
    parseCSense(model);
    buildFluxObjectives(model, obj);
    parseBValue(model);
  }


  /**
   * @param correctedStruct
   * @param field
   */
  private void checkModelField(Struct correctedStruct, String structName, Array field, String fieldName) {
    boolean invalidField = false;
    try {
      logger.finest(format(MESSAGES.getString("FOUND_COMPO"), ModelField.valueOf(fieldName)));
    } catch (IllegalArgumentException exc) {
      logException(exc);
      invalidField = true;
    }
    finally {
      // For each non matched struct field, check if an enum variant exists in a case insensitive way
      if (invalidField) {
        for (ModelField variant : ModelField.values()) {
          String variantLC = variant.name().toLowerCase();
          if (variantLC.equals(fieldName.toLowerCase()) || variantLC.startsWith(fieldName.toLowerCase())) {
            if (MatlabFields.getStructField(correctedStruct, variant.name()) != null) {
              logger.warning(format(MESSAGES.getString("FIELD_ALREADY_PRESENT"), variant.name(), fieldName));
              break;
            }
            correctedStruct.set(variant.name(), field);
            logger.warning(format(MESSAGES.getString("CHANGED_TO_VARIANT"), fieldName, variant.name()));
            invalidField = false;
            break;
          }
        }
        if (invalidField) {
          logger.warning(format(MESSAGES.getString("CORRECT_VARIANT_FAILED"), fieldName));
        }
      } else {
        correctedStruct.set(fieldName, field);
      }
    }
  }


  /**
   * @param builder
   * @param correctedStruct
   * @return
   */
  private Model parseModel(Struct correctedStruct, ModelBuilder builder) {
    Model model = builder.getModel();
    buildBasicUnits(builder);
    mlField = new MatlabFields(correctedStruct);
    parseDescription(model);
    parseMetabolites(builder);
    parseGenes(builder);
    parseRxns(builder);
    return model;
  }


  /**
   * @param builder
   */
  private void buildBasicUnits(ModelBuilder builder) {
    UnitDefinition ud = builder.buildUnitDefinition("mmol_per_gDW_per_hr", null);
    ModelBuilder.buildUnit(ud, 1d, -3, Unit.Kind.MOLE, 1d);
    ModelBuilder.buildUnit(ud, 1d, 0, Unit.Kind.GRAM, -1d);
    ModelBuilder.buildUnit(ud, 3600d, 0, Unit.Kind.SECOND, -1d);
  }


  /**
   * @param model
   */
  private void parseDescription(Model model) {
    if (mlField.description == null) {
      logger.warning(format(MESSAGES.getString("FIELD_MISSING"), ModelField.description));
      return;
    }
    if (mlField.description.getType() == MatlabType.Character) {
      Char description = (Char) mlField.description;
      if (description.getDimensions()[0] == 1) {
        model.setName(description.getRow(0));
      } else {
        logger.warning(
          format(MESSAGES.getString("MANY_IDS_IN_DESC"), ((Char) mlField.description).asCharSequence()));
      }
    } else if (mlField.description.getType() == MatlabType.Structure) {
      mlField.setDescriptionFields();
      if (mlField.name != null) {
        model.setName(toString(mlField.name));
      }
      if (mlField.organism != null) {
        // TODO
      }
      if (mlField.author != null) {
        // TODO
      }
      if (mlField.geneindex != null) {
        // TODO
      }
      if (mlField.genedate != null) {
        // TODO
      }
      if (mlField.genesource != null) {
        // TODO
      }
      if (mlField.notes != null) {
        try {
          model.appendNotes(SBMLtools.toNotesString("<p>" + toString(mlField.notes) + "</p>"));
        } catch (XMLStreamException exc) {
          logException(exc);
        }
      }
    }
  }


  /**
   * @param exc
   */
  private void logException(Exception exc) {
    logger.warning(format("{0}: {1}", exc.getClass().getSimpleName(), Utils.getMessage(exc)));
  }


  /**
   * @param builder
   */
  private void parseMetabolites(ModelBuilder builder) {
    Model model = builder.getModel();
    for (int i = 0; (mlField.mets != null) && (i < mlField.mets.getNumElements()); i++) {
      parseMetabolite(model, i);
    }
  }


  /**
   * @param model
   * @param i
   */
  private void parseMetabolite(Model model, int i) {
    String id = toString(mlField.mets.get(i), ModelField.mets.name(), i + 1);
    if (id.isEmpty()) {
      return;
    }
    BiGGId.createMetaboliteId(id).ifPresent(biggId -> {
      Species species = model.createSpecies(biggId.toBiGGId());
      parseSpeciesFields(species, i);
      parseAnnotation(species, i);
    });
  }


  /**
   * @param species
   * @param i
   */
  private void parseSpeciesFields(Species species, int i) {
    if (mlField.metNames != null) {
      species.setName(toString(mlField.metNames.get(i), ModelField.metNames.name(), i + 1));
    }
    if ((mlField.metFormulas != null) || (mlField.metCharge != null)) {
      FBCSpeciesPlugin specPlug = (FBCSpeciesPlugin) species.getPlugin(FBCConstants.shortLabel);
      if (exists(mlField.metFormulas, i)) {
        specPlug.setChemicalFormula(toString(mlField.metFormulas.get(i), ModelField.metFormulas.name(), i + 1));
      }
      if ((mlField.metCharge != null) && (mlField.metCharge.getNumElements() > i)) {
        double charge = mlField.metCharge.getDouble(i);
        specPlug.setCharge((int) charge);
        if (charge - ((int) charge) != 0d) {
          logger.warning(format(MESSAGES.getString("CHARGE_TO_INT_COBRA"), charge, specPlug.getCharge()));
        }
      }
    }
    if ((mlField.metSmile != null) && (mlField.metSmile.get(i) != null)) {
      String smile = toString(mlField.metSmile.get(i), ModelField.metSmile.name(), i + 1);
      if (!isEmptyString(smile)) {
        try {
          species.appendNotes("<html:p>SMILES: " + smile + "</html:p>");
        } catch (XMLStreamException exc) {
          logException(exc);
        }
      }
    }
    if (species.isSetAnnotation()) {
      species.setMetaId(species.getId());
    }
  }


  /**
   * @param cell
   * @param i
   * @return
   */
  private boolean exists(Array cell, int i) {
    if (cell != null) {
      if (cell instanceof Cell) {
        return (((Cell) cell).get(i) != null);
      }
      return true;
    }
    return false;
  }


  /**
   * @param string
   *        the {@link String} to be tested.
   * @return {@code true} if the given {@link String} is either {@code null} or
   *         empty or equals empty square brackets.
   */
  private boolean isEmptyString(String string) {
    return (string == null) || string.isEmpty() || string.equals("[]");
  }


  /**
   * @param species
   * @param i
   */
  private void parseAnnotation(Species species, int i) {
    CVTerm term = new CVTerm();
    term.setQualifierType(CVTerm.Type.BIOLOGICAL_QUALIFIER);
    term.setBiologicalQualifierType(CVTerm.Qualifier.BQB_IS);
    addResource(mlField.metCHEBIID, i, term, "ChEBI");
    addResource(mlField.metHMDB, i, term, "HMDB");
    addResource(mlField.metInchiString, i, term, "InChI");
    addKEGGResources(term, i);
    addPubChemResources(term, i);
    if (term.getResourceCount() > 0) {
      species.addCVTerm(term);
    }
  }


  /**
   * Adds resources to provided CVTerm if catalog and id from MLCell
   * provide a valid URI. Logs ids not matching collection patterns
   * and invalid collections. In both cases no resource is added.
   *
   * @param cell
   * @param i
   *        the index within the cell.
   * @param term
   * @param collection
   */
  private boolean addResource(Cell cell, int i, CVTerm term, String collection) {
    boolean success = false;
    if (exists(cell, i)) {
      String id = Optional.ofNullable(toMIRIAMid((Array) cell.get(i))).orElse("");
      if (!id.isEmpty()) {
        id = checkId(id);
        String prefix = Registry.getPrefixForCollection(collection);
        if (!prefix.isEmpty()) {
          String resource;
          if (id.startsWith("http")) {
            resource = id;
          } else {
            resource = Registry.createURI(prefix, id);
          }
          String finalId = id;
          success = Registry.checkResourceUrl(resource).map(res -> {
            term.addResource(res);
            logger.finest(format(MESSAGES.getString("ADDED_URI_COBRA"), res));
            return true;
          }).orElseGet(() -> {
            logger.severe(format(MESSAGES.getString("ADD_URI_FAILED_COBRA"), collection, finalId));
            return false;
          });
        }
      }
    }
    return success;
  }


  /**
   * @param array:
   *        MLArray to be stringified
   * @return String representation of the given array
   */
  private String toMIRIAMid(Array array) {
    return toMIRIAMid(toString(array));
  }


  /**
   * @param idCandidate:
   *        id part for the uri
   * @return finalized id, without [, ], ' at the string boundaries
   */
  private String toMIRIAMid(String idCandidate) {
    if ((idCandidate == null) || idCandidate.isEmpty()) {
      return null;
    }
    int start = 0;
    int end = idCandidate.length() - 1;
    if ((idCandidate.charAt(start) == '[') || (idCandidate.charAt(start) == '\'')) {
      start++;
    }
    if ((idCandidate.charAt(end) == ']') || (idCandidate.charAt(end) == '\'')) {
      end--;
    }
    // substring method already decrements second index
    return (start < end) ? idCandidate.substring(start, end + 1) : null;
  }


  /**
   * Necessary to check for a special whitespace (code 160) at beginning of id
   * (iCHOv1.mat, possibly other models) and to remove trailing ';'
   *
   * @param id
   * @return: trimmed id without ';' at the end
   */
  private String checkId(String id) {
    if (id.startsWith("InChI")) {
      return id;
    }
    if (id.startsWith(Character.toString((char) 160)) || id.startsWith("/")) {
      id = id.substring(1);
    }
    if (id.endsWith(";")) {
      id = id.substring(0, id.length() - 1);
    } else if (id.contains(";")) {
      logger.warning(MESSAGES.getString("TRUNCATED_ID") + id);
      id = id.substring(0, id.indexOf(";"));
    }
    return id;
  }


  /**
   * Checks if id belongs to a given collection by matching it with the
   * respective regexp
   *
   * @param prefix:
   *        Miriam collection
   * @param id:
   *        id to test for membership
   * @return {@code true}, if it matches, else {@code false}
   */
  private boolean validId(String prefix, String id) {
    if (id.isEmpty()) {
      return false;
    }
    String pattern = Registry.getPattern(Registry.getCollectionForPrefix(prefix));
    boolean validId = false;
    if (!pattern.equals("")) {
      validId = Registry.checkPattern(id, pattern);
      if (!validId) {
        logger.warning(format(MESSAGES.getString("PATTERN_MISMATCH"), id, pattern));
      }
    } else {
      logger.severe(format(MESSAGES.getString("COLLECTION_UNKNOWN"), prefix));
    }
    return validId;
  }


  /**
   * @param term
   * @param i
   * @return
   */
  private boolean addKEGGResources(CVTerm term, int i) {
    // use short circuit evaluation to only run addResource until one of them returns true
    // return type is needed for this to work
    return addResource(mlField.metKeggID, i, term, "KEGG Compound")
      || addResource(mlField.metKeggID, i, term, "KEGG Drug") || addResource(mlField.metKeggID, i, term, "KEGG Genes")
      || addResource(mlField.metKeggID, i, term, "KEGG Glycan")
      || addResource(mlField.metKeggID, i, term, "KEGG Pathway");
  }


  /**
   * @param i
   * @param term
   * @return
   */
  private boolean addPubChemResources(CVTerm term, int i) {
    return addResource(mlField.metPubChemID, i, term, "PubChem-compound")
      || addResource(mlField.metPubChemID, i, term, "PubChem-substance");
  }


  /**
   * @param builder
   */
  private void parseGenes(ModelBuilder builder) {
    if (mlField.genes == null) {
      logger.info(MESSAGES.getString("GENES_MISSING"));
      return;
    }
    Model model = builder.getModel();
    FBCModelPlugin modelPlug = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
    for (int i = 0; i < mlField.genes.getNumElements(); i++) {
      parseGene(modelPlug, i);
    }
  }


  /**
   * @param modelPlug
   * @param i
   */
  private void parseGene(FBCModelPlugin modelPlug, int i) {
    String id = toString(mlField.genes.get(i), ModelField.genes.name(), i + 1);
    if (id.isEmpty()) {
      return;
    }
    BiGGId.createGeneId(id).ifPresent(biggId -> {
      GeneProduct gp = modelPlug.createGeneProduct(biggId.toBiGGId());
      gp.setLabel(id);
      gp.setName(id);
    });
  }


  /**
   * @param builder
   */
  @SuppressWarnings("unchecked")
  private void parseRxns(ModelBuilder builder) {
    for (int j = 0; (mlField.rxns != null) && (j < mlField.rxns.getNumElements()); j++) {
      parseRxn(builder, j);
    }
  }


  /**
   * @param builder
   * @param index
   */
  private void parseRxn(ModelBuilder builder, int index) {
    Model model = builder.getModel();
    String reactionId = toString(mlField.rxns.get(index), ModelField.rxns.name(), index + 1);
    if (reactionId.isEmpty()) {
      return;
    }
    BiGGId.createReactionId(reactionId).ifPresent(biggId -> {
      Reaction reaction = model.createReaction(biggId.toBiGGId());
      setNameAndReversibility(reaction, index);
      setReactionBounds(builder, reaction, index);
      buildReactantsProducts(model, reaction, index);
      parseAnnotations(builder, reaction, reactionId, index);
      if (reaction.getCVTermCount() > 0) {
        reaction.setMetaId(reaction.getId());
      }
    });
  }


  /**
   * @param reaction
   * @param index
   */
  private void setNameAndReversibility(Reaction reaction, int index) {
    if (mlField.rxnNames != null) {
      reaction.setName(toString(mlField.rxnNames.get(index), ModelField.rxnNames.name(), index + 1));
    }
    if (mlField.rev != null) {
      reaction.setReversible(mlField.rev.getDouble(index) != 0d);
    }
  }


  /**
   * @param builder
   * @param reaction
   * @param index
   */
  private void setReactionBounds(ModelBuilder builder, Reaction reaction, int index) {
    FBCReactionPlugin rPlug = (FBCReactionPlugin) reaction.getPlugin(FBCConstants.shortLabel);
    if (mlField.lb != null) {
      rPlug.setLowerFluxBound(builder.buildParameter(reaction.getId() + "_lb", reaction.getId() + "_lb",
        mlField.lb.getDouble(index), true, (String) null));
    }
    if (mlField.ub != null) {
      rPlug.setUpperFluxBound(builder.buildParameter(reaction.getId() + "_ub", reaction.getId() + "_lb",
        mlField.ub.getDouble(index), true, (String) null));
    }
  }


  /**
   * @param model
   * @param reaction
   * @param index
   */
  private void buildReactantsProducts(Model model, Reaction reaction, int index) {
    // Take the current column of S and look for all non-zero coefficients
    for (int i = 0; (mlField.S != null) && (i < mlField.S.getNumRows()); i++) {
      double coeff = mlField.S.getDouble(i, index);
      if (coeff != 0d) {
        try {
          String id = toString(mlField.mets.get(i), ModelField.mets.name(), i + 1);
          BiGGId.createMetaboliteId(id).ifPresent(metId -> {
            Species species = model.getSpecies(metId.toBiGGId());
            if (coeff < 0d) { // Reactant
              ModelBuilder.buildReactants(reaction, pairOf(-coeff, species));
            } else if (coeff > 0d) { // Product
              ModelBuilder.buildProducts(reaction, pairOf(coeff, species));
            }
          });
        } catch (IllegalArgumentException exc) {
          logger.warning(format(MESSAGES.getString("REACT_PARTIC_INVALID"), Utils.getMessage(exc)));
        }
      }
    }
  }


  /**
   * @param builder
   * @param reaction
   * @param rId
   * @param index
   */
  private void parseAnnotations(ModelBuilder builder, Reaction reaction, String rId, int index) {
    if (exists(mlField.ecNumbers, index)) {
      parseECcodes(toString(mlField.ecNumbers.get(index), ModelField.ecNumbers.name(), index + 1), reaction);
    }
    if (exists(mlField.rxnKeggID, index)) {
      parseRxnKEGGids(toString(mlField.rxnKeggID.get(index), ModelField.rxnKeggID.name(), index + 1), reaction);
    }
    if (exists(mlField.rxnKeggOrthology, index)) {
      parseRxnKEGGOrthology(
        toString(mlField.rxnKeggOrthology.get(index), ModelField.rxnKeggOrthology.name(), index + 1), reaction);
    }
    if (exists(mlField.comments, index)) {
      String comment = toString(mlField.comments.get(index), ModelField.comments.name(), index + 1);
      appendComment(comment, reaction);
    }
    if (exists(mlField.confidenceScores, index)) {
      Array cell = mlField.confidenceScores.get(index);
      if (cell instanceof Matrix) {
        if (cell.getNumElements() == 0) {
          logger.warning(MESSAGES.getString("CONF_CELL_WRONG_DIMS"));
          return;
        }
        double score = ((Matrix) cell).getDouble(0);
        logger.fine(format(MESSAGES.getString("DISPLAY_CONF_SCORE"), score, reaction.getId()));
        builder.buildParameter("P_confidenceScore_of_" + SBMLtools.toSId(rId), // id
          format("Confidence score of reaction {0}", reaction.isSetName() ? reaction.getName() : reaction.getId()), // name
          score, // value
          true, // constant
          Unit.Kind.DIMENSIONLESS // unit
        ).setSBOTerm(613);
        // TODO: there should be a specific term for confidence scores.
        // Use "613 - reaction parameter" for now.
      } else {
        logger.warning(format(MESSAGES.getString("TYPE_MISMATCH_MLDOUBLE"), cell.getClass().getSimpleName()));
      }
    }
    if (exists(mlField.citations, index)) {
      parseCitation(toString(mlField.citations.get(index), ModelField.citations.name(), index + 1), reaction);
    }
  }


  /**
   * @param ec
   * @param reaction
   */
  private void parseECcodes(String ec, Reaction reaction) {
    if (isEmptyString(ec)) {
      return;
    }
    CVTerm term = findOrCreateCVTerm(reaction, CVTerm.Qualifier.BQB_HAS_PROPERTY);
    StringTokenizer st = new StringTokenizer(ec, DELIM);
    boolean match = false;
    while (st.hasMoreElements()) {
      String ecCode = st.nextElement().toString().trim();
      if (!ecCode.isEmpty() && validId("ec-code", ecCode)) {
        String resource = Registry.createURI("ec-code", ecCode);
        if (!term.getResources().contains(resource)) {
          match = term.addResource(resource);
        }
      }
    }
    if (!match) {
      logger.warning(format(MESSAGES.getString("EC_CODES_UNKNOWN"), ec));
    }
    if ((term.getResourceCount() > 0) && (term.getParent() == null)) {
      reaction.addCVTerm(term);
    }
  }


  /**
   * @param keggId
   * @param reaction
   */
  private void parseRxnKEGGids(String keggId, Reaction reaction) {
    if (isEmptyString(keggId)) {
      return;
    }
    String prefix = "kegg.reaction";
    String pattern = Registry.getPattern(Registry.getCollectionForPrefix(prefix));
    CVTerm term = findOrCreateCVTerm(reaction, CVTerm.Qualifier.BQB_IS);
    StringTokenizer st = new StringTokenizer(keggId, DELIM);
    while (st.hasMoreElements()) {
      String kId = st.nextElement().toString().trim();
      if (!kId.isEmpty() && Registry.checkPattern(kId, pattern)) {
        term.addResource(Registry.createURI(prefix, kId));
      }
    }
    if (term.getResourceCount() == 0) {
      // This is actually bad.. should only be KEGG ids, not EC-Codes
      parseECcodes(keggId, reaction);
    }
    if ((term.getResourceCount() > 0) && (term.getParent() == null)) {
      reaction.addCVTerm(term);
    }
  }


  /**
   * @param keggId
   * @param reaction
   */
  private void parseRxnKEGGOrthology(String keggId, Reaction reaction) {
    if (isEmptyString(keggId)) {
      return;
    }
    String catalog = "kegg.orthology";
    String pattern = Registry.getPattern(Registry.getCollectionForPrefix(catalog));
    CVTerm term = findOrCreateCVTerm(reaction, CVTerm.Qualifier.BQB_IS);
    StringTokenizer st = new StringTokenizer(keggId, DELIM);
    while (st.hasMoreElements()) {
      String kId = st.nextElement().toString().trim();
      if (!kId.isEmpty() && Registry.checkPattern(kId, pattern)) {
        term.addResource(Registry.createURI(catalog, kId));
      }
    }
    if ((term.getResourceCount() > 0) && (term.getParent() == null)) {
      reaction.addCVTerm(term);
    }
  }


  /**
   * Searches for the first {@link CVTerm} within the given {@link SBase} that
   * has the given {@link CVTerm.Qualifier}.
   *
   * @param sbase
   * @param qualifier
   * @return the found {@link CVTerm} or a new {@link CVTerm} if non exists.
   *         To distinguish between both cases, test if the parent is
   *         {@code null}.
   */
  private CVTerm findOrCreateCVTerm(SBase sbase, CVTerm.Qualifier qualifier) {
    if (sbase.getCVTermCount() > 0) {
      for (CVTerm term : sbase.getCVTerms()) {
        if (term.getQualifier().equals(qualifier)) {
          return term;
        }
      }
    }
    return new CVTerm(CVTerm.Type.BIOLOGICAL_QUALIFIER, qualifier);
  }


  /**
   * @param comment
   * @param sbase
   */
  private void appendComment(String comment, SBase sbase) {
    try {
      if (!isEmptyString(comment)) {
        sbase.appendNotes(SBMLtools.toNotesString("<p>" + comment + "</p>"));
      }
    } catch (XMLStreamException exc) {
      logException(exc);
    }
  }


  /**
   * @param citation
   * @param reaction
   */
  private void parseCitation(String citation, Reaction reaction) {
    StringBuilder otherCitation = new StringBuilder();
    if (isEmptyString(citation)) {
      return;
    }
    CVTerm term = new CVTerm(CVTerm.Type.BIOLOGICAL_QUALIFIER, CVTerm.Qualifier.BQB_IS_DESCRIBED_BY);
    StringTokenizer st = new StringTokenizer(citation, ",");
    while (st.hasMoreElements()) {
      String ref = st.nextElement().toString().trim();
      if (!addResource(ref, term, "pubmed")) {
        if (!addResource(ref, term, "doi")) {
          if (otherCitation.length() > 0) {
            otherCitation.append(", ");
          }
          otherCitation.append(ref);
        }
      }
    }
    if (otherCitation.length() > 0) {
      try {
        if (reaction.isSetNotes()) {
          reaction.appendNotes("\n\nReference: " + otherCitation);
        } else {
          reaction.appendNotes(SBMLtools.toNotesString("<p>Reference: " + otherCitation.toString() + "</p>"));
        }
      } catch (XMLStreamException exc) {
        logException(exc);
      }
    }
    if ((term.getResourceCount() > 0) && (term.getParent() == null)) {
      reaction.addCVTerm(term);
    }
  }


  /**
   * Tries to update a resource according to pre-defined rules. If the resource
   * starts with the MIRIAM name followed by a colon, its value is added to the
   * given term. This method assumes that there is a colon between catalog id
   * and resource id. If this is not the case, {@code false} will be returned.
   *
   * @param resource
   * @param term
   * @param prefix
   * @return {@code true} if successful, {@code false} otherwise.
   */
  private boolean addResource(String resource, CVTerm term, String prefix) {
    StringTokenizer st = new StringTokenizer(resource, " ");
    while (st.hasMoreElements()) {
      String r = st.nextElement().toString().trim();
      if (r.contains(":")) {
        r = r.substring(r.indexOf(':') + 1).trim();
      } else {
        continue;
      }
      if (r.endsWith("'") || r.endsWith(".")) {
        r = r.substring(0, r.length() - 1);
      }
      r = checkId(r);
      if (validId(prefix, r)) {
        if (!resource.isEmpty()) {
          if (st.countTokens() > 1) {
            logger.warning(format(MESSAGES.getString("SKIP_COMMENT"), resource, r, prefix));
          }
          resource = Registry.createURI(prefix, r);
          logger.finest(format(MESSAGES.getString("ADDED_URI"), resource));
          return term.addResource(resource);
        }
      }
    }
    return false;
  }


  /**
   * @param model
   */
  private void parseGPRsAndSubsystems(Model model) {
    for (int i = 0; (mlField.grRules != null) && (i < mlField.grRules.getNumElements()); i++) {
      String geneReactionRule = toString(mlField.grRules.get(i), ModelField.grRules.name(), i + 1);
      if (model.getReaction(i) == null) {
        logger.severe(format(MESSAGES.getString("CREATE_GPR_FAILED"), i));
      } else {
        GPRParser.parseGPR(model.getReaction(i), geneReactionRule, omitGenericTerms);
      }
    }
    if ((mlField.subSystems != null) && (mlField.subSystems.getNumElements() > 0)) {
      parseSubsystems(model);
    }
  }


  /**
   * @param model
   */
  private void parseSubsystems(Model model) {
    // this is to avoid creating the identical group multiple times.
    Map<String, Group> nameToGroup = new HashMap<>();
    GroupsModelPlugin groupsModelPlugin = (GroupsModelPlugin) model.getPlugin(GroupsConstants.shortLabel);
    for (int i = 0; (mlField.subSystems != null) && (i < mlField.subSystems.getNumElements()); i++) {
      String name = toString(mlField.subSystems.get(i), ModelField.subSystems.name(), i + 1);
      Group group = nameToGroup.get(name);
      if (group == null) {
        group = groupsModelPlugin.createGroup();
        group.setName(name);
        group.setKind(Group.Kind.partonomy);
        nameToGroup.put(name, group);
      }
      if (model.getReaction(i) != null) {
        SBMLUtils.createSubsystemLink(model.getReaction(i), group.createMember());
      } else {
        logger.severe(format(MESSAGES.getString("SUBSYS_LINK_ERROR"), i));
      }
    }
  }


  /**
   * @param model
   */
  private void parseCSense(Model model) {
    if (mlField.csense == null) {
      return;
    }
    for (int i = 0; (mlField.csense != null) && (i < mlField.csense.getNumElements()); i++) {
      try {
        char c = mlField.csense.getChar(i, 0);
        // TODO: only 'E' (equality) is supported for now!
        if (c != 'E' && model.getListOfSpecies().size() > i) {
          logger.severe(format(MESSAGES.getString("NEQ_RELATION_UNSUPPORTED"), model.getSpecies(i).getId()));
        }
      } catch (Exception e) {
        logger.info(e.toString());
        return;
      }
    }
  }


  /**
   * @param model
   * @param obj
   */
  private void buildFluxObjectives(Model model, Objective obj) {
    for (int i = 0; (mlField.coefficients != null) && (i < mlField.coefficients.getNumElements()); i++) {
      double coefficient = mlField.coefficients.getDouble(i);
      if (coefficient != 0d) {
        Reaction r = model.getReaction(i);
        if (r == null) {
          break;
        }
        FluxObjective fo = obj.createFluxObjective("fo_" + r.getId());
        fo.setCoefficient(coefficient);
        fo.setReaction(r);
      }
    }
  }


  /**
   * @param model
   */
  private void parseBValue(Model model) {
    for (int i = 0; (mlField.b != null) && (i < mlField.b.getNumElements()); i++) {
      double bVal = mlField.b.getDouble(i);
      if (bVal != 0d && model.getListOfSpecies().size() > i) {
        // TODO: this should be incorporated into FBC version 3.
        logger.warning(format(MESSAGES.getString("B_VALUE_UNSUPPORTED"), bVal, model.getSpecies(i).getId()));
      }
    }
  }


  private String toString(Array array) {
    return toString(array, null, -1);
  }


  /**
   * @param array
   * @param parentName
   * @param parentIndex
   * @return
   */
  private String toString(Array array, String parentName, int parentIndex) {
    StringBuilder sb = new StringBuilder();
    if (array.getType() == MatlabType.Character) {
      Char string = (Char) array;
      if (string.getDimensions()[0] > 1) {
        logger.fine(format(MESSAGES.getString("MANY_STRINGS_IN_CELL"), string.asCharSequence()));
      }
      for (int i = 0; i < string.getDimensions()[0]; i++) {
        if (i > 0) {
          sb.append('\n');
        }
        sb.append(string.getRow(i));
      }
    } else if (!Arrays.equals(array.getDimensions(), new int[] {0, 0})) {
      logger.warning(format(MESSAGES.getString("TYPE_MISMATCH_STRING"), array.getType().toString(),
        "parentName = %s", parentName, "parentIndex = %s", parentIndex));
    }
    return sb.toString();
  }
}
