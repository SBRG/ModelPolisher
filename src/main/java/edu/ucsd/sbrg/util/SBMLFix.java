/**
 * 
 */
package edu.ucsd.sbrg.util;

import de.zbit.io.ZIPUtils;
import de.zbit.io.filefilter.SBFileFilter;
import de.zbit.util.ResourceManager;
import de.zbit.util.Utils;
import de.zbit.util.logging.LogUtil;
import edu.ucsd.sbrg.bigg.ModelPolisher;
import edu.ucsd.sbrg.bigg.polishing.ReactionPolishing;
import org.sbml.jsbml.ListOf;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLReader;
import org.sbml.jsbml.TidySBMLWriter;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCModelPlugin;
import org.sbml.jsbml.ext.fbc.Objective;
import org.sbml.jsbml.ext.groups.Group;
import org.sbml.jsbml.ext.groups.GroupsConstants;
import org.sbml.jsbml.ext.groups.GroupsModelPlugin;
import org.sbml.jsbml.util.filters.NameFilter;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * This is a stand-alone bug-fix program. It recursively traverses a directory
 * of SBML files and applies fixes to each model found. The result is saved to
 * a target directory, which can be in-place, i.e., identical to the input
 * directory. Otherwise, an identical directory structure will be created within
 * the target directory.
 * <p>
 * This program became necessary as a temporary solution for invalid SBML models
 * in BiGG database before a new version of {@link ModelPolisher} could be
 * released.
 * <p>
 * The methods in this class can also be used in other parts of ModelPolisher,
 * and are used in fact. This class can become a collection of repair functions
 * for invalid SBML models.
 * 
 * @author Andreas Dr&auml;ger
 * @date 2016-02-19
 */
