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

import de.zbit.kegg.AtomBalanceCheck;
import de.zbit.kegg.AtomBalanceCheck.AtomCheckResult;
import de.zbit.util.progressbar.AbstractProgressBar;
import de.zbit.util.progressbar.ProgressBar;
import edu.ucsd.sbrg.miriam.Registry;
import edu.ucsd.sbrg.util.GPRParser;
import edu.ucsd.sbrg.util.SBMLFix;
import edu.ucsd.sbrg.util.SBMLUtils;
import org.sbml.jsbml.CVTerm;
import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.InitialAssignment;
import org.sbml.jsbml.KineticLaw;
import org.sbml.jsbml.ListOf;
import org.sbml.jsbml.LocalParameter;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.NamedSBase;
import org.sbml.jsbml.Parameter;
import org.sbml.jsbml.Reaction;
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
import org.sbml.jsbml.ext.fbc.FBCReactionPlugin;
import org.sbml.jsbml.ext.fbc.FluxObjective;
import org.sbml.jsbml.ext.fbc.GeneProduct;
import org.sbml.jsbml.ext.fbc.Objective;
import org.sbml.jsbml.util.ModelBuilder;
import org.sbml.jsbml.util.ResourceManager;
import org.sbml.jsbml.xml.XMLNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static edu.ucsd.sbrg.bigg.ModelPolisher.MESSAGES;
import static java.text.MessageFormat.format;

/**
 * @author Andreas Dr&auml;ger
 */
public class SBMLPolisher {

  /**
   *
   */
  public enum Patterns {

    ATP_MAINTENANCE(".*[Aa][Tt][Pp][Mm]"),
    BIOMASS_CASE_INSENSITIVE(".*[Bb][Ii][Oo][Mm][Aa][Ss][Ss].*"),
    BIOMASS_CASE_SENSITIVE(".*BIOMASS.*"),
    DEFAULT_FLUX_BOUND("(.*_)?[Dd][Ee][Ff][Aa][Uu][Ll][Tt]_.*"),
    DEMAND_REACTION("(.*_)?[Dd][Mm]_.*"),
    EXCHANGE_REACTION("(.*_)?[Ee][Xx]_.*"),
    SINK_REACTION("(.*_)?[Ss]([Ii][Nn])?[Kk]_.*");

    private Pattern pattern;

    Patterns(String regex) {
      pattern = Pattern.compile(regex);
    }


    public Pattern getPattern() {
      return pattern;
    }
  }

  /**
   * A {@link Logger} for this class.
   */
  public static final transient Logger logger = Logger.getLogger(SBMLPolisher.class.getName());
  /**
   *
   */
  private boolean checkMassBalance = true;
  /**
   * Switch to decide if generic and obvious terms should be used.
   */
  public boolean omitGenericTerms;
  /**
   * Switch to decide if also references to data sources can be included into
   * {@link CVTerm}s whose URLs are not (yet) part of the MIRIAM registry.
   */
  public boolean includeAnyURI;
  /**
   *
   */
  private AbstractProgressBar progress;
  /**
   *
   */
  private double[] fluxCoefficients;
  /**
   *
   */
  private String[] fluxObjectives;

  /**
   *
   */
  public SBMLPolisher() {
    omitGenericTerms = false;
  }


