package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.CategorieRetour;
import mr.gov.finances.sgci.domain.enums.ProfilFeedback;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutEnquete;
import mr.gov.finances.sgci.domain.enums.TypeQuestion;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.FeedbackService;
import mr.gov.finances.sgci.service.PermissionService;
import mr.gov.finances.sgci.web.dto.feedback.FeedbackDtos;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Le feedback des usagers et les enquêtes de satisfaction (cahier des charges, point 3). */
@SpringBootTest
@ActiveProfiles("test")
class FeedbackIT {

    @Autowired private FeedbackService service;
    @Autowired private PermissionService permissionService;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private AuthenticatedUser entreprise;
    private AuthenticatedUser dgd;
    private AuthenticatedUser president;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        entreprise = auth("entreprise", Role.ENTREPRISE);
        dgd = auth("dgd", Role.DGD);
        president = auth("president", Role.PRESIDENT);
        admin = auth("admin", Role.ADMIN_SI);
    }

    @Test
    void lesProfilsSuiventLesRoles() {
        assertThat(ProfilFeedback.deRole(Role.SOUS_TRAITANT)).isEqualTo(ProfilFeedback.ENTREPRISE);
        assertThat(ProfilFeedback.deRole(Role.AUTORITE_UEP)).isEqualTo(ProfilFeedback.AUTORITE_CONTRACTANTE);
        assertThat(ProfilFeedback.deRole(Role.DGB)).isEqualTo(ProfilFeedback.COMMISSION_FISCALE);
        assertThat(ProfilFeedback.deRole(Role.DGTCP)).isEqualTo(ProfilFeedback.TRESOR);
        assertThat(ProfilFeedback.deRole(Role.ADMIN_SI)).isNull();
        assertThat(permissionService.findPermissionCodesByRole(Role.ENTREPRISE)).contains("feedback.submit");
        assertThat(permissionService.findPermissionCodesByRole(Role.ADMIN_SI))
                .contains("feedback.enquete.manage", "feedback.dashboard.view").doesNotContain("feedback.submit");
    }

    @Test
    @Transactional
    void unAvisSpontaneEntreDansLeTableauDeBord() {
        service.deposerRetour(FeedbackDtos.RetourRequest.builder()
                .note(2).categorie(CategorieRetour.PROCEDURE).processus("UTILISATION")
                .commentaire("Trop de pièces demandées").build(), entreprise);
        service.deposerRetour(FeedbackDtos.RetourRequest.builder()
                .note(5).categorie(CategorieRetour.ERGONOMIE).build(), dgd);

        FeedbackDtos.TableauDeBord t = service.tableauDeBord(Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(t.getNbAvis()).isEqualTo(2);
        assertThat(t.getNoteMoyenne()).isEqualByComparingTo("3.50");
        assertThat(t.getSatisfaitsPct()).isEqualTo(50.0);
        assertThat(agregat(t.getParProfil(), "ENTREPRISE").getNoteMoyenne()).isEqualByComparingTo("2");
        assertThat(t.getPointsForts()).extracting(FeedbackDtos.Agregat::getCle).containsExactly("ERGONOMIE");
        assertThat(t.getDifficultes()).extracting(FeedbackDtos.Agregat::getCle).containsExactly("PROCEDURE");
        // Le commentaire est présenté avec le profil, sans le nom de son auteur.
        assertThat(t.getCommentaires()).extracting(FeedbackDtos.Commentaire::getProfil).containsExactly(ProfilFeedback.ENTREPRISE);
    }

    @Test
    @Transactional
    void unAvisRenvoyeDansLaMinuteNEstPasEnregistreDeuxFois() {
        FeedbackDtos.RetourRequest avis = FeedbackDtos.RetourRequest.builder()
                .note(3).categorie(CategorieRetour.PERFORMANCE).commentaire("Lent ce matin").build();

        FeedbackDtos.RetourDto premier = service.deposerRetour(avis, dgd);
        FeedbackDtos.RetourDto renvoi = service.deposerRetour(avis, dgd);
        FeedbackDtos.RetourDto autre = service.deposerRetour(FeedbackDtos.RetourRequest.builder()
                .note(3).categorie(CategorieRetour.PERFORMANCE).commentaire("Lent cet après-midi").build(), dgd);

        assertThat(renvoi.getId()).as("A50 : l'envoi répété rend l'avis déjà enregistré").isEqualTo(premier.getId());
        assertThat(autre.getId()).isNotEqualTo(premier.getId());
    }

    @Test
    void uneCategorieSaisieEnClairEstReconnueEtUneInconnueEstExpliquee() throws Exception {
        assertThat(objectMapper.readValue("{\"note\":4,\"categorie\":\"Procédure\"}", FeedbackDtos.RetourRequest.class)
                .getCategorie()).isEqualTo(CategorieRetour.PROCEDURE);
        assertThat(objectMapper.readValue("{\"note\":4,\"categorie\":\"\"}", FeedbackDtos.RetourRequest.class)
                .getCategorie()).isNull();

        String corps = "{\"titre\":\"Enquête\",\"questions\":[{\"libelle\":\"Q1\",\"categorie\":\"Délais\"}]}";
        var erreur = org.junit.jupiter.api.Assertions.assertThrows(
                com.fasterxml.jackson.databind.JsonMappingException.class,
                () -> objectMapper.readValue(corps, FeedbackDtos.EnqueteRequest.class));
        var reponse = new mr.gov.finances.sgci.web.exception.GlobalExceptionHandler().handleNotReadable(
                new org.springframework.http.converter.HttpMessageNotReadableException("illisible", erreur,
                        new org.springframework.mock.http.MockHttpInputMessage(new byte[0])));

        assertThat(reponse.getStatusCode().value()).isEqualTo(400);
        assertThat(reponse.getBody().message())
                .contains("questions[0].categorie", "ERGONOMIE, PERFORMANCE, PROCEDURE, ASSISTANCE, AUTRE")
                .as("la valeur saisie n'est pas renvoyée").doesNotContain("Délais");
    }

    @Test
    void laNotificationDUneEnqueteDonneLaDateDeFinEnJourMoisAnnee() {
        assertThat(FeedbackService.messageInvitation("T4 2026", LocalDate.of(2026, 12, 15)))
                .contains("ouverte jusqu'au 15/12/2026").doesNotContain("2026-12-15");
        assertThat(FeedbackService.messageInvitation("Sans fin", null)).doesNotContain("null");
    }

    @Test
    void lAdministrateurNeDonnePasDAvis() {
        assertThatThrownBy(() -> service.deposerRetour(FeedbackDtos.RetourRequest.builder()
                .note(4).categorie(CategorieRetour.AUTRE).build(), admin))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(403));
    }

    @Test
    @Transactional
    void leCycleDUneEnqueteDeSatisfaction() {
        FeedbackDtos.EnqueteDto brouillon = service.creerEnquete(FeedbackDtos.EnqueteRequest.builder()
                .titre("Satisfaction des entreprises — T4 2026")
                .dateDebut(LocalDate.now().minusDays(1))
                .dateFin(LocalDate.now().plusDays(30))
                .profilsCibles(Set.of(ProfilFeedback.ENTREPRISE))
                .questions(List.of(
                        FeedbackDtos.QuestionDto.builder().libelle("Note globale").type(TypeQuestion.NOTE)
                                .categorie(CategorieRetour.ERGONOMIE).build(),
                        FeedbackDtos.QuestionDto.builder().libelle("Canal préféré").type(TypeQuestion.CHOIX)
                                .options(List.of("Plateforme", "Guichet")).build(),
                        FeedbackDtos.QuestionDto.builder().libelle("Vos suggestions").type(TypeQuestion.TEXTE)
                                .obligatoire(false).build()))
                .build(), president);
        assertThat(service.enquetesEnCours(entreprise)).extracting(FeedbackDtos.EnqueteDto::getId)
                .doesNotContain(brouillon.getId());

        FeedbackDtos.EnqueteDto publiee = service.publier(brouillon.getId(), president);
        assertThat(publiee.getStatut()).isEqualTo(StatutEnquete.PUBLIEE);
        assertThat(publiee.isOuverte()).isTrue();
        assertThat(service.enquetesEnCours(entreprise)).extracting(FeedbackDtos.EnqueteDto::getId)
                .contains(publiee.getId());
        assertThat(service.enquetesEnCours(dgd)).extracting(FeedbackDtos.EnqueteDto::getId)
                .doesNotContain(publiee.getId());

        Long note = publiee.getQuestions().get(0).getId();
        Long choix = publiee.getQuestions().get(1).getId();
        service.repondre(publiee.getId(), FeedbackDtos.ReponseEnqueteRequest.builder().reponses(List.of(
                FeedbackDtos.ReponseQuestionRequest.builder().questionId(note).note(4).build(),
                FeedbackDtos.ReponseQuestionRequest.builder().questionId(choix).texte("Plateforme").build()))
                .build(), entreprise);

        assertThat(service.enquetesEnCours(entreprise)).extracting(FeedbackDtos.EnqueteDto::getId)
                .doesNotContain(publiee.getId());
        assertThatThrownBy(() -> service.repondre(publiee.getId(), FeedbackDtos.ReponseEnqueteRequest.builder()
                .reponses(List.of(FeedbackDtos.ReponseQuestionRequest.builder().questionId(note).note(5).build()))
                .build(), entreprise))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(409));
        assertThatThrownBy(() -> service.repondre(publiee.getId(), FeedbackDtos.ReponseEnqueteRequest.builder()
                .reponses(List.of(FeedbackDtos.ReponseQuestionRequest.builder().questionId(note).note(5).build()))
                .build(), dgd))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(403));

        FeedbackDtos.ResultatEnquete resultat = service.tableauDeBord(null, null).getEnquetes().stream()
                .filter(r -> r.getEnqueteId().equals(publiee.getId())).findFirst().orElseThrow();
        assertThat(resultat.getNbReponses()).isEqualTo(1);
        assertThat(resultat.getNoteMoyenne()).isEqualByComparingTo("4");

        service.cloturer(publiee.getId());
        FeedbackDtos.EnqueteDto relance = service.relancer(publiee.getId(), FeedbackDtos.RelanceRequest.builder()
                .dateDebut(LocalDate.now().plusDays(90)).dateFin(LocalDate.now().plusDays(120)).build(), president);
        assertThat(relance.getStatut()).isEqualTo(StatutEnquete.BROUILLON);
        assertThat(relance.getEnqueteOrigineId()).isEqualTo(publiee.getId());
        assertThat(relance.getQuestions()).hasSize(3);
    }

    @Test
    @Transactional
    void uneReponseObligatoireManquanteEstRefusee() {
        FeedbackDtos.EnqueteDto e = service.publier(service.creerEnquete(FeedbackDtos.EnqueteRequest.builder()
                .titre("Douanes")
                .dateDebut(LocalDate.now())
                .dateFin(LocalDate.now().plusDays(7))
                .profilsCibles(Set.of(ProfilFeedback.DOUANES))
                .questions(List.of(
                        FeedbackDtos.QuestionDto.builder().libelle("Note").type(TypeQuestion.NOTE).build(),
                        FeedbackDtos.QuestionDto.builder().libelle("Commentaire").type(TypeQuestion.TEXTE).build()))
                .build(), president).getId(), president);

        assertThatThrownBy(() -> service.repondre(e.getId(), FeedbackDtos.ReponseEnqueteRequest.builder()
                .reponses(List.of(FeedbackDtos.ReponseQuestionRequest.builder()
                        .questionId(e.getQuestions().get(0).getId()).note(3).build()))
                .build(), dgd))
                .isInstanceOf(ApiException.class)
                .satisfies(x -> assertThat(((ApiException) x).getStatus()).isEqualTo(400));
    }

    private AuthenticatedUser auth(String username, Role role) {
        Utilisateur u = utilisateurRepository.findByUsername(username).orElseThrow();
        return new AuthenticatedUser(u.getId(), u.getUsername(), role);
    }

    private static FeedbackDtos.Agregat agregat(List<FeedbackDtos.Agregat> liste, String cle) {
        return liste.stream().filter(a -> a.getCle().equals(cle)).findFirst().orElseThrow();
    }
}
