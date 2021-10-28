package net.aschemann.jqassistant.plugin.hcl.api.model;

import com.buschmais.jqassistant.core.store.api.model.Descriptor;
import com.buschmais.xo.neo4j.api.annotation.Label;

/**
 * Defines the label which is shared by all nodes representing HCL structures.
 */
@Label("HCL")
public interface HclDescriptor extends Descriptor {
}