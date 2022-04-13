/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.opnipreprocessing.plugin;

import java.util.Collections;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.ingest.Processor;
import org.opensearch.plugins.IngestPlugin;
import org.opensearch.plugins.Plugin;


public class OpniPreprocessingPlugin extends Plugin implements IngestPlugin {
    // Implement the relevant Plugin Interfaces here
    @Override
    public Map<String, Processor.Factory> getProcessors(Processor.Parameters parameters) {

        return Collections.singletonMap(OpniPreProcessor.TYPE, new OpniPreProcessor.Factory());
    }
}
