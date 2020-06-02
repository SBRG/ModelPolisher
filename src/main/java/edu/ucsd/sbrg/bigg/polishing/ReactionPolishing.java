package edu.ucsd.sbrg.bigg.polishing;

import de.zbit.kegg.AtomBalanceCheck;
import de.zbit.kegg.AtomBalanceCheck.AtomCheckResult;
import edu.ucsd.sbrg.bigg.BiGGId;
import edu.ucsd.sbrg.bigg.Parameters;
import edu.ucsd.sbrg.util.GPRParser;
import org.sbml.jsbml.KineticLaw;
import org.sbml.jsbml.ListOf;
import org.sbml.jsbml.LocalParameter;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Parameter;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBO;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.SpeciesReference;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCModelPlugin;
import org.sbml.jsbml.ext.fbc.FBCReactionPlugin;
import org.sbml.jsbml.ext.fbc.FluxObjective;
import org.sbml.jsbml.ext.fbc.Objective;
import org.sbml.jsbml.util.ResourceManager;
import org.sbml.jsbml.xml.XMLNode;

import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static edu.ucsd.sbrg.bigg.ModelPolisher.MESSAGES;
import static java.text.MessageFormat.format;

public class ReactionPolishing {

  private final static transient Logger logger = Logger.getLogger(ReactionPolishing.class.getName());
  private final Reaction reaction;

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

    private final Pattern pattern;

    Patterns(String regex) {
      pattern = Pattern.compile(regex);
    }


