package com.prephub.importer;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class CsvQuestionParserTest {
    private final CsvQuestionParser parser = new CsvQuestionParser();

    @Test void parsesQuotedMultilineAndMultipleCompanies() {
        String csv = "\uFEFFQuestion,Topic,Tags,Company,Difficulty,Answer\r\n"
                + "\"Explain HashMap internals\",Java,\"HashMap, Collections\",\"Amazon; Microsoft ;amazon\",ADVANCED,\"Line1\nLine2 \"\"quoted\"\"\"\r\n"
                + ",,,,,\r\n"
                + "What is a Spring Bean?,Spring Boot,,,INTERMEDIATE,\r\n";
        var r = parser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertThat(r.candidates()).hasSize(2);
        var a = r.candidates().get(0);
        assertThat(a.sourceReference()).isEqualTo("row 2");
        assertThat(a.tags()).containsExactly("HashMap", "Collections");
        assertThat(a.companies()).containsExactly("Amazon", "Microsoft");
        assertThat(a.answer()).isEqualTo("Line1\nLine2 \"quoted\"");
        var b = r.candidates().get(1);
        assertThat(b.sourceReference()).isEqualTo("row 4");
        assertThat(b.companies()).isEmpty();
        assertThat(b.answer()).isNull();
    }

    @Test void requiresQuestionColumn() {
        assertThatThrownBy(() -> parser.parse("topic,tags\nJava,x\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void unterminatedQuoteFails() {
        assertThatThrownBy(() -> CsvReader.parse("question\n\"abc\n")).isInstanceOf(IllegalArgumentException.class);
    }
}
