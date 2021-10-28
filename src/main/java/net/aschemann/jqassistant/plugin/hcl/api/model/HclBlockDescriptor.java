package net.aschemann.jqassistant.plugin.hcl.api.model;


import com.buschmais.xo.neo4j.api.annotation.Label;
import com.buschmais.xo.neo4j.api.annotation.Property;
import com.buschmais.xo.neo4j.api.annotation.Relation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Label("Block")
public interface HclBlockDescriptor extends HclIdentifiedDescriptor {
    @Property("HAS TYPE")
    String getType();
    void setType(final String type);

    @Relation("HAS_NAME")
    List<HclNameDescriptor> getNames();

    @Relation("HAS_ATTRIBUTE")
    List<HclAttributeDescriptor> getAttributes();

    @Relation("HAS_BLOCK")
    List<HclBlockDescriptor> getBlocks();
}
