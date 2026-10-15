package com.prephub.importer;

import com.prephub.common.Enums.AttributionMode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CompanyAttributionResolverTest {
    @Test void noneIgnoresEverything() {
        assertThat(CompanyAttributionResolver.resolve(AttributionMode.NONE, "Amazon", List.of("Google"))).isEmpty();
    }
    @Test void singleCompanyAppliesDefault() {
        assertThat(CompanyAttributionResolver.resolve(AttributionMode.SINGLE_COMPANY, "Amazon", List.of())).containsExactly("Amazon");
    }
    @Test void rowOverridesDefault() {
        assertThat(CompanyAttributionResolver.resolve(AttributionMode.SINGLE_COMPANY, "Amazon", List.of("Infosys", "TCS")))
                .containsExactly("Infosys", "TCS");
    }
    @Test void perRowWithoutDefaultStaysUnknown() {
        assertThat(CompanyAttributionResolver.resolve(AttributionMode.PER_ROW, null, List.of())).isEmpty();
    }
}
