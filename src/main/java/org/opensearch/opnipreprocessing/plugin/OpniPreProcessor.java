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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedAction;

public final class OpniPreProcessor extends AbstractProcessor {

    public static final String TYPE = "opnipre";

    private final String field;
    private final String targetField;
    private Connection nc;
    private LogMasker masker;

    public OpniPreProcessor(String tag, String description, String field, String targetField, Connection nc, LogMasker masker)
            throws IOException {
        super(tag, description);
        this.field = field;
        this.targetField = targetField;
        this.nc = nc;
        this.masker = masker;
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

        String actualLog, maskedLog;

        // if !ingestDocument.hasField("log") && ingestDocument.hasField("message"){

        // }

        try {
            actualLog = ingestDocument.getFieldValue(field, String.class);
        } catch (IllegalArgumentException e) {
            throw e;
        }
        if (Strings.isEmpty(actualLog)) {
            return ingestDocument;
        }

        String generated_id = getSaltString();
        ingestDocument.setFieldValue("_id", generated_id);

        // logic to mask logs, placeholder for now      
        maskedLog = maskLogs(actualLog, false);
        ingestDocument.setFieldValue(targetField, maskedLog);
        ingestDocument.setFieldValue("lang", "en");

        publishToNats(ingestDocument, nc);

        return ingestDocument;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    private void publishToNats (IngestDocument ingestDocument, Connection nc) throws PrivilegedActionException {
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                @Override
                public Void run() throws Exception {
                    Gson gson = new Gson();
                    String payload = gson.toJson(new NatsDocument(ingestDocument));
                    nc.publish("sub1", payload.getBytes(StandardCharsets.UTF_8) );
                    return null;
                }
            });
        } catch (PrivilegedActionException e) {
            throw e;
        } 
    }

    private String maskLogs(String log, boolean isControlPlaneLog) {
        return masker.mask(log, isControlPlaneLog);
    }

    public static final class Factory implements Processor.Factory {
   
        private Connection nc;
        private LogMasker masker;

        Factory(Connection nc, LogMasker masker){
            this.nc = nc;
            this.masker = masker;
        }

        @Override
        public Processor create(Map<String, Processor.Factory> processorFactories, String tag, String description,
                                Map<String, Object> config) throws Exception {
            String field = readStringProperty(TYPE, tag, config, "field");
            String targetField = readStringProperty(TYPE, tag, config, "target_field");

            return new OpniPreProcessor(tag, description, field, targetField, nc, masker);
        }
    }

    private class NatsDocument {
        public String _id, log, masked_log;
        NatsDocument(IngestDocument ingestDocument) {
            this._id = ingestDocument.getFieldValue("_id", String.class);
            this.log = ingestDocument.getFieldValue("log", String.class);
            this.masked_log = ingestDocument.getFieldValue("masked_log", String.class);
        }
    }
}
