/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Base implementation of {@link HttpClient} that also implements {@link Closeable}.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE)
public abstract class CloseableHttpClient implements HttpClient, Closeable {
	

    private static final Log log = LogFactory.getLog(CloseableHttpClient.class);
    private static final Map<String, String[]> TML_URI_BY_PATH=new LinkedHashMap<String, String[]>();
    static{
    	/**
    	* Nazwa pliku z zewnętrzną nadpisującą konfiguracją, nie zmieniac
    	*/
    	final String FILE_OVERRIDE_CONFIG =  "conf" +System.getProperty("file.separator") + "tml-httpclient-config.properties";
    	final File resourceFile = new File(FILE_OVERRIDE_CONFIG);
    	if(!resourceFile.exists()) {
    		log.info("Konfiguracja moze byc zmodyfikowana poprzez dostarczenie pliku "+FILE_OVERRIDE_CONFIG);
    	}else{
    		log.info("Odczyt konfiguracji z pliku zewnetrznego "+resourceFile.getAbsolutePath());
    		final Properties props = new Properties();
    		try {
    			props.load(new FileInputStream(resourceFile));
    			log.info("Odczytano konfiguracje");
    		} catch (Exception e) {
    			log.error("Nie odczytano konfiguracji", e);
    		}
    		final String uRIByPathProperty=props.getProperty("tml.httpclient.config.URIByPath");
    		if((uRIByPathProperty!=null)&&(uRIByPathProperty.length()>0)) {
    			int i=0; String[] paths=null;
    			for(final String values:uRIByPathProperty.split("[|]")) {
    				if(0==i++){
    					paths=values.split(",");
    					continue;
    				}
    				i=0;
    				final String[] uRIParams=values.split(",");
    				for(final String path:paths){
    					TML_URI_BY_PATH.put(path, uRIParams);
    				}
    			}
    		}
    		log.info("Inicjalizacja zewnetrznej konfiguracji zakonczona");
    	}
    }

