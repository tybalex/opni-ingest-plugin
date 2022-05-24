package org.opensearch.opnipreprocessing.plugin;

import org.opensearch.OpenSearchException;
import org.opensearch.common.settings.SecureSetting;
import org.opensearch.common.settings.SecureString;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Setting.Property;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;

import java.io.IOException;
import java.nio.file.Path;

public class OpniPreprocessingConfig {
    static final Setting<String> ENDPOINT_SETTING = Setting.simpleString("nats.endpoint", Property.NodeScope);
    static final Setting<String> SEED_FILE_SETTING = Setting.simpleString("nats.seed_file", Property.NodeScope);

    private final String natsEndpoint;
    private final String seedFile;

    public OpniPreprocessingConfig(final Environment env) {
        final Path configDir = env.configDir();
        final Path settingsYaml = configDir.resolve("preprocessing/settings.yml");

        final Settings pluginSettings;
        try {
            pluginSettings = Settings.builder().loadFromPath(settingsYaml).build();
            assert pluginSettings != null;
        } catch (IOException e) {
            throw new OpenSearchException("failed to load settings", e);
        }

        this.natsEndpoint = ENDPOINT_SETTING.get(pluginSettings);
        this.seedFile = SEED_FILE_SETTING.get(pluginSettings);
    }

    public String getNatsEndpoint() {
        return natsEndpoint;
    }

    public String getSeedFile() {
        return seedFile;
    }
}
