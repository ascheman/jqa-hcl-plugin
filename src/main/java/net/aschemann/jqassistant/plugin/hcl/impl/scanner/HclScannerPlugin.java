package net.aschemann.jqassistant.plugin.hcl.impl.scanner;

import com.bertramlabs.plugins.hcl4j.HCLConfiguration;
import com.bertramlabs.plugins.hcl4j.HCLParser;
import com.bertramlabs.plugins.hcl4j.HCLParserException;
import com.bertramlabs.plugins.hcl4j.RuntimeSymbols.Variable;
import com.bertramlabs.plugins.hcl4j.symbols.HCLAttribute;
import com.bertramlabs.plugins.hcl4j.symbols.HCLBlock;
import com.bertramlabs.plugins.hcl4j.symbols.HCLValue;
import com.bertramlabs.plugins.hcl4j.symbols.Symbol;
import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.ScannerContext;
import com.buschmais.jqassistant.core.scanner.api.ScannerPlugin;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.core.store.api.Store;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.jqassistant.plugin.common.api.scanner.AbstractScannerPlugin;
import com.buschmais.jqassistant.plugin.common.api.scanner.filesystem.FileResource;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclAttributeDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclBlockDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclConfigurationDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclConfiguredDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclIdentifiedDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A HCL scanner plugin.
 */
// This plugin takes the file descriptor created by the file scanner plugin as input.
@ScannerPlugin.Requires(FileDescriptor.class)
public class HclScannerPlugin extends AbstractScannerPlugin<FileResource, HclDescriptor> {

    private static final String CONTEXT_DELIMITER_RE = "\\.";
    private static final Logger LOGGER = LoggerFactory.getLogger(HclScannerPlugin.class);

    private static HclObjectStore objects;
    static {
        resetObjectStore();
    }

    public static void resetObjectStore() {
        objects = new HclObjectStore("ROOT");
    }

    @Override
    public boolean accepts(FileResource item, String path, Scope scope) {
        String lowercasePath = path.toLowerCase();
        boolean decision = lowercasePath.endsWith(".tf")
                || lowercasePath.endsWith(".tfvars")
                || lowercasePath.endsWith(".hcl");
        LOGGER.debug("HCL: Checking '{}' ('{}') for acceptance: {}", path, lowercasePath, decision);
        return decision;
    }

    @Override
    public HclConfigurationDescriptor scan(FileResource item, String path, Scope scope, Scanner scanner)
            throws IOException {
        ScannerContext context = scanner.getContext();
        LOGGER.debug("HCL: Creating new Descriptor from '{}'", item.getFile().getPath());
        final Store store = context.getStore();
        objects.initDelegate(store);
        final FileDescriptor fileDescriptor = context.getCurrentDescriptor();
        final HclConfigurationDescriptor hclConfigurationDescriptor
                = store.addDescriptorType(fileDescriptor, HclConfigurationDescriptor.class);
        final String[] names = path.split("/");
        final String name = names[names.length - 1];
        final HclObjectStore hclObjectStore = new HclObjectStore(hclConfigurationDescriptor, objects, name);
        objects.add (name, hclObjectStore);
        HCLParser hclParser = new HCLParser();
        try {
            HCLConfiguration hclConfiguration = hclParser.parseConfiguration(item.getFile());
            for (HCLAttribute hclAttribute : hclConfiguration.getAttributes()) {
                objects.create(store, hclAttribute, path);
            }
            for (HCLBlock hclBlock : hclConfiguration.getBlocks()) {
                // TODO do this only for Terraform!
                // TODO do this only on top level?
                if ("data".equals(hclBlock.getName())) {
                    hclObjectStore.create(store, hclBlock, path);
                } else if ("locals".equals(hclBlock.getName())) {
                    List<String> blockNames = new ArrayList<>(hclBlock.blockNames);
                    blockNames.set(0, "local");
                    hclObjectStore.create(store, blockNames, hclBlock, path);
                } else if ("variable".equals(hclBlock.getName())) {
                    List<String> blockNames = new ArrayList<>(hclBlock.blockNames);
                    blockNames.set(0, "var");
                    hclObjectStore.create(store, blockNames, hclBlock, path);
                } else {
                    hclObjectStore.create(store, hclBlock.blockNames.subList(1, hclBlock.blockNames.size()), hclBlock, path);
                }
            }
        } catch (HCLParserException e) {
            LOGGER.error("Could not read HCL file '{}': {}", path, e, e);
        }
        return hclConfigurationDescriptor;
    }

    static class HclObjectStore {
        final Map<String, HclObjectStore> contained = new HashMap<>();
        HclDescriptor delegate;
        final HclObjectStore parent;
        final String name;

        HclObjectStore(final String name) {
            this(null, null, name);
        }

        HclObjectStore(final HclDescriptor delegate, final String name) {
            this(delegate, null, name);
        }

        HclObjectStore(final HclDescriptor delegate, final HclObjectStore parent, final String name) {
            this.delegate = delegate;
            this.parent = parent;
            this.name = name;
            LOGGER.debug("Created: '{}'", fullyQualifiedName());
        }

        protected void initDelegate(final Store store) {
            if (null != this.delegate) {
//                throw new IllegalArgumentException(String.format ("Objectstore '%s' already has delegate!",
//                        fullyQualifiedName()));
                LOGGER.trace("Objectstore '{}' already has delegate!", fullyQualifiedName());
                return;
            }
            this.delegate = store.create(HclBlockDescriptor.class);
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
            }
        }

        HclDescriptor find(String qualifiedName, final Store store, final String path) {
            List<String> qualifiedNameElems = Arrays.asList(qualifiedName.split(CONTEXT_DELIMITER_RE));
            return find(qualifiedNameElems, store, path);
        }

