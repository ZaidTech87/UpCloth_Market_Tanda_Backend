package com.clothmarket.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Writes a stored media reference (an R2 object key) as a short-lived signed URL.
 * Applied per field with @JsonSerialize(using = MediaUrlSerializer.class); it is NOT a global
 * String serializer, so ordinary text fields are unaffected.
 */
public class MediaUrlSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        MediaUrlResolver resolver = MediaUrlResolver.getInstance();
        String resolved = resolver == null ? value : resolver.resolve(value);
        if (resolved == null) {
            gen.writeNull();
        } else {
            gen.writeString(resolved);
        }
    }
}