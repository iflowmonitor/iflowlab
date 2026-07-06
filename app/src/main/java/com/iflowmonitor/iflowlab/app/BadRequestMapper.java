package com.iflowmonitor.iflowlab.app;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/** Client input errors (path traversal, bad names, missing workspace dir) → HTTP 400. */
@Provider
public class BadRequestMapper implements ExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(IllegalArgumentException e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", e.getMessage() == null ? "bad request" : e.getMessage()))
                .build();
    }
}
