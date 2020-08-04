package edu.ucsd.sbrg.parsers.json;

import static java.text.MessageFormat.format;
import static org.sbml.jsbml.util.Pair.pairOf;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringJoiner;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.sbml.jsbml.CVTerm;
import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.Model;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import de.zbit.sbml.util.SBMLtools;
import de.zbit.util.ResourceManager;
import edu.ucsd.sbrg.bigg.BiGGId;
import edu.ucsd.sbrg.miriam.Registry;
import edu.ucsd.sbrg.parsers.json.models.Compartments;
import edu.ucsd.sbrg.parsers.json.models.Gene;
import edu.ucsd.sbrg.parsers.json.models.Metabolite;
import edu.ucsd.sbrg.parsers.json.models.Reaction;
import edu.ucsd.sbrg.parsers.json.models.Root;
import edu.ucsd.sbrg.util.GPRParser;
import edu.ucsd.sbrg.util.SBMLUtils;
import edu.ucsd.sbrg.util.UpdateListener;

/**
 * @author Thomas Jakob Zajac
 */
public class JSONParser {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(JSONParser.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");

  /**
   * 
   */
  public JSONParser() {
    super();
  }


  /**
   * @param jsonFile,
   *        to be read and parsed
   * @return parsed {@link SBMLDocument}
   * @throws IOException
   */
  public static SBMLDocument read(File jsonFile) throws IOException {
    JSONParser parser = new JSONParser();
    return parser.parse(jsonFile);
  }


  /**
   * Creates the {@link ModelBuilder}, {@link SBMLDocument} and reads the
   * jsonFile as a tree
   * 
   * @param jsonFile
   * @return
   */
  private SBMLDocument parse(File jsonFile) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    Root root = mapper.readValue(jsonFile, Root.class);
    ModelBuilder builder = new ModelBuilder(3, 1);
    SBMLDocument doc = builder.getSBMLDocument();
    doc.addTreeNodeChangeListener(new UpdateListener());
    // Has to be present
    String modelId = root.getId();
    // Set model name to id, if name is not provided
    String modelName = root.getName() != null ? root.getName() : modelId;
    builder.buildModel(modelId, modelName);
    Model model = builder.getModel();
    model.setId(modelId);
    model.setName(modelName);
    parseModel(builder, root);
    return doc;
  }


  /**
   * Sets all informational fields for the model (id, name, annotation, notes,
   * version), generates a basic unit definition (mmol_per_gDW_per_hr) and calls
   * the parse methods for the main fields (compartments, metabolites, genes,
   * reactions)
   * 
   * @param builder
   * @param root
   * @return
   */
  private void parseModel(ModelBuilder builder, Root root) {
    logger.info(MESSAGES.getString("JSON_PARSER_STARTED"));
    // get Model and set all informational fields
    Model model = builder.getModel();
    model.setVersion(root.getVersion());
    parseAnnotation(model, root.getAnnotation());
    parseNotes(model, root.getNotes());
    // Generate basic unit:
    UnitDefinition ud = builder.buildUnitDefinition("mmol_per_gDW_per_hr", null);
    ModelBuilder.buildUnit(ud, 1d, -3, Unit.Kind.MOLE, 1d);
    ModelBuilder.buildUnit(ud, 1d, 0, Unit.Kind.GRAM, -1d);
    ModelBuilder.buildUnit(ud, 3600d, 0, Unit.Kind.SECOND, -1d);
    // parse main fields
    Compartments compartments = root.getCompartments();
    if (compartments != null) {
      parseCompartments(builder, root.getCompartments().get());
    }
    parseMetabolites(builder, root.getMetabolites());
    parseGenes(builder, root.getGenes());
    parseReactions(builder, root.getReactions());
  }


  /**
   * @param node
   * @param annotation
   */
  @SuppressWarnings("unchecked")
  public void parseAnnotation(SBase node, Object annotation) {
    if (annotation == null || annotation.equals("")) {
      return;
    }
    Set<String> annotations = new HashSet<>();
    if (annotation instanceof LinkedHashMap) {
      for (Map.Entry<String, Object> entry : ((LinkedHashMap<String, Object>) annotation).entrySet()) {
        annotations.addAll(parseAnnotation(entry));
      }
    } else {
      logger.severe(format(MESSAGES.getString("OPEN_ISSUE_ANNOTATION"), annotation.getClass().getName()));
    }
    if (annotations.size() > 0) {
      CVTerm term = new CVTerm();
      term.setQualifierType(CVTerm.Type.BIOLOGICAL_QUALIFIER);
      term.setBiologicalQualifierType(CVTerm.Qualifier.BQB_IS);
      annotations.forEach(term::addResource);
      node.addCVTerm(term);
    }
  }