    public Pattern getPattern() {
      return pattern;
    }
  }

  public ReactionPolishing(Reaction reaction) {
    this.reaction = reaction;
  }


  /**
   * @return {@code true} if the given reaction qualifies for strict FBC.
   */
  @SuppressWarnings("deprecated")
  public boolean polish() {
    String id = reaction.getId();
    if (id.isEmpty()) {
      // remove species with missing id, produces invalid SBML
      if (reaction.isSetName()) {
        logger.severe(format(
          "Removing reaction '{0}' due to missing id. Check your Model for entries missing the id attribute or duplicates.",
          reaction.getName()));
      } else {
        logger.severe("Removing reaction with missing id and name. Check your Model for reaction without id and name.");
      }
      reaction.getModel().removeReaction(reaction);
      return false;
    }
    BiGGId.createReactionId(id).ifPresent(this::setSBOTermFromPattern);
    // TODO: this is confusing, as null is used to represent two different outcomes...
    String compartmentId = reaction.isSetCompartment() ? reaction.getCompartment() : null;
    boolean conflict = false;
    if (reaction.isSetListOfReactants()) {
      // Polishing of speciesReferences is a side effect here, which is ... well, not optimal
      Optional<String> cIdFromReactants = polish(reaction.getListOfReactants(), SBO.getReactant());
      conflict = cIdFromReactants.isEmpty();
      // only set compartment code if all source agree
      if (!conflict && (compartmentId == null || compartmentId.equals(cIdFromReactants.get()))) {
        reaction.setCompartment(cIdFromReactants.get());
      }
    }
    if (reaction.isSetListOfProducts()) {
      Optional<String> cIdFromProducts = polish(reaction.getListOfProducts(), SBO.getProduct());
      conflict |= cIdFromProducts.isEmpty();
      // only set compartment code if all source agree, else unset
      if (!conflict && (compartmentId == null || compartmentId.equals(cIdFromProducts.get()))) {
        reaction.setCompartment(cIdFromProducts.get());
      } else {
        reaction.unsetCompartment();
      }
    }
    if (!reaction.isSetMetaId() && (reaction.getCVTermCount() > 0)) {
      reaction.setMetaId(reaction.getId());
    }
    String rName = reaction.getName();
    if (rName.matches(".*_copy\\d*")) {
      rName = rName.substring(0, rName.lastIndexOf('_'));
      reaction.setName(rName);
    }
    if (!reaction.isSetFast()) {
      reaction.setFast(false);
    }
    if (!reaction.isSetReversible()) {
      reaction.setReversible(false);
    }
    // This is a check if we are producing invalid SBML.
    if ((reaction.getReactantCount() == 0) && (reaction.getProductCount() == 0)) {
      ResourceBundle bundle = ResourceManager.getBundle("org.sbml.jsbml.resources.cfg.Messages");
      logger.severe(format(bundle.getString("SBMLCoreParser.reactionWithoutParticipantsError"), reaction.getId()));
    } else {
      checkBalance();
    }
    // bounds cannot be fetched, if no model exists, thus for such cases the default should be false
    boolean strict = false;
    // only run when model is present, as this code either depends on the model or has side effects creating children
    // objects for the model
    if (reaction.getModel() != null) {
      GPRParser.convertAssociationsToFBCV2(reaction, Parameters.get().omitGenericTerms());
      fluxObjectiveFromLocalParameter();
      associationFromNotes();
      strict = checkBounds();
    }
    strict = checkReactantsProducts(strict);
    return strict;
  }


  /**
   * @param id
   * @return
   */
  private void setSBOTermFromPattern(BiGGId id) {
    String abbrev = id.getAbbreviation();
    if (Patterns.BIOMASS_CASE_INSENSITIVE.getPattern().matcher(abbrev).matches()) {
      reaction.setSBOTerm(629); // biomass production
    } else if (Patterns.DEMAND_REACTION.getPattern().matcher(abbrev).matches()) {
      reaction.setSBOTerm(628); // demand reaction
    } else if (Patterns.EXCHANGE_REACTION.getPattern().matcher(abbrev).matches()) {
      reaction.setSBOTerm(627); // exchange reaction
    } else if (Patterns.ATP_MAINTENANCE.getPattern().matcher(abbrev).matches()) {
      reaction.setSBOTerm(630); // ATP maintenance
    } else if (Patterns.SINK_REACTION.getPattern().matcher(abbrev).matches()) {
      reaction.setSBOTerm(632);
    }
  }


  /**
   * Polishes {@link SpeciesReference}s, i.e. reactants or products and tries to retrieve a compartment code for the
   * reaction,
   * if it can be resolved unambiguously from the references
   *
   * @param speciesReferences:
   *        List of reactants or products
   * @param defaultSBOterm:
   *        reactant or product SBO term
   * @return {@link Optional#empty()} if compartment was not set for one of the species or could not be resolved
   *         unambiguously, else {@link Optional#of}, where the wrapped string is the compartment code
   */
  private Optional<String> polish(ListOf<SpeciesReference> speciesReferences, int defaultSBOterm) {
    Optional<String> compartmentId = Optional.empty();
    Model model = speciesReferences.getModel();
    for (SpeciesReference sr : speciesReferences) {
      if (!sr.isSetSBOTerm() && !Parameters.get().omitGenericTerms()) {
        sr.setSBOTerm(defaultSBOterm);
      }
      if (!sr.isSetConstant()) {
        sr.setConstant(false);
      }
      if (model == null) {
        continue;
      }
      Species species = model.getSpecies(sr.getSpecies());
      if (species != null) {
        // Assumed intention here, that conflicting compartment information cannot be resolved
        if (!species.isSetCompartment()
          || compartmentId.map(id -> !id.equals(species.getCompartment())).orElse(false)) {
          return compartmentId;
        } else {
          compartmentId = Optional.of(species.getCompartment());
        }
      } else {
        logger.info(format(MESSAGES.getString("SPECIES_REFERENCE_INVALID"), sr.getSpecies()));
      }
    }
    return compartmentId;
  }


  /**
   *
   */
  private void checkBalance() {
    // TODO: change messages
    if (!reaction.isSetSBOTerm()) {
      // The reaction has not been recognized as demand or exchange reaction
      if (reaction.getReactantCount() == 0) {
        // fixme: Messages are wrong
        if (reaction.isReversible()) {
          // TODO: sink reaction
        } else if (reaction.getSBOTerm() != 628) {
          // logger.info(format(mpMessageBundle.getString("REACTION_DM_NOT_IN_ID"), r.getId()));
          reaction.setSBOTerm(628); // demand reaction
        }
      } else if (reaction.getProductCount() == 0) {
        if (reaction.isReversible()) {
          // TODO: source reaction
        } else {
          // logger.warning(format(mpMessageBundle.getString("REACTION_DM_NOT_IN_ID"), r.getId()));
          reaction.setSBOTerm(628); // demand reaction
        }
      }
    }
    if (Parameters.get().checkMassBalance() && ((reaction.getSBOTerm() < 627) || (630 < reaction.getSBOTerm()))) {
      // check atom balance only if the reaction is not identified as biomass
      // production, demand, exchange or ATP maintenance.
      AtomCheckResult<Reaction> defects = AtomBalanceCheck.checkAtomBalance(reaction, 1);
      if ((defects != null) && (defects.hasDefects())) {
        logger.warning(format(MESSAGES.getString("ATOMS_MISSING"), reaction.getId(), defects.getDefects().toString()));
      } else if (defects == null) {
        logger.fine(format(MESSAGES.getString("CHECK_ATOM_BALANCE_FAILED"), reaction.getId()));
      } else {
        logger.fine(format(MESSAGES.getString("ATOMS_OK"), reaction.getId()));
      }
    }
  }


  /**
   * Set flux objective and its coefficient from reaction kinetic law, if no flux objective exists for the reaction
   */
  private void fluxObjectiveFromLocalParameter() {
    FBCModelPlugin modelPlugin = (FBCModelPlugin) reaction.getModel().getPlugin(FBCConstants.shortLabel);
    Objective obj = modelPlugin.getObjective(0);
    if (obj == null) {
      obj = modelPlugin.createObjective("obj");
      obj.setType(Objective.Type.MAXIMIZE);
      modelPlugin.getListOfObjectives().setActiveObjective(obj.getId());
    }
    boolean foExists = obj.getListOfFluxObjectives().stream().anyMatch(fo -> fo.getReactionInstance().equals(reaction));
    if (foExists) {
      return;
    }
    KineticLaw kl = reaction.getKineticLaw();
    if (kl != null) {
      LocalParameter coefficient = kl.getLocalParameter("OBJECTIVE_COEFFICIENT");
      if (coefficient != null && coefficient.getValue() != 0d) {
        FluxObjective fo = obj.createFluxObjective("fo_" + reaction.getId());
        fo.setCoefficient(coefficient.getValue());
        fo.setReaction(reaction);
      }
    }
  }


  /**
   * Convert GENE_ASSOCIATION in reaction notes to FBCv2 {#GeneProductAssociation}
   */
  private void associationFromNotes() {
    FBCReactionPlugin reactionPlugin = (FBCReactionPlugin) reaction.getPlugin(FBCConstants.shortLabel);
    if (!reactionPlugin.isSetGeneProductAssociation() && reaction.isSetNotes()) {
      XMLNode body = reaction.getNotes().getChildElement("body", null);
      if (body != null) {
        for (XMLNode p : body.getChildElements("p", null)) {
          if (p.getChildCount() == 1) {
            String associationCandidate = p.getChildAt(0).getCharacters();
            if (associationCandidate.startsWith("GENE_ASSOCIATION: ")) {
              String[] splits = associationCandidate.split("GENE_ASSOCIATION: ");
              if (splits.length == 2) {
                String association = splits[1];
                if (!association.isEmpty()) {
                  GPRParser.parseGPR(reaction, association, Parameters.get().omitGenericTerms());
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
   * @return
   */
  private boolean checkBounds() {
    FBCReactionPlugin rPlug = (FBCReactionPlugin) reaction.getPlugin(FBCConstants.shortLabel);
    Parameter lb = rPlug.getLowerFluxBoundInstance();
    Parameter ub = rPlug.getUpperFluxBoundInstance();
    boolean lbExists = polishFluxBound(lb);
    // set bounds from KineticLaw, if they are not set in FBC, create global Parameter, as required by specification
    if (!lbExists) {
      LocalParameter bound = getBoundFromLocal(reaction, "LOWER_BOUND");
      if (bound != null) {
        lb = new Parameter(bound);
        Parameter existingParameter = getParameterVariant(reaction, lb, bound.getValue());
        if (existingParameter != null) {
          rPlug.setLowerFluxBound(existingParameter);
        } else {
          reaction.getModel().addParameter(lb);
          rPlug.setLowerFluxBound(lb);
        }
        lbExists = polishFluxBound(rPlug.getLowerFluxBoundInstance());
      }
    }
    boolean ubExists = polishFluxBound(ub);
    if (!ubExists) {
      LocalParameter bound = getBoundFromLocal(reaction, "UPPER_BOUND");
      if (bound != null) {
        ub = new Parameter(bound);
        Parameter existingParameter = getParameterVariant(reaction, ub, bound.getValue());
        if (existingParameter != null) {
          rPlug.setUpperFluxBound(existingParameter);
        } else {
          reaction.getModel().addParameter(ub);
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
        logger.warning(format(MESSAGES.getString("FLUX_BOUND_ERROR"), reaction.getId()));
      }
    } else {
      logger.warning(format(MESSAGES.getString("FLUX_BOUNDS_MISSING"), reaction.getId()));
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
   * @param strict
   * @return
   */
  private boolean checkReactantsProducts(boolean strict) {
    if (strict && reaction.isSetListOfReactants()) {
      strict = checkSpeciesReferences(reaction.getListOfReactants());
      if (!strict) {
        logger.warning(format(MESSAGES.getString("ILLEGAL_STOICH_REACT"), reaction.getId()));
      }
    }
    if (strict && reaction.isSetListOfProducts()) {
      strict = checkSpeciesReferences(reaction.getListOfProducts());
      if (!strict) {
        logger.warning(format(MESSAGES.getString("ILLEGAL_STOICH_PROD"), reaction.getId()));
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
}
