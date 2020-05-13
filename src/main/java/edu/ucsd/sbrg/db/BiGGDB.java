/*
 * $Id$
 * $URL$
 * ---------------------------------------------------------------------
 * This file is part of the program NetBuilder.
 * Copyright (C) 2013 by the University of California, San Diego.
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation. A copy of the license
 * agreement is provided in the file named "LICENSE.txt" included with
 * this software distribution and also available online as
 * <http://www.gnu.org/licenses/lgpl-3.0-standalone.html>.
 * ---------------------------------------------------------------------
 */
package edu.ucsd.sbrg.db;

import static edu.ucsd.sbrg.bigg.ModelPolisher.mpMessageBundle;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.TYPE_GENE_PRODUCT;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.TYPE_REACTION;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.TYPE_SPECIES;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.ACCESSION_VALUE;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.BIGG_ID;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.CHARGE;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.COMPARTMENTALIZED_COMPONENT_ID;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.COMPARTMENT_ID;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.COMPONENT_ID;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.DATA_SOURCE_ID;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.FORMULA;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.GENE_REACTION_RULE;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.GENOME_ID;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.ID;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.LOCUS_TAG;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.MODEL_ID;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.OME_ID;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.ORGANISM;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.PUBLICATION_ID;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.REACTION_ID;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.REFERENCE_ID;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.REFERENCE_TYPE;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.SUBSYSTEM;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.SYNONYM_COL;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Column.TAXON_ID;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.COMPARTMENT;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.COMPARTMENTALIZED_COMPONENT;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.COMPONENT;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.DATA_SOURCE;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.GENE;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.GENOME;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.GENOME_REGION;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.MCC;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.MODEL;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.MODEL_REACTION;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.OLD_BIGG_ID;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.PUBLICATION;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.PUBLICATION_MODEL;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.REACTION;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.REFSEQ_NAME;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.REFSEQ_PATTERN;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.SYNONYM;
import static edu.ucsd.sbrg.db.BiGGDBContract.Constants.Table.URL_PREFIX;
import static java.text.MessageFormat.format;
import static org.sbml.jsbml.util.Pair.pairOf;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.sbml.jsbml.util.Pair;

import de.zbit.util.Utils;
import edu.ucsd.sbrg.bigg.BiGGId;
import edu.ucsd.sbrg.miriam.Registry;

/**
 * @author Andreas Dr&auml;ger
 */
public class BiGGDB {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(BiGGDB.class.getName());
  /**
   * The connection to the database.
   */
  private static PostgreSQLConnector connector;

  /**
   * Don't allow instantiation
   */
  private BiGGDB() {
  }


  public static void init(String host, String port, String user, String passwd, String name) {
    connector = new PostgreSQLConnector(host, Integer.parseInt(port), user, passwd, name);
  }


  /**
   *
   */
  public static void close() {
    connector.close();
  }


  /**
   * @return
   */
  public static boolean inUse() {
    return connector != null;
  }


  /**
   * @return
   */
  public static Optional<Date> getBiGGVersion() {
    Optional<Date> date = Optional.empty();
    try {
      Connection connection = connector.getConnection();
      PreparedStatement pStatement = connection.prepareStatement("Select date_time FROM database_version");
      ResultSet resultSet = pStatement.executeQuery();
      if (resultSet.next()) {
        date = Optional.of(resultSet.getDate(1));
      }
      pStatement.close();
      connection.close();
    } catch (SQLException exc) {
      logger.finest(format("{0}: {1}", exc.getClass().getName(), Utils.getMessage(exc)));
    }
    return date;
  }


