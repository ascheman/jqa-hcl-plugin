package net.aschemann.jqassistant.plugin.hcl.api.model;


import com.buschmais.xo.neo4j.api.annotation.Label;
import com.buschmais.xo.neo4j.api.annotation.Relation;

import java.util.List;

@Label("Block")
public interface HclBlockDescriptor extends HclIdentifiedDescriptor {
    String getName();

    void setName(final String name);

    @Relation("HAS_ATTRIBUTE")
    List<HclAttributeDescriptor> getAttributes();

    @Relation("HAS_BLOCK")
    List<HclBlockDescriptor> getBlocks();
}
