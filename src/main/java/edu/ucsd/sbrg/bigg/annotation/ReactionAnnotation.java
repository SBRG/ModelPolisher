package edu.ucsd.sbrg.bigg.annotation;

import de.zbit.util.ResourceManager;
import edu.ucsd.sbrg.bigg.BiGGId;
import edu.ucsd.sbrg.bigg.Parameters;
import edu.ucsd.sbrg.bigg.polishing.SBMLPolisher;
import edu.ucsd.sbrg.db.BiGGDB;
import edu.ucsd.sbrg.db.QueryOnce;
import edu.ucsd.sbrg.util.GPRParser;
import edu.ucsd.sbrg.util.SBMLUtils;
import org.sbml.jsbml.CVTerm.Qualifier;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.ext.groups.Group;
import org.sbml.jsbml.ext.groups.GroupsConstants;
import org.sbml.jsbml.ext.groups.GroupsModelPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static edu.ucsd.sbrg.bigg.annotation.BiGGAnnotation.getBiGGIdFromResources;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.TYPE_REACTION;

public class ReactionAnnotation extends CVTermAnnotation {

  /**
   * A {@link Logger} for this class.
   */
  static final transient Logger logger = Logger.getLogger(ReactionAnnotation.class.getName());
  /**
   * Localization support.
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   * Instance of reaction to annotate
   */
  private final Reaction reaction;
  private static boolean triggeredSubsystemWarning = false;

  /**
   * @param reaction
   */
  public ReactionAnnotation(Reaction reaction) {
    this.reaction = reaction;
  }


  /**
   * Adds annotations to a reaction, parses associated gene reaction rules and creates missing gene products and
   * converts subsystem information into corresponding groups
   */
  @Override
  public void annotate() {
    // This biggId corresponds to BiGGId calculated from getSpeciesBiGGIdFromUriList method, if not present as
    // reaction.id
    checkId().ifPresent(biggId -> {
      setName(biggId);
      setSBOTerm(biggId);
      addAnnotations(biggId);
      parseGeneReactionRules(biggId);
      parseSubsystems(biggId);
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
    String id = reaction.getId();
    // extracting BiGGId if not present for species
    boolean isBiGGid = id.matches("^(R_)?([a-zA-Z][a-zA-Z0-9_]+)(?:_([a-z][a-z0-9]?))?(?:_([A-Z][A-Z0-9]?))?$")
      && QueryOnce.isReaction(id);
    if (!isBiGGid) {
      // Flatten all resources for all CVTerms into a list
      List<String> resources =
        reaction.getAnnotation().getListOfCVTerms().stream().filter(cvTerm -> cvTerm.getQualifier() == Qualifier.BQB_IS)
                .flatMap(term -> term.getResources().stream()).collect(Collectors.toList());
      if (!resources.isEmpty()) {
        // update id if we found something
        id = getBiGGIdFromResources(resources, TYPE_REACTION).orElse(id);
      }
    }
    return BiGGId.createReactionId(id);
  }


  /**
   * @param biggId
   */
  public void setName(BiGGId biggId) {
    String abbreviation = biggId.getAbbreviation();
    BiGGDB.getReactionName(abbreviation).filter(name -> !name.equals(reaction.getName())).map(SBMLPolisher::polishName)
          .ifPresent(reaction::setName);
  }


  /**
   * @param biggId
   */
  public void setSBOTerm(BiGGId biggId) {
    String abbreviation = biggId.getAbbreviation();
    Parameters parameters = Parameters.get();
    if (!reaction.isSetSBOTerm()) {
      if (BiGGDB.isPseudoreaction(abbreviation)) {
        reaction.setSBOTerm(631);
      } else if (!parameters.omitGenericTerms()) {
        reaction.setSBOTerm(375); // generic process
      }
    }
  }


  /**
   * Add annotations for reaction based on {@link BiGGId}, update http to https for MIRIAM URIs and merge duplicates
   *
   * @param biggId:
   *        {@link BiGGId} from reaction id
   */
  @Override
  public void addAnnotations(BiGGId biggId) {
    addAnnotations(reaction, biggId);
  }


  /**
   * @param biggId
   */
  public void parseGeneReactionRules(BiGGId biggId) {
    String abbreviation = biggId.getAbbreviation();
    Parameters parameters = Parameters.get();
    List<String> geneReactionRules = BiGGDB.getGeneReactionRule(abbreviation, reaction.getModel().getId());
    for (String geneRactionRule : geneReactionRules) {
      GPRParser.parseGPR(reaction, geneRactionRule, parameters.omitGenericTerms());
    }
  }


  /**
   * Retrieve subsystem information from BiGG Knowledgebase and convert subsystem information to corresponding group
   * using {@link GroupsModelPlugin} and link reaction to corresponding group
   *
   * @param biggId:
   *        {@link BiGGId} from reaction id
   */
  private void parseSubsystems(BiGGId biggId) {
    Model model = reaction.getModel();
    boolean isBiGGModel = QueryOnce.isModel(model.getId());
    List<String> subsystems;
    if (isBiGGModel) {
      subsystems = BiGGDB.getSubsystems(model.getId(), biggId.getAbbreviation());
    } else {
      if (!triggeredSubsystemWarning) {
        triggeredSubsystemWarning = true;
        logger.warning(MESSAGES.getString("SUBSYSTEM_MODEL_NOT_BIGG"));
      }
      subsystems = BiGGDB.getSubsystemsForReaction(biggId.getAbbreviation());
    }
    if (subsystems.size() < 1) {
      return;
    } else {
      // filter out duplicates only differing in case - relevant for #getSubsystemsForReaction results
      subsystems = subsystems.stream().map(String::toLowerCase).distinct().collect(Collectors.toList());
      // Code already allows for multiple results from one query. If we have no BiGG model id, this might lead to
      // ambiguous, incorrect results
      if (!isBiGGModel && subsystems.size() > 1) {
        return;
      }
    }
    String groupKey = "GROUP_FOR_NAME";
    if (model.getUserObject(groupKey) == null) {
      model.putUserObject(groupKey, new HashMap<String, Group>());
    }
    @SuppressWarnings("unchecked")
    Map<String, Group> groupForName = (Map<String, Group>) model.getUserObject(groupKey);
    for (String subsystem : subsystems) {
      Group group;
      if (groupForName.containsKey(subsystem)) {
        group = groupForName.get(subsystem);
      } else {
        GroupsModelPlugin groupsModelPlugin = (GroupsModelPlugin) model.getPlugin(GroupsConstants.shortLabel);
        group = groupsModelPlugin.createGroup("g" + (groupsModelPlugin.getGroupCount() + 1));
        group.setName(subsystem);
        group.setKind(Group.Kind.partonomy);
        group.setSBOTerm(633); // subsystem
        groupForName.put(subsystem, group);
      }
      SBMLUtils.createSubsystemLink(reaction, group.createMember());
    }
  }
}
