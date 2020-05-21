/**
 * 
 */
package edu.ucsd.sbrg.util;

import org.sbml.jsbml.ListOf;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.ext.fbc.Association;
import org.sbml.jsbml.ext.fbc.FBCModelPlugin;
import org.sbml.jsbml.ext.fbc.FluxObjective;
import org.sbml.jsbml.ext.fbc.GeneProduct;
import org.sbml.jsbml.ext.fbc.GeneProductAssociation;
import org.sbml.jsbml.ext.fbc.GeneProductRef;
import org.sbml.jsbml.ext.fbc.ListOfObjectives;
import org.sbml.jsbml.ext.fbc.LogicalOperator;
import org.sbml.jsbml.ext.fbc.Objective;
import org.sbml.jsbml.ext.groups.Member;

import javax.swing.tree.TreeNode;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static edu.ucsd.sbrg.bigg.ModelPolisher.MESSAGES;

/**
 * A collection of helpful functions for dealing with SBML data structures.
 * 
 * @author Andreas Dr&auml;ger
 */
public class SBMLUtils {

  /**
   * A {@link Logger} for this class.
   */
  private static final Logger logger = Logger.getLogger(SBMLUtils.class.getName());
  /**
   * Key to link from {@link Reaction} directly to {@link Member}s referencing
   * that reaction.
   */
  public static final String SUBSYSTEM_LINK = "SUBSYSTEM_LINK";
  /**
   * HashMap holding all gene product references in a model for updating
   */
  private static Map<String, GeneProductRef> geneProductReferences = new HashMap<>();

  /**
   * Apply updated GeneID to geneProductReferenece
   * 
   * @param gp
   */
  public static void updateGeneProductReference(GeneProduct gp) {
    if (geneProductReferences.isEmpty()) {
      initGPRMap(gp.getModel().getListOfReactions());
    }
    String id = gp.getId();
    if (id.startsWith("G_")) {
      id = id.split("G_")[1];
    }
    if (geneProductReferences.containsKey(id)) {
      GeneProductRef gpr = geneProductReferences.get(id);
      gpr.setGeneProduct(gp.getId());
    }
  }


  /**
   * @param reactions
   */
  private static void initGPRMap(ListOf<Reaction> reactions) {
    for (Reaction r : reactions) {
      for (int childIdx = 0; childIdx < r.getChildCount(); childIdx++) {
        TreeNode child = r.getChildAt(childIdx);
        if (child instanceof GeneProductAssociation) {
          Association association = ((GeneProductAssociation) child).getAssociation();
          if (association instanceof GeneProductRef) {
            GeneProductRef gpr = (GeneProductRef) association;
            geneProductReferences.put(gpr.getGeneProduct(), gpr);
          } else if (association instanceof LogicalOperator) {
            processNested(association);
          }
        }
      }
    }
  }


  /**
   * 
   */
  public static void cleanGPRMap() {
    geneProductReferences = new HashMap<>();
  }


  /**
   * @param association
   */
  private static void processNested(Association association) {
    for (int idx = 0; idx < association.getChildCount(); idx++) {
      TreeNode child = association.getChildAt(idx);
      if (child instanceof LogicalOperator) {
        processNested((Association) child);
      } else {
        // has to GeneProductReference
        GeneProductRef gpr = (GeneProductRef) child;
        geneProductReferences.put(gpr.getGeneProduct(), gpr);
      }
    }
  }


  /**
   * @param oldId
   * @param newId
   * @param fbcModelPlug
   */
  public static void updateReactionRef(String oldId, String newId, FBCModelPlugin fbcModelPlug) {
    if ((fbcModelPlug != null) && fbcModelPlug.isSetListOfObjectives()) {
      ListOfObjectives loo = fbcModelPlug.getListOfObjectives();
      for (Objective objective : loo) {
        if (objective.isSetListOfFluxObjectives()) {
          for (FluxObjective fo : objective.getListOfFluxObjectives()) {
            if (fo.getReaction().equals(oldId)) {
              fo.setReaction(newId);
            }
          }
        }
      }
    }
  }


  /**
   * @param r
   */
  @SuppressWarnings("deprecation")
  public static void setRequiredAttributes(Reaction r) {
    // TODO: make defaults user settings or take from L2V5.
    if (!r.isSetId()) {
      logger.severe(MessageFormat.format(MESSAGES.getString("ID_MISSING_FOR_TYPE"), r.getElementName()));
    }
    if (!r.isSetFast()) {
      r.setFast(false);
    }
    if (!r.isSetReversible()) {
      r.setReversible(false);
    }
    if (!r.isSetMetaId() && ((r.getCVTermCount() > 0) || r.isSetHistory())) {
      r.setMetaId(r.getId());
    }
    // TODO
    // if (!r.isSetSBOTerm()) {
    // r.setSBOTerm(SBO.getProcess());
    // }
  }


  /**
   * Add a direct link from the reaction to the member pointing to that
   * reaction.
   * 
   * @param r
   * @param member
   */
  @SuppressWarnings("unchecked")
  public static void createSubsystemLink(Reaction r, Member member) {
    member.setIdRef(r);
    if (r.getUserObject(SUBSYSTEM_LINK) == null) {
      r.putUserObject(SUBSYSTEM_LINK, new HashSet<Member>());
    }
    ((Set<Member>) r.getUserObject(SUBSYSTEM_LINK)).add(member);
  }
}
