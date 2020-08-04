package edu.ucsd.sbrg.bigg.polishing;

import static java.text.MessageFormat.format;

import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.SBase;
import org.sbml.jsbml.Species;

import de.zbit.util.ResourceManager;
import edu.ucsd.sbrg.bigg.BiGGId;

public class SpeciesPolishing {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(SpeciesPolishing.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   *
   */
  private final Species species;

  public SpeciesPolishing(Species species) {
    this.species = species;
  }


  /**
   * @return {@link Optional} containing a {@link Species} that should be removed from the model due to missing id
   */
  public Optional<Species> polish() {
    String id = species.getId();
    if (id.isEmpty()) {
      // remove species with missing id, produces invalid SBML
      if (species.isSetName()) {
        logger.severe(format(MESSAGES.getString("SPECIES_MISSING_ID"), species.getName()));
      } else {
        logger.severe(MESSAGES.getString("SPECIES_MISSING_ID_NAME"));
      }
      return Optional.of(species);
    }
    // TODO: this is likely not correct, something should be done with this species id
    if (species.getId().endsWith("_boundary")) {
      logger.warning(format(MESSAGES.getString("SPECIES_ID_INVALID"), id));
      if (!species.isSetBoundaryCondition() || !species.isBoundaryCondition()) {
        logger.warning(format(MESSAGES.getString("BOUNDARY_FLAG_MISSING"), id));
        species.setBoundaryCondition(true);
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
   * @param species
   */
  public void checkCompartment(Species species) {
    if (!species.isSetCompartment()) {
      Optional<BiGGId> biggId = BiGGId.createMetaboliteId(species.getId());
      boolean setCompartment = false;
      if (biggId.isPresent()) {
        if (biggId.get().isSetCompartmentCode()) {
          species.setCompartment(biggId.get().getCompartmentCode());
          setCompartment = true;
        }
      }
      if (!setCompartment) {
        return;
      }
    }
    String cId = species.getCompartment();
    Model model = species.getModel();
    // We could polish a species without model
    if (model == null) {
      return;
    }
    SBase candidate = model.findUniqueNamedSBase(cId);
    if (candidate instanceof Compartment) {
      // compartment can't be null here, instanceof would evaluate to false
      CompartmentPolishing compartmentPolishing = new CompartmentPolishing((Compartment) candidate);
      compartmentPolishing.polish();
    } else if (candidate == null) {
      logger.warning(format(MESSAGES.getString("CREATE_MISSING_COMP"), cId, species.getId(), species.getElementName()));
      CompartmentPolishing compartmentPolishing = new CompartmentPolishing(model.createCompartment(cId));
      compartmentPolishing.polish();
    }
  }
}