  /**
   * @param entry
   * @return
   */
  @SuppressWarnings("unchecked")
  private Set<String> parseAnnotation(Map.Entry<String, Object> entry) {
    Set<String> annotations = new HashSet<>();
    String providerCode = entry.getKey();
    Object ids = entry.getValue();
    if (ids instanceof String) {
      checkResource(providerCode, (String) ids).map(annotations::add);
    } else if (ids instanceof ArrayList) {
      for (String id : ((ArrayList<String>) ids)) {
        checkResource(providerCode, id).map(annotations::add);
      }
    } else {
      logger.severe(format(MESSAGES.getString("OPEN_ISSUE_ID_FORMAT"), ids.getClass().getName()));
    }
    return annotations;
  }


  /**
   * @param providerCode
   * @param id
   * @return
   */
  private Optional<String> checkResource(String providerCode, String id) {
    String resource;
    if (id.startsWith("http")) {
      resource = id;
    } else {
      resource = Registry.createURI(providerCode, id);
    }
    return Registry.checkResourceUrl(resource);
  }


  /**
   * @param node
   * @param notes
   */
  @SuppressWarnings("unchecked")
  public void parseNotes(SBase node, Object notes) {
    Set<String> content = new HashSet<>();
    if (notes == null || notes.equals("")) {
      return;
    }
    if (notes instanceof LinkedHashMap) {
      for (Map.Entry<String, Object> entry : ((LinkedHashMap<String, Object>) notes).entrySet()) {
        content.add(parseNotes(entry));
      }
      StringBuilder notesContent = new StringBuilder();
      content = content.stream().filter(item -> !item.isEmpty()).collect(Collectors.toSet());
      if (content.size() > 0) {
        content.forEach(line -> notesContent.append("<p>").append(line).append("</p>\n"));
      }
      if (notesContent.length() > 0) {
        try {
          node.appendNotes(SBMLtools.toNotesString(notesContent.toString()));
        } catch (XMLStreamException e) {
          e.printStackTrace();
        }
      }
    } else if (notes instanceof String) {
      try {
        node.appendNotes(SBMLtools.toNotesString("<p>" + notes + "</p>"));
      } catch (XMLStreamException e) {
        e.printStackTrace();
      }
    } else {
      logger.severe(format(MESSAGES.getString("OPEN_ISSUE_NOTES_FORMAT"), notes.getClass().getName()));
    }
  }


  /**
   * @param entry
   * @return
   */
  @SuppressWarnings("unchecked")
  private String parseNotes(Map.Entry<String, Object> entry) {
    String note = "";
    String key = entry.getKey();
    Object value = entry.getValue();
    if (value instanceof String || value instanceof Integer || value instanceof Boolean) {
      note = key + ":" + value;
    } else if (value instanceof ArrayList) {
      StringJoiner items = new StringJoiner(",", "[", "]");
      ((List<String>) value).forEach(items::add);
      note = key + ":" + items.toString();
    } else {
      logger.severe(format(MESSAGES.getString("OPEN_ISSUE_NOTES_CONTENT"), value.getClass().getName()));
    }
    return note;
  }


  /**
   * @param builder
   * @param compartments
   */
  public void parseCompartments(ModelBuilder builder, Map<String, String> compartments) {
    int compSize = compartments.size();
    logger.info(format(MESSAGES.getString("NUM_COMPART"), compSize));
    Model model = builder.getModel();
    for (Map.Entry<String, String> compartment : compartments.entrySet()) {
      BiGGId.extractCompartmentCode(compartment.getKey()).ifPresentOrElse(compartmentCode -> {
        Compartment comp = model.getCompartment(compartmentCode);
        if (comp == null) {
          comp = model.createCompartment();
          comp.setId(compartmentCode);
          comp.setName(compartment.getValue());
        } else {
          if (!comp.isSetName()) {
            comp.setName(compartment.getValue());
          }
        }
      }, () -> logger.info(format(MESSAGES.getString("INVALID_COMPARTMENT_CODE"), compartment.getKey())));
    }
  }


