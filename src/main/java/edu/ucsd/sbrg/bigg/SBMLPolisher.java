/*
 * $Id$
 * $URL$
 * ---------------------------------------------------------------------
 * This file is part of the program NetBuilder.
 * Copyright (C) 2013 by the University of California, San Diego.
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation. A copy of the license
 * agreement is provided in the file named "LICENSE.txt" included with
 * this software distribution and also available online as
 * <http://www.gnu.org/licenses/lgpl-3.0-standalone.html>.
 * ---------------------------------------------------------------------
 */
package edu.ucsd.sbrg.bigg;

import de.zbit.util.ResourceManager;
import de.zbit.util.progressbar.AbstractProgressBar;
import de.zbit.util.progressbar.ProgressBar;
import edu.ucsd.sbrg.bigg.polishing.CompartmentPolishing;
import edu.ucsd.sbrg.bigg.polishing.GeneProductPolishing;
import edu.ucsd.sbrg.bigg.polishing.ReactionPolishing;
import edu.ucsd.sbrg.bigg.polishing.SpeciesPolishing;
import edu.ucsd.sbrg.miriam.Registry;
import edu.ucsd.sbrg.util.SBMLFix;
import org.sbml.jsbml.CVTerm;
import org.sbml.jsbml.InitialAssignment;
import org.sbml.jsbml.ListOf;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Parameter;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBO;
import org.sbml.jsbml.SBase;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.SpeciesReference;
import org.sbml.jsbml.Unit;
import org.sbml.jsbml.UnitDefinition;
import org.sbml.jsbml.Variable;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCModelPlugin;
import org.sbml.jsbml.ext.fbc.FluxObjective;
import org.sbml.jsbml.ext.fbc.GeneProduct;
import org.sbml.jsbml.ext.fbc.Objective;
import org.sbml.jsbml.util.ModelBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static java.text.MessageFormat.format;

/**
 * @author Andreas Dr&auml;ger
 */
