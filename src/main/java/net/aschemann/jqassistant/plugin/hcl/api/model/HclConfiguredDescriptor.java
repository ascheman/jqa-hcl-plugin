package net.aschemann.jqassistant.plugin.hcl.api.model;


import com.buschmais.xo.neo4j.api.annotation.Label;

@Label("Configured")
public interface HclConfiguredDescriptor extends HclDescriptor {
    int getLine();

    void setLine(final int line);

    int getColumn();

    void setColumn(final int column);

    int getPosition();

    void setPosition(final int position);
}
