package edu.ucsd.sbrg.bigg.polishing;

import de.zbit.util.ResourceManager;
import de.zbit.util.progressbar.AbstractProgressBar;
import edu.ucsd.sbrg.bigg.Parameters;
import edu.ucsd.sbrg.bigg.SBMLPolisher;
import edu.ucsd.sbrg.util.SBMLFix;
import org.sbml.jsbml.InitialAssignment;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Parameter;
import org.sbml.jsbml.SBO;
import org.sbml.jsbml.SpeciesReference;
import org.sbml.jsbml.Variable;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCModelPlugin;
import org.sbml.jsbml.ext.fbc.FluxObjective;
import org.sbml.jsbml.ext.fbc.GeneProduct;
import org.sbml.jsbml.ext.fbc.Objective;

import java.util.ResourceBundle;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static java.text.MessageFormat.format;

public class ModelPolishing {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(ModelPolishing.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  public static transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   *
   */
  protected AbstractProgressBar progress;
  /**
   *
   */
  private final Model model;
  /**
   *
   */
  private boolean strict;

  public ModelPolishing(Model model, boolean strict, AbstractProgressBar progress) {
    this.model = model;
    this.strict = strict;
    this.progress = progress;
  }


  /**
   *
   */
  public void polish() {
    if (!model.isSetMetaId() && (model.getCVTermCount() > 0)) {
      model.setMetaId(model.getId());
    }
    if (strict && model.isSetListOfInitialAssignments()) {
      polishListOfInitialAssignments();
    }
    if (model.isSetPlugin(FBCConstants.shortLabel)) {
      FBCModelPlugin modelPlug = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
      if (modelPlug.isSetListOfObjectives()) {
        polishListOfObjectives(modelPlug);
      }
      if (modelPlug.isSetListOfGeneProducts()) {
        polishListOfGeneProducts(modelPlug);
      }
      modelPlug.setStrict(strict);
    }
    polishListOfParameters(model);
  }


  /**
   * @return
   */
  public void polishListOfInitialAssignments() {
    for (InitialAssignment ia : model.getListOfInitialAssignments()) {
      progress.DisplayBar("Polishing Initial Assignments (6/9)  ");
      Variable variable = ia.getVariableInstance();
      if (variable != null) {
        if (variable instanceof Parameter) {
          if (variable.isSetSBOTerm() && SBO.isChildOf(variable.getSBOTerm(), 625)) {
            strict = false;
            logger.warning(format(MESSAGES.getString("FLUX_BOUND_STRICT_CHANGE"), variable.getId()));
          }
        } else if (variable instanceof SpeciesReference) {
          strict = false;
        }
      }
    }
  }


  /**
   * @param modelPlug
   * @return
   */
  public void polishListOfObjectives(FBCModelPlugin modelPlug) {
    if (modelPlug.getObjectiveCount() == 0) {
      // Note: the strict attribute does not require the presence of any Objectives in the model.
      logger.warning(format(MESSAGES.getString("OBJ_MISSING"), modelPlug.getParent().getId()));
    } else {
      for (Objective objective : modelPlug.getListOfObjectives()) {
        progress.DisplayBar("Polishing Objectives (7/9)  "); // "Processing objective " + objective.getId());
        if (!objective.isSetListOfFluxObjectives()) {
          Model model = modelPlug.getParent();
          strict &= SBMLFix.fixObjective(model.getId(), model.getListOfReactions(), modelPlug,
            Parameters.get().fluxCoefficients(), Parameters.get().fluxObjectives());
        }
        if (objective.isSetListOfFluxObjectives()) {
          polishListOfFluxObjectives(objective);
        }
      }
      // removed unused objectives, i.e. those without flux objectives
      modelPlug.getListOfObjectives().stream().filter(Predicate.not(Objective::isSetListOfFluxObjectives))
               .forEach(modelPlug::removeObjective);
    }
  }


  /**
   * @param objective
   * @return
   */
  public void polishListOfFluxObjectives(Objective objective) {
    if (objective.getFluxObjectiveCount() == 0) {
      // Note: the strict attribute does not require the presence of any flux objectives.
      logger.warning(format(MESSAGES.getString("OBJ_FLUX_OBJ_MISSING"), objective.getId()));
    } else {
      if (objective.getFluxObjectiveCount() > 1) {
        logger.warning(format(MESSAGES.getString("TOO_MUCH_OBJ_TARGETS"), objective.getId()));
      }
      for (FluxObjective fluxObjective : objective.getListOfFluxObjectives()) {
        if (!fluxObjective.isSetCoefficient() || Double.isNaN(fluxObjective.getCoefficient())
          || !Double.isFinite(fluxObjective.getCoefficient())) {
          logger.warning(format(MESSAGES.getString("FLUX_OBJ_COEFF_INVALID"), fluxObjective.getReaction()));
        }
      }
    }
  }


  /**
   * @param fbcModelPlug
   */
  public void polishListOfGeneProducts(FBCModelPlugin fbcModelPlug) {
    for (GeneProduct geneProduct : fbcModelPlug.getListOfGeneProducts()) {
      progress.DisplayBar("Polishing Gene Products (8/9)  ");
      GeneProductPolishing geneProductPolishing = new GeneProductPolishing(geneProduct);
      geneProductPolishing.polish();
    }
  }


  /**
   * @param model
   */
  public void polishListOfParameters(Model model) {
    for (int i = 0; i < model.getParameterCount(); i++) {
      progress.DisplayBar("Polishing Parameters (9/9)  ");
      Parameter parameter = model.getParameter(i);
      polish(parameter);
    }
  }


  /**
   * @param p
   */
  private void polish(Parameter p) {
    if (p.isSetId() && !p.isSetName()) {
      // TODO: what is happening here?
      p.setName(SBMLPolisher.polishName(p.getId()));
    }
  }
}
