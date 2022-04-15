/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.opnipreprocessing.plugin;

import org.opensearch.common.Strings;
import org.opensearch.common.unit.ByteSizeUnit;
import org.opensearch.common.unit.ByteSizeValue;
import org.opensearch.ingest.AbstractProcessor;
import org.opensearch.ingest.IngestDocument;
import org.opensearch.ingest.Processor;

import java.io.IOException;
import java.util.Map;
import java.util.Random;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.impl.NatsMessage;
import java.nio.charset.StandardCharsets;


import static org.opensearch.ingest.ConfigurationUtils.readBooleanProperty;
import static org.opensearch.ingest.ConfigurationUtils.readOptionalStringProperty;
import static org.opensearch.ingest.ConfigurationUtils.readStringProperty;



public final class OpniPreProcessor extends AbstractProcessor {

    public static final String TYPE = "opnipre";

    private final String field;
    private final String targetField;
    private Connection nc;

    // public OpniPreProcessor(String tag, String description, String field, String targetField)
    public OpniPreProcessor(String tag, String description, String field, String targetField, Connection nc)
            throws IOException {
        super(tag, description);
        this.field = field;
        this.targetField = targetField;
        this.nc = nc;
    }

    public String getSaltString() {
        String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < 18) { // length of the random string.
            int index = (int) (rnd.nextFloat() * SALTCHARS.length());
            salt.append(SALTCHARS.charAt(index));
        }
        String saltStr = salt.toString();
        return saltStr;
    }

    @Override
    public IngestDocument execute(IngestDocument ingestDocument) throws Exception {

        String actual_log, masked_log;
        try {
            actual_log = ingestDocument.getFieldValue(field, String.class);
        } catch (IllegalArgumentException e) {
            throw e;
        }
        if (Strings.isEmpty(actual_log)) {
            return ingestDocument;
        }

        String generated_id = getSaltString();
        ingestDocument.setFieldValue("_id", generated_id);

        // logic to mask logs, placeholder for now      
        masked_log = "masked: " + generated_id + actual_log;
        ingestDocument.setFieldValue(targetField, masked_log);

        this.nc.publish("sub1", masked_log.getBytes(StandardCharsets.UTF_8) );

        return ingestDocument;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory implements Processor.Factory {
   
        private Connection nc;

        Factory(Connection nc){
            this.nc = nc;
        }

        @Override
        public Processor create(Map<String, Processor.Factory> processorFactories, String tag, String description,
                                Map<String, Object> config) throws Exception {
            String field = readStringProperty(TYPE, tag, config, "field");
            String targetField = readStringProperty(TYPE, tag, config, "target_field");

            // return new OpniPreProcessor(tag, description, field, targetField);
            return new OpniPreProcessor(tag, description, field, targetField, nc);
        }
    }
}