  /**
   * @param modelBiGGid
   * @param reactionBiGGid
   * @return
   */
  public static List<String> getSubsystems(String modelBiGGid, String reactionBiGGid) {
    String query = "SELECT DISTINCT mr." + SUBSYSTEM + " FROM " + REACTION + " r, " + MODEL + " m, " + MODEL_REACTION
      + " mr WHERE m." + BIGG_ID + " = ? AND r." + BIGG_ID + " = ? AND m." + ID + " = mr." + MODEL_ID
      + " AND r.id = mr.reaction_id AND LENGTH(mr.subsystem) > 0";
    List<String> list = new LinkedList<>();
    try {
      Connection connection = connector.getConnection();
      PreparedStatement pStatement = connection.prepareStatement(query);
      pStatement.setString(1, modelBiGGid);
      pStatement.setString(2, reactionBiGGid);
      ResultSet resultSet = pStatement.executeQuery();
      while (resultSet.next()) {
        list.add(resultSet.getString(1));
      }
      pStatement.close();
      connection.close();
    } catch (SQLException exc) {
      logger.warning(Utils.getMessage(exc));
    }
    return list;
  }


  /**
   * @param reactionBiGGid
   * @return
   */
  public static List<String> getSubsystemsForReaction(String reactionBiGGid) {
    String query =
      "SELECT DISTINCT mr.subsystem FROM reaction r, model_reaction mr WHERE r.bigg_id = ? AND r.id = mr.reaction_id AND LENGTH(mr.subsystem) > 0";
    List<String> list = new LinkedList<>();
    try {
      Connection connection = connector.getConnection();
      PreparedStatement pStatement = connection.prepareStatement(query);
      pStatement.setString(1, reactionBiGGid);
      ResultSet resultSet = pStatement.executeQuery();
      while (resultSet.next()) {
        list.add(resultSet.getString(1));
      }
      pStatement.close();
      connection.close();
    } catch (SQLException exc) {
      logger.warning(Utils.getMessage(exc));
    }
    return list;
  }


  /**
   * Get chemical formula for unknown model id
   *
   * @param componentId
   * @param compartmentId
   * @return
   */
  public static Optional<String> getChemicalFormulaByCompartment(String componentId, String compartmentId) {
    String query = "SELECT DISTINCT mcc." + FORMULA + " FROM " + MCC + " mcc, " + COMPARTMENTALIZED_COMPONENT + " cc, "
      + COMPONENT + " c, " + COMPARTMENT + " co WHERE c." + BIGG_ID + " = ? AND c." + ID + " = cc." + COMPONENT_ID
      + " AND co." + BIGG_ID + " = ? AND co." + ID + " = cc." + COMPARTMENT_ID + " and cc." + ID + " = mcc."
      + COMPARTMENTALIZED_COMPONENT_ID + " AND mcc.formula <> '' ORDER BY mcc." + FORMULA;
    Set<String> results = runFormulaQuery(query, componentId, compartmentId);
    if (results.size() == 1) {
      return Optional.of(results.iterator().next());
    } else {
      if (results.size() > 1) {
        logger.info(String.format("Could not retrieve unique chemical formula for component '%s' and compartment '%s'",
          componentId, compartmentId));
      }
      return Optional.empty();
    }
  }


  /**
   * @param query
   * @param componentId
   * @param compartmentOrModelId
   * @return
   */
  private static Set<String> runFormulaQuery(String query, String componentId, String compartmentOrModelId) {
    Set<String> results = new HashSet<>();
    try {
      Connection connection = connector.getConnection();
      PreparedStatement pStatement = connection.prepareStatement(query);
      pStatement.setString(1, componentId);
      pStatement.setString(2, compartmentOrModelId);
      ResultSet resultSet = pStatement.executeQuery();
      while (resultSet.next()) {
        results.add(resultSet.getString(1));
      }
      pStatement.close();
      connection.close();
    } catch (SQLException exc) {
      logger.warning(Utils.getMessage(exc));
    }
    return results.stream().filter(formula -> formula != null && !formula.isEmpty()).collect(Collectors.toSet());
  }


