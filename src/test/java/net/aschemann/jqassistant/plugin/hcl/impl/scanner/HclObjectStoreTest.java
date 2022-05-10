package net.aschemann.jqassistant.plugin.hcl.impl.scanner;

import com.bertramlabs.plugins.hcl4j.HCLParser;
import com.buschmais.jqassistant.core.store.api.Store;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclAttributeDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclBlockDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclConfigurationDescriptor;
import net.aschemann.jqassistant.plugin.hcl.api.model.HclFileDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class HclObjectStoreTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(HclObjectStoreTest.class);

    public static final String SIMPLE_HCL_TEST = "/hcl/simple";
    public static final String SIMPLE_TF_LB_CONFIGURATION = "/hcl/terraform/simple-lb";
    public static final String TF_AWS_EC2_EBS_DOCKER_HOST = "/hcl/aws_ec2_ebs_docker_host";

    private Store store;

    <C extends Class> void createForever(final C clazz) {
        doAnswer(invocationOnMock -> {
            LOGGER.debug("Creating new '{}'", clazz);
            return mock(clazz);
        }).when(store).create(clazz);
    }

    @BeforeEach
    void prepare() {
        store = mock(Store.class);
        createForever(HclAttributeDescriptor.class);
        createForever(HclFileDescriptor.class);
        createForever(HclBlockDescriptor.class);
    }

    @Test
    void scanSimpleTfConfiguration() throws IOException {
        HclObjectStore root = getHclObjectStore(SIMPLE_HCL_TEST);
        assertThat(root).isNotNull();
    }

    @Test
    void scanSimpleTfLbConfiguration() throws IOException {
        HclObjectStore root = getHclObjectStore(SIMPLE_TF_LB_CONFIGURATION);
        assertThat(root).isNotNull();
    }

    @Test
    void scanAwsEc2EbsDockerHost() throws IOException {
        HclObjectStore root = getHclObjectStore(TF_AWS_EC2_EBS_DOCKER_HOST);
        assertThat(root).isNotNull();
    }

    private HclObjectStore getHclObjectStore(final String configDirectory) throws IOException {
        HclScannerContext ctx = new HclScannerContext(store, new HCLParser());
        HclObjectStore root = new HclObjectStore(ctx, null,"ROOT");
        ctx.setRoot(root);
        File resourceDir = new File("src/test/resources");
        File configDir = new File (resourceDir, configDirectory);
        HclConfigurationDescriptor hclConfigurationDescriptor = mock(HclConfigurationDescriptor.class);
        for (File file : Objects.requireNonNull(configDir.listFiles(HclObjectStore.filterHclFiles()))) {
            root.add(hclConfigurationDescriptor, file);
        }
        LOGGER.info("Created: '{}'", root);
        return root;
    }
}