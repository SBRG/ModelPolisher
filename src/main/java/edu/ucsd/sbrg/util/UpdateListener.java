package edu.ucsd.sbrg.util;

import org.sbml.jsbml.Model;
import org.sbml.jsbml.NamedSBase;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.ext.fbc.Association;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCModelPlugin;
import org.sbml.jsbml.ext.fbc.GeneProduct;
import org.sbml.jsbml.ext.fbc.GeneProductRef;
import org.sbml.jsbml.ext.groups.Member;
import org.sbml.jsbml.util.TreeNodeChangeEvent;
import org.sbml.jsbml.util.TreeNodeChangeListener;
import org.sbml.jsbml.util.TreeNodeRemovedEvent;

import javax.swing.tree.TreeNode;
import java.beans.PropertyChangeEvent;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static edu.ucsd.sbrg.bigg.ModelPolisher.mpMessageBundle;

/**
 * This class keeps track of changes to the model and tries to keep cross
 * references etc. consistent.
 * 
 * @author Andreas Dr&auml;ger
 */
public class UpdateListener implements TreeNodeChangeListener {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(UpdateListener.class.getName());
  /**
   * Stores links from geneIds to {@link Association} objects where these are
   * used.
   */
  private Map<String, Set<GeneProductRef>> geneIdToAssociation;

  /**
   * 
   */
  public UpdateListener() {
    geneIdToAssociation = new HashMap<>();
  }


  /*
   * (non-Javadoc)
   * @see java.beans.PropertyChangeListener#propertyChange(java.beans.
   * PropertyChangeEvent)
   */
  @SuppressWarnings("unchecked")
  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt.getPropertyName().equals(TreeNodeChangeEvent.id)) {
      String oldId = (String) evt.getOldValue();
      if (oldId != null) {
        // There is only a need to do some further change if the id is updated
        // to a new id.
        String newId = (String) evt.getNewValue();
        NamedSBase nsb = (NamedSBase) evt.getSource();
        if (nsb instanceof Reaction) {
          Reaction r = (Reaction) nsb;
          Model model = r.getModel();
          FBCModelPlugin fbcModelPlug = (FBCModelPlugin) model.getPlugin(FBCConstants.shortLabel);
          SBMLUtils.updateReactionRef(oldId, newId, fbcModelPlug);
          Set<Member> subsystems = (Set<Member>) r.getUserObject(SBMLUtils.SUBSYSTEM_LINK);
          if (subsystems != null) {
            for (Member m : subsystems) {
              m.setIdRef(newId);
            }
          }
        } else if (nsb instanceof GeneProduct) {
          Set<GeneProductRef> geneRefs = geneIdToAssociation.remove(oldId);
          if (geneRefs != null) {
            for (GeneProductRef ref : geneRefs) {
              ref.setGeneProduct(newId);
            }
            geneIdToAssociation.put(newId, geneRefs);
          }
        } else {
          logger.severe(
            MessageFormat.format(mpMessageBundle.getString("ID_CHANGE_WARNING"), nsb.getElementName(), oldId, newId));
        }
      }
    }
  }


  /*
   * (non-Javadoc)
   * @see org.sbml.jsbml.util.TreeNodeChangeListener#nodeAdded(javax.swing.tree.
   * TreeNode)
   */
  @Override
  public void nodeAdded(TreeNode node) {
    // Memorize link from GeneProduct to Associations when this association is
    // being added.
    if (node instanceof GeneProductRef) {
      GeneProductRef gpr = (GeneProductRef) node;
      Set<GeneProductRef> geneRefs = geneIdToAssociation.get(gpr.getGeneProduct());
      if (geneRefs == null) {
        geneRefs = new HashSet<GeneProductRef>();
        geneIdToAssociation.put(gpr.getGeneProduct(), geneRefs);
      }
      geneRefs.add(gpr);
      // GeneProduct gene = gpr.getGeneProductInstance();
      // if (gene != null) {
      // gene.putUserObject("ASSOCIATION_LINK", geneRefs);
      // }
    }
    logger.fine(node.toString());
  }


  /*
   * (non-Javadoc)
   * @see
   * org.sbml.jsbml.util.TreeNodeChangeListener#nodeRemoved(org.sbml.jsbml.util.
   * TreeNodeRemovedEvent)
   */
  @Override
  public void nodeRemoved(TreeNodeRemovedEvent event) {
    logger.fine(event.toString());
  }
}