  /**
   * Get chemical formula for models that are present in BiGG
   *
   * @param componentId
   * @param modelId
   * @return
   */
  public static Optional<String> getChemicalFormula(String componentId, String modelId) {
    String query = "SELECT DISTINCT mcc." + FORMULA + "\n FROM " + COMPONENT + " c,\n" + COMPARTMENTALIZED_COMPONENT
      + " cc,\n" + MODEL + " m,\n" + MCC + " mcc\n WHERE c." + ID + " = cc." + COMPONENT_ID + " AND\n cc." + ID
      + " = mcc." + COMPARTMENTALIZED_COMPONENT_ID + " AND\n c." + BIGG_ID + " = ? AND\n m." + BIGG_ID + " = ? AND\n m."
      + ID + " = mcc." + MODEL_ID + " AND mcc.formula <> ''";
    Set<String> results = runFormulaQuery(query, componentId, modelId);
    if (results.size() == 1) {
      return Optional.of(results.iterator().next());
    } else {
      if (results.size() > 1) {
        logger.info(String.format("Could not retrieve unique chemical formula for component '%s' and model '%s'",
          componentId, modelId));
      }
      return Optional.empty();
    }
  }


  /**
   * @param biggId
   * @return
   */
  public static Optional<String> getCompartmentName(BiGGId biggId) {
    String query = "SELECT name FROM compartment WHERE bigg_id = ? AND name <> ''";
    return singleParamStatement(query, biggId.getAbbreviation());
  }


  /**
   * @param query
   * @param param
   * @return
   */
  public static Optional<String> singleParamStatement(String query, String param) {
    Set<String> results = new HashSet<>();
    try {
      Connection connection = connector.getConnection();
      PreparedStatement pStatement = connection.prepareStatement(query);
      pStatement.setString(1, param);
      ResultSet resultSet = pStatement.executeQuery();
      while (resultSet.next()) {
        results.add(resultSet.getString(1));
      }
      pStatement.close();
      connection.close();
    } catch (SQLException exc) {
      logger.warning(Utils.getMessage(exc));
    }
    results = results.stream().filter(result -> result != null && !result.isEmpty()).collect(Collectors.toSet());
    if (results.size() == 1) {
      return Optional.of(results.iterator().next());
    } else {
      if (results.size() > 1) {
        logger.severe(String.format("Query returned multiple results for parameter %s\nQuery:\n%s", param, query));
      }
      return Optional.empty();
    }
  }


  /**
   * @param biggId
   * @return
   */
  public static Optional<String> getComponentName(BiGGId biggId) {
    String query = "SELECT name FROM component WHERE bigg_id = ? AND name <> ''";
    return singleParamStatement(query, biggId.getAbbreviation());
  }


  /**
   * @param biggId
   * @return
   */
  public static Optional<String> getComponentType(BiGGId biggId) {
    String query = "SELECT type FROM component WHERE bigg_id = ? AND name <> ''";
    return singleParamStatement(query, biggId.getAbbreviation());
  }


  /**
   * Here we get all possible MIRIAM annotation for Gene Labels, but we ignore
   * all those entries that are not MIRIAM-compliant for now.
   *
   * @param label
   * @return
   */
  public static TreeSet<String> getGeneIds(String label) {
    TreeSet<String> results = new TreeSet<>();
    String query = "SELECT " + URL_PREFIX + ", s." + SYNONYM + "\n" + "FROM  " + DATA_SOURCE + " d, " + SYNONYM + " s, "
      + GENOME_REGION + " gr\n" + "WHERE d." + ID + " = s." + DATA_SOURCE_ID + " AND\n s." + OME_ID + " = gr." + ID
      + " AND\n gr." + BIGG_ID + " = ? AND\n d." + BIGG_ID + " != " + OLD_BIGG_ID + " AND\n d." + BIGG_ID + " NOT LIKE "
      + REFSEQ_PATTERN;
    try {
      Connection connection = connector.getConnection();
      PreparedStatement pStatement = connection.prepareStatement(query);
      pStatement.setString(1, label);
      ResultSet resultSet = pStatement.executeQuery();
      while (resultSet.next()) {
        String resource;
        String collection = resultSet.getString(1);
        String identifier = resultSet.getString(2);
        if (collection != null && identifier != null) {
          resource = collection.replaceAll("http://", "https://") + identifier;
        } else if (collection == null) {
          logger.fine(mpMessageBundle.getString("COLLECTION_NULL_GENE"));
          continue;
        } else {
          logger.warning(format(mpMessageBundle.getString("IDENTIFIER_NULL_GENE"), collection));
          continue;
        }
        Registry.checkResourceUrl(resource).map(results::add);
      }
      pStatement.close();
      connection.close();
    } catch (SQLException exc) {
      logger.warning(Utils.getMessage(exc));
    }
    return results;
  }


