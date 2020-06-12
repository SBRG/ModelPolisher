package edu.ucsd.sbrg.miriam.models;

import java.util.Map;

public class Miriam {

  private static final Miriam miriam = new Miriam();
  private static Map<String, Namespace> namespaces;

  private Miriam() {
    super();
  }


  public static Miriam initFrom(Map<String, Namespace> namespaces) {
    Miriam.namespaces = namespaces;
    return miriam;
  }


  public Miriam getInstance() {
    if (miriam == null) {
      throw new IllegalStateException();
    }
    return miriam;
  }

  public Map<String, Namespace> getNamespaces(){
    return namespaces;
  }
}
