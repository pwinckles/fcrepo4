/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.http.api;

import org.fcrepo.http.commons.api.rdf.HttpIdentifierConverter;
import org.fcrepo.search.api.Condition;
import org.fcrepo.search.api.InvalidConditionExpressionException;
import org.fcrepo.search.api.InvalidQueryException;
import org.fcrepo.search.api.SearchParameters;
import org.fcrepo.search.api.SearchResult;
import org.fcrepo.search.api.SearchService;
import org.slf4j.Logger;
import org.springframework.context.annotation.Scope;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.ok;
import static org.fcrepo.http.commons.domain.RDFMediaType.TEXT_PLAIN_WITH_CHARSET;
import static org.fcrepo.kernel.api.FedoraTypes.FEDORA_ID_PREFIX;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author dbernstein
 * @since 05/06/20
 */

@Scope("request")
@Path("/fcr:search")
public class FedoraSearch extends FedoraBaseResource {

    private static final Logger LOGGER = getLogger(FedoraSearch.class);

    @Inject
    private SearchService service;

    /**
     * Default JAX-RS entry point
     */
    public FedoraSearch() {
        super();
    }

    /**
     * Perform simple search on the repository
     *
     * @param conditions The conditions constraining the query
     * @param maxResults The max number of results to return
     * @param offset     The zero-based offset of the first result to be returned
     * @return A response object with the search results
     */
    @GET
    @Produces({APPLICATION_JSON + ";qs=1.0",
            TEXT_PLAIN_WITH_CHARSET})
    public Response doSearch(@QueryParam(value = "condition") final List<String> conditions,
                             @DefaultValue("100") @QueryParam("max_results") final int maxResults,
                             @DefaultValue("0") @QueryParam("offset") final int offset) {

        try {
            final var conditionList = new ArrayList<Condition>();
            for (String condition : conditions) {
                final var parsedCondition = parse(condition, identifierConverter());
                conditionList.add(parsedCondition);
            }
            final var params = new SearchParameters(conditionList, maxResults, offset);
            final Response.ResponseBuilder builder = ok();
            final var result = this.service.doSearch(params);
            final var translatedResults = translateResults(result);

            builder.entity(translatedResults);
            return builder.build();
        } catch (final InvalidConditionExpressionException | InvalidQueryException ex) {
            throw new BadRequestException(ex);
        }
    }

    private SearchResult translateResults(final SearchResult result) {
        result.getItems().forEach(item -> {
            final var key = Condition.Field.FEDORA_ID.toString();
            final var fedoraId = item.get(key);
            if (fedoraId != null) {
                item.put(key, identifierConverter().toExternalId(fedoraId.toString()));
            }
        });
        return result;
    }

    /**
     * Parses the url decoded value of a single parameter passed by the
     * http layer into a {@link Condition}.
     *
     * @param expression The url decoded value of the condition parameter.
     * @return the parsed {@link Condition} object.
     */
    protected static Condition parse(final String expression, final HttpIdentifierConverter converter)
            throws InvalidConditionExpressionException {
        final Condition condition = Condition.fromExpression(expression);
        if (condition.getField().equals(Condition.Field.FEDORA_ID)) {
            //convert the object value to an internal identifier stem where appropriate
            final var object = condition.getObject();
            final var field = condition.getField();
            final var operator = condition.getOperator();
            if (!object.startsWith(FEDORA_ID_PREFIX) && isExternalUrl(object)) {
                return Condition.fromEnums(field, operator, converter.toInternalId(object));
            } else if (object.startsWith("/")) {
                return Condition.fromEnums(field, operator, FEDORA_ID_PREFIX + object);
            } else if (!object.startsWith(FEDORA_ID_PREFIX) && !object.equals("*")) {
                return Condition.fromEnums(field, operator, FEDORA_ID_PREFIX + "/" + object);
            }
        }

        return condition;
    }

    private static boolean isExternalUrl(final String str) {
        try {
            new URL(str);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

}

