package com.prephub.importer.parser;

import java.nio.ByteBuffer;
import java.nio.charset.*;

/** Decodes text files as UTF-8 (BOM stripped); falls back to Windows-1252 for legacy files. */
public final class TextDecoder {
    private TextDecoder() {}

    public static String decode(byte[] bytes) {
        String s;
        try {
            s = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            s = new String(bytes, Charset.forName("windows-1252"));
        }
        if (s.startsWith("\uFEFF")) s = s.substring(1);
        return s;
    }
}
