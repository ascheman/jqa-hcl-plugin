package net.aschemann.jqassistant.plugin.hcl.impl.scanner;

import com.bertramlabs.plugins.hcl4j.HCLConfiguration;
import com.bertramlabs.plugins.hcl4j.HCLParserException;
import com.bertramlabs.plugins.hcl4j.RuntimeSymbols.Variable;
import com.bertramlabs.plugins.hcl4j.symbols.HCLAttribute;
import com.bertramlabs.plugins.hcl4j.symbols.HCLBlock;
import com.bertramlabs.plugins.hcl4j.symbols.HCLValue;
import com.bertramlabs.plugins.hcl4j.symbols.Symbol;
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

public class HclObjectStore {
    private static final String CONTEXT_DELIMITER_RE = "\\.";

    private static final Logger LOGGER = LoggerFactory.getLogger(HclObjectStore.class);

    private final Map<String, HclObjectStore> contained = new HashMap<>();
    private final HclScannerContext ctx;
    private final String name;
    private HclObjectStore parent = null;
    private Optional<HclDescriptor> delegate;

    public static FileFilter filterHclFiles() {
        return file -> {
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



    HclObjectStore(@NotNull HclScannerContext ctx, @NotNull final String name,
                   @NotNull final Optional<HclDescriptor> delegate) {
        this.ctx = ctx;
        this.name = name;
        this.delegate = delegate;
        LOGGER.debug("'{}': created ({})", fullyQualifiedName(), hashCode());
    }

    public HclObjectStore(@NotNull HclScannerContext ctx, final HclObjectStore parent, final String name) {
        this(ctx, name, Optional.empty());
        this.parent = parent;
    }

    HclObjectStore(final HclObjectStore parent, final String name) {
        this(parent.ctx, name, Optional.empty());
    }

    HclObjectStore(final HclObjectStore parent, final String name, @NotNull final Optional<HclDescriptor> delegate) {
        this(parent.ctx, name, delegate);
        this.parent = parent;
    }

    public void add(final HclConfigurationDescriptor containerDescriptor, final File item) throws IOException {
        ctx.setCurrentFilename(item.getName());
        try {
            HCLConfiguration hclConfiguration = ctx.getParser().parseConfiguration(item);
            containerDescriptor.getFiles().add(ctx.getRoot().add(hclConfiguration));
        } catch (HCLParserException e) {
            LOGGER.error("Could not read HCL file '{}': {}", ctx.getCurrentFilename(), e, e);
        }
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
            if (hclObjectStore.parent == null) {
                LOGGER.error ("'{}': This object seems to be the 'root'?", fullyQualifiedName());
            } else if (hclObjectStore.parent.delegate.isPresent()) {
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
            HclObjectStore fake = new HclObjectStore(ctx, this, currentQualifiedName);
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
        final HclFileDescriptor result = ctx.getStore().create(HclFileDescriptor.class);
        result.setFileName(ctx.getCurrentFilename());
        final HclObjectStore resultStore = new HclObjectStore(this, ctx.getCurrentFilename(), Optional.of(result));
        add(ctx.getCurrentFilename(), resultStore);
        resultStore.addChildren(hclConfiguration);
        return result;
    }

    private void addChildren(HCLConfiguration hclConfiguration) {
        for (HCLAttribute hclAttribute : hclConfiguration.getAttributes()) {
            ctx.getRoot().add(hclAttribute.getName(), add(hclAttribute)); // Why are we missing other and seven from
            // the ROOT?
        }
        for (HCLBlock hclBlock : hclConfiguration.getBlocks()) {
            HclObjectStore hclObjectStore = add(hclBlock);
            ctx.getRoot().add(hclBlock.blockNames.get(0), hclObjectStore);
            if ("data".equals(hclBlock.getName())) {
                ctx.getRoot().add("data", hclObjectStore);
            } else if ("locals".equals(hclBlock.getName())) {
                ctx.getRoot().add("local", hclObjectStore);
            } else if ("variable".equals(hclBlock.getName())) {
                ctx.getRoot().add("var", hclObjectStore);
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
        HclBlockDescriptor result = ctx.getStore().create(HclBlockDescriptor.class);
        result.setName(name);
        ((HclBlockDescriptor) this.delegate.get()).getBlocks().add(result);
        HclObjectStore resultStore = new HclObjectStore(this, name, Optional.of(result));
        add(name, resultStore);
        resultStore.add(blockNames.subList(1, blockNames.size()), hclBlock);
        return resultStore;
    }

    HclObjectStore add(final String name, final HCLBlock hclBlock) {
        final HclBlockDescriptor result = create(hclBlock, HclBlockDescriptor.class);
        result.setName(name);
        final HclObjectStore resultStore = new HclObjectStore(this, name, Optional.of(result));
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
                        ctx.getCurrentFilename(), hclBlock.getSymbolName(), child.getClass().getName(),
                        fullyQualifiedName());
            }
        }
    }

    private HclObjectStore add(final HCLAttribute hclAttribute) {
        final HclAttributeDescriptor result = create(hclAttribute, HclAttributeDescriptor.class);
        final HclObjectStore resultStore = new HclObjectStore(this, hclAttribute.getName(), Optional.of(result));
        add(hclAttribute.getName(), resultStore);
        if (this.delegate.isPresent()) {
            ((HclBlockDescriptor) this.delegate.get()).getAttributes().add(result);
        } else {
            LOGGER.debug("'{}': No embracing block present", fullyQualifiedName());
        }

        for (Symbol child : hclAttribute.getChildren()) {
            String childName = child.getName();
            if (child instanceof HCLValue) {
                LOGGER.debug("'{} ({})': set value for '{}' to '{}'", fullyQualifiedName(), hashCode(), childName,
                        ((HCLValue) child).getValue());
                result.setValue((String) ((HCLValue) child).getValue());
            } else if (child instanceof HCLAttribute || child instanceof Variable) {
                Optional<HclDescriptor> hclDescriptor = ctx.getRoot().find(childName).delegate;
                if (hclDescriptor.isPresent()) {
                    LOGGER.debug("'{} ({})': set reference for '{}' to '{}'", fullyQualifiedName(), hashCode(),
                            childName, hclDescriptor.get());
                    result.setReference((HclIdentifiedDescriptor) hclDescriptor.get());
                } else {
                    LOGGER.error("No Reference Attribute present for '{}'", child.getName());
                }
            } else {
                LOGGER.warn("{}: Attribute '{}' has unknown child type '{}' in context '{}'",
                        ctx.getCurrentFilename(), hclAttribute.getName(), child.getClass().getName(),
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
        HclIdentifiedSub identified = ctx.getStore().create(identifiedClass);
        identified.setIdentifier(symbol.getName());
        setConfiguredValues(symbol, identified);
        return identified;
    }
}
