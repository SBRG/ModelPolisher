package edu.ucsd.sbrg.miriam;

import edu.ucsd.sbrg.miriam.models.Resource;

public class CompactResource {

  private final long id;
  private final String providerCode;
  private final String sampleId;
  private final String urlPattern;
  private final boolean deprecated;
  private final boolean official;

  private CompactResource(Resource resource) {
    id = resource.getId();
    providerCode = resource.getProviderCode();
    sampleId = resource.getSampleId();
    urlPattern = resource.getUrlPattern();
    deprecated = resource.isDeprecated();
    official = resource.isOfficial();
  }


  public static CompactResource fromResource(Resource resource) {
    return new CompactResource(resource);
  }


  public long getId() {
    return id;
  }


  public String getProviderCode() {
    return providerCode;
  }


  public String getSampleId() {
    return sampleId;
  }


  public String getUrlPattern() {
    return urlPattern;
  }


  public boolean isDeprecated() {
    return deprecated;
  }


  public boolean isOfficial() {
    return official;
  }
}
