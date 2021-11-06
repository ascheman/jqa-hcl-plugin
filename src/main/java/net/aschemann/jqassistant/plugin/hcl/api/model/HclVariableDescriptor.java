package net.aschemann.jqassistant.plugin.hcl.api.model;

import com.buschmais.xo.neo4j.api.annotation.Label;
import com.buschmais.xo.neo4j.api.annotation.Property;
import com.buschmais.xo.neo4j.api.annotation.Relation;

@Label("Variable")
public interface HclVariableDescriptor extends HclIdentifiedDescriptor {
    String getValue();

    void setValue(final String value);

    @Relation("REFERS_TO")
    HclAttributeDescriptor getReference();

    void setReference(final HclAttributeDescriptor reference);
}
