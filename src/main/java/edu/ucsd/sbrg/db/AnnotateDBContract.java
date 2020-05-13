package edu.ucsd.sbrg.db;

public final class AnnotateDBContract {

  public static abstract class Constants {

    public static class Table {

      static final String MAPPING_VIEW = "mapping_view";
      static final String ADB_COLLECTION = "adb_collection";
    }

    public static class Column {

      static final String ID = "id";
      static final String SOURCE_NAMESPACE = "source_namespace";
      static final String SOURCE_MIRIAM = "source_miriam";
      static final String SOURCE_TERM = "source_term";
      static final String QUALIFIER = "qualifier";
      static final String TARGET_NAMESPACE = "target_namespace";
      static final String TARGET_MIRIAM = "target_miriam";
      static final String TARGET_TERM = "target_term";
      static final String EVIDENCE_SOURCE = "evidence_source";
      static final String EVIDENCE_VERSION = "evidence_version";
      static final String EVIDENCE = "evidence";
      static final String NAMESPACE = "namespace";
      static final String URLPATTERN = "urlpattern";
    }

    // Other constants
    public static final String BIGG_METABOLITE = "bigg.metabolite";
    public static final String BIGG_REACTION = "bigg.reaction";
    static final String METABOLITE_PREFIX = "M_";
    static final String REACTION_PREFIX = "R_";
    static final String GENE_PREFIX = "G_";
  }
}
