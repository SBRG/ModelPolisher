package edu.ucsd.sbrg.bigg.annotation;

import de.zbit.util.ResourceManager;
import de.zbit.util.Utils;
import edu.ucsd.sbrg.bigg.BiGGId;
import edu.ucsd.sbrg.bigg.Parameters;
import edu.ucsd.sbrg.bigg.SBMLPolisher;
import edu.ucsd.sbrg.db.BiGGDB;
import edu.ucsd.sbrg.db.QueryOnce;
import org.sbml.jsbml.CVTerm.Qualifier;
import org.sbml.jsbml.SBO;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCSpeciesPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static edu.ucsd.sbrg.bigg.BiGGAnnotation.getBiGGIdFromResources;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.TYPE_SPECIES;
import static java.text.MessageFormat.format;

public class SpeciesAnnotation extends CVTermAnnotation {

  /**
   * A {@link Logger} for this class.
   */
  static final transient Logger logger = Logger.getLogger(SpeciesAnnotation.class.getName());
  /**
   * Localization support.
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   * Instance of chemical species to annotate
   */
  private final Species species;

  public SpeciesAnnotation(Species species) {
    this.species = species;
  }


  /**
   * Annotates given species with different information from BiGG Knowledgebase.
   * Sets a name, if possible, if none is set the name corresponds to the species BiGGId.
   * Adds chemical formula for the species.
   **/
  @Override
  public void annotate() {
    // This biggId corresponds to BiGGId calculated from getSpeciesBiGGIdFromUriList method, if not present as
    // species.id
    checkId().ifPresent(biggId -> {
      setName(biggId);
      setSBOTerm(biggId);
      addAnnotations(biggId);
      FBCSetFormulaCharge(biggId);
    });
  }


  /**
   * Checks if {@link Species#getId()} returns a correct {@link BiGGId} and tries to retrieve a corresponding
   * {@link BiGGId} based on annotations present.
   *
   * @return If creation was successful, internal ModelPolisher internal BiGGId representation wrapped in an Optional is
   *         returned, else Optional.empty() is returned
   */
  @Override
  public Optional<BiGGId> checkId() {
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
    // Create BiGGId from retrieved id or return BiGGId constructed for original id
    return id.map(BiGGId::createMetaboliteId).orElse(metaboliteId);
  }


  /**
   * Set species name from BiGG Knowledgebase if name is not yet set or corresponds to the species id.
   * Depends on the presence of the BiGGId in BiGG
   *
   * @param biggId:
   *        {@link BiGGId} constructed from the species id
   */
  public void setName(BiGGId biggId) {
    if (!species.isSetName()
      || species.getName().equals(format("{0}_{1}", biggId.getAbbreviation(), biggId.getCompartmentCode()))) {
      BiGGDB.getComponentName(biggId).map(SBMLPolisher::polishName).ifPresent(species::setName);
    }
  }


  /**
   * Set SBO terms for species, depending on its component type, i.e. metabolite, protein or a generic material entity.
   * Annotation for the last case is only written, if {@link Parameters#omitGenericTerms()} returns {@code false}.
   * If no component type can be retrieved from BiGG, annotation with material entity can still be performed
   *
   * @param biggId:
   *        {@link BiGGId} constructed from the species id
   */
  private void setSBOTerm(BiGGId biggId) {
    Parameters parameters = Parameters.get();
    BiGGDB.getComponentType(biggId).ifPresentOrElse(type -> {
      switch (type) {
      case "metabolite":
        species.setSBOTerm(SBO.getSimpleMolecule());
        break;
      case "protein":
        species.setSBOTerm(SBO.getProtein());
        break;
      default:
        if (!parameters.omitGenericTerms()) {
          species.setSBOTerm(SBO.getMaterialEntity());
        }
        break;
      }
    }, () -> {
      if (!parameters.omitGenericTerms()) {
        species.setSBOTerm(SBO.getMaterialEntity());
      }
    });
  }


  /**
   * Add annotations for species based on {@link BiGGId}, update http to https for MIRIAM URIs and merge duplicates
   *
   * @param biggId:
   *        {@link BiGGId} from species id
   */
  @Override
  public void addAnnotations(BiGGId biggId) {
    addAnnotations(species, biggId);
  }


  /**
   * Tries to set chemical formula and charge for the given species
   *
   * @param biggId:
   *        {@link BiGGId} from species id
   */
  @SuppressWarnings("deprecation")
  private void FBCSetFormulaCharge(BiGGId biggId) {
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
          logger.severe(format(MESSAGES.getString("CHEM_FORMULA_INVALID"), Utils.getMessage(exc)));
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
        format(MESSAGES.getString("CHARGE_CONTRADICTION"), charge, species.getCharge(), species.getId())));
      species.unsetCharge();
    }
    chargeFromBiGG.filter(charge -> charge != 0).ifPresent(fbcSpecPlug::setCharge);
  }
}
