/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.systest.jaxrs.description.openapi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.jaxrs.model.AbstractResourceInfo;
import org.apache.cxf.jaxrs.model.Parameter;
import org.apache.cxf.jaxrs.model.ParameterType;
import org.apache.cxf.jaxrs.model.UserApplication;
import org.apache.cxf.jaxrs.model.UserOperation;
import org.apache.cxf.jaxrs.model.UserResource;
import org.apache.cxf.jaxrs.openapi.OpenApiFeature;
import org.apache.cxf.jaxrs.openapi.parse.OpenApiParseUtils;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.cxf.testutil.common.AbstractBusTestServerBase;
import org.hamcrest.CoreMatchers;

import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;

import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

public abstract class AbstractOpenApiServiceDescriptionTest extends AbstractBusClientServerTestBase {
    static final String SECURITY_DEFINITION_NAME = "basicAuth";
    
    private static final String CONTACT = "cxf@apache.org";
    private static final String TITLE = "CXF unittest";
    private static final String DESCRIPTION = "API Description";
    private static final String LICENSE = "API License";
    private static final String LICENSE_URL = "API License URL";
    
    @Ignore
    public abstract static class Server extends AbstractBusTestServerBase {
        protected final String port;
        protected final boolean runAsFilter;

        Server(final String port, final boolean runAsFilter) {
            this.port = port;
            this.runAsFilter = runAsFilter;
        }

        @Override
        protected void run() {
            final JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
            sf.setResourceClasses(BookStoreOpenApi.class);
            sf.setResourceClasses(BookStoreStylesheetsOpenApi.class);
            sf.setResourceProvider(BookStoreOpenApi.class,
                new SingletonResourceProvider(new BookStoreOpenApi()));
            sf.setProvider(new JacksonJsonProvider());
            final OpenApiFeature feature = createOpenApiFeature();
            sf.setFeatures(Arrays.asList(feature));
            sf.setAddress("http://localhost:" + port + "/");
            sf.create();
        }
        
        protected OpenApiFeature createOpenApiFeature() {
            final OpenApiFeature feature = new OpenApiFeature();
            feature.setRunAsFilter(runAsFilter);
            feature.setContactName(CONTACT);
            feature.setTitle(TITLE);
            feature.setDescription(DESCRIPTION);
            feature.setLicense(LICENSE);
            feature.setLicenseUrl(LICENSE_URL);
            
            feature.setSecurityDefinitions(Collections.singletonMap(SECURITY_DEFINITION_NAME,
                new SecurityScheme().type(Type.HTTP)));

            return feature;
        }

        protected static void start(final Server s) {
            try {
                s.start();
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(-1);
            } finally {
                System.out.println("done!");
            }
        }
    }

    protected static void startServers(final Class< ? extends Server> serverClass) throws Exception {
        AbstractResourceInfo.clearAllMaps();
        //keep out of process due to stack traces testing failures
        assertTrue("server did not launch correctly", launchServer(serverClass, false));
        createStaticBus();
    }

    protected abstract String getPort();

