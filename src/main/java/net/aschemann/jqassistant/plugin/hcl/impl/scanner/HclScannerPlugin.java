package net.aschemann.jqassistant.plugin.hcl.impl.scanner;

import com.bertramlabs.plugins.hcl4j.HCLConfiguration;
import com.bertramlabs.plugins.hcl4j.HCLParser;
import com.bertramlabs.plugins.hcl4j.HCLParserException;
import com.buschmais.jqassistant.core.scanner.api.ScannerContext;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.plugin.common.api.scanner.AbstractDirectoryScannerPlugin;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclConfigurationDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Optional;

import static net.aschemann.jqassistant.plugin.hcl.api.HclScope.HCL;

/**
 * A HCL scanner plugin.
 */
//@ScannerPlugin.Requires(DirectoryDescriptor.class)
public class HclScannerPlugin extends AbstractDirectoryScannerPlugin<HclConfigurationDescriptor> {

    private static final Logger LOGGER = LoggerFactory.getLogger(HclScannerPlugin.class);

    private final HCLParser hclParser = new HCLParser();
    private HclScannerContext ctx;

    @Override
    public boolean accepts(File item, String path, Scope scope) throws IOException {
        boolean accepts = super.accepts(item, path, scope);
        LOGGER.debug("Test for acceptance of file '{}' @ '{}': {}", item, path, scope);
        return accepts;
    }

    @Override
    protected Scope getRequiredScope() {
        return HCL;
    }

    @Override
    protected HclConfigurationDescriptor getContainerDescriptor(File container, ScannerContext scannerContext) {
        return scannerContext.getStore().create(HclConfigurationDescriptor.class);
    }

    @Override
    protected void enterContainer(@NotNull final File container, HclConfigurationDescriptor containerDescriptor,
                                  ScannerContext scannerContext) throws IOException {
        final String containerPath = container.getPath();
        LOGGER.debug("Entering Directory '{}'", containerPath);
        containerDescriptor.setName(containerPath);
        // TODO For some reason `setConfigured()` does not work -> setLine(0) is a work around!
        // containerDescriptor.setConfigured();
        containerDescriptor.setLine(0);
        ctx = new HclScannerContext(scannerContext.getStore(), hclParser);
        HclObjectStore root = new HclObjectStore(ctx, "ROOT", Optional.of(containerDescriptor));
        ctx.setRoot(root);

        for (File file : container.listFiles(HclObjectStore.filterHclFiles())) {
            root.add(containerDescriptor, file);
        }

    }

    @Override
    protected void leaveContainer(File container, HclConfigurationDescriptor containerDescriptor,
                                  ScannerContext scannerContext) {
        LOGGER.debug("Leaving Directory '{}'", container.getPath());
    }
}