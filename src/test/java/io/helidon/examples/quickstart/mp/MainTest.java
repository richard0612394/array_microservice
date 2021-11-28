
package io.helidon.examples.quickstart.mp;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.client.WebTarget;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.List;


@HelidonTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MainTest {

    private int id1;
    private List ids = new ArrayList<>();;
    private JsonObject jsonObject;
    private Response response;

    @Inject
    private WebTarget target;

    @Test
    @Order(1)
    void testPostOfIdenticalArrayReturnsTheSameId() {
        id1 = testArrayPostAndReturnId("[1, 2]");

        Assertions.assertEquals(testArrayPostAndReturnId("[1, 2]"), id1,
                "Ids of requests having identical arrays are not the same.");
    }

    @Test
    @Order(2)
    void testPostOfDifferentArrayReturnsDifferentId() {
        Assertions.assertNotEquals(testArrayPostAndReturnId("[1, 3]"), id1,
                "Ids of requests having different arrays are the same.");
    }

    @Test
    @Order(3)
    void testPostRequests() {
        for (int i = 0; i < 10; i ++) {
            ids.add(testArrayPostAndReturnId("[1, " + i + "]"));
        }
    }

    @Test()
    @Order(4)
    void testSynchronousGetRequests() {
        for (int i = 0; i < 10; i ++) {
            testSynchronousResponseOnArray(ids.get(i).toString(), "[[1, " + i +"], [" + i + ", 1]]");
        }
    }

    @Test
    @Order(5)
    void testAsynchronousGetRequests() {
        for (int i = 0; i < 10; i ++) {
            testAsynchronousGetProgress(ids.get(i).toString(), "100%");
        }
    }

    @Test
    @Order(6)
    void testBadRequestWhenJsonIsMissingArrayKey() {
        response = target
                .path("array")
                .request()
                .post(Entity.entity("{\"randomKey\" : [1, 2, 3]}", MediaType.APPLICATION_JSON));
        Assertions.assertEquals(Response.status(Response.Status.BAD_REQUEST).build().getStatus(), response.getStatus());
        Assertions.assertEquals(ArrayResource.MISSING_FIELD_ARRAY_BAD_REQUEST_CAUSE, response.getHeaderString("cause"));
    }

    @Test
    @Order(7)
    void testBadRequestWhenJsonIsMissingValidJsonArray() {
        response = target
                .path("array")
                .request()
                .post(Entity.entity("{\"array\" : 1}", MediaType.APPLICATION_JSON));
        Assertions.assertEquals(Response.status(Response.Status.BAD_REQUEST).build().getStatus(),
                response.getStatus());
        Assertions.assertEquals(ArrayResource.CAN_NOT_PARSE_ARRAY_VALUE_BAD_REQUEST_CAUSE,
                response.getHeaderString("cause"));
    }

    @Test
    @Order(8)
    void testNotFoundWhenNonExistingIdPassedAsParameter() {
        response = target
                .path("array/0")
                .request()
                .get();
        Assertions.assertEquals(Response.status(Response.Status.NOT_FOUND).build().getStatus(),
                response.getStatus());
    }

    @Test
    @Order(9)
    void testMetrics() {
        response = target
                .path("metrics")
                .request()
                .get();
        Assertions.assertEquals(Response.status(Response.Status.OK).build().getStatus(), response.getStatus());
    }

    @Test
    @Order(10)
    void testHealth() {
        response = target
                .path("health")
                .request()
                .get();
        Assertions.assertEquals(Response.status(Response.Status.OK).build().getStatus(), response.getStatus());
    }

    private void testSynchronousResponseOnArray(String id, String expectedResponseContent) {
        JsonObject jsonObject = target
                .path("array/" + id)
                .request()
                .get(JsonObject.class);
        Assertions.assertEquals(expectedResponseContent, jsonObject.getString("array"),
                "Returned permutations do not match expected value.");
    }

    private int testArrayPostAndReturnId(String array) {
        int id = 0;
        response = target
                .path("array")
                .request()
                .post(Entity.entity("{\"array\" : " + array + "}", MediaType.APPLICATION_JSON));
        Assertions.assertEquals(Response.status(Response.Status.ACCEPTED).build().getStatus(), response.getStatus());

        try {
            id = Integer.parseInt(response.getHeaderString("id"));
        } catch (NumberFormatException e) {
            Assertions.fail(e.getMessage());
        }
        Assertions.assertNotEquals(id, 0, "Id of array not returned.");
        return id;
    }

    private void testAsynchronousGetProgress(String arrayKey, String expectedResponseContent) {
        jsonObject = target
                .path("array/" + arrayKey)
                .queryParam("async", "true")
                .request()
                .get(JsonObject.class);
        Assertions.assertEquals(expectedResponseContent, jsonObject.getString("progress"));
    }
}