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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        store = scannerContext.getStore();
        objects = new HclObjectStore(containerDescriptor, null, "ROOT");

        for (File file : container.listFiles(filterHclFiles())) {
            currentFilename = file.getName();
            add(containerDescriptor, file);
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
            LOGGER.debug("Checking '{}' ('{}') for acceptance: {}", name, file.getPath(), decision);
            return decision;
        };
    }

    @Override
    protected void leaveContainer(File container, HclConfigurationDescriptor containerDescriptor,
                                  ScannerContext scannerContext) {
        LOGGER.debug("Leaving Directory '{}'", container.getPath());
    }

    private void add(final HclConfigurationDescriptor containerDescriptor, final File item) throws IOException {
        try {
            HCLConfiguration hclConfiguration = hclParser.parseConfiguration(item);
            containerDescriptor.getFiles().add(objects.add(hclConfiguration));
        } catch (HCLParserException e) {
            LOGGER.error("Could not read HCL file '{}': {}", currentFilename, e, e);
        }
    }

    class HclObjectStore {
        final Map<String, HclObjectStore> contained = new HashMap<>();
        final HclObjectStore parent;
        final String name;
        Optional<HclDescriptor> delegate = Optional.empty();

        HclObjectStore(@NotNull final HclDescriptor delegate, final HclObjectStore parent, final String name) {
            this(parent, name);
            this.delegate = Optional.of(delegate);
        }

        HclObjectStore(final HclObjectStore parent, final String name) {
            this.parent = parent;
            this.name = name;
            LOGGER.debug("'{}': created ({})", fullyQualifiedName(), hashCode());
        }

        void add(final String name, final HclObjectStore hclObjectStore) {
            replaceDelegateIfUnconfigured(hclObjectStore);
            if (contained.containsKey(name)) {
                LOGGER.debug("'{} ({})': merge in '{} ({})'", hclObjectStore.fullyQualifiedName(), hashCode(), name,
                        contained.get(name).hashCode());
                if (delegate.isPresent() && !(delegate.get() instanceof HclBlockDescriptor)) {
                    throw new IllegalArgumentException(String.format("HCL element '%s' must not contain sub "
                                    + "elements (name: '%s', type: '%s')", name,
                            hclObjectStore.delegate.get().getId(), hclObjectStore.delegate.getClass()));
                }
                hclObjectStore.contained.forEach((final String key, final HclObjectStore value) -> contained.get(name).add(key, value));
            } else {
                LOGGER.debug("'{} ({})': add in '{} ({})'", hclObjectStore.fullyQualifiedName(), hashCode(), name,
                        hclObjectStore.hashCode());
                contained.put(name, hclObjectStore);
            }
        }

        private void replaceDelegateIfUnconfigured(HclObjectStore hclObjectStore) {
            if (delegate.isPresent()) {
                if (hclObjectStore.parent.delegate.isPresent()) {
                    if (delegate == hclObjectStore.parent.delegate) {
                        LOGGER.debug("'{}': delegate is equal to '{}'", fullyQualifiedName(),
                                hclObjectStore.parent.fullyQualifiedName());
                    } else {
                        HclDescriptor me = delegate.get();
                        if (me instanceof HclConfiguredDescriptor) {
                            if (((HclConfiguredDescriptor) me).isConfigured()) {
                                LOGGER.warn("'{}': not overriding existing delegate '{}' by '{}' from '{}'",
                                        fullyQualifiedName(), delegate, hclObjectStore.parent.delegate,
                                        hclObjectStore.parent.fullyQualifiedName());
                            } else {
                                LOGGER.debug("'{}': Replacing unconfigured delegate by '{}'", fullyQualifiedName(),
                                        hclObjectStore.parent.delegate);
                                delegate = hclObjectStore.parent.delegate;
                            }
                        }
                    }
                }
            } else {
                if (hclObjectStore.parent.delegate.isPresent()) {
                    LOGGER.debug("'{}': merge in delegate (parent) from '{}'",
                            hclObjectStore.parent.fullyQualifiedName(),
                            hclObjectStore.fullyQualifiedName());
                    delegate = hclObjectStore.parent.delegate;
                }
            }
        }

        HclObjectStore find(String qualifiedName) {
            LOGGER.debug("'{}': Searching for '{}'", fullyQualifiedName(), qualifiedName);
            List<String> qualifiedNameElems = Arrays.asList(qualifiedName.split(CONTEXT_DELIMITER_RE));
            return find(qualifiedNameElems);
        }

        HclObjectStore find(final List<String> qualifiedNameElems) {
            if (qualifiedNameElems.size() == 0) {
                return this;
            }
            final String currentQualifiedName = qualifiedNameElems.get(0);
            if (contained.containsKey(currentQualifiedName)) {
                LOGGER.debug("'{}': Found existing ObjectStore '{}'", fullyQualifiedName(),
                        contained.get(currentQualifiedName).hashCode());
            } else {
                if (qualifiedNameElems.size() == 1) {
                    LOGGER.debug("'{}': add implicit '{}' ({})", fullyQualifiedName(),
                            currentQualifiedName, hashCode());
                    return this.add(new HCLAttribute(currentQualifiedName, -1, -1, -1L));
                }
                LOGGER.debug("'{}': add fake '{}' ({})", fullyQualifiedName(), currentQualifiedName, hashCode());
                HclObjectStore fake = new HclObjectStore(this, currentQualifiedName);
                add(currentQualifiedName, fake);
            }
            return contained.get(currentQualifiedName).find(qualifiedNameElems.subList(1,
                    qualifiedNameElems.size()));
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
            resultStore.addChildren(hclConfiguration);
            return result;
        }

        private void addChildren(HCLConfiguration hclConfiguration) {
            for (HCLAttribute hclAttribute : hclConfiguration.getAttributes()) {
                add(hclAttribute);
            }
            for (HCLBlock hclBlock : hclConfiguration.getBlocks()) {
                HclObjectStore hclObjectStore = add(hclBlock);
                if ("data".equals(hclBlock.getName())) {
                    objects.add("data", hclObjectStore);
                } else if ("locals".equals(hclBlock.getName())) {
                    objects.add("local", hclObjectStore);
                } else if ("variable".equals(hclBlock.getName())) {
                    objects.add("var", hclObjectStore);
                } else {
                    hclObjectStore.contained.forEach((final String name, final HclObjectStore objectStore) -> objects.add(name, objectStore));
                }
            }
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
            ((HclBlockDescriptor) this.delegate.get()).getBlocks().add(result);
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
            ((HclBlockDescriptor) this.delegate.get()).getBlocks().add(result);
            resultStore.addChildren(hclBlock);
            return resultStore;
        }

        private void addChildren(HCLBlock hclBlock) {
            for (Symbol child : hclBlock.getChildren()) {
                if (child instanceof HCLAttribute) {
                    add((HCLAttribute) child);
                } else if (child instanceof HCLBlock) {
                    add((HCLBlock) child);
                } else {
                    LOGGER.warn("{}: Block '{}' has unknown child type '{}' in context '{}'",
                            currentFilename, hclBlock.getSymbolName(), child.getClass().getName(),
                            fullyQualifiedName());
                }
            }
        }

        private HclObjectStore add(final HCLAttribute hclAttribute) {
            final HclAttributeDescriptor result = create(hclAttribute, HclAttributeDescriptor.class);
            final HclObjectStore resultStore = new HclObjectStore(result, this, hclAttribute.getName());
            add(hclAttribute.getName(), resultStore);
            if (this.delegate.isPresent()) {
                ((HclBlockDescriptor) this.delegate.get()).getAttributes().add(result);
            } else {
                LOGGER.debug("'{}': No embracing block present", fullyQualifiedName());
            }

            for (Symbol child : hclAttribute.getChildren()) {
                if (child instanceof HCLValue) {
                    result.setValue((String) ((HCLValue) child).getValue());
                } else if (child instanceof HCLAttribute || child instanceof Variable) {
                    String childName = child.getName();
                    Optional<HclDescriptor> hclDescriptor = objects.find(childName).delegate;
                    if (hclDescriptor.isPresent()) {
                        LOGGER.debug("'{} ({})': set reference for '{}' to '{}'", fullyQualifiedName(), hashCode(),
                                childName, hclDescriptor.get());
                        result.setReference((HclIdentifiedDescriptor) hclDescriptor.get());
                    } else {
                        LOGGER.error("No Reference Attribute present for '{}'", child.getName());
                    }
                } else {
                    LOGGER.warn("{}: Attribute '{}' has unknown child type '{}' in context '{}'",
                            currentFilename, hclAttribute.getName(), child.getClass().getName(),
                            fullyQualifiedName());
                }
            }
            return resultStore;
        }

        private <HclConfiguredSub extends HclConfiguredDescriptor> void setConfiguredValues(final Symbol symbol,
                                                                                            HclConfiguredSub configured) {
            configured.setLine(symbol.getLine());
            configured.setColumn(symbol.getColumn());
            configured.setPosition(symbol.getPosition());
//            configured.setConfigured(true);
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