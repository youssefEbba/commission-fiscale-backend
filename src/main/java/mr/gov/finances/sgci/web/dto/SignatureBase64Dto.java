package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Image encodée en data URL — nécessaire car les PDF sont générés côté navigateur (jsPDF), qui ne
 * sait pas suivre une URL protégée par authentification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignatureBase64Dto {
    private String dataUrl;
}
