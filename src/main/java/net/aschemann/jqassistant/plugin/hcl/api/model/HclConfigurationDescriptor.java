package net.aschemann.jqassistant.plugin.hcl.api.model;


import com.buschmais.jqassistant.plugin.common.api.model.DirectoryDescriptor;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.xo.neo4j.api.annotation.Label;
import com.buschmais.xo.neo4j.api.annotation.Property;
import com.buschmais.xo.neo4j.api.annotation.Relation;

import java.util.List;

@Label("Configuration")
public interface HclConfigurationDescriptor extends HclFileDescriptor, DirectoryDescriptor {
    @Relation("CONTAINS_FILE")
    List<HclFileDescriptor> getFiles();
}
