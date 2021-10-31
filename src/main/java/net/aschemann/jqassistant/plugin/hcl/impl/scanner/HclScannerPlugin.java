package net.aschemann.jqassistant.plugin.hcl.impl.scanner;

import com.bertramlabs.plugins.hcl4j.HCLConfiguration;
import com.bertramlabs.plugins.hcl4j.HCLParser;
import com.bertramlabs.plugins.hcl4j.HCLParserException;
import com.bertramlabs.plugins.hcl4j.RuntimeSymbols.Variable;
import com.bertramlabs.plugins.hcl4j.symbols.HCLAttribute;
import com.bertramlabs.plugins.hcl4j.symbols.HCLBlock;
import com.bertramlabs.plugins.hcl4j.symbols.HCLValue;
import com.bertramlabs.plugins.hcl4j.symbols.Symbol;
import com.buschmais.jqassistant.core.scanner.api.ScannerContext;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.core.store.api.Store;
import com.buschmais.jqassistant.plugin.common.api.scanner.AbstractDirectoryScannerPlugin;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclAttributeDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclBlockDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclConfigurationDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclConfiguredDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclFileDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclIdentifiedDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.aschemann.jqassistant.plugin.hcl.api.HclScope.HCL;

/**
 * A HCL scanner plugin.
 */
//@ScannerPlugin.Requires(DirectoryDescriptor.class)
public class HclScannerPlugin extends AbstractDirectoryScannerPlugin<HclConfigurationDescriptor> {

    private static final String CONTEXT_DELIMITER_RE = "\\.";
    private static final Logger LOGGER = LoggerFactory.getLogger(HclScannerPlugin.class);

    private final HCLParser hclParser = new HCLParser();
    private HclObjectStore objects;
    private Store store;
    private String currentFilename;

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
        store = scannerContext.getStore();
        objects = new HclObjectStore(containerDescriptor, null, "ROOT");

