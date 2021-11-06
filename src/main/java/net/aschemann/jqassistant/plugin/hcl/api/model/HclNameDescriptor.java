package net.aschemann.jqassistant.plugin.hcl.api.model;

import com.buschmais.xo.neo4j.api.annotation.Label;

@Label("Name")
public interface HclNameDescriptor extends HclDescriptor {
    String getValue();

    void setValue(final String value);

    Integer getOrder();

    void setOrder(final Integer order);
}
