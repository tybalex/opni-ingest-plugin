/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.opnipreprocessing.plugin;

import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.ingest.IngestDocument;
import org.opensearch.ingest.Processor;
import org.opensearch.ingest.RandomDocumentPicks;

import java.util.Map;
import java.util.Collections;
import java.util.HashMap;

import static org.hamcrest.Matchers.hasEntry;

public class OpniPreprocessingTests extends OpenSearchTestCase {
    // Add unit tests for your plugin
    public void testThatProcessorWorks() throws Exception {
        // Map<String, Object> data = ingestDocument(config("log", "masked_log"),
        //         "log", "I0316 12:03:47.578853       1 get.go:259] \"Starting watch\" path=\"/api/v1/namespaces/dgps/secrets\" resourceVersion=\"1035642112\" labels=\"\" fields=\"metadata.name=jenkins-master-token\"\n timeout=\"5m25s\"");

        // assertThat(data, hasEntry("lang", "en"));
    }

    // private Map<String, Object> ingestDocument(Map<String, Object> config, String field, String value) throws Exception {
    //     Map<String, Object> document = new HashMap<>();
    //     document.put(field, value);
    //     IngestDocument ingestDocument = RandomDocumentPicks.randomIngestDocument(random(), document);
    //     Connection nc; 
    //     LogMasker masker = new LogMasker();

    //     Processor processor = new OpniPreProcessor.Factory().create(Collections.emptyMap(), randomAlphaOfLength(10), "desc", config);
    //     return processor.execute(ingestDocument).getSourceAndMetadata();
    // }

    // private Map<String, Object> config(String sourceField, String targetField) {
    //     final Map<String, Object> config = new HashMap<>();
    //     config.put("field", sourceField);
    //     config.put("target_field", targetField);
    //     return config;
    // }
}
