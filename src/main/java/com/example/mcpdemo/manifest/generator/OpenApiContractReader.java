package com.example.mcpdemo.manifest.generator;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Component;

@Component
public class OpenApiContractReader {

    public OpenAPI read(String location) {
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(location, null, null);
        if (result == null || result.getOpenAPI() == null) {
            throw new IllegalStateException("OpenAPI could not be parsed from " + location + ". Messages=" + (result != null ? result.getMessages() : "null"));
        }
        return result.getOpenAPI();
    }
}
