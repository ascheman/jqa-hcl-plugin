package net.aschemann.jqassistant.plugin.hcl.api.model;


import com.buschmais.xo.neo4j.api.annotation.Label;

@Label("Identified")
public interface HclIdentifiedDescriptor extends HclConfiguredDescriptor {
    String getIdentifier();

    void setIdentifier(final String identifier);
}
