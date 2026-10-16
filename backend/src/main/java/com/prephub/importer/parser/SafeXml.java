package com.prephub.importer.parser;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** XXE-safe StAX reader and size-limited ZIP extraction used by the OOXML (XLSX/DOCX) parsers. */
public final class SafeXml {
    private SafeXml() {}

    private static final XMLInputFactory FACTORY;
    static {
        FACTORY = XMLInputFactory.newFactory();
        FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        FACTORY.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        FACTORY.setProperty(XMLInputFactory.IS_COALESCING, true);
    }

    public static XMLStreamReader reader(byte[] xml) {
        try { return FACTORY.createXMLStreamReader(new ByteArrayInputStream(xml)); }
        catch (XMLStreamException e) { throw new IllegalArgumentException("Malformed XML inside the document"); }
    }

    /**
     * Reads only the wanted entries (exact names, or names starting with a wanted prefix ending in '/').
     * Enforces entry-count and uncompressed-size limits to defeat zip bombs.
     */
    public static Map<String, byte[]> unzip(byte[] zip, Set<String> wanted) {
        Map<String, byte[]> out = new HashMap<>();
        long total = 0;
        int entries = 0;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zin.getNextEntry()) != null) {
                if (++entries > ImportLimits.MAX_ZIP_ENTRIES) throw new IllegalArgumentException("Document contains too many parts");
                String name = e.getName();
                if (e.isDirectory() || !isWanted(name, wanted)) {
                    total += drain(zin, buf, ImportLimits.MAX_ZIP_ENTRY_BYTES);
                } else {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    long n = 0;
                    int r;
                    while ((r = zin.read(buf)) > 0) {
                        n += r;
                        if (n > ImportLimits.MAX_ZIP_ENTRY_BYTES) throw new IllegalArgumentException("Document part is too large");
                        bos.write(buf, 0, r);
                    }
                    total += n;
                    out.put(name, bos.toByteArray());
                }
                if (total > ImportLimits.MAX_ZIP_TOTAL_BYTES) throw new IllegalArgumentException("Document expands to more than the allowed size");
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("File is not a valid Office document (ZIP could not be read)");
        }
        if (entries == 0) throw new IllegalArgumentException("File is not a valid Office document (empty ZIP)");
        return out;
    }

    private static boolean isWanted(String name, Set<String> wanted) {
        for (String w : wanted) if (w.endsWith("/") ? name.startsWith(w) : name.equals(w)) return true;
        return false;
    }

    private static long drain(InputStream in, byte[] buf, long limit) throws IOException {
        long n = 0;
        int r;
        while ((r = in.read(buf)) > 0) {
            n += r;
            if (n > limit) throw new IllegalArgumentException("Document part is too large");
        }
        return n;
    }
}
