package net.aschemann.jqasssistant.plugin.hcl.impl.scanner;

import com.buschmais.jqassistant.core.scanner.api.DefaultScope;
import com.buschmais.jqassistant.core.store.api.model.Descriptor;
import com.buschmais.jqassistant.plugin.common.test.AbstractPluginIT;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclConfigurationDescriptor;
import net.aschemann.jqassistant.plugin.hcl.impl.scanner.HclScannerPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HclScannerPluginTest extends AbstractPluginIT {

    public static final String SIMPLE_HCL_TESTFILE = "/hcl-files/simple.hcl";
    public static final String SIMPLE_TF_LB_CONFIGURATION = "/hcl-files/simple-lb.tf";

    @BeforeEach
    public void resetScanner() {
        HclScannerPlugin.resetObjectStore();
    }

    @Test
    public void scanSimpleTfLbConfiguration() throws IOException {
        File testFile = new File(getClassesDirectory(HclScannerPluginTest.class), SIMPLE_TF_LB_CONFIGURATION);
        HclConfigurationDescriptor hclConfigurationDescriptor = scan(testFile);
        assertEquals(6, hclConfigurationDescriptor.getBlocks().size());
        // TODO add more tests on the graph
//        store.commitTransaction();
        store.rollbackTransaction();
    }

    @Test
    public void scanSimpleHclFile() throws IOException {
        File testFile = new File(getClassesDirectory(HclScannerPluginTest.class), SIMPLE_HCL_TESTFILE);
        HclConfigurationDescriptor hclConfigurationDescriptor = scan(testFile);
        // TODO add more tests on the graph
//        store.commitTransaction();
        store.rollbackTransaction();
    }

    private HclConfigurationDescriptor scan(File testFile) throws IOException {
        Descriptor descriptor = getScanner().scan(testFile, testFile.getCanonicalPath(), DefaultScope.NONE);
        assertThat(descriptor).isInstanceOf(HclConfigurationDescriptor.class);

        store.beginTransaction();
        return (HclConfigurationDescriptor) descriptor;
    }
}