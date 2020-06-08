package edu.ucsd.sbrg.parsers.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import edu.ucsd.sbrg.parsers.json.models.Gene;
import edu.ucsd.sbrg.parsers.json.models.Metabolite;
import edu.ucsd.sbrg.parsers.json.models.Metabolites;
import org.sbml.jsbml.Parameter;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCReactionPlugin;
import org.sbml.jsbml.ext.fbc.FBCSpeciesPlugin;
import org.sbml.jsbml.ext.fbc.GeneProduct;

import java.util.logging.Logger;

public class JSONwriter {

  private static final Logger logger = Logger.getLogger(JSONwriter.class.getName());

  /**
   * @param g
   */
  public static void geneToJSON(GeneProduct g) {
    Gene gene = new Gene();
    gene.setId(g.getId());
    gene.setName(g.getName());
    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    try {
      logger.info(String.format("GENE JSON:\n%s\n", mapper.writeValueAsString(gene)));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }


  /**
   * @param r:
   *        SBML Reaction
   */
  public static void reactionToJSON(Reaction r) {
    edu.ucsd.sbrg.parsers.json.models.Reaction reaction = new edu.ucsd.sbrg.parsers.json.models.Reaction();
    reaction.setId(r.getId());
    reaction.setName(r.getName());
    Metabolites metabolites = new Metabolites();
    r.getListOfReactants().forEach(reference -> metabolites.add(reference.getSpecies(),
      reference.getStoichiometry() < 0 ? reference.getStoichiometry() * -1 : reference.getStoichiometry()));
    r.getListOfProducts().forEach(reference -> metabolites.add(reference.getSpecies(), reference.getStoichiometry()));
    reaction.setMetabolites(metabolites);
    FBCReactionPlugin fbc = (FBCReactionPlugin) r.getPlugin(FBCConstants.shortLabel);
    // dummy value
    reaction.setGeneReactionRule("");
    boolean reversible = r.isReversible();
    Parameter lb = fbc.getLowerFluxBoundInstance();
    double lbVal;
    if(reversible){
      lbVal = -1000d;
    }else{
      lbVal = 0d;
    }
    if(lb != null){
      lbVal = lb.getValue();
    }
    reaction.setLowerBound(lbVal);
    Parameter ub = fbc.getUpperFluxBoundInstance();
    double ubVal = 1000;
    if(ub != null){
      ubVal = ub.getValue();
    }
    reaction.setUpperBound(ubVal);
    // dummy value
    reaction.setObjectiveCoefficient(0);
    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    try {
      logger.info(String.format("REACTION JSON:\n%s\n", mapper.writeValueAsString(reaction)));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }


  /**
   * @param species:
   *        SBML Species
   */
  public static void speciesToJSON(Species species) {
    Metabolite metabolite = new Metabolite();
    metabolite.setId(species.getId());
    metabolite.setName(species.getName());
    metabolite.setCompartment(species.getCompartment());
    FBCSpeciesPlugin fbc = (FBCSpeciesPlugin) species.getPlugin(FBCConstants.shortLabel);
    if (fbc.isSetCharge()) {
      metabolite.setCharge(fbc.getCharge());
    }
    if (fbc.isSetChemicalFormula()) {
      metabolite.setFormula(fbc.getChemicalFormula());
    }
    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    try {
      logger.info(String.format("SPECIES JSON:\n%s\n", mapper.writeValueAsString(metabolite)));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }
}
