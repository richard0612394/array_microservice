
package io.helidon.examples.quickstart.mp;

import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.util.Collections;
import java.util.List;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.stream.JsonParsingException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;


@Path("/array")
@RequestScoped
public class ArrayResource {
    public static final String MISSING_FIELD_ARRAY_BAD_REQUEST_CAUSE = "missing field 'array'";
    public static final String CAN_NOT_PARSE_ARRAY_VALUE_BAD_REQUEST_CAUSE =
            "value of key 'array' in json JSON did not contain JsonArray object";
    private static final String ARRAY_SIZE_TOO_BIG_REQUEST_CAUSE = "size of the array is too big";

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private final ArrayPermutationProvider arrayProvider;

    @Inject
    public ArrayResource(ArrayPermutationProvider arrayPermutationProvider) {
        this.arrayProvider = arrayPermutationProvider;
    }

    /**
     * Return JsonObject with String value identified by key.
     *
     * @param   key
     * @param   value
     * @return  JsonObject with key=value
     */
    private JsonObject createStringResponse(String key, String value) {
        return JSON.createObjectBuilder()
                .add(key, value)
                .build();
    }

    /**
     * Return JsonObject with JsonArray value identified by key.
     *
     * @param   key
     * @param   array
     * @return  JsonObject with key=value
     */
    private JsonObject createArrayResponse(String key, List array) {
        return JSON.createObjectBuilder()
                .add(key, array.toString())
                .build();
    }

    /**
     * Return list of all permutations of input array synchronously.
     * Return progress of permutation operation in % in case parameter 'async=true' is used.
     *
     * @param   id      string identifier of the array
     * @return  Object  list of all permutations of input array or progress in %
     */
    @Path("/{id}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Object getMessage(@PathParam("id") String id,
                             @DefaultValue("false") @QueryParam("async") boolean async) {
        try {
            if (async) {
                return createStringResponse("progress", arrayProvider.getProgressInPercents(id));
            } else {
                List<List<Object>> array = arrayProvider.getPermutationsOfArray(id);
                arrayProvider.confirmReception(id);
                return createArrayResponse("array", array);
            }
        } catch (InvalidParameterException e) {
            System.out.println(e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .build();
        } catch (InvalidKeyException e) {
            System.out.println(e.getMessage());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .build();
        }
    }

    /**
     * Return response on post of json array.
     * JsonObject must contain field 'array' with JsonArray value.
     *
     * @return  Response with status accepted and header with id of posted array.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RequestBody(name = "array",
            required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(type = SchemaType.STRING, example = "{\"array\" : [1, 2, 3]}")))
    @APIResponses({
            @APIResponse(name = "normal", responseCode = "202", description = "Array updated"),
            @APIResponse(name = MISSING_FIELD_ARRAY_BAD_REQUEST_CAUSE, responseCode = "400",
                    description = "JSON did not contain setting for 'array'"),
            @APIResponse(name = "can not cast content", responseCode = "400",
                    description = CAN_NOT_PARSE_ARRAY_VALUE_BAD_REQUEST_CAUSE)})
    public Response updateArray(JsonObject jsonObject) {
        if (!jsonObject.containsKey("array")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .header("status", Response.Status.BAD_REQUEST.getStatusCode())
                    .header("cause", MISSING_FIELD_ARRAY_BAD_REQUEST_CAUSE)
                    .build();
        }

        List newArray;

        try {
            newArray = jsonObject.getJsonArray("array");
        } catch (ClassCastException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .header("status", Response.Status.BAD_REQUEST.getStatusCode())
                    .header("cause", CAN_NOT_PARSE_ARRAY_VALUE_BAD_REQUEST_CAUSE)
                    .build();
        } catch (JsonParsingException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .header("status", Response.Status.BAD_REQUEST.getStatusCode())
                    .header("cause", CAN_NOT_PARSE_ARRAY_VALUE_BAD_REQUEST_CAUSE)
                    .build();
        }

        String id;

        try {
            id = arrayProvider.setArray(newArray);
        } catch (InvalidParameterException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .header("cause", ARRAY_SIZE_TOO_BIG_REQUEST_CAUSE + ", maximum allowed array size is: "
                            + ArrayPermutationProvider.MAXIMUM_ALLOWED_ARRAY_SIZE + ", actual size: " + newArray.size())
                    .build();
        } catch (ServiceUnavailableException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .header("cause", Response.Status.SERVICE_UNAVAILABLE.getReasonPhrase())
                    .build();
        }

        return Response.status(Response.Status.ACCEPTED)
                .header("id", id)
                .build();
    }
}