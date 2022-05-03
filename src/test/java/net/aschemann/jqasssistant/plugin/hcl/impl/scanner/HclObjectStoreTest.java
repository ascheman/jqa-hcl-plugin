package net.aschemann.jqasssistant.plugin.hcl.impl.scanner;

import com.bertramlabs.plugins.hcl4j.HCLParser;
import com.buschmais.jqassistant.core.store.api.Store;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclAttributeDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclBlockDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclConfigurationDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclFileDescriptor;
import net.aschemann.jqassistant.plugin.hcl.impl.scanner.HclObjectStore;
import net.aschemann.jqassistant.plugin.hcl.impl.scanner.HclScannerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HclObjectStoreTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(HclObjectStoreTest.class);

    public static final String SIMPLE_HCL_TEST = "/hcl/simple";
    public static final String SIMPLE_TF_LB_CONFIGURATION = "/hcl/terraform/simple-lb";

    private Store store = mock(Store.class);

    @BeforeEach
    void prepare() {
        Class hclFileDescriptorClazz = HclFileDescriptor.class;
        when(store.create(hclFileDescriptorClazz)).thenReturn(mock(HclFileDescriptor.class));
        Class hclBlockDescriptorClazz = HclBlockDescriptor.class;
        doAnswer(invocation -> {
            LOGGER.debug("Creating new '{}'", invocation);
            return mock(HclBlockDescriptor.class);
        }).when(store).create(hclBlockDescriptorClazz);
        Class hclAttributeDescriptorClazz = HclAttributeDescriptor.class;
        doAnswer(invocation -> {
            LOGGER.debug("Creating new '{}'", invocation);
            return mock(HclAttributeDescriptor.class);
        }).when(store).create(hclAttributeDescriptorClazz);
    }

    @Test
    void scanSimpleTfLbConfiguration() throws IOException, ClassNotFoundException {
        HclScannerContext ctx = new HclScannerContext(store, new HCLParser());
        HclObjectStore root = new HclObjectStore(ctx, null,"ROOT");
        ctx.setRoot(root);
        File resourceDir = new File("src/test/resources");
        File configDir = new File (resourceDir, SIMPLE_TF_LB_CONFIGURATION);
//        File main = new File(configDir, "main.tf");
        HclConfigurationDescriptor hclConfigurationDescriptor = mock(HclConfigurationDescriptor.class);
//        root.add(hclConfigurationDescriptor, main);
        for (File file : configDir.listFiles(HclObjectStore.filterHclFiles())) {
            root.add(hclConfigurationDescriptor, file);
        }
        LOGGER.info("Created: '{}'", root);
    }
}