  /**
   * @param label
   * @return
   */
  public static Optional<String> getGeneName(String label) {
    String query = "SELECT s." + SYNONYM + "\n" + "FROM  " + DATA_SOURCE + " d, " + SYNONYM + " s, " + GENOME_REGION
      + " gr\n" + "WHERE d." + ID + " = s." + DATA_SOURCE_ID + " AND\n s." + OME_ID + " = gr." + ID + " AND\n gr."
      + BIGG_ID + " = ? AND\n d." + BIGG_ID + " LIKE " + REFSEQ_NAME + " AND s.synonym <> ''";
    return singleParamStatement(query, label);
  }


  /**
   * @param reactionId
   * @param modelId
   * @return
   */
  public static List<String> getGeneReactionRule(String reactionId, String modelId) {
    return getReactionRules("SELECT REPLACE(REPLACE(RTRIM(REPLACE(REPLACE(mr." + GENE_REACTION_RULE
      + ", 'or', '||'), 'and', '&&'), '.'), '.', '__SBML_DOT__'), '_AT', '__SBML_DOT__') AS " + GENE_REACTION_RULE
      + " FROM " + MODEL_REACTION + " mr, " + REACTION + " r, " + MODEL + " m WHERE r." + ID + " = mr." + REACTION_ID
      + " AND m." + ID + " = mr." + MODEL_ID + " AND mr." + GENE_REACTION_RULE + " IS NOT NULL AND  LENGTH(mr."
      + GENE_REACTION_RULE + ") > 0 AND r." + BIGG_ID + " = ? AND m." + BIGG_ID
      + " = ? AND mr.gene_reaction_rule <> '' ORDER BY mr." + ID, reactionId, modelId);
  }


  /**
   * @param query
   * @param reactionId
   * @param modelId
   * @return
   */
  public static List<String> getReactionRules(String query, String reactionId, String modelId) {
    List<String> results = new ArrayList<>();
    try {
      Connection connection = connector.getConnection();
      PreparedStatement pStatement = connection.prepareStatement(query);
      pStatement.setString(1, reactionId);
      pStatement.setString(2, modelId);
      ResultSet resultSet = pStatement.executeQuery();
      while (resultSet.next()) {
        results.add(resultSet.getString(1));
      }
      pStatement.close();
      connection.close();
    } catch (SQLException exc) {
      logger.warning(Utils.getMessage(exc));
    }
    return results;
  }


  /**
   * @param abbreviation
   * @return
   */
  public static Optional<String> getOrganism(String abbreviation) {
    String query = "SELECT g." + ORGANISM + " FROM " + GENOME + " g, " + MODEL + " m WHERE m." + GENOME_ID + " = g."
      + ID + " AND m." + BIGG_ID + " = ?";
    return singleParamStatement(query, abbreviation);
  }