  /**
   * @param builder
   * @param metabolites
   */
  private void parseMetabolites(ModelBuilder builder, List<Metabolite> metabolites) {
    int metSize = metabolites.size();
    logger.info(format(MESSAGES.getString("NUM_METABOLITES"), metSize));
    Model model = builder.getModel();
    for (Metabolite metabolite : metabolites) {
      String id = metabolite.getId();
      BiGGId.createMetaboliteId(id).ifPresent(metId -> {
        if (model.getSpecies(metId.toBiGGId()) != null) {
          logger.warning(format(MESSAGES.getString("DUPLICATE_SPECIES_ID"), id));
        } else {
          parseMetabolite(model, metabolite, metId);
        }
      });
    }
  }


  /**
   * @param model
   * @param metabolite
   */
  public void parseMetabolite(Model model, Metabolite metabolite, BiGGId biggId) {
    Species species = model.createSpecies(biggId.toBiGGId());
    String name = metabolite.getName();
    if (name.isEmpty()) {
      name = biggId.toBiGGId();
    }
    species.setName(name);
    String formula = metabolite.getFormula() != null ? metabolite.getFormula() : "";
    int charge = metabolite.getCharge();
    FBCSpeciesPlugin specPlug = (FBCSpeciesPlugin) species.getPlugin(FBCConstants.shortLabel);
    boolean validFormula = false;
    if (!formula.isEmpty()) {
      try {
        specPlug.setChemicalFormula(formula);
        validFormula = true;
      } catch (IllegalArgumentException exc) {
        logger.warning(format(MESSAGES.getString("INVALID_SPECIES_FORMULA"), biggId.toBiGGId(), formula));
      }
    }
    specPlug.setCharge(charge);
    String compartmentCode = metabolite.getCompartment();
    if (compartmentCode.isEmpty() && biggId.isSetCompartmentCode()) {
      compartmentCode = biggId.getCompartmentCode();
    }
    species.setCompartment(compartmentCode);
    // constraint sense is specified in former parser, not specified in scheme, thus ignored for now
    parseAnnotation(species, metabolite.getAnnotation());
    parseNotes(species, metabolite.getNotes());
    if (!validFormula) {
      try {
        species.appendNotes(SBMLtools.toNotesString("<p>FORMULA: " + formula + "</p>"));
      } catch (XMLStreamException e) {
        e.printStackTrace();
      }
    }
    if (species.isSetAnnotation()) {
      species.setMetaId(species.getId());
    }
  }


  /**
   * @param builder
   * @param genes
   */
  private void parseGenes(ModelBuilder builder, List<Gene> genes) {
    int genSize = genes.size();
    logger.info(format(MESSAGES.getString("NUM_GENES"), genSize));
    Model model = builder.getModel();
    for (Gene gene : genes) {
      String id = gene.getId();
      BiGGId.createGeneId(id).ifPresent(geneId -> {
        FBCModelPlugin modelPlug = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
        if (modelPlug.getGeneProduct(geneId.toBiGGId()) != null) {
          logger.warning(format(MESSAGES.getString("DUPLICATE_GENE_ID"), id));
        } else {
          parseGene(model, gene, geneId.toBiGGId());
        }
      });
    }
  }


  /**
   * @param model
   * @param gene
   */
  public void parseGene(Model model, Gene gene, String id) {
    FBCModelPlugin modelPlug = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
    GeneProduct gp = modelPlug.createGeneProduct(id);
    gp.setLabel(id);
    String name = gene.getName();
    if (name.isEmpty()) {
      name = id;
    }
    gp.setName(name);
    parseAnnotation(gp, gene.getAnnotation());
    parseNotes(gp, gene.getNotes());
  }


  /**
   * @param builder
   * @param reactions
   */
  private void parseReactions(ModelBuilder builder, List<Reaction> reactions) {
    int reactSize = reactions.size();
    logger.info(format(MESSAGES.getString("NUM_REACTIONS"), reactSize));
    for (Reaction reaction : reactions) {
      String id = reaction.getId();
      // Add prefix for BiGGId
      BiGGId.createReactionId(id).ifPresent(reactionId -> {
        if (builder.getModel().getReaction(reactionId.toBiGGId()) != null) {
          logger.warning(format(MESSAGES.getString("DUPLICATE_REACTION_ID"), id));
        } else {
          parseReaction(builder, reaction, reactionId.toBiGGId());
        }
      });
    }
  }