    protected void doTestApiListingIsProperlyReturnedJSON() throws Exception {
        doTestApiListingIsProperlyReturnedJSON(false);
    }
    protected void doTestApiListingIsProperlyReturnedJSON(boolean useXForwarded) throws Exception {
        doTestApiListingIsProperlyReturnedJSON(useXForwarded, null);
    }
    protected void doTestApiListingIsProperlyReturnedJSON(boolean useXForwarded, String basePath) throws Exception {
        doTestApiListingIsProperlyReturnedJSON(createWebClient("/openapi.json"), useXForwarded, basePath);
        checkUiResource();
    }
    protected static void doTestApiListingIsProperlyReturnedJSON(final WebClient client,
            boolean useXForwarded, String basePath) throws Exception {    
        if (useXForwarded) {
            client.header("USE_XFORWARDED", true);
        }
        try {
            String swaggerJson = client.get(String.class);
            UserApplication ap = OpenApiParseUtils.getUserApplicationFromJson(swaggerJson);
            assertNotNull(ap);
            
            if (basePath == null) {
                assertEquals(useXForwarded ? "/reverse" : "/", ap.getBasePath());
            } else {
                assertEquals(basePath, ap.getBasePath());
            }
            
            List<UserResource> urs = ap.getResources();
            assertNotNull(urs);
            assertEquals(1, urs.size());
            UserResource r = urs.get(0);
            
            Map<String, UserOperation> map = r.getOperationsAsMap();
            assertEquals(3, map.size());
            UserOperation getBooksOp = map.get("getBooks");
            assertEquals(HttpMethod.GET, getBooksOp.getVerb());
            assertEquals("/bookstore", getBooksOp.getPath());
            // see https://github.com/swagger-api/swagger-core/issues/2646
            if (getBooksOp.getProduces() != null) {
                assertEquals(MediaType.APPLICATION_JSON, getBooksOp.getProduces());
            }
            List<Parameter> getBooksOpParams = getBooksOp.getParameters();
            assertEquals(1, getBooksOpParams.size());
            assertEquals(ParameterType.QUERY, getBooksOpParams.get(0).getType());
            UserOperation getBookOp = map.get("getBook");
            assertEquals(HttpMethod.GET, getBookOp.getVerb());
            assertEquals("/bookstore/{id}", getBookOp.getPath());
            assertEquals(MediaType.APPLICATION_JSON, getBookOp.getProduces());
            List<Parameter> getBookOpParams = getBookOp.getParameters();
            assertEquals(1, getBookOpParams.size());
            assertEquals(ParameterType.PATH, getBookOpParams.get(0).getType());
            UserOperation deleteOp = map.get("delete");
            assertEquals(HttpMethod.DELETE, deleteOp.getVerb());
            assertEquals("/bookstore/{id}", deleteOp.getPath());
            List<Parameter> delOpParams = deleteOp.getParameters();
            assertEquals(1, delOpParams.size());
            assertEquals(ParameterType.PATH, delOpParams.get(0).getType());

            assertThat(swaggerJson, CoreMatchers.containsString(CONTACT));
            assertThat(swaggerJson, CoreMatchers.containsString(TITLE));
            assertThat(swaggerJson, CoreMatchers.containsString(DESCRIPTION));
            assertThat(swaggerJson, CoreMatchers.containsString(LICENSE));
            assertThat(swaggerJson, CoreMatchers.containsString(LICENSE_URL));
            assertThat(swaggerJson, CoreMatchers.containsString(SECURITY_DEFINITION_NAME));
        } finally {
            client.close();
        }
    }

    @Test
    public void testNonUiResource() {
        // Test that Swagger UI resources do not interfere with 
        // application-specific ones.
        WebClient uiClient = WebClient
            .create("http://localhost:" + getPort() + "/css/book.css")
            .accept("text/css");
        String css = uiClient.get(String.class);
        assertThat(css, equalTo("body { background-color: lightblue; }"));
    }
    
    @Test
    public void testUiResource() {
        // Test that Swagger UI resources do not interfere with 
        // application-specific ones and are accessible.
        WebClient uiClient = WebClient
            .create("http://localhost:" + getPort() + "/swagger-ui.css")
            .accept("text/css");
        String css = uiClient.get(String.class);
        assertThat(css, containsString(".swagger-ui{font"));
    }
    

    protected WebClient createWebClient(final String url) {
        return WebClient
            .create("http://localhost:" + getPort() + url,
                Arrays.< Object >asList(new JacksonJsonProvider()),
                Arrays.< Feature >asList(new LoggingFeature()),
                null)
            .accept(MediaType.APPLICATION_JSON).accept("application/yaml");
    }

    protected void checkUiResource() {
        WebClient uiClient = WebClient.create("http://localhost:" + getPort() + "/api-docs")
            .accept(MediaType.WILDCARD);
        String uiHtml = uiClient.get(String.class);
        assertTrue(uiHtml.contains("<title>Swagger UI</title>"));
    }
}
