package com.prephub.settings;

import com.prephub.ai.AiTextClient;
import com.prephub.importer.SemanticDuplicateDetector;
import com.prephub.importer.parser.ParserRegistry;
import com.prephub.semantic.EmbeddingClient;
import com.prephub.semantic.EmbeddingIndexer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Runtime configuration for the Settings page. Never returns secrets. */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {
    private final AiTextClient ai;
    private final EmbeddingClient embeddings;
    private final EmbeddingIndexer indexer;
    private final SemanticDuplicateDetector detector;
    private final ParserRegistry parsers;
    private final DataSize maxUpload;
    private final String timezone;

    public SettingsController(AiTextClient ai, EmbeddingClient embeddings, EmbeddingIndexer indexer, SemanticDuplicateDetector detector,
                              ParserRegistry parsers, @Value("${spring.servlet.multipart.max-file-size:10MB}") DataSize maxUpload,
                              @Value("${prephub.timezone:Asia/Kolkata}") String timezone) {
        this.ai = ai; this.embeddings = embeddings; this.indexer = indexer; this.detector = detector; this.parsers = parsers;
        this.maxUpload = maxUpload; this.timezone = timezone;
    }

    public record AiSettings(boolean enabled, String provider, String model, String status) {}
    public record SemanticSettings(boolean enabled, String model, double likelyThreshold, double possibleThreshold, EmbeddingIndexer.Status index) {}
    public record ImportSettings(List<String> formats, long maxUploadMb, String companyDelimiter, String tagDelimiter) {}
    public record Settings(AiSettings ai, SemanticSettings semantic, ImportSettings imports, String timezone) {}

    @GetMapping
    public Settings get() {
        return new Settings(new AiSettings(ai.available(), ai.provider(), ai.model(), ai.status()),
                new SemanticSettings(embeddings.available(), embeddings.model(), detector.likelyThreshold(), detector.possibleThreshold(), indexer.status()),
                new ImportSettings(List.copyOf(parsers.supportedExtensions()), maxUpload.toMegabytes(), ";", ","), timezone);
    }

    public record ConnectionTest(boolean chatOk, String chatMessage, boolean embeddingsOk, String embeddingsMessage) {}

    /** Makes one tiny chat call and one tiny embedding call so you can confirm the API key and models work. */
    @PostMapping("/ai/test")
    public ConnectionTest test() {
        boolean chatOk = false, embOk = false;
        String chatMsg, embMsg;
        if (!ai.available()) chatMsg = ai.status();
        else try {
            String r = ai.complete("You are a connectivity check.", "Reply with the single word OK.");
            chatOk = r != null && !r.isBlank();
            chatMsg = chatOk ? "Chat model " + ai.model() + " replied" : "Empty reply";
        } catch (RuntimeException e) { chatMsg = e.getMessage(); }
        if (!embeddings.available()) embMsg = embeddings.status();
        else try {
            int dims = embeddings.embed(List.of("connectivity check")).get(0).length;
            embOk = dims > 0;
            embMsg = "Embedding model " + embeddings.model() + " returned " + dims + " dimensions";
        } catch (RuntimeException e) { embMsg = e.getMessage(); }
        return new ConnectionTest(chatOk, chatMsg, embOk, embMsg);
    }
}