public class SBMLFix {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(SBMLFix.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   * 
   */
  private static final double DEFAULT_COEFFICIENT = 1d;

  /**
   * @param input
   * @param output
   * @throws XMLStreamException
   * @throws IOException
   */
  public static void batchProcess(File input, File output) {
    if (!output.exists() && !output.isFile() && !(input.isFile() && input.getName().equals(output.getName()))) {
      logger.info(MessageFormat.format(MESSAGES.getString("DIRECTORY_CREATED"), output.getAbsolutePath()));
      output.mkdir();
    }
    if (input.isFile()) {
      if (SBFileFilter.isSBMLFile(input)) {
        if (output.isDirectory()) {
          String fName = input.getName();
          output = new File(Utils.ensureSlash(output.getAbsolutePath()) + fName);
        }
        try {
          fixSBML(input, output);
        } catch (XMLStreamException | IOException exc) {
          logger.severe(exc.getMessage());
        }
      }
    } else {
      if (!output.isDirectory()) {
        logger.severe(MessageFormat.format(MESSAGES.getString("WRITE_TO_FILE_ERROR"), output.getAbsolutePath()));
      }
      for (File file : input.listFiles()) {
        File target = new File(Utils.ensureSlash(output.getAbsolutePath()) + file.getName());
        batchProcess(file, target);
      }
    }
  }


  /**
   * Set group kind where required
   * 
   * @param model
   */
  public static void fixGroups(Model model) {
    GroupsModelPlugin gPlug = (GroupsModelPlugin) model.getExtension(GroupsConstants.shortLabel);
    if ((gPlug != null) && gPlug.isSetListOfGroups()) {
      for (Group group : gPlug.getListOfGroups()) {
        if (!group.isSetKind()) {
          logger.info(MessageFormat.format(MESSAGES.getString("ADD_KIND_TO_GROUP"),
            group.isSetName() ? group.getName() : group.getId()));
          group.setKind(Group.Kind.partonomy);
        }
      }
    }
  }


  /**
   * Check for missing objective function.
   * 
   * @param modelDescriptor
   *        this can be the path to the model file or some name that describes
   *        this model.
   * @param model
   */
  public static void fixObjective(String modelDescriptor, Model model) {
    FBCModelPlugin fbcPlug = (FBCModelPlugin) model.getExtension(FBCConstants.shortLabel);
    if ((fbcPlug != null) && fbcPlug.isSetListOfObjectives()) {
      fixObjective(modelDescriptor, model.isSetListOfReactions() ? model.getListOfReactions() : null, fbcPlug);
    }
  }


  /**
   * @param modelDescriptor
   *        some descriptive String for the model, e.g., its id, the path to the
   *        file, or any other meaningful information for users.
   * @param listOfReactions
   * @param fbcPlug
   * @return {@code true} if this operation was successful and {@code false} if
   *         the problem could not be fixed.
   */
  public static boolean fixObjective(String modelDescriptor, ListOf<Reaction> listOfReactions, FBCModelPlugin fbcPlug) {
    return fixObjective(modelDescriptor, listOfReactions, fbcPlug, null, null);
  }


  /**
   * @param modelDescriptor
   * @param listOfReactions
   * @param fbcPlug
   * @param fluxCoefficients
   * @param fluxObjectives
   * @return
   */
  public static boolean fixObjective(String modelDescriptor, ListOf<Reaction> listOfReactions, FBCModelPlugin fbcPlug,
    double[] fluxCoefficients, String[] fluxObjectives) {
    Objective activeObjective = null;
    if (!fbcPlug.isSetActiveObjective()) {
      logger.severe(MessageFormat.format(MESSAGES.getString("OBJ_NOT_DEFINED"), modelDescriptor));
      if (fbcPlug.getObjectiveCount() == 1) {
        activeObjective = fbcPlug.getObjective(0);
        fbcPlug.setActiveObjective(activeObjective);
        logger.info(MessageFormat.format(MESSAGES.getString("OBJ_SOLUTION"), activeObjective.getId()));
      }
    } else {
      activeObjective =
        fbcPlug.getListOfObjectives().firstHit(new NameFilter(fbcPlug.getListOfObjectives().getActiveObjective()));
    }
    if (activeObjective != null) {
      if (!activeObjective.isSetListOfFluxObjectives()) {
        logger.severe(MessageFormat.format(MESSAGES.getString("TRY_GUESS_MISSING_FLUX_OBJ"), modelDescriptor));
        if (listOfReactions != null) {
          if (fluxObjectives != null) {
            /*
             * An array of target reactions is provided. We want to use this as
             * flux objectives.
             */
            boolean strict = false;
            for (int i = 0; i < fluxObjectives.length; i++) {
              final String id = fluxObjectives[i];
              Reaction r = listOfReactions.firstHit((obj) -> {
                return (obj instanceof Reaction) && id.equals(((Reaction) obj).getId());
              });
              if (r != null) {
                createFluxObjective(modelDescriptor, r, fluxCoefficients, activeObjective, i);
                // if at least one flux objective exists, the model qualifies as
                // strict model.
                strict = true;
              } else {
                logger.severe(MessageFormat.format(MESSAGES.getString("REACTION_UNKNOWN_ERROR"), id, modelDescriptor));
              }
            }
            return strict;
          } else {
            /*
             * Search for biomass reaction in the model and use this as
             * objective.
             */
            final Pattern pattern = ReactionPolishing.Patterns.BIOMASS_CASE_INSENSITIVE.getPattern();
            Reaction rBiomass = listOfReactions.firstHit((obj) -> {
              return (obj instanceof Reaction) && pattern.matcher(((Reaction) obj).getId()).matches();
            });
            if (rBiomass != null) {
              createFluxObjective(modelDescriptor, rBiomass, fluxCoefficients, activeObjective, 0);
              return true;
            } else {
              logger.severe(MESSAGES.getString("REACTION_BIOMASS_UNKNOWN_ERROR"));
            }
          }
        } else {
          logger.severe(MessageFormat.format(MESSAGES.getString("REACTION_LIST_MISSING"), modelDescriptor));
        }
      }
    }
    return false;
  }


  /**
   * @param modelDescriptor
   * @param r
   * @param fluxCoefficients
   * @param o
   * @param i
   */
  private static void createFluxObjective(String modelDescriptor, Reaction r, double[] fluxCoefficients, Objective o,
    int i) {
    double coeff = DEFAULT_COEFFICIENT;
    if ((fluxCoefficients != null) && (fluxCoefficients.length > i)) {
      coeff = fluxCoefficients[i];
    }
    logger.info(MessageFormat.format(MESSAGES.getString("ADDED_FLUX_OBJ"), r.getId(), coeff, modelDescriptor));
    o.createFluxObjective(null, null, coeff, r);
  }


  /**
   * @param in
   * @param out
   * @throws XMLStreamException
   * @throws IOException
   */
  public static void fixSBML(File in, File out) throws XMLStreamException, IOException {
    long time = System.currentTimeMillis();
    logger.info(MessageFormat.format(MESSAGES.getString("READ_FILE_INFO"), in.getAbsolutePath()));
    SBMLDocument doc = SBMLReader.read(in);
    Model model = doc.getModel();
    fixGroups(model);
    fixObjective(in.getAbsolutePath(), model);
    logger.info(MessageFormat.format(MESSAGES.getString("WRITE_FILE_INFO"), out.getAbsolutePath()));
    TidySBMLWriter.write(doc, out, ModelPolisher.class.getName(), "1.1", ' ', (short) 2);
    String archive = out.getAbsolutePath() + ".gz";
    logger.info(MessageFormat.format("ARCHIVE", archive));
    ZIPUtils.GZip(out.getAbsolutePath(), archive);
    logger.info(MessageFormat.format("Done. Time elapsed: {0,number,integer} ms", System.currentTimeMillis() - time));
  }


  /**
   * @param args
   */
  public static void main(String[] args) {
    LogUtil.initializeLogging("de.zbit", "edu.ucsd.sbrg");
    batchProcess(new File(args[0]), new File(args[1]));
  }
}
