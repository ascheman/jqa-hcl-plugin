package net.aschemann.jqasssistant.plugin.hcl.impl.scanner;

import com.buschmais.jqassistant.core.store.api.model.Descriptor;
import com.buschmais.jqassistant.plugin.common.test.AbstractPluginIT;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclConfigurationDescriptor;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static net.aschemann.jqassistant.plugin.hcl.api.HclScope.HCL;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HclScannerPluginTest extends AbstractPluginIT {

    public static final String SIMPLE_HCL_TEST = "/hcl/simple";
    public static final String SIMPLE_TF_LB_CONFIGURATION = "/hcl/terraform/simple-lb";

    @Test
    void scanSimpleTfLbConfiguration() throws IOException {
        File testFile = new File(getClassesDirectory(HclScannerPluginTest.class), SIMPLE_TF_LB_CONFIGURATION);
        HclConfigurationDescriptor hclConfigurationDescriptor = scan(testFile);
        assertEquals(4, hclConfigurationDescriptor.getFiles().size());
        // TODO add more tests on the graph
        store.rollbackTransaction();
    }

    @Test
    void scanSimpleHclFile() throws IOException {
        File testFile = new File(getClassesDirectory(HclScannerPluginTest.class), SIMPLE_HCL_TEST);
        HclConfigurationDescriptor hclConfigurationDescriptor = scan(testFile);
        // TODO add more tests on the graph
        store.rollbackTransaction();
    }

    private HclConfigurationDescriptor scan(File testFile) throws IOException {
        store.beginTransaction();
        Descriptor descriptor = getScanner().scan(testFile, testFile.getCanonicalPath(), HCL);
        assertThat(descriptor).isInstanceOf(HclConfigurationDescriptor.class);

        return (HclConfigurationDescriptor) descriptor;
    }
}