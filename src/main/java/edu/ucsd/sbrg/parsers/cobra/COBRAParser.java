package edu.ucsd.sbrg.parsers.cobra;

import de.zbit.sbml.util.SBMLtools;
import de.zbit.util.ResourceManager;
import edu.ucsd.sbrg.bigg.Parameters;
import edu.ucsd.sbrg.util.GPRParser;
import edu.ucsd.sbrg.util.SBMLUtils;
import edu.ucsd.sbrg.util.UpdateListener;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.Unit;
import org.sbml.jsbml.UnitDefinition;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCModelPlugin;
import org.sbml.jsbml.ext.fbc.FluxObjective;
import org.sbml.jsbml.ext.fbc.Objective;
import org.sbml.jsbml.ext.groups.Group;
import org.sbml.jsbml.ext.groups.GroupsConstants;
import org.sbml.jsbml.ext.groups.GroupsModelPlugin;
import org.sbml.jsbml.util.ModelBuilder;
import us.hebi.matlab.mat.format.Mat5;
import us.hebi.matlab.mat.format.Mat5File;
import us.hebi.matlab.mat.types.Array;
import us.hebi.matlab.mat.types.MatFile;
import us.hebi.matlab.mat.types.MatlabType;
import us.hebi.matlab.mat.types.Struct;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import static java.text.MessageFormat.format;

/**
 * @author Andreas Dr&auml;ger
 */
