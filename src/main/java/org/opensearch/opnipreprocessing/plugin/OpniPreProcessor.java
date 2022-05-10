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

        // normalize field time
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

        // normalize field log
        String actualLog = "NONE";
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
                ingestDocument.setFieldValue("log_source_field", "NONE");
            }
        }
        else {
            actualLog = ingestDocument.getFieldValue("log", String.class);
            ingestDocument.setFieldValue("log_source_field", "log");
        }
        actualLog = actualLog.trim(); // for java 11+ we should use strip()
        ingestDocument.setFieldValue("log", actualLog);

        // normalize field log_type and kubernetesComponent conponent
        boolean isControlPlaneLog = false;
        String kubernetesComponent = "";
        // if (!ingestDocument.hasField("agent") || ingestDocument.getFieldValue("agent", String.class).equals("support")) {
        //     isControlPlaneLog = false;
        //     kubernetesComponent = "";
        // }
        
        if (ingestDocument.hasField("filename")) {
            String controlPlaneName = ingestDocument.getFieldValue("filename", String.class);
            if (controlPlaneName.contains("rke/log/etcd") ||
                controlPlaneName.contains("rke/log/kubelet") ||
                controlPlaneName.contains("/rke/log/kube-apiserver") ||
                controlPlaneName.contains("rke/log/kube-controller-manager") ||
                controlPlaneName.contains("rke/log/kube-proxy") ||
                controlPlaneName.contains("rke/log/kube-scheduler") 
                ) { // `contains` has better performance for simple cases
                isControlPlaneLog = true;
                kubernetesComponent = (controlPlaneName.split("_"))[0];
            }
            else if (controlPlaneName.contains("k3s.log")){
                isControlPlaneLog = true;
                kubernetesComponent = "k3s";
            }
            else if (controlPlaneName.contains("rke2/agent/logs/kubelet")){
                isControlPlaneLog = true;
                kubernetesComponent = "kubelet";
            }
        }  
        else if (ingestDocument.hasField("COMM")){
            String controlPlaneName = ingestDocument.getFieldValue("COMM", String.class);
            if (controlPlaneName.contains("kubelet") ||
                controlPlaneName.contains("k3s-agent") ||
                controlPlaneName.contains("k3s-server") ||
                controlPlaneName.contains("rke2-agent") ||
                controlPlaneName.contains("rke2-server")
                ){
                isControlPlaneLog = true;
                kubernetesComponent = (controlPlaneName.split("-"))[0];
            }
        }
        else {
            String controlPlaneName = "";
            if (ingestDocument.hasField("kubernetes")) {// kubernetes.labels.tier
                Map<String, Object> kubernetes = ingestDocument.getFieldValue("kubernetes", Map.class);
                if (kubernetes.containsKey("labels")) {
                    HashMap<String, String> labels = (HashMap)kubernetes.get("labels");
                    if (labels.containsKey("tier")) {
                        controlPlaneName = labels.get("tier");
                        if (controlPlaneName.contains("control-plane")){
                            isControlPlaneLog = true;
                            kubernetesComponent = "control-plane";
                        }
                    }
                }
            }
            
        }      
        ingestDocument.setFieldValue("is_control_plane_log", isControlPlaneLog);
        ingestDocument.setFieldValue("kubernetes_component", kubernetesComponent);
        
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