  /**
   * @param abbreviation
   * @return
   */
  public static List<Pair<String, String>> getPublications(String abbreviation) {
    List<Pair<String, String>> results = new LinkedList<>();
    String query = "SELECT p." + REFERENCE_TYPE + ", p." + REFERENCE_ID + " FROM  " + PUBLICATION + " p, "
      + PUBLICATION_MODEL + " pm, " + MODEL + " m WHERE p." + ID + " = pm." + PUBLICATION_ID + " AND pm." + MODEL_ID
      + " = m." + ID + " AND m." + BIGG_ID + " = ?";
    try {
      Connection connection = connector.getConnection();
      PreparedStatement pStatement = connection.prepareStatement(query);
      pStatement.setString(1, abbreviation);
      ResultSet resultSet = pStatement.executeQuery();
      while (resultSet.next()) {
        String key = resultSet.getString(1);
        results.add(pairOf(key.equals("pmid") ? "pubmed" : key, resultSet.getString(2)));
      }
      pStatement.close();
      connection.close();
    } catch (SQLException exc) {
      logger.warning(Utils.getMessage(exc));
    }
    return results;
  }


  /**
   * @param abbreviation
   * @return
   */
  public static Optional<String> getReactionName(String abbreviation) {
    String query = "SELECT name FROM reaction WHERE bigg_id = ? AND name <> ''";
    return singleParamStatement(query, abbreviation);
  }


  /**
   * @param biggId
   * @param includeAnyURI
   * @param isReaction
   * @return a set of external source together with external id.
   */
  public static Set<String> getResources(BiGGId biggId, boolean includeAnyURI, boolean isReaction) {
    String type = isReaction ? REACTION : COMPONENT;
    Set<String> resources = new TreeSet<>();
    try {
      String query = String.format(
        "SELECT CONCAT(url_prefix, s.synonym) AS url FROM %s t, synonym s, data_source d WHERE t.id = s.ome_id AND s.data_source_id = d.id AND url_prefix IS NOT NULL AND %s AND t.bigg_id = ? %s",
        type, getTypeQuery(isReaction), includeAnyURI ? "" : "AND url_prefix like '%%identifiers.org%%'");
      Connection connection = connector.getConnection();
      PreparedStatement pStatement = connection.prepareStatement(query);
      pStatement.setString(1, biggId.getAbbreviation());
      ResultSet resultSet = pStatement.executeQuery();
      while (resultSet.next()) {
        Registry.checkResourceUrl(resultSet.getString(1)).map(resources::add);
      }
      pStatement.close();
      connection.close();
    } catch (SQLException exc) {
      logger.warning(Utils.getMessage(exc));
    }
    return resources;
  }


  /**
   * @param isReaction
   * @return
   */
  private static String getTypeQuery(boolean isReaction) {
    if (isReaction) {
      return "CAST(s.type AS \"text\") = '" + REACTION + "'";
    }
    return "(CAST(s.type AS \"text\") = '" + COMPONENT + "' OR CAST(s.type AS \"text\") = '"
      + COMPARTMENTALIZED_COMPONENT + "')";
  }


  /**
   * @param abbreviation
   * @return
   */
  public static Optional<Integer> getTaxonId(String abbreviation) {
    Integer result = null;
    String query = "SELECT " + TAXON_ID + " FROM " + GENOME + " g, " + MODEL + " m WHERE g." + ID + " = m." + GENOME_ID
      + " AND m." + BIGG_ID + " = ? AND taxon_id IS NOT NULL";
    try {
      Connection connection = connector.getConnection();
      PreparedStatement pStatement = connection.prepareStatement(query);
      pStatement.setString(1, abbreviation);
      ResultSet resultSet = pStatement.executeQuery();
      while (resultSet.next()) {
        if (result != null) {
          logger.severe(String.format("Taxon id query returned multiple results for abbreviation: %s", abbreviation));
        } else {
          result = resultSet.getInt(1);
        }
      }
      pStatement.close();
      connection.close();
    } catch (SQLException exc) {
      logger.warning(format(mpMessageBundle.getString("GET_TAXON_ERROR"), abbreviation, Utils.getMessage(exc)));
    }
    return result == null ? Optional.empty() : Optional.of(result);
  }


