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
import java.util.HashMap;
import java.util.Random;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.impl.NatsMessage;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;


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
        String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890abcdefghijklmnopq_";
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
    public String getType() {
        return TYPE;
    }

    @Override
    public IngestDocument execute(IngestDocument ingestDocument) throws Exception {
        // main entry of logic of executing each document
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<IngestDocument>() {
                @Override
                public IngestDocument run() throws Exception {
                    String generated_id = getSaltString();
                    ingestDocument.setFieldValue("_id", generated_id);
                    preprocessingDocument(ingestDocument);
                    publishToNats(ingestDocument, nc);
                    return ingestDocument;
                }
            });
        } catch (PrivilegedActionException e) {
            throw e;
        }         
    }

    @SuppressWarnings({"unchecked"})
    private void preprocessingDocument(IngestDocument ingestDocument) {
        /**
        preprocessing documents:
        1. normalize time field
        2. normalize log field
        3. identify controlplane logs
        **/

        // take care of field for time
        if (!ingestDocument.hasField("time")){
            if (ingestDocument.hasField("timestamp")) {
                ingestDocument.setFieldValue("time", ingestDocument.getFieldValue("timestamp", String.class));
                ingestDocument.setFieldValue("raw_ts", "yes");
            }
            else {
                long unixTime = System.currentTimeMillis() ;// / 1000L;
                ingestDocument.setFieldValue("timestamp", Long.toString(unixTime));
                ingestDocument.setFieldValue("time", Long.toString(unixTime));
                ingestDocument.setFieldValue("raw_ts", "no");
            }
        }
        else {
            ingestDocument.setFieldValue("raw_ts", "raw time");
        }
        if (ingestDocument.hasField("timestamp")) {
            ingestDocument.removeField("timestamp"); 
        }

        // take care of fields for log
        String actualLog = "";
        if (!ingestDocument.hasField("log")) {
            if (ingestDocument.hasField("message")) {
                actualLog = ingestDocument.getFieldValue("message", String.class);              
                ingestDocument.removeField("message");
                ingestDocument.setFieldValue("log_source_field", "message");
            }
            else if (ingestDocument.hasField("MESSAGE")) {
                actualLog = ingestDocument.getFieldValue("MESSAGE", String.class);
                ingestDocument.removeField("MESSAGE");
                ingestDocument.setFieldValue("log_source_field", "MESSAGE");
            }
            else {
                actualLog = "";
                ingestDocument.setFieldValue("log_source_field", "NONE");
            }
        }
        else {
            actualLog = ingestDocument.getFieldValue("log", String.class);
            ingestDocument.setFieldValue("log_source_field", "log");
        }
        actualLog = actualLog.trim(); // for java 11+ we should use strip()
        ingestDocument.setFieldValue("log", actualLog);

        // access nested json
        // if (ingestDocument.hasField("kubernetes")) {
        //     Map<String, Object> kubernetes;
        //     kubernetes = ingestDocument.getFieldValue("kubernetes", Map.class);
        //     if (kubernetes.containsKey("labels")) {
        //         ingestDocument.setFieldValue("nested" , ((HashMap)kubernetes.get("labels")).get("app"));
        //     }
        // }

        // boolean isControlPlaneLog;
        // String kubernetesComponent;
        // if (!ingestDocument.hasField("agent") || ingestDocument.getFieldValue("agent", String.class).equals("support")) {
        //     isControlPlaneLog = false;
        //     kubernetesComponent = "";
        // }

        // if (ingestDocument.hasField("filename")) {

        // }        
        

        //     // maskedLog = maskLogs(actualLog, false);
        //     // ingestDocument.setFieldValue(targetField, maskedLog);
    }

    private void publishToNats (IngestDocument ingestDocument, Connection nc) throws PrivilegedActionException {
        // push to nats using gson
        Gson gson = new Gson();
        String payload = gson.toJson(ingestDocument.getSourceAndMetadata()); // send everything
        nc.publish("raw_logs", payload.getBytes(StandardCharsets.UTF_8) );
    }

    private String maskLogs(String log) {
        return masker.mask(log);
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

}
