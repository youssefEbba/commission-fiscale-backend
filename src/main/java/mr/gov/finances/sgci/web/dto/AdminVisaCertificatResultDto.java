package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Compte rendu d'un visa de certificat posé par l'administrateur à la place du rôle titulaire. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminVisaCertificatResultDto {

    /** Le certificat après application du visa. */
    private CertificatCreditDto certificat;

    /** Décision créée. {@code null} lorsqu'il s'agit d'une validation à la place du Président. */
    private DecisionCreditDto decision;

    /** Document téléversé avec le visa. {@code null} si aucun fichier n'a été fourni. */
    private DocumentCertificatCreditDto document;

    /** État complet des visas après l'opération. */
    @Builder.Default
    private List<VisaCertificatStatutDto> visas = new ArrayList<>();
}
