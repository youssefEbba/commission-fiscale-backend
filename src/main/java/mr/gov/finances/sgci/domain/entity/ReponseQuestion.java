package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "reponse_question")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReponseQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reponse_enquete_id")
    private ReponseEnquete reponseEnquete;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id")
    private QuestionEnquete question;

    /** La note, pour une question notée (1 à 5). */
    private Integer note;

    /** Le commentaire ou l'option choisie. */
    @Column(length = 2000)
    private String texte;
}
