package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutReclamationCorrection;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReclamationDemandeCorrectionDto {

    private Long id;
    private Long demandeCorrectionId;
    private StatutReclamationCorrection statut;
    private String texte;
    private Instant dateCreation;
    private Instant dateModification;
    private Instant dateTraitement;
    private Long auteurUserId;
    private String auteurNom;
    private Long traiteParUserId;
    private String motifReponse;
    private String pieceJointeChemin;
    private String pieceJointeNomFichier;
    private Long pieceJointeTaille;
    private Instant pieceJointeDateUpload;
    private String reponseRejetChemin;
    private String reponseRejetNomFichier;
    private Long reponseRejetTaille;
    private Instant reponseRejetDateUpload;
}
