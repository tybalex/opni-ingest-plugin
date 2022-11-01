/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.opnijsondetector.plugin;

import org.opensearch.common.Strings;
import org.opensearch.common.unit.ByteSizeUnit;
import org.opensearch.common.unit.ByteSizeValue;
import org.opensearch.ingest.AbstractProcessor;
import org.opensearch.ingest.IngestDocument;
import org.opensearch.ingest.Processor;
import org.opensearch.common.SuppressForbidden;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Setting.Property;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;



import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.sql.Timestamp;
import java.security.PrivilegedActionException;
import java.security.PrivilegedAction;

import static org.opensearch.ingest.ConfigurationUtils.readBooleanProperty;
import static org.opensearch.ingest.ConfigurationUtils.readOptionalStringProperty;
import static org.opensearch.ingest.ConfigurationUtils.readStringProperty;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.lang.NullPointerException;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Iterator;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.JsonObject;


public final class OpniJsonDetector extends AbstractProcessor {

    public static final String TYPE = "opni-logging-processor";

    private final String field;
    private final String targetField;
    private Pattern jsonObjectPattern;
    private String[] jsonPresetKeywords = {"severity", "level", "time"};
    private static final Set<String> LOGFIELDS;
    static {
        Set<String> tmpSet = new HashSet<>();
        tmpSet.add("log");
        tmpSet.add("message");
        LOGFIELDS = Collections.unmodifiableSet(tmpSet);
    }

    public OpniJsonDetector(String tag, String description, String field, String targetField)
            throws IOException, PrivilegedActionException {
        super(tag, description);
        this.field = field;
        this.targetField = targetField;
        this.jsonObjectPattern = Pattern.compile("\\{(?:[^{}]|(\\{(?:[^{}]|((\\{(?:[^{}]|())*\\})))*\\}))*\\}");
    }

    @Override
    public IngestDocument execute(IngestDocument ingestDocument) throws Exception {
        // main entry
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<IngestDocument>() {
                @Override
                public IngestDocument run() throws Exception {
                    long startTime = System.nanoTime();

                    normalizeDocument(ingestDocument);
                    jsonExtractionFromLog(ingestDocument);

                    long endTime = System.nanoTime();
                    // ingestDocument.setFieldValue("json_extraction_time_ms", (endTime-startTime) / 1000000.0);
                    
                    return ingestDocument;
                }
            });
        } catch (PrivilegedActionException e) {
            throw e;
        }         
    }

    private void normalizeDocument(IngestDocument ingestDocument) {
        /*
         * this method normalize:
        1. normalize time field
        2. normalize log field
         */
        
        // normalize field `time`
        long unixTime = System.currentTimeMillis();
        ingestDocument.setFieldValue("ingest_at", ((Date)new Timestamp(unixTime)).toString());
        if (!ingestDocument.hasField("time")){
            if (ingestDocument.hasField("timestamp")) {
                ingestDocument.setFieldValue("time", ingestDocument.getFieldValue("timestamp", String.class));
                ingestDocument.setFieldValue("raw_ts", "yes");
            }
            else {
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

        // If it's an event we don't need to do any further processing
        if (ingestDocument.hasField("log_type") && ingestDocument.getFieldValue("log_type", String.class).equals("event")) {
            return;
        }

        // normalize field `log`
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
        actualLog = actualLog.trim();
        ingestDocument.setFieldValue("log", actualLog);
    }

    private JsonObject jsonFlatten (JsonObject jsonObject, String parentName, JsonObject resJsonObject) {
        /*
         * This method flatten nested jsonobject.
         */
        String delimiter = "_";
        Set<String> keys = jsonObject.keySet();
        Iterator<?> keysIterator = keys.iterator ();
        while( keysIterator.hasNext ()) {
            String key = (String) keysIterator.next ();
            String thisName = parentName + key;
            
            if (jsonObject.get (key) instanceof JsonObject) {
                resJsonObject = jsonFlatten ((jsonObject.get (key)).getAsJsonObject(),thisName + delimiter, resJsonObject);
            } else {
                resJsonObject.add(thisName, jsonObject.get(key));
            }
        }
        return resJsonObject;
    }


    // @SuppressWarnings({"unchecked"})
    // @SuppressForbidden(reason = "allow #toLowerCase() usage for now")
    private void jsonExtractionFromLog(IngestDocument ingestDocument) {
        /**
         * detect and extract nested or embedded json in log strings
        **/
        String parsedLog = ingestDocument.getFieldValue("log", String.class);
        Matcher matchedRes = matchJsonInString(parsedLog);
        Map<String, String> jsonExtractKeywordsMap = new HashMap<>();
        String matchedJson = "";
        if (matchedRes.find()) { // there's json
            if (matchedRes.start()==0 && matchedRes.end()==parsedLog.length()) {
                JsonObject parsedJson;
                try {
                    // TODO -- https://jsoniter.com might be faster than Gson
                    parsedJson= JsonParser.parseString(parsedLog).getAsJsonObject();
                    parsedJson = jsonFlatten(parsedJson, "", new JsonObject());
                } catch ( JsonSyntaxException e) {
                    parsedJson = null;
                }
                if (parsedJson != null) {
                    matchedJson = parsedJson.toString();
                    for (String k : parsedJson.keySet()) {
                        String lowerK = k.toLowerCase(Locale.ENGLISH);
                        for (String presetKeyword : this.jsonPresetKeywords) {
                            if (lowerK.equals(presetKeyword)) {
                                jsonExtractKeywordsMap.put(presetKeyword, parsedJson.get(k).getAsString());
                            }
                        }
                        
                    }
                }
            }
        }
        for (String key : jsonExtractKeywordsMap.keySet()) {
            ingestDocument.setFieldValue("log_" + key, jsonExtractKeywordsMap.get(key));
        }      
        ingestDocument.setFieldValue("log_jsonObject", matchedJson);
    }

    private Matcher matchJsonInString(String str) {
        return this.jsonObjectPattern.matcher(str);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public static final class Factory implements Processor.Factory {

        public Factory() {

        }

        @Override
        public Processor create(Map<String, Processor.Factory> processorFactories, String tag, String description,
                                Map<String, Object> config) throws Exception {
            String field = readStringProperty(TYPE, tag, config, "field");
            String targetField = readStringProperty(TYPE, tag, config, "target_field");

            return new OpniJsonDetector(tag, description, field, targetField);
        }
    }
}
