package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mr.gov.finances.sgci.domain.enums.CategorieRetour;
import mr.gov.finances.sgci.domain.enums.TypeQuestion;

@Entity
@Table(name = "question_enquete")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionEnquete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enquete_id")
    private EnqueteSatisfaction enquete;

    @Column(nullable = false)
    private Integer ordre;

    @Column(nullable = false, length = 500)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TypeQuestion type;

    /** Le sujet mesuré par une question notée : il alimente les points forts et les difficultés. */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private CategorieRetour categorie;

    /** Les options d'une question à choix, une par ligne. */
    @Column(length = 2000)
    private String options;

    @Column(nullable = false)
    @Builder.Default
    private boolean obligatoire = true;
}