        HclDescriptor find(final List<String> qualifiedNameElems, final Store store, final String path) {
            if (qualifiedNameElems.size() == 0) {
                return delegate;
            }
            final String currentQualifiedName = qualifiedNameElems.get(0);
            if (contained.containsKey(currentQualifiedName)) {
                return contained.get(currentQualifiedName).find(qualifiedNameElems.subList(1,
                        qualifiedNameElems.size()), store, path);
            } else {
                if (qualifiedNameElems.size() == 1) {
                    if (delegate instanceof HclBlockDescriptor) {
                        LOGGER.debug("'{}' adding implicit attribute '{}'", fullyQualifiedName(),
                                currentQualifiedName);
                        HclAttributeDescriptor result = this.create(store,
                                new HCLAttribute(currentQualifiedName, -1, -1, -1), path);
                        ((HclBlockDescriptor) delegate).getAttributes().add(result);
//                        contained.put(currentQualifiedName, new HclObjectStore(result, this, currentQualifiedName));
                        return result;
                    } else {
                        throw new RuntimeException(String.format("'%s' with id '%s' must not contain element '%s'",
                                fullyQualifiedName(),
                                delegate.getId(), currentQualifiedName));
                    }
                }
            }
            LOGGER.error("'{}' does not contain element '{}'", fullyQualifiedName(), currentQualifiedName);
            return null;
        }

        private String fullyQualifiedName() {
            if (null == parent) {
                return name;
            }
            return parent.fullyQualifiedName() + ":" + this.name;
        }

        HclBlockDescriptor create(final Store store, final HCLBlock hclBlock, final String path) {
            return create(store, hclBlock.blockNames, hclBlock, path);
        }

        HclBlockDescriptor create(final Store store, final List<String> blockNames, final HCLBlock hclBlock,
                                  final String path) {
            String name = blockNames.get(0);
            if (1 == blockNames.size()) {
                return create(store, name, hclBlock, path);
            }
            HclBlockDescriptor result = store.create(HclBlockDescriptor.class);
            ((HclBlockDescriptor) this.delegate).getBlocks().add(result);
//            result.setType(name);
            HclObjectStore resultStore = new HclObjectStore(result, this, name);
            add(name, resultStore);
            HclBlockDescriptor subBlock = resultStore.create(store, blockNames.subList(1, blockNames.size()), hclBlock,
                    path);
            return result;
        }

        HclBlockDescriptor create(final Store store, final String name, final HCLBlock hclBlock,
                                  final String path) {
            HclBlockDescriptor result = create(store, hclBlock, HclBlockDescriptor.class);
            HclObjectStore resultStore = new HclObjectStore(result, this, name);
            add(name, resultStore);
            ((HclBlockDescriptor) this.delegate).getBlocks().add(result);
//            int orderOfName = 0;
//            for (String name : hclBlock.blockNames.subList(1, hclBlock.blockNames.size())) {
//                HclNameDescriptor hclNameDescriptor = store.create(HclNameDescriptor.class);
//                hclNameDescriptor.setValue(name);
//                hclNameDescriptor.setOrder(orderOfName++);
//                result.getNames().add(hclNameDescriptor);
//            }
            for (Symbol child : hclBlock.getChildren()) {
                if (child instanceof HCLAttribute) {
                    // TODO Move this to the resultStore.create since it contains result as delegate
                    resultStore.create(store, (HCLAttribute) child, path);
                } else if (child instanceof HCLBlock) {
                    resultStore.create(store, (HCLBlock) child, path);
                } else {
                    LOGGER.warn("{}: Block '{}' has unknown child type '{}' in context '{}'",
                            path, hclBlock.getSymbolName(), child.getClass().getName(), fullyQualifiedName());
                }
            }
            return result;
        }

        private HclAttributeDescriptor create(Store store, final HCLAttribute hclAttribute, final String path) {
            HclAttributeDescriptor result = create(store, hclAttribute, HclAttributeDescriptor.class);
            HclObjectStore resultStore = new HclObjectStore(result, this, hclAttribute.getName());
            add(hclAttribute.getName(), resultStore);
            ((HclBlockDescriptor) this.delegate).getAttributes().add(result);
            for (Symbol child : hclAttribute.getChildren()) {
                if (child instanceof HCLValue) {
                    result.setValue((String) ((HCLValue) child).value);
                } else if (child instanceof HCLAttribute || child instanceof Variable) {
                    HclDescriptor hclDescriptor = objects.find(child.getName(), store, path);
                    if (null != hclDescriptor) {
                        result.setReference((HclAttributeDescriptor) hclDescriptor);
                    }
                } else {
                    LOGGER.warn("{}: Attribute '{}' has unknown child type '{}' in context '{}'",
                            path, hclAttribute.getName(), child.getClass().getName(), fullyQualifiedName());
                }
            }
            return result;
        }

        private <HclConfiguredSub extends HclConfiguredDescriptor> void setConfiguredValues(final Symbol symbol,
                                                                                            HclConfiguredSub configured) {
            configured.setLine(symbol.getLine());
            configured.setColumn(symbol.getColumn());
            configured.setPosition(symbol.getPosition());
        }

        private <HclIdentifiedSub extends HclIdentifiedDescriptor> HclIdentifiedSub create(final Store store,
                                                                                           final Symbol symbol,
                                                                                           Class<HclIdentifiedSub> identifiedClass) {
            HclIdentifiedSub identified = store.create(identifiedClass);
            identified.setIdentifier(symbol.getName());
            setConfiguredValues(symbol, identified);
            return identified;
        }
    }
}