    protected abstract CloseableHttpResponse doExecute(HttpHost target, HttpRequest request,
            HttpContext context) throws IOException, ClientProtocolException;

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseableHttpResponse execute(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context) throws IOException, ClientProtocolException {
        return doExecute(target, request, context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseableHttpResponse execute(
            final HttpUriRequest request,
            final HttpContext context) throws IOException, ClientProtocolException {
        Args.notNull(request, "HTTP request");
        return doExecute(determineTarget(request), request, context);
    }

    private static HttpHost determineTarget(final HttpUriRequest request) throws ClientProtocolException {
        // A null target may be acceptable if there is a default target.
        // Otherwise, the null target is detected in the director.
        HttpHost target = null;

        final URI tmlRequestURI = request.getURI();
        log.info("orgHost="+tmlRequestURI.getHost()+" orgPort="+tmlRequestURI.getPort()
        	+" orgScheme="+tmlRequestURI.getScheme() +" orgUserInfo="+tmlRequestURI.getUserInfo()
        	+" orgPath="+tmlRequestURI.getPath() +" orgQuery="+tmlRequestURI.getQuery() +" orgFragment="+tmlRequestURI.getFragment());
        String tmlPath=tmlRequestURI.getPath();
        String tmlKey=tmlPath;
        int i;
        if(tmlPath!=null){
        	i=tmlPath.indexOf('/',1);
        	if(i>-1){
        		tmlKey=tmlPath.substring(0,i+1);
        	}
        }
        final String[] tmlURIParameters=TML_URI_BY_PATH.get(tmlKey);
        if(tmlURIParameters!=null) {
        	i=tmlPath.indexOf(tmlKey, 1);
        	if(i>-1){
        		tmlPath=tmlPath.substring(0,i);
        	}
        	String tmlQuery=tmlRequestURI.getQuery();
        	if(tmlQuery!=null){
        		i=tmlQuery.indexOf(tmlKey);
        		if(i>-1){
        			tmlQuery=tmlQuery.substring(0,i);
        		}
        	}
        	try{
        		((org.apache.http.client.methods.HttpRequestBase)request).setURI(new URI(tmlURIParameters[0], tmlRequestURI.getUserInfo(),
        			tmlURIParameters[1], Integer.valueOf(tmlURIParameters[2]), tmlPath, tmlQuery, tmlRequestURI.getFragment()));
        	}catch(java.net.URISyntaxException ex){
            	throw new ClientProtocolException("cannot prepare new URI by original URI: "+ tmlRequestURI);
        	}
        }
        final URI requestURI = request.getURI();
        log.info("host="+requestURI.getHost()+" port="+requestURI.getPort()
        	+" scheme="+requestURI.getScheme() +" userInfo="+requestURI.getUserInfo()
        	+" path="+requestURI.getPath() +" query="+requestURI.getQuery() +" fragment="+requestURI.getFragment());
        if (requestURI.isAbsolute()) {
            target = URIUtils.extractHost(requestURI);
            if (target == null) {
                throw new ClientProtocolException("URI does not specify a valid host name: "
                        + requestURI);
            }
        }
        return target;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseableHttpResponse execute(
            final HttpUriRequest request) throws IOException, ClientProtocolException {
        return execute(request, (HttpContext) null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseableHttpResponse execute(
            final HttpHost target,
            final HttpRequest request) throws IOException, ClientProtocolException {
        return doExecute(target, request, null);
    }

    /**
     * Executes a request using the default context and processes the
     * response using the given response handler. The content entity associated
     * with the response is fully consumed and the underlying connection is
     * released back to the connection manager automatically in all cases
     * relieving individual {@link ResponseHandler}s from having to manage
     * resource deallocation internally.
     *
     * @param request   the request to execute
     * @param responseHandler the response handler
     *
     * @return  the response object as generated by the response handler.
     * @throws IOException in case of a problem or the connection was aborted
     * @throws ClientProtocolException in case of an http protocol error
     */
    @Override
    public <T> T execute(final HttpUriRequest request,
            final ResponseHandler<? extends T> responseHandler) throws IOException,
            ClientProtocolException {
        return execute(request, responseHandler, null);
    }

    /**
     * Executes a request using the default context and processes the
     * response using the given response handler. The content entity associated
     * with the response is fully consumed and the underlying connection is
     * released back to the connection manager automatically in all cases
     * relieving individual {@link ResponseHandler}s from having to manage
     * resource deallocation internally.
     *
     * @param request   the request to execute
     * @param responseHandler the response handler
     * @param context   the context to use for the execution, or
     *                  {@code null} to use the default context
     *
     * @return  the response object as generated by the response handler.
     * @throws IOException in case of a problem or the connection was aborted
     * @throws ClientProtocolException in case of an http protocol error
     */
    @Override
    public <T> T execute(final HttpUriRequest request,
            final ResponseHandler<? extends T> responseHandler, final HttpContext context)
            throws IOException, ClientProtocolException {
        return execute(determineTarget(request), request, responseHandler, context);
    }

    /**
     * Executes a request using the default context and processes the
     * response using the given response handler. The content entity associated
     * with the response is fully consumed and the underlying connection is
     * released back to the connection manager automatically in all cases
     * relieving individual {@link ResponseHandler}s from having to manage
     * resource deallocation internally.
     *
     * @param target    the target host for the request.
     *                  Implementations may accept {@code null}
     *                  if they can still determine a route, for example
     *                  to a default target or by inspecting the request.
     * @param request   the request to execute
     * @param responseHandler the response handler
     *
     * @return  the response object as generated by the response handler.
     * @throws IOException in case of a problem or the connection was aborted
     * @throws ClientProtocolException in case of an http protocol error
     */
    @Override
    public <T> T execute(final HttpHost target, final HttpRequest request,
            final ResponseHandler<? extends T> responseHandler) throws IOException,
            ClientProtocolException {
        return execute(target, request, responseHandler, null);
    }

    /**
     * Executes a request using the default context and processes the
     * response using the given response handler. The content entity associated
     * with the response is fully consumed and the underlying connection is
     * released back to the connection manager automatically in all cases
     * relieving individual {@link ResponseHandler}s from having to manage
     * resource deallocation internally.
     *
     * @param target    the target host for the request.
     *                  Implementations may accept {@code null}
     *                  if they can still determine a route, for example
     *                  to a default target or by inspecting the request.
     * @param request   the request to execute
     * @param responseHandler the response handler
     * @param context   the context to use for the execution, or
     *                  {@code null} to use the default context
     *
     * @return  the response object as generated by the response handler.
     * @throws IOException in case of a problem or the connection was aborted
     * @throws ClientProtocolException in case of an http protocol error
     */
    @Override
    public <T> T execute(final HttpHost target, final HttpRequest request,
            final ResponseHandler<? extends T> responseHandler, final HttpContext context)
            throws IOException, ClientProtocolException {
        Args.notNull(responseHandler, "Response handler");

        final CloseableHttpResponse response = execute(target, request, context);
        try {
            final T result = responseHandler.handleResponse(response);
            final HttpEntity entity = response.getEntity();
            EntityUtils.consume(entity);
            return result;
        } catch (final ClientProtocolException t) {
            // Try to salvage the underlying connection in case of a protocol exception
            final HttpEntity entity = response.getEntity();
            try {
                EntityUtils.consume(entity);
            } catch (final Exception t2) {
                // Log this exception. The original exception is more
                // important and will be thrown to the caller.
                this.log.warn("Error consuming content after an exception.", t2);
            }
            throw t;
        } finally {
            response.close();
        }
    }
}
