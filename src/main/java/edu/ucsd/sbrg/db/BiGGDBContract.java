package edu.ucsd.sbrg.db;

public final class BiGGDBContract {

  BiGGDBContract() {
  }

  public static abstract class Constants {

    public static class Table {

      static final String COMPARTMENT = "compartment";
      static final String COMPARTMENTALIZED_COMPONENT = "compartmentalized_component";
      static final String COMPONENT = "component";
      static final String DATABASE_VERSION = "database_version";
      static final String DATA_SOURCE = "data_source";
      static final String GENE = "gene";
      static final String GENOME = "genome";
      static final String GENOME_REGION = "genome_region";
      static final String MCC = "model_compartmentalized_component";
      static final String MODEL = "model";
      static final String MODEL_REACTION = "model_reaction";
      static final String OLD_BIGG_ID = "'old_bigg_id'";
      static final String PUBLICATION = "publication";
      static final String PUBLICATION_MODEL = "publication_model";
      static final String REACTION = "reaction";
      static final String REFSEQ_PATTERN = "'refseq_%%'";
      static final String REFSEQ_NAME = "'refseq_name'";
      static final String SYNONYM = "synonym";
      static final String URL = "url";
      static final String URL_PREFIX = "url_prefix";
    }

    public static class Column {

      static final String ACCESSION_VALUE = "accesion_value";
      static final String BIGG_ID = "bigg_id";
      static final String CHARGE = "charge";
      static final String COMPARTMENTALIZED_COMPONENT_ID = "compartmentalized_component_id";
      static final String COMPARTMENT_ID = "compartment_id";
      static final String COMPONENT_ID = "component_id";
      static final String DATA_SOURCE_ID = "data_source_id";
      static final String DATE_TIME = "date_time";
      static final String DESCRIPTION = "description";
      static final String FIRST_CREATED = "first_created";
      static final String FORMULA = "formula";
      static final String GENE_REACTION_RULE = "gene_reaction_rule";
      static final String GENOME_ID = "genome_id";
      static final String ID = "id";
      static final String MODEL_ID = "model_id";
      static final String NAME = "name";
      static final String OME_ID = "ome_id";
      static final String ORGANISM = "organism";
      static final String PSEUDOREACTION = "pseudoreaction";
      static final String PUBLICATION_ID = "publication_id";
      static final String REACTION_ID = "reaction_id";
      static final String REFERENCE_ID = "reference_id";
      static final String REFERENCE_TYPE = "reference_type";
      static final String SUBSYSTEM = "subsystem";
      static final String TAXON_ID = "taxon_id";
      static final String TYPE = "type";
      static final String SYNONYM_COL = "synonym";
      static final String LOCUS_TAG = "locus_tag";
    }

    // Other constants
    public static final String TYPE_SPECIES = "SPECIES";
    public static final String TYPE_REACTION = "REACTION";
    public static final String TYPE_GENE_PRODUCT = "GENE_PRODUCT";
  }
}