  /**
   * @param builder
   * @param reaction
   */
  public void parseReaction(ModelBuilder builder, Reaction reaction, String id) {
    Model model = builder.getModel();
    org.sbml.jsbml.Reaction r = model.createReaction(id);
    String name = reaction.getName();
    if (name.isEmpty()) {
      name = id;
    }
    r.setName(name);
    setReactionFluxBounds(builder, reaction, r);
    setReactionStoichiometry(reaction, model, r);
    String geneReactionRule = reaction.getGeneReactionRule();
    if (!geneReactionRule.isEmpty()) {
      GPRParser.parseGPR(r, geneReactionRule, false);
    }
    createSubsystem(model, reaction, r);
    setObjectiveCoefficient(reaction, model, r);
    parseAnnotation(r, reaction.getAnnotation());
    parseNotes(r, reaction.getNotes());
  }


  /**
   * @param builder
   * @param reaction
   * @param r
   */
  private void setReactionFluxBounds(ModelBuilder builder, Reaction reaction, org.sbml.jsbml.Reaction r) {
    FBCReactionPlugin rPlug = (FBCReactionPlugin) r.getPlugin(FBCConstants.shortLabel);
    double lowerBound = reaction.getLowerBound();
    // used the definition of reversibility given by the cobrapy sbml module
    if (lowerBound < 0) {
      r.setReversible(true);
    }
    double upperBound = reaction.getUpperBound();
    rPlug.setLowerFluxBound(
      builder.buildParameter(r.getId() + "_lb", r.getId() + "_lb", lowerBound, true, (String) null));
    rPlug.setUpperFluxBound(
      builder.buildParameter(r.getId() + "_ub", r.getId() + "_ub", upperBound, true, (String) null));
  }


  /**
   * @param reaction
   * @param model
   * @param r
   */
  @SuppressWarnings("unchecked")
  private void setReactionStoichiometry(Reaction reaction, Model model, org.sbml.jsbml.Reaction r) {
    Map<String, Double> metabolites = reaction.getMetabolites().get();
    for (Map.Entry<String, Double> metabolite : metabolites.entrySet()) {
      // removed mu code, as unused not not matching schema
      String id = metabolite.getKey();
      BiGGId.createMetaboliteId(id).ifPresent(metId -> {
        double value = metabolite.getValue();
        if (value != 0d) {
          Species species = model.getSpecies(metId.toBiGGId());
          if (species == null) {
            species = model.createSpecies(metId.toBiGGId());
            logger.info(format(MESSAGES.getString("SPECIES_UNDEFINED"), metId, r.getId()));
          }
          if (value < 0d) {
            ModelBuilder.buildReactants(r, pairOf(-value, species));
          } else {
            ModelBuilder.buildProducts(r, pairOf(value, species));
          }
        }
      });
    }
  }


  /**
   * @param model
   * @param reaction
   * @param r
   */
  private void createSubsystem(Model model, Reaction reaction, org.sbml.jsbml.Reaction r) {
    String subsystem = reaction.getSubsystem() != null ? reaction.getSubsystem() : "";
    if (!subsystem.isEmpty()) {
      GroupsModelPlugin groupsModelPlugin = (GroupsModelPlugin) model.getPlugin(GroupsConstants.shortLabel);
      Group group = (Group) groupsModelPlugin.getGroup(subsystem);
      for (Group existingGroup : groupsModelPlugin.getListOfGroups()) {
        if (subsystem.equals(existingGroup.getName())) {
          group = existingGroup;
          break;
        }
      }
      if (group == null) {
        group = groupsModelPlugin.createGroup();
        group.setName(subsystem);
        group.setKind(Group.Kind.partonomy);
      }
      SBMLUtils.createSubsystemLink(r, group.createMember());
    }
  }


  /**
   * @param reaction
   * @param model
   * @param r
   */
  private void setObjectiveCoefficient(Reaction reaction, Model model, org.sbml.jsbml.Reaction r) {
    FBCModelPlugin fbc = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
    Objective obj = fbc.getObjective(0);
    if (obj == null) {
      obj = fbc.createObjective("obj");
      obj.setType(Objective.Type.MAXIMIZE);
      fbc.getListOfObjectives().setActiveObjective(obj.getId());
    }
    double coefficient = reaction.getObjectiveCoefficient();
    if (coefficient != 0d) {
      FluxObjective fo = obj.createFluxObjective("fo_" + r.getId());
      fo.setCoefficient(coefficient);
      fo.setReaction(r);
    }
  }
}