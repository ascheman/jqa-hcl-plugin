package net.aschemann.jqassistant.plugin.hcl.impl.scanner;

import com.buschmais.jqassistant.core.store.api.model.Descriptor;
import com.buschmais.jqassistant.plugin.common.test.AbstractPluginIT;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclConfigurationDescriptor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static net.aschemann.jqassistant.plugin.hcl.api.HclScope.HCL;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HclScannerPluginTest extends AbstractPluginIT {

    public static final String SIMPLE_HCL_TEST = "/hcl/simple";
    public static final String SIMPLE_TF_LB_CONFIGURATION = "/hcl/terraform/simple-lb";
    public static final String TF_AWS_EC2_EBS_DOCKER_HOST = "/hcl/aws_ec2_ebs_docker_host";

    @Test
    void scanSimpleTfLbConfiguration() throws IOException {
        HclConfigurationDescriptor hclConfigurationDescriptor = scan(SIMPLE_TF_LB_CONFIGURATION);
        assertEquals(5, hclConfigurationDescriptor.getFiles().size());
        // TODO add more tests on the graph
        store.rollbackTransaction();
    }

    @Test
    void scanSimpleHclFile() throws IOException {
        HclConfigurationDescriptor hclConfigurationDescriptor = scan(SIMPLE_HCL_TEST);
        // TODO add more tests on the graph
        store.rollbackTransaction();
    }

    @Test
    void scanAwsEc2EbsDockerHost() throws IOException {
        HclConfigurationDescriptor hclConfigurationDescriptor = scan(TF_AWS_EC2_EBS_DOCKER_HOST);
        assertThat(hclConfigurationDescriptor).isNotNull();
        String query = "MATCH (security_group {name:'aws_security_group'}) "
                + "--> (this {name:'this'}) "
                // This works
                + "--> (id {identifier:'vpc_id'})"
                // This should alternatively work as well, but doesn't!
//                + "--> (id {identifier:'id'})"
                + " RETURN *";
        TestResult testResult = query(query);
        Assertions.assertThat(testResult.getColumns()).containsKeys("id");

        query = "MATCH (subnet {name:'aws_subnet'}) "
                + "--> (this {name:'this'}) "
                + "--> (id {identifier:'id'})"
                + " RETURN *";
        testResult = query(query);
        Assertions.assertThat(testResult.getColumns()).containsKeys("id");
    }


    private HclConfigurationDescriptor scan(final String configDirectory) throws IOException {
        File testFile = new File(getClassesDirectory(HclScannerPluginTest.class), configDirectory);
        return scan(testFile);
    }

    private HclConfigurationDescriptor scan(final File testFile) throws IOException {
        store.beginTransaction();
        Descriptor descriptor = getScanner().scan(testFile, testFile.getCanonicalPath(), HCL);
        assertThat(descriptor).isInstanceOf(HclConfigurationDescriptor.class);

        return (HclConfigurationDescriptor) descriptor;
    }
}