public class SBMLPolisher {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(SBMLPolisher.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  public static transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   *
   */
  private AbstractProgressBar progress;

  /**
   *
   */
  public SBMLPolisher() {
  }


  /**
   * Entrypoint for #ModelPolisher class
   * 
   * @param doc:
   *        SBMLDocument containing the model to polish
   * @return SBMLDocument containing polished model
   */
  public SBMLDocument polish(SBMLDocument doc) {
    if (!doc.isSetModel()) {
      logger.severe(MESSAGES.getString("NO_MODEL_FOUND"));
      return doc;
    }
    Model model = doc.getModel();
    polish(model);
    doc.setSBOTerm(624); // flux balance framework
    if (progress != null) {
      progress.finished();
    }
    return doc;
  }


  /**
   * Main method delegating all polishing tasks
   * 
   * @param model:
   *        SBML Model to polish
   */
  public void polish(Model model) {
    logger.info(format(MESSAGES.getString("PROCESSING_MODEL"), model.getId()));
    // initialize ProgressBar
    int count = 1 // for model properties
      + model.getUnitDefinitionCount() + model.getCompartmentCount() + model.getParameterCount()
      + model.getReactionCount() + model.getSpeciesCount() + model.getInitialAssignmentCount();
    if (model.isSetPlugin(FBCConstants.shortLabel)) {
      FBCModelPlugin fbcModelPlug = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
      count += fbcModelPlug.getObjectiveCount() + fbcModelPlug.getGeneProductCount();
    }
    progress = new ProgressBar(count);
    progress.DisplayBar("Polishing Model (1/9)  ");
    if (!model.isSetMetaId() && (model.getCVTermCount() > 0)) {
      model.setMetaId(model.getId());
    }
    polishListOfUnitDefinitions(model);
    polishListOfCompartments(model);
    polishListOfSpecies(model);
    boolean strict = polishListOfReactions(model);
    if (strict && model.isSetListOfInitialAssignments()) {
      strict = polishListOfInitialAssignments(model, true);
    }
    FBCModelPlugin modelPlug = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
    if (modelPlug.isSetListOfObjectives()) {
      strict &= polishListOfObjectives(strict, modelPlug);
    }
    if (model.isSetPlugin(FBCConstants.shortLabel)) {
      FBCModelPlugin fbcModelPlug = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
      if (fbcModelPlug.isSetListOfGeneProducts()) {
        polishListOfGeneProducts(fbcModelPlug);
      }
    }
    polishListOfParameters(model);
    modelPlug.setStrict(strict);
  }


  /**
   * @param model
   */
  public void polishListOfUnitDefinitions(Model model) {
    progress.DisplayBar("Polishing Unit Definitions (2/9)  "); // "Processing unit definitions");
    int udCount = model.getUnitDefinitionCount();
    ListOf<UnitDefinition> unitDefinitions = model.getListOfUnitDefinitions();
    UnitDefinition mmol_per_gDW_per_hr = setBasicUnitDefinition(model);
    UnitDefinition substanceUnits = setUnits(model, unitDefinitions);
    boolean substanceExists = true;
    if (substanceUnits == null) {
      substanceUnits = model.createUnitDefinition(UnitDefinition.SUBSTANCE);
      substanceUnits.setName("Millimoles per gram (dry weight)");
      substanceExists = false;
    }
    if (!model.isSetExtentUnits()) {
      model.setExtentUnits(substanceUnits.getId());
    }
    if (!model.isSetSubstanceUnits()) {
      model.setSubstanceUnits(substanceUnits.getId());
    }
    for (Unit unit : mmol_per_gDW_per_hr.getListOfUnits()) {
      switch (unit.getKind()) {
      case SECOND:
        // Assumes it is per hour:
        UnitDefinition ud = model.getTimeUnitsInstance();
        if (ud == null) {
          ud = model.createUnitDefinition(UnitDefinition.TIME);
          model.setTimeUnits(ud.getId());
          Unit timeUnit = safeClone(unit);
          timeUnit.setExponent(1d);
          ud.setName("Hour");
          ud.addUnit(timeUnit);
          timeUnit.addCVTerm(new CVTerm(CVTerm.Qualifier.BQB_IS, Registry.createURI("unit", "UO:0000032")));
          unit.addCVTerm(new CVTerm(CVTerm.Qualifier.BQB_IS_VERSION_OF, Registry.createURI("unit", "UO:0000032")));
        }
        break;
      case GRAM:
        unit.addCVTerm(new CVTerm(CVTerm.Qualifier.BQB_IS_VERSION_OF, unit.getKind().getUnitOntologyIdentifier()));
        if (!substanceExists) {
          substanceUnits.addUnit(safeClone(unit));
        }
        break;
      case MOLE:
        if (unit.getScale() == -3) {
          unit.addCVTerm(new CVTerm(CVTerm.Qualifier.BQB_IS, Registry.createURI("unit", "UO:0000040")));
        }
        if (!substanceExists) {
          substanceUnits.addUnit(safeClone(unit));
        }
        break;
      default:
        break;
      }
    }
    while (progress.getCallNumber() < udCount) {
      progress.DisplayBar("Polishing Unit Definitions (2/9)  ");
    }
  }


  /**
   * @param model
   * @return
   */
  private UnitDefinition setBasicUnitDefinition(Model model) {
    UnitDefinition mmol_per_gDW_per_hr = model.getUnitDefinition("mmol_per_gDW_per_hr");
    if (mmol_per_gDW_per_hr == null) {
      mmol_per_gDW_per_hr = model.createUnitDefinition("mmol_per_gDW_per_hr");
      logger.finest(MESSAGES.getString("ADDED_UNIT_DEF"));
    }
    if (mmol_per_gDW_per_hr.getUnitCount() < 1) {
      ModelBuilder.buildUnit(mmol_per_gDW_per_hr, 1d, -3, Unit.Kind.MOLE, 1d);
      ModelBuilder.buildUnit(mmol_per_gDW_per_hr, 1d, 0, Unit.Kind.GRAM, -1d);
      ModelBuilder.buildUnit(mmol_per_gDW_per_hr, 3600d, 0, Unit.Kind.SECOND, -1d);
    }
    if (!mmol_per_gDW_per_hr.isSetName()) {
      mmol_per_gDW_per_hr.setName("Millimoles per gram (dry weight) per hour");
    }
    if (!mmol_per_gDW_per_hr.isSetMetaId()) {
      mmol_per_gDW_per_hr.setMetaId(mmol_per_gDW_per_hr.getId());
    }
    mmol_per_gDW_per_hr.addCVTerm(
      new CVTerm(CVTerm.Qualifier.BQB_IS_DESCRIBED_BY, Registry.createURI("pubmed", 7986045)));
    return mmol_per_gDW_per_hr;
  }


  /**
   * @param model
   * @param unitDefinitions
   * @return
   */
  private UnitDefinition setUnits(Model model, ListOf<UnitDefinition> unitDefinitions) {
    UnitDefinition substanceUnits = model.getSubstanceUnitsInstance();
    if (substanceUnits == null && unitDefinitions.get("substance") != null) {
      model.setSubstanceUnits(UnitDefinition.SUBSTANCE);
      substanceUnits = model.getSubstanceUnitsInstance();
    }
    UnitDefinition volumeUnits = model.getVolumeUnitsInstance();
    if (volumeUnits == null && unitDefinitions.get("volume") != null) {
      model.setVolumeUnits(UnitDefinition.VOLUME);
    }
    UnitDefinition timeUnits = model.getTimeUnitsInstance();
    if (timeUnits == null && unitDefinitions.get("time") != null) {
      model.setTimeUnits(UnitDefinition.TIME);
    }
    return substanceUnits;
  }


  /**
   * @param model
   */
  public void polishListOfCompartments(Model model) {
    for (int i = 0; i < model.getCompartmentCount(); i++) {
      progress.DisplayBar("Polishing Compartments (3/9)  ");
      CompartmentPolishing compartmentPolishing = new CompartmentPolishing(model.getCompartment(i));
      compartmentPolishing.polish();
    }
  }


  /**
   * @param model
   */
  public void polishListOfSpecies(Model model) {
    List<Species> speciesToRemove = new ArrayList<>();
    for (Species species : model.getListOfSpecies()) {
      progress.DisplayBar("Polishing Species (4/9)  "); // "Processing species " + species.getId());
      SpeciesPolishing speciesPolishing = new SpeciesPolishing(species);
      speciesPolishing.polish().ifPresent(speciesToRemove::add);
    }
    for (Species species : speciesToRemove) {
      model.removeSpecies(species);
    }
  }


  /**
   * @param model
   * @return
   */
  public boolean polishListOfReactions(Model model) {
    boolean strict = true;
    for (int i = 0; i < model.getReactionCount(); i++) {
      progress.DisplayBar("Polishing Reactions (5/9)  ");
      ReactionPolishing reactionPolishing = new ReactionPolishing(model.getReaction(i));
      strict &= reactionPolishing.polish();
    }
    return strict;
  }


  /**
   * @param model
   * @param strict
   * @return
   */
  public boolean polishListOfInitialAssignments(Model model, boolean strict) {
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
    return strict;
  }


  /**
   * @param strict
   * @param modelPlug
   * @return
   */
  public boolean polishListOfObjectives(boolean strict, FBCModelPlugin modelPlug) {
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
          strict &= polishListOfFluxObjectives(strict, objective);
        }
      }
      // removed unused objectives, i.e. those without flux objectives
      modelPlug.getListOfObjectives().stream().filter(Predicate.not(Objective::isSetListOfFluxObjectives))
               .forEach(modelPlug::removeObjective);
    }
    return strict;
  }


  /**
   * @param strict
   * @param objective
   * @return
   */
  public boolean polishListOfFluxObjectives(boolean strict, Objective objective) {
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
    return strict;
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
      p.setName(polishName(p.getId()));
    }
  }


  /**
   * @param name
   * @return
   */
  public static String polishName(String name) {
    // can this be replaced by BiGGId creation?
    String newName = name;
    if (name.startsWith("?_")) {
      newName = name.substring(2);
    }
    if (newName.matches("__.*__")) {
      newName = newName.replaceAll("__.*__", "(.*)");
    } else if (newName.contains("__")) {
      newName = newName.replace("__", "-");
    }
    if (newName.matches(".*_C?\\d*.*\\d*")) {
      newName =
        newName.substring(0, newName.lastIndexOf('_')) + " - " + newName.substring(newName.lastIndexOf('_') + 1);
    }
    newName = newName.replace("_", " ");
    if (!newName.equals(name)) {
      logger.fine(format(MESSAGES.getString("CHANGED_NAME"), name, newName));
    }
    return newName;
  }


  /**
   * @param sbase
   * @return
   */
  private <T extends SBase> T safeClone(T sbase) {
    @SuppressWarnings("unchecked")
    T sb = (T) sbase.clone();
    if (sb.isSetMetaId()) {
      sb.unsetMetaId();
    }
    return sb;
  }
}
