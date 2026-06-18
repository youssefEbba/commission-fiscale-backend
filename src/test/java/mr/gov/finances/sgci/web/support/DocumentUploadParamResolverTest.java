package mr.gov.finances.sgci.web.support;

import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentUploadParamResolverTest {

    @Test
    void resolve_prefers_codeDocument() {
        assertThat(DocumentUploadParamResolver.resolveCodeDocument("OFFRE", "OTHER", "LEGACY"))
                .isEqualTo("OFFRE");
    }

    @Test
    void resolve_falls_back_to_typeDocument_then_type() {
        assertThat(DocumentUploadParamResolver.resolveCodeDocument(null, "CREDIT_EXTERIEUR", null))
                .isEqualTo("CREDIT_EXTERIEUR");
        assertThat(DocumentUploadParamResolver.resolveCodeDocument(null, null, "  DECLARATION_DOUANE  "))
                .isEqualTo("DECLARATION_DOUANE");
    }

    @Test
    void resolve_throws_when_all_missing() {
        assertThatThrownBy(() -> DocumentUploadParamResolver.resolveCodeDocument(null, "", " "))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("codeDocument");
    }
}
