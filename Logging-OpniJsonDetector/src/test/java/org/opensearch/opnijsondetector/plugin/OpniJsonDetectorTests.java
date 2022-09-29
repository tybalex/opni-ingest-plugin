/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.opnijsondetector.plugin;

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

public class OpniJsonDetectorTests extends OpenSearchTestCase {
    // Add unit tests for your plugin
    public void testGetProcessors() throws Exception {
        // case 1
        String targetValue1 = "{\"currency_unit\":\"CAD\",\"http.req.id\":\"8f690a88-ee58-474c-8240-38e10c6f9f43\",\"http.req.method\":\"GET\",\"http.req.path\":\"/product/2ZYFJ3GM2N\",\"id\":\"2ZYFJ3GM2N\",\"message\":\"serving product page\",\"session\":\"0f0214f4-36cd-4155-b097-93b33255bb27\",\"severity\":\"debug\",\"timestamp\":\"2022-04-28T08:08:05.525945107Z\"}";
        //"{\"currency\":{\"unit\":\"CAD\"},\"http.req.id\":\"8f690a88-ee58-474c-8240-38e10c6f9f43\",\"http.req.method\":\"GET\",\"http.req.path\":\"/product/2ZYFJ3GM2N\",\"id\":\"2ZYFJ3GM2N\",\"message\":\"serving product page\",\"session\":\"0f0214f4-36cd-4155-b097-93b33255bb27\",\"severity\":\"debug\",\"timestamp\":\"2022-04-28T08:08:05.525945107Z\"}";
        String inputValue1 = "{\"currency\":{\"unit\":\"CAD\"},\"http.req.id\":\"8f690a88-ee58-474c-8240-38e10c6f9f43\",\"http.req.method\":\"GET\",\"http.req.path\":\"/product/2ZYFJ3GM2N\",\"id\":\"2ZYFJ3GM2N\",\"message\":\"serving product page\",\"session\":\"0f0214f4-36cd-4155-b097-93b33255bb27\",\"severity\":\"debug\",\"timestamp\":\"2022-04-28T08:08:05.525945107Z\"}";
        Map<String, Object> data1 = ingestDocumentTest(mockConfig("log", "json_object"),
                "log", inputValue1);

        assertTrue(data1.containsKey("log_jsonObject"));
        assertTrue(data1.get("log_jsonObject").equals(targetValue1));
        // case 2
        String targetValue2 = "";
        String inputValue2 = "GetCartAsync called with userId=131210";
        Map<String, Object> data2 = ingestDocumentTest(mockConfig("log", "json_object"),
                "log", inputValue2);

        assertTrue(data2.containsKey("log_jsonObject"));
        assertTrue(data2.get("log_jsonObject").equals(targetValue2));
    }

    private Map<String, Object> ingestDocumentTest(Map<String, Object> config, String field, String value) throws Exception {
        Map<String, Object> document = new HashMap<>();
        document.put(field, value);
        IngestDocument ingestDocument = RandomDocumentPicks.randomIngestDocument(random(), document);

        Processor processor = new OpniJsonDetector.Factory().create(Collections.emptyMap(), randomAlphaOfLength(10), "desc", config);
        return processor.execute(ingestDocument).getSourceAndMetadata();
    }

    private Map<String, Object> mockConfig(String sourceField, String targetField) {
        final Map<String, Object> config = new HashMap<>();
        config.put("field", sourceField);
        config.put("target_field", targetField);
        return config;
    }
}