  /**
   * @param id:
   *        Model id present in BiGG
   * @return Accession that can be resolved as using either https://identifiers.org/refseq:{$id},
   *         https://www.ncbi.nlm.nih.gov/nuccore/{$id} or https://www.ncbi.nlm.nih.gov/assembly/{$id}
   */
  public static String getGenomeAccesion(String id) {
    String query = "SELECT g." + ACCESSION_VALUE + " FROM " + GENOME + " g, " + MODEL + " m WHERE m." + BIGG_ID
      + " = ? AND m." + GENOME_ID + " = g." + ID;
    String result = "";
    try {
      Connection connection = connector.getConnection();
      PreparedStatement pStatement = connection.prepareStatement(query);
      pStatement.setString(1, id);
      ResultSet resultSet = pStatement.executeQuery();
      // should always be exactly one entry, as we check beforehand, if we have a BiGG model id
      if (resultSet.next()) {
        result = resultSet.getString(1);
      }
      pStatement.close();
      connection.close();
    } catch (SQLException e) {
      logger.warning(format("Could not retrieve genome accession for model id {0}.", id));
    }
    // Will be non empty, as query always returns exactly one result
    return result;
  }


  /**
   * @param table
   * @return
   */
  public static Set<String> getOnce(String table) {
    Set<String> biggIds = new LinkedHashSet<>();
    try {
      Connection connection = connector.getConnection();
      String query = "SELECT bigg_id FROM " + table + " ORDER BY bigg_id";
      PreparedStatement pStatement = connection.prepareStatement(query);
      ResultSet resultSet = pStatement.executeQuery();
      while (resultSet.next()) {
        biggIds.add(resultSet.getString(1));
      }
      pStatement.close();
      connection.close();
    } catch (SQLException exc) {
      logger.warning(String.format("Failed to fetch BiGGIDs for table '%s': '%s'", table, Utils.getMessage(exc)));
    }
    return biggIds;
  }


  /**
   * Get charge for unknown model id
   *
   * @param componentId
   * @param compartmentId
   * @return
   */
  public static Optional<Integer> getChargeByCompartment(String componentId, String compartmentId) {
    String query = "SELECT DISTINCT mcc." + CHARGE + " FROM " + MCC + " mcc, " + COMPARTMENTALIZED_COMPONENT + " cc, "
      + COMPONENT + " c, " + COMPARTMENT + " co WHERE c." + BIGG_ID + " = ? AND c." + ID + " = cc." + COMPONENT_ID
      + " AND co." + BIGG_ID + " = ? AND co." + ID + " = cc." + COMPARTMENT_ID + " and cc." + ID + " = mcc."
      + COMPARTMENTALIZED_COMPONENT_ID + " AND LENGTH(CAST( mcc." + CHARGE + " AS text)) > 0 ORDER BY mcc." + CHARGE;
    Set<String> results = runChargeQuery(query, componentId, compartmentId);
    if (results.size() == 1) {
      return Optional.of(Integer.parseInt(results.iterator().next()));
    } else {
      if (results.size() > 1) {
        logger.warning(String.format("Could not retrieve unique charge for component '%s' and compartment '%s'",
          componentId, compartmentId));
      }
      return Optional.empty();
    }
  }


  /**
   * @param query
   * @param componentId
   * @param compartmentOrModelId
   * @return
   */
  private static Set<String> runChargeQuery(String query, String componentId, String compartmentOrModelId) {
    Set<String> results = new HashSet<>();
    try {
      Connection connection = connector.getConnection();
      PreparedStatement pStatement = connection.prepareStatement(query);
      pStatement.setString(1, componentId);
      pStatement.setString(2, compartmentOrModelId);
      ResultSet resultSet = pStatement.executeQuery();
      while (resultSet.next()) {
        results.add(resultSet.getString(1));
      }
      pStatement.close();
      connection.close();
    } catch (SQLException exc) {
      logger.warning(Utils.getMessage(exc));
    }
    return results.stream().filter(charge -> charge != null && !charge.isEmpty()).collect(Collectors.toSet());
  }


