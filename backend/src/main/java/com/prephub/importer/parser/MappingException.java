package com.prephub.importer.parser;

import java.util.List;
import java.util.Map;

/** Column mapping problem in a tabular file; carries the headers so the UI can offer manual mapping. */
public class MappingException extends IllegalArgumentException {
    private final List<String> headers;
    private final Map<String, String> mapping;
    public MappingException(String message, List<String> headers, Map<String, String> mapping) {
        super(message);
        this.headers = headers; this.mapping = mapping;
    }
    public List<String> getHeaders() { return headers; }
    public Map<String, String> getMapping() { return mapping; }
}
