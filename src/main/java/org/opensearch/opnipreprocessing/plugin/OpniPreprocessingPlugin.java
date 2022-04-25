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
		        	return Nats.connect("nats://3.145.37.107:4222"); // TODO replace with nats address from ENV variables
		        }
		    });
		} catch (PrivilegedActionException e) {
		    throw e;
		}
	}
    

}
