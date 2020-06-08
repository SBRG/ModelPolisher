package edu.ucsd.sbrg.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import edu.ucsd.sbrg.parsers.json.JSONParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sbml.jsbml.AbstractSBase;
import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Parameter;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.SpeciesReference;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCReactionPlugin;
import org.sbml.jsbml.ext.fbc.FBCSpeciesPlugin;
import org.sbml.jsbml.ext.groups.GroupsConstants;
import org.sbml.jsbml.ext.groups.GroupsModelPlugin;
import org.sbml.jsbml.util.ModelBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.sbrg.bigg.BiGGId;
import edu.ucsd.sbrg.parsers.json.models.Compartments;
import edu.ucsd.sbrg.parsers.json.models.Metabolite;
import edu.ucsd.sbrg.parsers.json.models.Reaction;

public class JSONParserTest {

  private static ModelBuilder builder;
  private static final int LEVEL = 3;
  private static final int VERSION = 1;
  // TODO: add test for compartment parsing

  @BeforeEach
  public void setUp() {
    builder = new ModelBuilder(LEVEL, VERSION);
  }


  @Test
  public void parseCompartmentsTest() {
    String compartmentsJSON = "{\n" + "\"\":\"\",\n" + "\"c\":\"cytoplasm\",\n" + "\"C_c\":\"cytoplasm\",\n"
      + "\"e\":\"extracellular\",\n" + "\"w\":\"cell wall\"\n" + "}\n";
    ObjectMapper mapper = new ObjectMapper();
    Compartments compartments = null;
    try {
      compartments = mapper.readValue(compartmentsJSON, Compartments.class);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    assertNotNull(compartments);
    JSONParser parser = new JSONParser();
    parser.parseCompartments(builder, compartments.get());
    Model model = builder.getModel();
    assertEquals(3, model.getListOfCompartments().size());
    Compartment compartment = model.getCompartment("c");
    assertNotNull(compartment);
    assertEquals("cytoplasm", compartment.getName());
    compartment = model.getCompartment("e");
    assertNotNull(compartment);
    assertEquals("extracellular", compartment.getName());
    compartment = model.getCompartment("w");
    assertNotNull(compartment);
    assertEquals("cell wall", compartment.getName());
  }


  @Test
  public void parseGeneTest() {
    // TODO: implement test
  }


  @Test
  public void parseReactionTest() {
    String reactionJSON = "{\n" + "\"id\":\"FAH1\",\n" + "\"name\":\"Fatty acid omega-hydroxylase\",\n"
      + "\"metabolites\":{\n" + "\"ddca_c\":-1.0,\n" + "\"h2o_c\":1.0,\n" + "\"h_c\":-1.0,\n" + "\"nadp_c\":1.0,\n"
      + "\"nadph_c\":-1.0,\n" + "\"o2_c\":-1.0,\n" + "\"whddca_c\":1.0\n" + "},\n" + "\"lower_bound\":1.0,\n"
      + "\"upper_bound\":10.0,\n"
      + "\"gene_reaction_rule\":\"( 100767149 or 100767921 or 100768211 or 100768783 or 100769255 or 100755384 or 100773614 or 100750743 ) and 100689241\",\n"
      + "\"subsystem\":\"dummy\"\n" + "}";
    ObjectMapper mapper = new ObjectMapper();
    Reaction reaction = null;
    try {
      reaction = mapper.readValue(reactionJSON, Reaction.class);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    assertNotNull(reaction);
    JSONParser parser = new JSONParser();
    BiGGId.createReactionId(reaction.getId()).ifPresentOrElse(id -> assertEquals("R_FAH1", id.toBiGGId()),
      Assertions::fail);
    parser.parseReaction(builder, reaction, "R_FAH1");
    // Needs to be fetched no matter what, checking for id equality is thus already done here indirectly
    org.sbml.jsbml.Reaction r = builder.getModel().getReaction("R_FAH1");
    assertNotNull(r);
    assertEquals("Fatty acid omega-hydroxylase", r.getName());
    FBCReactionPlugin fbc = (FBCReactionPlugin) r.getPlugin(FBCConstants.shortLabel);
    Parameter lb = fbc.getLowerFluxBoundInstance();
    assertNotNull(lb);
    assertEquals(1d, lb.getValue());
    Parameter ub = fbc.getUpperFluxBoundInstance();
    assertNotNull(ub);
    assertEquals(10d, ub.getValue());
    assertTrue(fbc.isSetGeneProductAssociation());
    assertFalse(r.isSetAnnotation());
    assertFalse(r.isSetNotes());
    List<SpeciesReference> reactants = new ArrayList<>();
    SpeciesReference sr = new SpeciesReference(LEVEL, VERSION);
    sr.setSpecies("M_ddca_c");
    sr.setStoichiometry(1d);
    sr.setConstant(true);
    reactants.add(sr);
    sr = new SpeciesReference(LEVEL, VERSION);
    sr.setSpecies("M_h_c");
    sr.setStoichiometry(1d);
    sr.setConstant(true);
    reactants.add(sr);
    sr = new SpeciesReference(LEVEL, VERSION);
    sr.setSpecies("M_nadph_c");
    sr.setStoichiometry(1d);
    sr.setConstant(true);
    reactants.add(sr);
    sr = new SpeciesReference(LEVEL, VERSION);
    sr.setSpecies("M_o2_c");
    sr.setStoichiometry(1d);
    sr.setConstant(true);
    reactants.add(sr);
    assertEquals(r.getNumReactants(), reactants.size());
    assertTrue(r.getListOfReactants().containsAll(reactants));
    List<SpeciesReference> products = new ArrayList<>();
    sr = new SpeciesReference(LEVEL, VERSION);
    sr.setSpecies("M_h2o_c");
    sr.setStoichiometry(1d);
    sr.setConstant(true);
    products.add(sr);
    sr = new SpeciesReference(LEVEL, VERSION);
    sr.setSpecies("M_nadp_c");
    sr.setStoichiometry(1d);
    sr.setConstant(true);
    products.add(sr);
    sr = new SpeciesReference(LEVEL, VERSION);
    sr.setSpecies("M_whddca_c");
    sr.setStoichiometry(1d);
    sr.setConstant(true);
    products.add(sr);
    assertEquals(r.getNumProducts(), products.size());
    assertTrue(r.getListOfProducts().containsAll(products));
    GroupsModelPlugin groupsModelPlugin = (GroupsModelPlugin) builder.getModel().getPlugin(GroupsConstants.shortLabel);
    List<String> groupNames =
      groupsModelPlugin.getListOfGroups().stream().map(AbstractSBase::getName).collect(Collectors.toList());
    assertEquals(1, groupNames.size());
    assertTrue(groupNames.contains("dummy"));
    // TODO: add edge case
  }


  @Test
  public void parseSpeciesTest() {
    String speciesJSON = "{\n" + "  \"id\" : \"amp_e\",\n" + "  \"name\" : \"AMP\",\n" + "  \"compartment\" : \"e\",\n"
      + "  \"charge\" : -2,\n" + "  \"formula\" : \"C10H12N5O7P\"\n" + "}\n";
    ObjectMapper mapper = new ObjectMapper();
    Metabolite metabolite = null;
    try {
      metabolite = mapper.readValue(speciesJSON, Metabolite.class);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    assertNotNull(metabolite);
    BiGGId.createMetaboliteId(metabolite.getId()).ifPresentOrElse(id -> assertEquals("M_amp_e", id.toBiGGId()),
      Assertions::fail);
    JSONParser parser = new JSONParser();
    parser.parseMetabolite(builder.getModel(), metabolite, BiGGId.createMetaboliteId(metabolite.getId()).get());
    Species species = builder.getModel().getSpecies("M_amp_e");
    assertNotNull(species);
    assertEquals("AMP", metabolite.getName());
    assertEquals("e", metabolite.getCompartment());
    FBCSpeciesPlugin fbc = (FBCSpeciesPlugin) species.getPlugin(FBCConstants.shortLabel);
    assertEquals(-2d, fbc.getCharge());
    assertEquals("C10H12N5O7P", fbc.getChemicalFormula());
    assertFalse(species.isSetAnnotation());
    assertFalse(species.isSetNotes());
    // TODO: add edge cases
  }
}
