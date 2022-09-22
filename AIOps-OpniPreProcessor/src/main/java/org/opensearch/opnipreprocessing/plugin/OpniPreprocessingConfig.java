/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

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
    static final Setting<String> SEED_FILE_SETTING = Setting.simpleString("nats.seed_file", "/etc/nkey/seed", value -> {}, Property.NodeScope);

    private final String natsEndpoint;
    private final String seedFile;

    public OpniPreprocessingConfig(final Environment env) {
        // In a later version of Opensearch this method is renamed configDir
        final Path configDir = env.configFile();
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
