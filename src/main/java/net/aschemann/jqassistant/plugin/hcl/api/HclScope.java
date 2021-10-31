package net.aschemann.jqassistant.plugin.hcl.api;

import com.buschmais.jqassistant.core.scanner.api.Scope;

public enum HclScope implements Scope {

    HCL, TERRAFORM;

    @Override
    public String getPrefix() {
        return "hcl";
    }

    @Override
    public String getName() {
        return name();
    }
}