  /**
   * @return the checkMassBalance
   */
  public boolean isCheckMassBalance() {
    return checkMassBalance;
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
      Compartment c = model.getCompartment(i);
      progress.DisplayBar("Polishing Compartments (3/9)  "); // "Processing compartment " + c.getId());
      polish(c);
    }
  }


  /**
   * @param c
   */
  public void polish(Compartment c) {
    if (!c.isSetId()) {
      c.setId("d"); // default
    } else {
      // remove C_ prefix of compartment code, not in BiGGId specification
      BiGGId.extractCompartmentCode(c.getId()).ifPresentOrElse(c::setId,
        () -> logger.warning(format("CompartmentCode '{0}' is not BiGGId conform.", c.getId())));
    }
    c.setSBOTerm(410); // implicit compartment
    if (!c.isSetName()) {
      // TODO: make the name of a compartment a user setting
      c.setName("default");
    }
    if (!c.isSetMetaId() && (c.getCVTermCount() > 0)) {
      c.setMetaId(c.getId());
    }
    if (!c.isSetConstant()) {
      c.setConstant(true);
    }
    if (!c.isSetSpatialDimensions()) {
      // TODO: check with biGG id, not for surfaces etc.
      // c.setSpatialDimensions(3d);
    }
    if (!c.isSetUnits()) {
      Model model = c.getModel();
      // Let's take the model's default unless we don't have anything defined.
      if ((model == null) || !(model.isSetLengthUnits() || model.isSetAreaUnits() || model.isSetVolumeUnits())) {
        // TODO: set compartment units.
        /*
         * This is a temporary solution until we agree on something better.
         */
        c.setUnits(Unit.Kind.DIMENSIONLESS);
      }
    }
  }


  /**
   * @param model
   */
  public void polishListOfSpecies(Model model) {
    List<Species> speciesToRemove = new ArrayList<>();
    for (Species species : model.getListOfSpecies()) {
      progress.DisplayBar("Polishing Species (4/9)  "); // "Processing species " + species.getId());
      polish(species).ifPresent(speciesToRemove::add);
    }
    for (Species species : speciesToRemove) {
      model.removeSpecies(species);
    }
  }


  /**
   * @param species
   */
  public Optional<Species> polish(Species species) {
    String id = species.getId();
    if (id.isEmpty()) {
      // remove species with missing id, produces invalid SBML
      if (species.isSetName()) {
        logger.severe(format(
          "Removing species '{0}' due to missing id. Check your Model for entries missing the id attribute or duplicates.",
          species.getName()));
      } else {
        logger.severe("Removing species with missing id and name. Check your Model for species without id and name.");
      }
      return Optional.of(species);
    }
    if (species.getId().endsWith("_boundary")) {
      logger.warning(format(MESSAGES.getString("SPECIES_ID_INVALID"), id));
      id = id.substring(0, id.length() - 9);
      boolean uniqueId = species.getModel().findUniqueNamedSBase(id) == null;
      if (uniqueId) {
        if (!species.isSetBoundaryCondition() || !species.isBoundaryCondition()) {
          logger.warning(format(MESSAGES.getString("BOUNDARY_FLAG_MISSING"), id));
          species.setBoundaryCondition(true);
        }
      }
    } else if (!species.isSetBoundaryCondition()) {
      species.setBoundaryCondition(false);
    }
    /*
     * Set mandatory attributes to default values
     * TODO: make those maybe user settings.
     */
    if (!species.isSetHasOnlySubstanceUnits()) {
      species.setHasOnlySubstanceUnits(true);
    }
    if (!species.isSetConstant()) {
      species.setConstant(false);
    }
    if ((species.getCVTermCount() > 0) && !species.isSetMetaId()) {
      species.setMetaId(species.getId());
    }
    BiGGId.createMetaboliteId(id).ifPresent(biggId -> {
      if (biggId.isSetCompartmentCode() && species.isSetCompartment()
        && !biggId.getCompartmentCode().equals(species.getCompartment())) {
        logger.warning(format(MESSAGES.getString("CHANGE_COMPART_REFERENCE"), species.getId(), species.getCompartment(),
          biggId.getCompartmentCode()));
        species.setCompartment(biggId.getCompartmentCode());
      }
    });
    checkCompartment(species);
    return Optional.empty();
  }


  /**
   * @param nsb
   */
  public void checkCompartment(NamedSBase nsb) {
    if ((nsb instanceof Species) && !((Species) nsb).isSetCompartment()) {
      Optional<BiGGId> biggId = BiGGId.createMetaboliteId(nsb.getId());
      boolean setCompartment = false;
      if (biggId.isPresent()) {
        if (biggId.get().isSetCompartmentCode()) {
          ((Species) nsb).setCompartment(biggId.get().getCompartmentCode());
          setCompartment = true;
        }
      }
      if (!setCompartment) {
        return;
      }
    } else if ((nsb instanceof Reaction) && !((Reaction) nsb).isSetCompartment()) {
      return;
    }
    if (nsb instanceof Species) {
      String cId = ((Species) nsb).getCompartment();
      Model model = nsb.getModel();
      SBase candidate = model.findUniqueNamedSBase(cId);
      if (candidate instanceof Compartment) {
        // compartment can't be null here, instanceof would evaluate to false
        Compartment c = (Compartment) candidate;
        polish(c);
      } else if (candidate == null) {
        logger.warning(format(MESSAGES.getString("CREATE_MISSING_COMP"), cId, nsb.getId(), nsb.getElementName()));
        polish(model.createCompartment(cId));
      }
    }
  }


  /**
   * @param model
   * @return
   */
  public boolean polishListOfReactions(Model model) {
    boolean strict = true;
    for (int i = 0; i < model.getReactionCount(); i++) {
      Reaction r = model.getReaction(i);
      strict &= polish(r);
      progress.DisplayBar("Polishing Reactions (5/9)  "); // "Processing reaction " + r.getId());
    }
    return strict;
  }


  /**
   * @param r
   * @return {@code true} if the given reaction qualifies for strict FBC.
   */
  public boolean polish(Reaction r) {
    String id = r.getId();
    if (id.isEmpty()) {
      // remove species with missing id, produces invalid SBML
      if (r.isSetName()) {
        logger.severe(format(
          "Removing reaction '{0}' due to missing id. Check your Model for entries missing the id attribute or duplicates.",
          r.getName()));
      } else {
        logger.severe("Removing reaction with missing id and name. Check your Model for reaction without id and name.");
      }
      r.getModel().removeReaction(r);
      return false;
    }
    BiGGId.createReactionId(id).ifPresent(biggId -> setSBOTermFromPattern(r, biggId));
    // TODO: make code more robust -> 'conflicting compartment codes?'
    String compartmentId = r.isSetCompartment() ? r.getCompartment() : null;
    if (r.isSetListOfReactants()) {
      String cId = polish(r.getListOfReactants(), SBO.getReactant());
      compartmentId = checkCId(cId, compartmentId);
      if (compartmentId != null) {
        r.setCompartment(compartmentId);
      }
    }
    if (r.isSetListOfProducts()) {
      String cId = polish(r.getListOfProducts(), SBO.getProduct());
      compartmentId = checkCId(cId, compartmentId);
      if (compartmentId != null) {
        r.setCompartment(compartmentId);
      }
    }
    if (!r.isSetMetaId() && (r.getCVTermCount() > 0)) {
      r.setMetaId(r.getId());
    }
    String rName = r.getName();
    if (rName.matches(".*_copy\\d*")) {
      rName = rName.substring(0, rName.lastIndexOf('_'));
      r.setName(rName);
    }
    SBMLUtils.setRequiredAttributes(r);
    // This is a check if we are producing invalid SBML.
    if ((r.getReactantCount() == 0) && (r.getProductCount() == 0)) {
      ResourceBundle bundle = ResourceManager.getBundle("org.sbml.jsbml.resources.cfg.Messages");
      logger.severe(format(bundle.getString("SBMLCoreParser.reactionWithoutParticipantsError"), r.getId()));
    } else {
      checkBalance(r);
    }
    fluxObjectiveFromLocalParameter(r);
    associationFromNotes(r);
    boolean strict = checkBounds(r);
    strict = checkReactantsProducts(r, strict);
    return strict;
  }


  /**
   * @param r
   * @param id
   * @return
   */
  private void setSBOTermFromPattern(Reaction r, BiGGId id) {
    String abbrev = id.getAbbreviation();
    if (Patterns.BIOMASS_CASE_INSENSITIVE.getPattern().matcher(abbrev).matches()) {
      r.setSBOTerm(629); // biomass production
    } else if (Patterns.DEMAND_REACTION.getPattern().matcher(abbrev).matches()) {
      r.setSBOTerm(628); // demand reaction
    } else if (Patterns.EXCHANGE_REACTION.getPattern().matcher(abbrev).matches()) {
      r.setSBOTerm(627); // exchange reaction
    } else if (Patterns.ATP_MAINTENANCE.getPattern().matcher(abbrev).matches()) {
      r.setSBOTerm(630); // ATP maintenance
    } else if (Patterns.SINK_REACTION.getPattern().matcher(abbrev).matches()) {
      r.setSBOTerm(632);
    }
  }


  /**
   * @param speciesReferences
   * @param defaultSBOterm
   * @return
   */
  private String polish(ListOf<SpeciesReference> speciesReferences, int defaultSBOterm) {
    String compartmentId = "";
    Model model = speciesReferences.getModel();
    for (SpeciesReference sr : speciesReferences) {
      if (!sr.isSetSBOTerm() && !omitGenericTerms) {
        sr.setSBOTerm(defaultSBOterm);
      }
      if (!sr.isSetConstant()) {
        sr.setConstant(false);
      }
      Species species = model.getSpecies(sr.getSpecies());
      if (species != null) {
        if (!species.isSetCompartment() || (compartmentId == null)
          || (!compartmentId.isEmpty() && !compartmentId.equals(species.getCompartment()))) {
          compartmentId = null;
        } else {
          compartmentId = species.getCompartment();
        }
      } else {
        logger.info(format(MESSAGES.getString("SPECIES_REFERENCE_INVALID"), sr.getSpecies()));
      }
    }
    if ((compartmentId == null) || compartmentId.isEmpty()) {
      return null;
    }
    return compartmentId;
  }


  /**
   * @param cId
   * @param compartmentId
   * @return
   */
  private String checkCId(String cId, String compartmentId) {
    if (cId == null) {
      compartmentId = null;
    } else {
      if (compartmentId == null) {
        compartmentId = cId;
      } else if (!compartmentId.equals(cId)) {
        compartmentId = null;
      }
    }
    return compartmentId;
  }


  /**
   * @param r
   */
  private void checkBalance(Reaction r) {
    // TODO: change messages
    if (!r.isSetSBOTerm()) {
      // The reaction has not been recognized as demand or exchange reaction
      if (r.getReactantCount() == 0) {
        // fixme: Messages are wrong
        if (r.isReversible()) {
          // TODO: sink reaction
        } else if (r.getSBOTerm() != 628) {
          // logger.info(format(mpMessageBundle.getString("REACTION_DM_NOT_IN_ID"), r.getId()));
          r.setSBOTerm(628); // demand reaction
        }
      } else if (r.getProductCount() == 0) {
        if (r.isReversible()) {
          // TODO: source reaction
        } else {
          // logger.warning(format(mpMessageBundle.getString("REACTION_DM_NOT_IN_ID"), r.getId()));
          r.setSBOTerm(628); // demand reaction
        }
      }
    }
    if (isCheckMassBalance() && ((r.getSBOTerm() < 627) || (630 < r.getSBOTerm()))) {
      // check atom balance only if the reaction is not identified as biomass
      // production, demand, exchange or ATP maintenance.
      AtomCheckResult<Reaction> defects = AtomBalanceCheck.checkAtomBalance(r, 1);
      if ((defects != null) && (defects.hasDefects())) {
        logger.warning(format(MESSAGES.getString("ATOMS_MISSING"), r.getId(), defects.getDefects().toString()));
      } else if (defects == null) {
        logger.fine(format(MESSAGES.getString("CHECK_ATOM_BALANCE_FAILED"), r.getId()));
      } else {
        logger.fine(format(MESSAGES.getString("ATOMS_OK"), r.getId()));
      }
    }
    GPRParser.convertAssociationsToFBCV2(r, omitGenericTerms);
  }


  /**
   * Set flux objective and its coefficient from reaction kinetic law, if no flux objective exists for the reaction
   *
   * @param r:
   *        Reaction
   */
  private void fluxObjectiveFromLocalParameter(Reaction r) {
    FBCModelPlugin modelPlugin = (FBCModelPlugin) r.getModel().getPlugin(FBCConstants.shortLabel);
    Objective obj = modelPlugin.getObjective(0);
    if (obj == null) {
      obj = modelPlugin.createObjective("obj");
      obj.setType(Objective.Type.MAXIMIZE);
      modelPlugin.getListOfObjectives().setActiveObjective(obj.getId());
    }
    boolean foExists = obj.getListOfFluxObjectives().stream().anyMatch(fo -> fo.getReactionInstance().equals(r));
    if (foExists) {
      return;
    }
    KineticLaw kl = r.getKineticLaw();
    if (kl != null) {
      LocalParameter coefficient = kl.getLocalParameter("OBJECTIVE_COEFFICIENT");
      if (coefficient != null && coefficient.getValue() != 0d) {
        FluxObjective fo = obj.createFluxObjective("fo_" + r.getId());
        fo.setCoefficient(coefficient.getValue());
        fo.setReaction(r);
      }
    }
  }


  /**
   * Convert GENE_ASSOCIATION in reaction notes to FBCv2 {#GeneProductAssociation}
   *
   * @param r:
   *        Reaction
   */
  private void associationFromNotes(Reaction r) {
    FBCReactionPlugin reactionPlugin = (FBCReactionPlugin) r.getPlugin(FBCConstants.shortLabel);
    if (!reactionPlugin.isSetGeneProductAssociation() && r.isSetNotes()) {
      XMLNode body = r.getNotes().getChildElement("body", null);
      if (body != null) {
        for (XMLNode p : body.getChildElements("p", null)) {
          if (p.getChildCount() == 1) {
            String associationCandidate = p.getChildAt(0).getCharacters();
            if (associationCandidate.startsWith("GENE_ASSOCIATION: ")) {
              String[] splits = associationCandidate.split("GENE_ASSOCIATION: ");
              if (splits.length == 2) {
                String association = splits[1];
                if (!association.isEmpty()) {
                  GPRParser.parseGPR(r, association, Parameters.get().omitGenericTerms());
                }
              }
            }
          }
        }
      }
    }
  }


  /**
   * Check if existing FBC flux bounds fulfill the strict requirement.
   * Bounds with no instance present are tried to be inferred from the reaction {#KineticLaw}
   *
   * @param r
   * @return
   */
  private boolean checkBounds(Reaction r) {
    FBCReactionPlugin rPlug = (FBCReactionPlugin) r.getPlugin(FBCConstants.shortLabel);
    Parameter lb = rPlug.getLowerFluxBoundInstance();
    Parameter ub = rPlug.getUpperFluxBoundInstance();
    boolean lbExists = polishFluxBound(lb);
    // set bounds from KineticLaw, if they are not set in FBC, create global Parameter, as required by specification
    if (!lbExists) {
      LocalParameter bound = getBoundFromLocal(r, "LOWER_BOUND");
      if (bound != null) {
        lb = new Parameter(bound);
        Parameter existingParameter = getParameterVariant(r, lb, bound.getValue());
        if (existingParameter != null) {
          rPlug.setLowerFluxBound(existingParameter);
        } else {
          r.getModel().addParameter(lb);
          rPlug.setLowerFluxBound(lb);
        }
        lbExists = polishFluxBound(rPlug.getLowerFluxBoundInstance());
      }
    }
    boolean ubExists = polishFluxBound(ub);
    if (!ubExists) {
      LocalParameter bound = getBoundFromLocal(r, "UPPER_BOUND");
      if (bound != null) {
        ub = new Parameter(bound);
        Parameter existingParameter = getParameterVariant(r, ub, bound.getValue());
        if (existingParameter != null) {
          rPlug.setUpperFluxBound(existingParameter);
        } else {
          r.getModel().addParameter(ub);
          rPlug.setUpperFluxBound(ub);
        }
        ubExists = polishFluxBound(rPlug.getUpperFluxBoundInstance());
      }
    }
    boolean strict = lbExists && ubExists;
    if (strict) {
      strict = checkBound(lb) && lb.getValue() < Double.POSITIVE_INFINITY && checkBound(ub)
        && ub.getValue() > Double.NEGATIVE_INFINITY && lb.getValue() <= ub.getValue();
      if (!strict) {
        logger.warning(format(MESSAGES.getString("FLUX_BOUND_ERROR"), r.getId()));
      }
    } else {
      logger.warning(format(MESSAGES.getString("FLUX_BOUNDS_MISSING"), r.getId()));
    }
    return strict;
  }


  /**
   * @param bound
   * @return {@code true} if this method successfully updated the bound
   *         parameter.
   */
  public boolean polishFluxBound(Parameter bound) {
    if (bound == null) {
      return false;
    }
    if (Patterns.DEFAULT_FLUX_BOUND.getPattern().matcher(bound.getId()).matches()) {
      bound.setSBOTerm(626); // default flux bound
    } else {
      bound.setSBOTerm(625); // flux bound
    }
    return true;
  }


  /**
   * @param r:
   *        Reaction
   * @param parameterName:
   *        LOWER_BOUND or UPPER_BOUND
   * @return
   */
  private LocalParameter getBoundFromLocal(Reaction r, String parameterName) {
    KineticLaw kl = r.getKineticLaw();
    if (kl != null) {
      return kl.getLocalParameter(parameterName);
    }
    return null;
  }


  /**
   * @param r:
   *        Reaction
   * @param bound:
   *        lower or upper bound instance
   * @param boundValue:
   *        value of {#LocalParameter} bound obtained from {{@link #getBoundFromLocal(Reaction, String)}}
   * @return
   */
  private Parameter getParameterVariant(Reaction r, Parameter bound, double boundValue) {
    if (boundValue == -1000d) {
      bound.setId("DEFAULT_LOWER_BOUND");
    } else if (boundValue == 0d) {
      bound.setId("DEFAULT_BOUND");
    } else if (boundValue == 1000d) {
      bound.setId("DEFAULT_UPPER_BOUND");
    } else {
      bound.setId(r.getId() + "_" + bound.getId());
    }
    return r.getModel().getParameter(bound.getId());
  }


  /**
   * Checks if a given bound parameter satisfies the required properties of a
   * strict flux bound parameter:
   * <li>not null
   * <li>constant
   * <li>defined value
   * other than {@link Double#NaN}
   *
   * @param bound
   * @return {@code true} if the given parameter can be used as a flux bound in
   *         strict FBC models, {@code false} otherwise.
   */
  public boolean checkBound(Parameter bound) {
    return (bound != null) && bound.isConstant() && bound.isSetValue() && !Double.isNaN(bound.getValue());
  }


  /**
   * @param r
   * @param strict
   * @return
   */
  private boolean checkReactantsProducts(Reaction r, boolean strict) {
    if (strict && r.isSetListOfReactants()) {
      strict = checkSpeciesReferences(r.getListOfReactants());
      if (!strict) {
        logger.warning(format(MESSAGES.getString("ILLEGAL_STOICH_REACT"), r.getId()));
      }
    }
    if (strict && r.isSetListOfProducts()) {
      strict = checkSpeciesReferences(r.getListOfProducts());
      if (!strict) {
        logger.warning(format(MESSAGES.getString("ILLEGAL_STOICH_PROD"), r.getId()));
      }
    }
    return strict;
  }


  /**
   * @param listOfSpeciesReference
   * @return
   */
  public boolean checkSpeciesReferences(ListOf<SpeciesReference> listOfSpeciesReference) {
    boolean strict = true;
    for (SpeciesReference sr : listOfSpeciesReference) {
      strict &=
        sr.isConstant() && sr.isSetStoichiometry() && !Double.isNaN(sr.getValue()) && Double.isFinite(sr.getValue());
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
      Variable variable = ia.getVariableInstance();
      progress.DisplayBar("Polishing Initial Assignments (6/9)  "); // "Processing initial assignment for " +
                                                                    // variable.getId());
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
          strict &= SBMLFix.fixObjective(model.getId(), model.getListOfReactions(), modelPlug, fluxCoefficients,
            fluxObjectives);
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
      polish(geneProduct);
    }
  }


  /**
   * @param geneProduct
   */
  public void polish(GeneProduct geneProduct) {
    String label = null;
    if (geneProduct.isSetLabel() && !geneProduct.getLabel().equalsIgnoreCase("None")) {
      label = geneProduct.getLabel();
    } else if (geneProduct.isSetId()) {
      label = geneProduct.getId();
    }
    if (label == null) {
      return;
    }
    BiGGId.createGeneId(geneProduct.getId()).ifPresent(biggId -> {
      String id = biggId.toBiGGId();
      if (!id.equals(geneProduct.getId())) {
        geneProduct.setId(id);
      }
      if (geneProduct.getCVTermCount() > 0) {
        geneProduct.setMetaId(id);
      }
    });
    if (!geneProduct.isSetName() || geneProduct.getName().equalsIgnoreCase("None")) {
      geneProduct.setName(label);
    }
  }


  /**
   * @param model
   */
  public void polishListOfParameters(Model model) {
    for (int i = 0; i < model.getParameterCount(); i++) {
      Parameter parameter = model.getParameter(i);
      progress.DisplayBar("Polishing Parameters (9/9)  ");
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


  /**
   * @param includeAnyURI
   *        define if only identifiers.org URIs are included in MIRIAM
   *        annotation.
   */
  public void setIncludeAnyURI(boolean includeAnyURI) {
    this.includeAnyURI = includeAnyURI;
  }


  /**
   * @param checkMassBalance
   */
  public void setCheckMassBalance(boolean checkMassBalance) {
    this.checkMassBalance = checkMassBalance;
  }


  /**
   * @param modelNamePattern
   *        the modelNamePattern to set
   */
  public void setDocumentTitlePattern(String modelNamePattern) {
    Parameters parameters = Parameters.get();
    parameters.setDocumentTitlePattern(modelNamePattern);
  }


  /**
   * @param omitGenericTerms
   *        the omitGenericTerms to set
   */
  public void setOmitGenericTerms(boolean omitGenericTerms) {
    this.omitGenericTerms = omitGenericTerms;
  }


  /**
   * @param fluxCoefficients
   */
  public void setFluxCoefficients(double[] fluxCoefficients) {
    this.fluxCoefficients = fluxCoefficients;
  }


  /**
   * @param fluxObjectives
   */
  public void setFluxObjectives(String[] fluxObjectives) {
    this.fluxObjectives = fluxObjectives;
  }
}
