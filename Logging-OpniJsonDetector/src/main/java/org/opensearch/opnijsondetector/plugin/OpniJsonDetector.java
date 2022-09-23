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
import java.util.Locale;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;


public final class OpniJsonDetector extends AbstractProcessor {

    public static final String TYPE = "opni-json-detector";

    private final String field;
    private final String targetField;
    private Pattern jsonObjectPattern;
    private static final Set<String> LOGFIELDS;
    static {
        Set<String> tmpSet = new HashSet();
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
                    JsonExtractionFromLog(ingestDocument);
                    return ingestDocument;
                }
            });
        } catch (PrivilegedActionException e) {
            throw e;
        }         
    }


    // @SuppressWarnings({"unchecked"})
    // @SuppressForbidden(reason = "allow #toLowerCase() usage for now")
    private void JsonExtractionFromLog(IngestDocument ingestDocument) {
        /**
         * simply detect and extract nested or embedded json in log strings
        **/
        String parsedLog = ingestDocument.getFieldValue("log", String.class);
        Matcher matchedRes = matchJsonInString(parsedLog);
        String severity = "";
        String matchedJson = "";
        if (matchedRes.find()) { // there's json
            if (matchedRes.start()==0 && matchedRes.end()==parsedLog.length()) {
                JsonObject parsedJson;
                try {
                    parsedJson= JsonParser.parseString(parsedLog).getAsJsonObject();
                } catch ( JsonSyntaxException e) {
                    parsedJson = null;
                }
                if (parsedJson != null) {
                    matchedJson = parsedJson.toString();
                    for (String k : parsedJson.keySet()) {
                        if (LOGFIELDS.contains(k.toLowerCase(Locale.ENGLISH))) {
                            // do something
                            break;
                        }
                    }
                    for (String k : parsedJson.keySet()) {
                        if (k.toLowerCase(Locale.ENGLISH).equals("severity")) {
                            severity = parsedJson.get(k).getAsString();
                        }
                    }
                }
            }
        }
        ingestDocument.setFieldValue("log.severity", severity);
        ingestDocument.setFieldValue("log.jsonObject", matchedJson);
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