  /**
   * Get charge for known model id
   *
   * @param componentId
   * @param modelId
   * @return
   */
  public static Optional<Integer> getCharge(String componentId, String modelId) {
    String query = "SELECT DISTINCT mcc." + CHARGE + "\n FROM " + COMPONENT + " c,\n" + COMPARTMENTALIZED_COMPONENT
      + " cc,\n" + MODEL + " m,\n" + MCC + " mcc\n WHERE c." + ID + " = cc." + COMPONENT_ID + " AND\n cc." + ID
      + " = mcc." + COMPARTMENTALIZED_COMPONENT_ID + " AND\n c." + BIGG_ID + " = ? AND\n m." + BIGG_ID + " = ? AND\n m."
      + ID + " = mcc." + MODEL_ID + " AND mcc.charge IS NOT NULL";
    Set<String> results = runChargeQuery(query, componentId, modelId);
    if (results.size() == 1) {
      return Optional.of(Integer.parseInt(results.iterator().next()));
    } else {
      if (results.size() > 1) {
        logger.warning(String.format("Could not retrieve unique charge for component '%s' and compartment '%s'",
          componentId, modelId));
      }
      return Optional.empty();
    }
  }


  /**
   * @param reactionId
   * @return
   */
  public static boolean isPseudoreaction(String reactionId) {
    String query = "SELECT pseudoreaction FROM reaction WHERE bigg_id = ?";
    Optional<String> result = singleParamStatement(query, reactionId);
    return result.isPresent() && result.get().equals("t");
  }


  /**
   * @param synonym
   * @param type
   * @param dataSourceId
   * @return String
   */
  public static Optional<String> getBiggIdFromSynonym(String dataSourceId, String synonym, String type) {
    Set<String> results = new HashSet<>();
    String query;
    switch (type) {
    case TYPE_SPECIES:
      query = "SELECT " + "c." + BIGG_ID + " FROM " + COMPONENT + " c, " + DATA_SOURCE + " d, " + SYNONYM + " s"
        + " WHERE " + "d." + BIGG_ID + " = ? AND d." + ID + " = s." + DATA_SOURCE_ID + " AND s." + SYNONYM_COL
        + " = ? AND s." + OME_ID + " = c." + ID;
      break;
    case TYPE_REACTION:
      query = "SELECT " + "r." + BIGG_ID + " FROM " + REACTION + " r, " + DATA_SOURCE + " d, " + SYNONYM + " s"
        + " WHERE " + "d." + BIGG_ID + " = ? AND d." + ID + " = s." + DATA_SOURCE_ID + " AND s." + SYNONYM_COL
        + " = ? AND s." + OME_ID + " = r." + ID;
      break;
    case TYPE_GENE_PRODUCT:
      query = "SELECT " + "g." + LOCUS_TAG + " FROM " + GENE + " g, " + DATA_SOURCE + " d, " + SYNONYM + " s"
        + " WHERE " + "d." + BIGG_ID + " = ? AND d." + ID + " = s." + DATA_SOURCE_ID + " AND s." + SYNONYM_COL
        + " = ? AND s." + OME_ID + " = g." + ID;
      break;
    default:
      return Optional.empty();
    }
    try {
      Connection connection = connector.getConnection();
      PreparedStatement pStatement = connection.prepareStatement(query);
      pStatement.setString(1, dataSourceId);
      pStatement.setString(2, synonym);
      ResultSet resultSet = pStatement.executeQuery();
      while (resultSet.next()) {
        results.add(resultSet.getString(1));
      }
      connection.close();
    } catch (SQLException exc) {
      logger.warning(Utils.getMessage(exc));
    }
    results = results.stream().filter(biggId -> biggId != null && !biggId.isEmpty()).collect(Collectors.toSet());
    if (results.size() == 1) {
      return Optional.of(results.iterator().next());
    } else {
      return Optional.empty();
    }
  }
}
