package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "referentiel_type_document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferentielTypeDocument {

    @Id
    @Column(length = 64)
    private String code;

    @Column(nullable = false, length = 500)
    private String libelle;

    @Column(name = "libelle_ar", length = 500)
    private String libelleAr;

    @Column(nullable = false)
    @Builder.Default
    private Boolean actif = Boolean.TRUE;

    @Column(nullable = false)
    @Builder.Default
    private Boolean systeme = Boolean.FALSE;
}