public class COBRAParser {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(COBRAParser.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  private static MatlabFields matlabFields;

  /**
   *
   */
  private COBRAParser() {
    super();
  }


  /**
   * @param matFile
   * @return
   * @throws IOException
   */
  public static SBMLDocument read(File matFile) throws IOException {
    COBRAParser parser = new COBRAParser();
    return parser.parse(matFile);
  }


  /**
   * @param matFile
   * @return
   * @throws IOException
   */
  private SBMLDocument parse(File matFile) throws IOException {
    Mat5File mat5File = Mat5.readFromFile(matFile);
    SBMLDocument doc = parseModel(mat5File);
    mat5File.close();
    return doc;
  }


  /**
   * @param matFile
   * @return
   */
  private SBMLDocument parseModel(Mat5File matFile) {
    String modelName = "";
    for (MatFile.Entry entry : matFile.getEntries()) {
      if (entry.getValue().getType().equals(MatlabType.Structure)) {
        // found top level model structure
        modelName = entry.getName();
        break;
      }
    }
    if (modelName.isEmpty()) {
      logger.severe("Model name is empty for matlab model, aborting");
      return new SBMLDocument();
    }
    Struct modelStruct = matFile.getStruct(modelName);
    if (!Arrays.equals(modelStruct.getDimensions(), new int[] {1, 1})) {
      logger.severe("Model struct dimensions are wrong, aborting");
      return new SBMLDocument();
    }
    ModelBuilder builder = new ModelBuilder(3, 1);
    builder.buildModel(SBMLtools.toSId(modelName), null);
    SBMLDocument doc = builder.getSBMLDocument();
    doc.addTreeNodeChangeListener(new UpdateListener());
    fixFields(modelStruct);
    buildBasicUnits(builder);
    MatlabFields.init(modelStruct);
    matlabFields = MatlabFields.getInstance();
    parseFields(builder);
    return doc;
  }


  /**
   * @param modelStruct
   * @return
   */
  private void fixFields(Struct modelStruct) {
    List<String> fieldNames = new ArrayList<>(modelStruct.getFieldNames());
    for (String fieldName : fieldNames) {
      List<String> matches = ModelField.getCorrectName(fieldName);
      if (matches.size() == 1) {
        String match = matches.get(0);
        if (match.equals(fieldName)) {
          logger.info(format("Found known model field {0}", match));
        } else {
          logger.info(format("Field name {0} has wrong case, changing to known variant {1}", fieldName, match));
          Array array = modelStruct.get(fieldName);
          modelStruct.remove(fieldName);
          modelStruct.set(match, array);
        }
      } else {
        logger.info(format("Unknown field {0}, trying to interpret as prefix of known variant", fieldName));
        matches = ModelField.getNameForPrefix(fieldName);
        if (matches.size() == 1) {
          String match = matches.get(0);
          logger.info(format("Changing field name {0} to known variant {1}", fieldName, match));
          Array array = modelStruct.get(fieldName);
          modelStruct.remove(fieldName);
          modelStruct.set(match, array);
        } else {
          logger.warning(format("Could not resolve field {0} to known variant", fieldName));
        }
      }
    }
  }


  /**
   * @param builder
   */
  private void parseFields(ModelBuilder builder) {
    Model model = builder.getModel();
    parseDescription(model);
    parseMetabolites(model);
    parseGenes(model);
    parseRxns(builder);
    parseGPRs(model);
    parseSubsystems(model);
    FBCModelPlugin fbc = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
    Objective obj = fbc.createObjective("obj");
    obj.setType(Objective.Type.MAXIMIZE);
    fbc.getListOfObjectives().setActiveObjective(obj.getId());
    parseCSense(model);
    buildFluxObjectives(model, obj);
    parseBValue(model);
  }


  /**
   * @param model
   */
  private void parseDescription(Model model) {
    matlabFields.setDescriptionFields(model);
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
  private void parseMetabolites(Model model) {
    matlabFields.getCell(ModelField.mets.name()).ifPresent(mets -> {
      for (int i = 0; i < mets.getNumElements(); i++) {
        SpeciesParser speciesParser = new SpeciesParser(model, i);
        speciesParser.parse();
      }
    });
  }


  /**
   * @param model
   */
  private void parseGenes(Model model) {
    matlabFields.getCell(ModelField.genes.name()).ifPresentOrElse(genes -> {
      FBCModelPlugin modelPlug = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
      for (int i = 0; i < genes.getNumElements(); i++) {
        GeneParser geneParser = new GeneParser(modelPlug, i);
        geneParser.parse();
      }
    }, () -> logger.info(MESSAGES.getString("GENES_MISSING")));
  }


  /**
   * @param builder
   */
  private void parseRxns(ModelBuilder builder) {
    matlabFields.getCell(ModelField.rxns.name()).ifPresent(rxns -> {
      for (int index = 0; index < rxns.getNumElements(); index++) {
        ReactionParser reactionParser = new ReactionParser(builder, index);
        reactionParser.parse();
      }
    });
  }


  /**
   * @param model
   */
  private void parseGPRs(Model model) {
    matlabFields.getCell(ModelField.grRules.name()).ifPresent(grRules -> {
      for (int i = 0; i < grRules.getNumElements(); i++) {
        String geneReactionRule = COBRAUtils.asString(grRules.get(i), ModelField.grRules.name(), i + 1);
        if (model.getReaction(i) == null) {
          logger.severe(format(MESSAGES.getString("CREATE_GPR_FAILED"), i));
        } else {
          GPRParser.parseGPR(model.getReaction(i), geneReactionRule, Parameters.get().omitGenericTerms());
        }
      }
    });
  }


  /**
   * @param model
   */
  private void parseSubsystems(Model model) {
    // this is to avoid creating the identical group multiple times.
    matlabFields.getCell(ModelField.subSystems.name()).ifPresent(subSystems -> {
      if (subSystems.getNumElements() > 0) {
        Map<String, Group> nameToGroup = new HashMap<>();
        GroupsModelPlugin groupsModelPlugin = (GroupsModelPlugin) model.getPlugin(GroupsConstants.shortLabel);
        for (int i = 0; i < subSystems.getNumElements(); i++) {
          String name = COBRAUtils.asString(subSystems.get(i), ModelField.subSystems.name(), i + 1);
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
    });
  }


  /**
   * @param model
   */
  private void parseCSense(Model model) {
    matlabFields.getChar(ModelField.csense.name()).ifPresent(csense -> {
      for (int i = 0; i < csense.getNumElements(); i++) {
        try {
          char c = csense.getChar(0, i);
          // TODO: only 'E' (equality) is supported for now!
          if (c != 'E' && model.getListOfSpecies().size() > i) {
            logger.severe(format(MESSAGES.getString("NEQ_RELATION_UNSUPPORTED"), model.getSpecies(i).getId()));
          }
        } catch (Exception e) {
          logger.severe(e.toString());
          return;
        }
      }
    });
  }


  /**
   * @param model
   * @param obj
   */
  private void buildFluxObjectives(Model model, Objective obj) {
    matlabFields.getMatrix(ModelField.coefficients.name()).ifPresent(coefficients -> {
      for (int i = 0; i < coefficients.getNumElements(); i++) {
        double coefficient = coefficients.getDouble(i);
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
    });
  }


  /**
   * @param model
   */
  private void parseBValue(Model model) {
    matlabFields.getMatrix(ModelField.b.name()).ifPresent(b -> {
      for (int i = 0; i < b.getNumElements(); i++) {
        double bVal = b.getDouble(i);
        if (bVal != 0d && model.getListOfSpecies().size() > i) {
          // TODO: this should be incorporated into FBC version 3.
          logger.warning(format(MESSAGES.getString("B_VALUE_UNSUPPORTED"), bVal, model.getSpecies(i).getId()));
        }
      }
    });
  }
}
