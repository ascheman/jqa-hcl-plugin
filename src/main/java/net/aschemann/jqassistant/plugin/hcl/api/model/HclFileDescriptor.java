package net.aschemann.jqassistant.plugin.hcl.api.model;

import com.buschmais.jqassistant.plugin.common.api.model.DirectoryDescriptor;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.xo.neo4j.api.annotation.Label;

@Label("File")
public interface HclFileDescriptor extends HclBlockDescriptor, FileDescriptor {
}
