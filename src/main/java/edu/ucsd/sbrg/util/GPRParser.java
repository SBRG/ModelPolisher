package edu.ucsd.sbrg.util;

import static java.text.MessageFormat.format;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Logger;

import org.sbml.jsbml.ASTNode;
import org.sbml.jsbml.Annotation;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.ext.fbc.And;
import org.sbml.jsbml.ext.fbc.Association;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCModelPlugin;
import org.sbml.jsbml.ext.fbc.FBCReactionPlugin;
import org.sbml.jsbml.ext.fbc.GeneProduct;
import org.sbml.jsbml.ext.fbc.GeneProductAssociation;
import org.sbml.jsbml.ext.fbc.GeneProductRef;
import org.sbml.jsbml.ext.fbc.LogicalOperator;
import org.sbml.jsbml.ext.fbc.Or;
import org.sbml.jsbml.text.parser.CobraFormulaParser;
import org.sbml.jsbml.xml.XMLNode;

import de.zbit.util.ResourceManager;
import de.zbit.util.Utils;
import edu.ucsd.sbrg.bigg.BiGGId;

public class GPRParser {

  /**
   * A {@link Logger} for this class.
   */
  private static final Logger logger = Logger.getLogger(GPRParser.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   * Mapping holding geneAssociations from model annotations
   */
  private static Map<String, XMLNode> oldGeneAssociations;

  /**
   * resets Map containing geneAssociation XMLNodes, as it is only valid for one model
   */
  public static void clearAssociationMap() {
    if (oldGeneAssociations != null) {
      oldGeneAssociations = null;
    }
  }


  /**
   * @param r
   * @param geneReactionRule
   */
  public static void parseGPR(Reaction r, String geneReactionRule, boolean omitGenericTerms) {
    if ((geneReactionRule != null) && (geneReactionRule.length() > 0)) {
      Association association = null;
      try {
        association =
          convertToAssociation(ASTNode.parseFormula(geneReactionRule, new CobraFormulaParser(new StringReader(""))),
            r.getId(), r.getModel(), omitGenericTerms);
      } catch (Throwable exc) {
        logger.warning(format(MESSAGES.getString("PARSE_GPR_ERROR"), geneReactionRule, Utils.getMessage(exc)));
      }
      if (association != null) {
        parseGPR(r, association, omitGenericTerms);
      }
    }
  }


  /**
   * @param ast
   * @param reactionId
   * @param model
   * @return
   */
  public static Association convertToAssociation(ASTNode ast, String reactionId, Model model,
    boolean omitGenericTerms) {
    int level = model.getLevel(), version = model.getVersion();
    if (ast.isLogical()) {
      LogicalOperator operator;
      if (ast.getType() == ASTNode.Type.LOGICAL_AND) {
        operator = new And(level, version);
        if (!omitGenericTerms) {
          operator.setSBOTerm(173); // AND
        }
      } else {
        operator = new Or(level, version);
        if (!omitGenericTerms) {
          operator.setSBOTerm(174); // OR
        }
      }
      for (ASTNode child : ast.getListOfNodes()) {
        Association tmp = convertToAssociation(child, reactionId, model, omitGenericTerms);
        if (tmp.getClass().equals(operator.getClass())) {
          // flatten binary trees to compact representation
          LogicalOperator lo = (LogicalOperator) tmp;
          for (int i = lo.getAssociationCount() - 1; i >= 0; i--) {
            operator.addAssociation(lo.removeAssociation(i));
          }
        } else {
          operator.addAssociation(tmp);
        }
      }
      return operator;
    }
    return createGPR(ast.toString(), reactionId, model);
  }


  /**
   * @param identifier
   * @param reactionId
   * @param model
   * @return
   */
  public static GeneProductRef createGPR(String identifier, String reactionId, Model model) {
    // TODO: check if this could return an empty gpr in real cases
    int level = model.getLevel(), version = model.getVersion();
    GeneProductRef gpr = new GeneProductRef(level, version);
    // check if this id exists in the model
    String oldId = identifier.startsWith("G_") ? identifier : "G_" + identifier;
    boolean containsOldId = !model.containsUniqueNamedSBase(oldId);
    BiGGId.createGeneId(identifier).map(BiGGId::toBiGGId).ifPresent(id -> {
      if (!model.containsUniqueNamedSBase(id)) {
        GeneProduct gp;
        if (containsOldId) {
          gp = (GeneProduct) model.findUniqueNamedSBase(oldId);
        } else {
          gp = (GeneProduct) model.findUniqueNamedSBase(id);
        }
        if (gp == null) {
          logger.warning(format(MESSAGES.getString("CREATE_MISSING_GPR"), id, reactionId));
          FBCModelPlugin fbcPlug = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
          gp = fbcPlug.createGeneProduct(id);
          gp.setLabel(id);
        } else {
          logger.info(format(MESSAGES.getString("UPDATE_GP_ID"), gp.getId(), id));
          gp.setId(id);
        }
      }
      gpr.setGeneProduct(id);
    });
    return gpr;
  }


  /**
   * @param r
   * @param association
   * @param omitGenericTerms
   */
  private static void parseGPR(Reaction r, Association association, boolean omitGenericTerms) {
    FBCReactionPlugin plugin = (FBCReactionPlugin) r.getPlugin(FBCConstants.shortLabel);
    if (!plugin.isSetGeneProductAssociation()) {
      GeneProductAssociation gpa = new GeneProductAssociation(r.getLevel(), r.getVersion());
      gpa.setAssociation(association);
      plugin.setGeneProductAssociation(gpa);
    } else if (!areEqual(association, plugin.getGeneProductAssociation().getAssociation())) {
      mergeAssociation(r, association, plugin, omitGenericTerms);
    }
  }


  /**
   * @param gpa1
   * @param gpa2
   * @return
   */
  private static boolean areEqual(Association gpa1, Association gpa2) {
    if (gpa1.isLeaf() && gpa2.isLeaf()) {
      return true;
    } else if (gpa1 instanceof Or && gpa2 instanceof Or) {
      if (((Or) gpa1).getNumChildren() != ((Or) gpa2).getNumChildren()) {
        return false;
      } else {
        boolean childrenEqual = true;
        for (int i = 0; i < ((Or) gpa1).getNumChildren(); i++) {
          childrenEqual &= areEqual((Association) gpa1.getChildAt(i), (Association) gpa2.getChildAt(i));
        }
        return childrenEqual;
      }
    } else if (gpa1 instanceof And && gpa2 instanceof And) {
      if (((And) gpa1).getNumChildren() != ((And) gpa2).getNumChildren()) {
        return false;
      } else {
        boolean childrenEqual = true;
        for (int i = 0; i < ((And) gpa1).getNumChildren(); i++) {
          childrenEqual &= areEqual((Association) gpa1.getChildAt(i), (Association) gpa2.getChildAt(i));
        }
        return childrenEqual;
      }
    }
    return false;
  }


  /**
   * @return
   */
  public static String stringify(Association association) {
    if (association instanceof GeneProductRef) {
      return ((GeneProductRef) association).getGeneProduct();
    } else if (association instanceof And) {
      List<Association> children = ((And) association).getListOfAssociations();
      int numChildren = ((And) association).getAssociationCount();
      StringBuilder sb = new StringBuilder();
      sb.append("(").append(stringify(children.get(0))).append(")");
      for (int i = 1; i < numChildren; i++) {
        sb.append(" and (").append(stringify(children.get(i))).append(")");
      }
      return sb.toString();
    } else {
      List<Association> children = ((Or) association).getListOfAssociations();
      int numChildren = ((Or) association).getAssociationCount();
      StringBuilder sb = new StringBuilder();
      sb.append("(").append(stringify(children.get(0))).append(")");
      for (int i = 1; i < numChildren; i++) {
        sb.append(" or (").append(stringify(children.get(i))).append(")");
      }
      return sb.toString();
    }
  }


  /**
   * @param r
   * @param association
   * @param plugin
   * @param omitGenericTerms
   */
  private static void mergeAssociation(Reaction r, Association association, FBCReactionPlugin plugin,
    boolean omitGenericTerms) {
    // get current association to replace
    Association old_association = plugin.getGeneProductAssociation().getAssociation();
    plugin.getGeneProductAssociation().unsetAssociation();
    GeneProductAssociation gpa = new GeneProductAssociation(r.getLevel(), r.getVersion());
    // link all GPRs fetched with or
    LogicalOperator or = new Or(r.getLevel(), r.getVersion());
    if (!omitGenericTerms) {
      or.setSBOTerm(174); // OR
    }
    if (old_association instanceof And) {
      or.addAssociation(old_association);
      or.addAssociation(association);
      Set<String> gprs = new HashSet<>();
      for (int i = 0; i < or.getChildCount(); i++) {
        Association current = (Association) or.getChildAt(i);
        if (current instanceof GeneProductRef) {
          String geneProduct = ((GeneProductRef) current).getGeneProduct();
          if (gprs.contains(geneProduct)) {
            if (!or.removeAssociation(current)) {
              logger.warning(format("Failed to unset duplicate GeneProductReference '{0}' for reaction '{1}'",
                geneProduct, r.getId()));
            }
          } else {
            gprs.add(geneProduct);
          }
        }
      }
      gpa.setAssociation(or);
    } else if (old_association instanceof GeneProductRef) {
      if (association instanceof Or) {
        or = (Or) association;
      } else {
        or.addAssociation(association);
      }
      or.addAssociation(old_association);
      gpa.setAssociation(or);
    } else { // OR
      if (association instanceof Or) {
        for (int idx = 0; idx < association.getChildCount(); idx++) {
          Association child = (Association) association.getChildAt(idx);
          ((Or) association).removeAssociation(idx);
          ((LogicalOperator) old_association).addAssociation(child);
        }
      } else {
        ((LogicalOperator) old_association).addAssociation(association);
      }
      gpa.setAssociation(old_association);
    }
    plugin.setGeneProductAssociation(gpa);
  }


  /**
   * @param reaction
   */
  public static void convertAssociationsToFBCV2(Reaction reaction, boolean omitGenericTerms) {
    Model model = reaction.getModel();
    Annotation annotation = model.getAnnotation();
    XMLNode node = annotation.getNonRDFannotation();
    if (node == null) {
      return;
    }
    if (oldGeneAssociations == null) {
      oldGeneAssociations = new HashMap<>();
      for (int i = 0; i < node.getChildCount(); i++) {
        XMLNode current = node.getChild(i);
        if (current.getName().equals("geneAssociation")) {
          String reactionId = current.getAttributes().getValue("reaction");
          oldGeneAssociations.put(reactionId, node.getChild(i));
          node.removeChild(i);
        }
      }
    }
    String id = reaction.getId();
    XMLNode ga = oldGeneAssociations.getOrDefault(id, null);
    if (ga != null) {
      FBCReactionPlugin plugin = (FBCReactionPlugin) reaction.getPlugin(FBCConstants.shortLabel);
      GeneProductAssociation gpa = new GeneProductAssociation(reaction.getLevel(), reaction.getVersion());
      List<Association> associations = processAssociation(ga, model, omitGenericTerms);
      if (associations.size() == 1) {
        gpa.setAssociation(associations.get(0));
        plugin.setGeneProductAssociation(gpa);
      }
    }
  }


  /**
   * @param association
   * @return
   */
  private static List<Association> processAssociation(XMLNode association, Model model, boolean omitGenericTerms) {
    int level = model.getLevel(), version = model.getVersion();
    List<Association> associations = new ArrayList<>();
    for (int i = 0; i < association.getChildCount(); i++) {
      XMLNode current = association.getChild(i);
      switch (current.getName()) {
      case "and":
        And and = new And(level, version);
        if (!omitGenericTerms) {
          and.setSBOTerm(173); // AND
        }
        and.addAllAssociations(processAssociation(current, model, omitGenericTerms));
        if (and.isSetListOfAssociations()) {
          associations.add(and);
        }
        break;
      case "or":
        Or or = new Or(level, version);
        if (!omitGenericTerms) {
          or.setSBOTerm(174); // OR
        }
        or.addAllAssociations(processAssociation(current, model, omitGenericTerms));
        if (or.isSetListOfAssociations()) {
          associations.add(or);
        }
        break;
      case "gene":
        String geneReference = current.getAttributes().getValue("reference");
        GeneProductRef gpr = new GeneProductRef(level, version);
        BiGGId.createGeneId(geneReference).map(BiGGId::toBiGGId).ifPresent(id -> {
          if (!model.containsUniqueNamedSBase(id)) {
            GeneProduct gp = (GeneProduct) model.findUniqueNamedSBase(id);
            if (gp == null) {
              logger.warning(format("Creating missing gene product {0}", id));
              FBCModelPlugin fbcPlug = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
              gp = fbcPlug.createGeneProduct(id);
              gp.setLabel(id);
            } else {
              logger.info(format(MESSAGES.getString("UPDATE_GP_ID"), gp.getId(), id));
              gp.setId(id);
            }
          }
          gpr.setGeneProduct(id);
        });
        if (gpr.isSetGeneProduct()) {
          associations.add(gpr);
        }
        break;
      }
    }
    return associations;
  }
}
