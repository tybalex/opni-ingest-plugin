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

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.impl.NatsMessage;
import io.nats.client.NKey;
import io.nats.client.Options;
import io.nats.client.AuthHandler;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.FileSystem;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.lang.NullPointerException;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedAction;


public class OpniPreprocessingPlugin extends Plugin implements IngestPlugin {

    private Connection nc; 
    private LogMasker masker;

    public OpniPreprocessingPlugin()throws PrivilegedActionException{
    	try{
    		this.nc = connectNats();
    	}catch (PrivilegedActionException e) {
		    throw e;
		}
    	masker = new LogMasker();
    }

    @Override
    public Map<String, Processor.Factory> getProcessors(Processor.Parameters parameters) {

        return Collections.singletonMap(OpniPreProcessor.TYPE, new OpniPreProcessor.Factory(nc, masker));
    }

    private Connection connectNats() throws PrivilegedActionException {
    	/***
    	this method assigns privilege to create a nats connection. 
        ***/
    	try {
		    return AccessController.doPrivileged(new PrivilegedExceptionAction<Connection>() {
		        @Override
		        public Connection run() throws Exception {
                    return Nats.connect(getNKeyOption()); // "nats://opni-log-anomaly-nats-client.opni-cluster-system.svc:4222"
		        	// return Nats.connect("nats://3.145.37.107:4222"); // TODO replace with nats address from ENV variables
		        }
		    });
		} catch (PrivilegedActionException e) {
		    throw e;
		}
	}

    private Options getNKeyOption() throws GeneralSecurityException, IOException, NullPointerException{
        Path path = Paths.get("/etc/nkey/seed"); // for java 11+ , should use Path.of()
        char[] seed = new String(Files.readAllBytes(path)).toCharArray();
        // char[] seed = Files.readString(path).toCharArray();
        NKey theNKey = NKey.fromSeed(seed);
        Options options = new Options.Builder().
                    server("nats://opni-log-anomaly-nats-client.opni-cluster-system.svc:4222").
                    // server("nats://localhost:4222").
                    authHandler(new AuthHandler(){
                        public char[] getID() {
                            try {
                                return theNKey.getPublicKey();
                            } catch (GeneralSecurityException|IOException|NullPointerException ex) {
                                return null;
                            }
                        }

                        public byte[] sign(byte[] nonce) {
                            try {
                                return theNKey.sign(nonce);
                            } catch (GeneralSecurityException|IOException|NullPointerException ex) {
                                return null;
                            }
                        }

                        public char[] getJWT() {
                            return null;
                        }
                    }).
                    build();
        return options;
    }
    

}