        for (File file : container.listFiles(filterHclFiles())) {
            currentFilename = file.getName();
            HclFileDescriptor hclFileDescriptor = scan(file);
            containerDescriptor.getFiles().add(hclFileDescriptor);
        }

    }

    private FileFilter filterHclFiles() {
        return (file) -> {
            final String name = file.getName();
            final boolean decision = file.isFile()
                    && !name.startsWith(".terraform")
                    && (
                    name.endsWith(".tf")
                            || name.endsWith(".tfvars")
                            || name.endsWith(".hcl")
            );
            LOGGER.debug("HCL: Checking '{}' ('{}') for acceptance: {}", name, file.getPath(), decision);
            return decision;
        };
    }

    @Override
    protected void leaveContainer(File container, HclConfigurationDescriptor containerDescriptor,
                                  ScannerContext scannerContext) throws IOException {
        LOGGER.debug("Leaving Directory '{}'", container.getPath());
    }

    private HclFileDescriptor scan(final File item) throws IOException {
        try {
            HCLConfiguration hclConfiguration = hclParser.parseConfiguration(item);
            return objects.add(hclConfiguration);
        } catch (HCLParserException e) {
            LOGGER.error("Could not read HCL file '{}': {}", currentFilename, e, e);
        }
        return null;
    }

    class HclObjectStore {
        final Map<String, HclObjectStore> contained = new HashMap<>();
        final HclObjectStore parent;
        final String name;
        HclDescriptor delegate;

        HclObjectStore(final HclDescriptor delegate, final HclObjectStore parent, final String name) {
            this.delegate = delegate;
            this.parent = parent;
            this.name = name;
            LOGGER.debug("Created: '{}'", fullyQualifiedName());
        }

        void add(String name, final HclObjectStore hclObjectStore) {
            if (!contained.containsKey(name)) {
                contained.put(name, hclObjectStore);
            } else {
                if (!(delegate instanceof HclBlockDescriptor)) {
                    throw new IllegalArgumentException(String.format("HCL element '%s' must not contain sub "
                                    + "elements (name: '%s', type: '%s')", name,
                            hclObjectStore.delegate.getId(), hclObjectStore.delegate.getClass()));
                }
                hclObjectStore.contained.forEach((n, o) -> {
                    LOGGER.debug("{}: Merging in '{}'", fullyQualifiedName(), n);
                    contained.get(name).add(n, o);
                });
            }
        }

        HclObjectStore find(String qualifiedName) {
            List<String> qualifiedNameElems = Arrays.asList(qualifiedName.split(CONTEXT_DELIMITER_RE));
            return find(qualifiedNameElems);
        }

        HclObjectStore find(final List<String> qualifiedNameElems) {
            if (qualifiedNameElems.size() == 0) {
                return this;
            }
            final String currentQualifiedName = qualifiedNameElems.get(0);
            if (contained.containsKey(currentQualifiedName)) {
                return contained.get(currentQualifiedName).find(qualifiedNameElems.subList(1,
                        qualifiedNameElems.size()));
            } else {
                if (qualifiedNameElems.size() == 1) {
                    if (delegate instanceof HclBlockDescriptor) {
                        LOGGER.debug("'{}' adding implicit attribute '{}'", fullyQualifiedName(),
                                currentQualifiedName);
                        HclObjectStore result = this.add(
                                new HCLAttribute(currentQualifiedName, -1, -1, -1));
                        return result;
                    } else {
                        throw new RuntimeException(String.format("'%s' must not contain element '%s'",
                                fullyQualifiedName(), currentQualifiedName));
                    }
                }
            }
            throw new RuntimeException(String.format("'%s' does not contain element '%s'", fullyQualifiedName(),
                    currentQualifiedName));
        }

        private String fullyQualifiedName() {
            if (null == parent) {
                return name;
            }
            return parent.fullyQualifiedName() + ":" + this.name;
        }

        HclFileDescriptor add(final HCLConfiguration hclConfiguration) {
            final HclFileDescriptor result = store.create(HclFileDescriptor.class);
            result.setFileName(currentFilename);
            final HclObjectStore resultStore = new HclObjectStore(result, objects, currentFilename);
            add(currentFilename, resultStore);
//            ((HclBlockDescriptor) this.delegate).getBlocks().add(result);

            for (HCLAttribute hclAttribute : hclConfiguration.getAttributes()) {
                result.getAttributes().add((HclAttributeDescriptor) objects.add(hclAttribute).delegate);
            }
            for (HCLBlock hclBlock : hclConfiguration.getBlocks()) {
                // TODO do this only for Terraform!
                // TODO do this only on top level?
                HclObjectStore hclBlockObjectStore = objects.add(hclBlock);
                result.getBlocks().add((HclBlockDescriptor) hclBlockObjectStore.delegate);
                if ("data".equals(hclBlock.getName())) {
                    add("data", hclBlockObjectStore);
                } else if ("locals".equals(hclBlock.getName())) {
                    add("local", hclBlockObjectStore);
                } else if ("variable".equals(hclBlock.getName())) {
                    add("var", hclBlockObjectStore);
                } else {
                    hclBlockObjectStore.contained.forEach((String name, HclObjectStore objectStore)-> {
                        add(name, objectStore);
                    });
                }
            }
            return result;
        }

        HclObjectStore add(final HCLBlock hclBlock) {
            return add(hclBlock.blockNames, hclBlock);
        }

        HclObjectStore add(final List<String> blockNames, final HCLBlock hclBlock) {
            String name = blockNames.get(0);
            if (1 == blockNames.size()) {
                return add(name, hclBlock);
            }
            HclBlockDescriptor result = store.create(HclBlockDescriptor.class);
            result.setName(name);
            ((HclBlockDescriptor) this.delegate).getBlocks().add(result);
            HclObjectStore resultStore = new HclObjectStore(result, this, name);
            add(name, resultStore);
            resultStore.add(blockNames.subList(1, blockNames.size()), hclBlock);
            return resultStore;
        }

        HclObjectStore add(final String name, final HCLBlock hclBlock) {
            final HclBlockDescriptor result = create(hclBlock, HclBlockDescriptor.class);
            result.setName(name);
            final HclObjectStore resultStore = new HclObjectStore(result, this, name);
            add(name, resultStore);
            ((HclBlockDescriptor) this.delegate).getBlocks().add(result);

            for (Symbol child : hclBlock.getChildren()) {
                if (child instanceof HCLAttribute) {
                    resultStore.add((HCLAttribute) child);
                } else if (child instanceof HCLBlock) {
                    resultStore.add((HCLBlock) child);
                } else {
                    LOGGER.warn("{}: Block '{}' has unknown child type '{}' in context '{}'",
                            currentFilename, hclBlock.getSymbolName(), child.getClass().getName(),
                            fullyQualifiedName());
                }
            }
            return resultStore;
        }

        private HclObjectStore add(final HCLAttribute hclAttribute) {
            final HclAttributeDescriptor result = create(hclAttribute, HclAttributeDescriptor.class);
            final HclObjectStore resultStore = new HclObjectStore(result, this, hclAttribute.getName());
            add(hclAttribute.getName(), resultStore);
            ((HclBlockDescriptor) this.delegate).getAttributes().add(result);

            for (Symbol child : hclAttribute.getChildren()) {
                if (child instanceof HCLValue) {
                    result.setValue((String) ((HCLValue) child).value);
                } else if (child instanceof HCLAttribute || child instanceof Variable) {
                    HclDescriptor hclDescriptor = objects.find(child.getName()).delegate;
                    if (null != hclDescriptor) {
                        result.setReference((HclAttributeDescriptor) hclDescriptor);
                    }
                } else {
                    LOGGER.warn("{}: Attribute '{}' has unknown child type '{}' in context '{}'",
                            currentFilename, hclAttribute.getName(), child.getClass().getName(), fullyQualifiedName());
                }
            }
            return resultStore;
        }

        private <HclConfiguredSub extends HclConfiguredDescriptor> void setConfiguredValues(final Symbol symbol,
                                                                                            HclConfiguredSub configured) {
            configured.setLine(symbol.getLine());
            configured.setColumn(symbol.getColumn());
            configured.setPosition(symbol.getPosition());
        }

        private <HclIdentifiedSub extends HclIdentifiedDescriptor> HclIdentifiedSub create(final Symbol symbol,
                                                                                           Class<HclIdentifiedSub> identifiedClass) {
            HclIdentifiedSub identified = store.create(identifiedClass);
            identified.setIdentifier(symbol.getName());
            setConfiguredValues(symbol, identified);
            return identified;
        }
    }
}