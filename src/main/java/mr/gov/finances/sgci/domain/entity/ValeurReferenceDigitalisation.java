package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mr.gov.finances.sgci.domain.enums.IndicateurReference;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * La valeur d'un indicateur avant la digitalisation, saisie une fois par l'administrateur à partir
 * des registres de la Commission. Une seule valeur par indicateur : la comparaison avant / après ne
 * doit pas dépendre de laquelle de deux saisies on lit.
 */
@Entity
@Table(name = "valeur_reference_digitalisation",
        uniqueConstraints = @UniqueConstraint(name = "uk_valeur_reference_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValeurReferenceDigitalisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private IndicateurReference code;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal valeur;

    /** La période dont la valeur est tirée, en clair (« 2021-2023, registre des dépôts »). */
    @Column(length = 255)
    private String periodeSource;

    @Column(length = 1000)
    private String commentaire;

    private Long saisiParUtilisateurId;

    private Instant dateSaisie;
}
