package net.aschemann.jqassistant.plugin.hcl.impl.scanner;

import com.bertramlabs.plugins.hcl4j.HCLParser;
import com.buschmais.jqassistant.core.store.api.Store;

import javax.validation.constraints.NotNull;

public class HclScannerContext {
    private final Store store;
    private HclObjectStore root;
    private String currentFilename;
    private final HCLParser parser;

    public HclScannerContext(@NotNull final Store store, @NotNull final HCLParser parser) {
        this.store = store;
        this.parser = parser;
    }

    protected Store getStore() {
        return store;
    }

    protected HclObjectStore getRoot() {
        return root;
    }

    public void setRoot(HclObjectStore root) {
        this.root = root;
    }

    protected String getCurrentFilename() {
        return currentFilename;
    }

    protected void setCurrentFilename(String currentFilename) {
        this.currentFilename = currentFilename;
    }

    public HCLParser getParser() {
        return parser;
    }
}
