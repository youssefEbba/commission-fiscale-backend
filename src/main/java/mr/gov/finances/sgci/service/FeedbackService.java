package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.EnqueteSatisfaction;
import mr.gov.finances.sgci.domain.entity.QuestionEnquete;
import mr.gov.finances.sgci.domain.entity.ReponseEnquete;
import mr.gov.finances.sgci.domain.entity.ReponseQuestion;
import mr.gov.finances.sgci.domain.entity.RetourUtilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.CategorieRetour;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.ProfilFeedback;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutEnquete;
import mr.gov.finances.sgci.domain.enums.TypeQuestion;
import mr.gov.finances.sgci.repository.EnqueteSatisfactionRepository;
import mr.gov.finances.sgci.repository.ReponseEnqueteRepository;
import mr.gov.finances.sgci.repository.RetourUtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.feedback.FeedbackDtos;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La collecte du feedback des usagers et les enquêtes de satisfaction (cahier des charges, point 3).
 *
 * <p>Deux sources alimentent le même tableau de bord : les avis spontanés, donnés à tout moment, et
 * les réponses aux enquêtes périodiques. Les commentaires y sont présentés avec le profil de leur
 * auteur, jamais son nom : l'objet est l'amélioration de la plateforme, pas l'évaluation des
 * personnes, et un usager qui se sait identifié note moins franchement.
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final int NB_COMMENTAIRES = 30;
    private static final BigDecimal SEUIL_POINT_FORT = new BigDecimal("4");
    private static final BigDecimal SEUIL_DIFFICULTE = new BigDecimal("2.5");

    private static final Map<ProfilFeedback, String> LIBELLES_PROFIL = Map.of(
            ProfilFeedback.ENTREPRISE, "Entreprises bénéficiaires",
            ProfilFeedback.COMMISSION_FISCALE, "Commission fiscale",
            ProfilFeedback.AUTORITE_CONTRACTANTE, "Autorités contractantes",
            ProfilFeedback.DOUANES, "Douanes",
            ProfilFeedback.TRESOR, "Trésor");

    private static final Map<CategorieRetour, String> LIBELLES_CATEGORIE = Map.of(
            CategorieRetour.ERGONOMIE, "Ergonomie et clarté des écrans",
            CategorieRetour.PERFORMANCE, "Rapidité et disponibilité",
            CategorieRetour.PROCEDURE, "Procédures et pièces demandées",
            CategorieRetour.ASSISTANCE, "Assistance et accompagnement",
            CategorieRetour.AUTRE, "Autre");

    private record Note(ProfilFeedback profil, CategorieRetour categorie, String processus, int note, Instant date) {
    }

    private final RetourUtilisateurRepository retourRepository;
    private final EnqueteSatisfactionRepository enqueteRepository;
    private final ReponseEnqueteRepository reponseRepository;
    private final WorkflowRecipientResolver recipientResolver;
    private final NotificationService notificationService;
    private final AuditService auditService;

    // ── avis spontanés ────────────────────────────────────────────────────────────────────────

    @Transactional
    public FeedbackDtos.RetourDto deposerRetour(FeedbackDtos.RetourRequest request, AuthenticatedUser user) {
        ProfilFeedback profil = profilObligatoire(user);
        // Un envoi coupé par le réseau est souvent enregistré, puis renvoyé : le même avis, du même
        // utilisateur, dans la minute, n'est pas enregistré deux fois (A50).
        RetourUtilisateur precedent = retourRepository
                .findFirstByUtilisateurIdAndDateCreationAfterOrderByDateCreationDesc(
                        user.getUserId(), Instant.now().minus(FENETRE_DOUBLON))
                .orElse(null);
        if (precedent != null && memeAvis(precedent, request)) {
            return toDto(precedent);
        }
        RetourUtilisateur retour = retourRepository.save(RetourUtilisateur.builder()
                .utilisateurId(user.getUserId())
                .role(user.getRole())
                .profil(profil)
                .note(request.getNote())
                .categorie(request.getCategorie())
                .processus(vide(request.getProcessus()))
                .page(vide(request.getPage()))
                .commentaire(vide(request.getCommentaire()))
                .dateCreation(Instant.now())
                .build());
        return toDto(retour);
    }

    private static final Duration FENETRE_DOUBLON = Duration.ofSeconds(60);

    private boolean memeAvis(RetourUtilisateur r, FeedbackDtos.RetourRequest req) {
        return Objects.equals(r.getNote(), req.getNote())
                && r.getCategorie() == req.getCategorie()
                && Objects.equals(r.getProcessus(), vide(req.getProcessus()))
                && Objects.equals(r.getPage(), vide(req.getPage()))
                && Objects.equals(r.getCommentaire(), vide(req.getCommentaire()));
    }

    // ── enquêtes : gestion ────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FeedbackDtos.EnqueteDto> listerEnquetes() {
        LocalDate aujourdhui = LocalDate.now(ZONE);
        return enqueteRepository.findAllByOrderByDateDebutDescIdDesc().stream()
                .map(e -> toDto(e, aujourdhui, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public FeedbackDtos.EnqueteDto enquete(Long id) {
        return toDto(trouver(id), LocalDate.now(ZONE), null);
    }

    @Transactional
    public FeedbackDtos.EnqueteDto creerEnquete(FeedbackDtos.EnqueteRequest request, AuthenticatedUser user) {
        EnqueteSatisfaction e = EnqueteSatisfaction.builder()
                .statut(StatutEnquete.BROUILLON)
                .creeParUtilisateurId(user != null ? user.getUserId() : null)
                .dateCreation(Instant.now())
                .build();
        appliquer(e, request);
        e = enqueteRepository.save(e);
        auditService.log(AuditAction.CREATE, "EnqueteSatisfaction", String.valueOf(e.getId()), e.getTitre());
        return toDto(e, LocalDate.now(ZONE), null);
    }

    /** Une enquête publiée ne se modifie plus : ses réponses perdraient leur question. */
    @Transactional
    public FeedbackDtos.EnqueteDto modifierEnquete(Long id, FeedbackDtos.EnqueteRequest request) {
        EnqueteSatisfaction e = trouver(id);
        if (e.getStatut() != StatutEnquete.BROUILLON) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Une enquête publiée ne se modifie plus : relancez-la pour une nouvelle période");
        }
        appliquer(e, request);
        e = enqueteRepository.save(e);
        auditService.log(AuditAction.UPDATE, "EnqueteSatisfaction", String.valueOf(id), e.getTitre());
        return toDto(e, LocalDate.now(ZONE), null);
    }

    @Transactional
    public void supprimerEnquete(Long id) {
        EnqueteSatisfaction e = trouver(id);
        if (e.getStatut() != StatutEnquete.BROUILLON) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Seul un brouillon d'enquête se supprime");
        }
        enqueteRepository.delete(e);
        auditService.log(AuditAction.DELETE, "EnqueteSatisfaction", String.valueOf(id), e.getTitre());
    }

    /** Publie l'enquête et prévient les usagers des profils ciblés. */
    @Transactional
    public FeedbackDtos.EnqueteDto publier(Long id, AuthenticatedUser user) {
        EnqueteSatisfaction e = trouver(id);
        if (e.getStatut() != StatutEnquete.BROUILLON) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "L'enquête est déjà publiée");
        }
        if (e.getQuestions().isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Une enquête comporte au moins une question");
        }
        e.setStatut(StatutEnquete.PUBLIEE);
        e.setDatePublication(Instant.now());
        e = enqueteRepository.save(e);
        notifierCibles(e);
        auditService.log(AuditAction.UPDATE, "EnqueteSatisfaction", String.valueOf(id), "Publiée : " + e.getTitre());
        return toDto(e, LocalDate.now(ZONE), null);
    }

    @Transactional
    public FeedbackDtos.EnqueteDto cloturer(Long id) {
        EnqueteSatisfaction e = trouver(id);
        if (e.getStatut() != StatutEnquete.PUBLIEE) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Seule une enquête publiée se clôture");
        }
        e.setStatut(StatutEnquete.CLOTUREE);
        e = enqueteRepository.save(e);
        auditService.log(AuditAction.UPDATE, "EnqueteSatisfaction", String.valueOf(id), "Clôturée : " + e.getTitre());
        return toDto(e, LocalDate.now(ZONE), null);
    }

    /**
     * La vague suivante d'une enquête périodique : mêmes questions, mêmes profils, nouvelles dates,
     * en brouillon pour relecture avant publication.
     */
    @Transactional
    public FeedbackDtos.EnqueteDto relancer(Long id, FeedbackDtos.RelanceRequest request, AuthenticatedUser user) {
        EnqueteSatisfaction origine = trouver(id);
        controlerDates(request.getDateDebut(), request.getDateFin());
        EnqueteSatisfaction copie = EnqueteSatisfaction.builder()
                .titre(origine.getTitre())
                .description(origine.getDescription())
                .dateDebut(request.getDateDebut())
                .dateFin(request.getDateFin())
                .profilsCibles(origine.getProfilsCibles())
                .statut(StatutEnquete.BROUILLON)
                .creeParUtilisateurId(user != null ? user.getUserId() : null)
                .dateCreation(Instant.now())
                .enqueteOrigineId(origine.getId())
                .build();
        for (QuestionEnquete q : origine.getQuestions()) {
            copie.getQuestions().add(QuestionEnquete.builder()
                    .enquete(copie)
                    .ordre(q.getOrdre())
                    .libelle(q.getLibelle())
                    .type(q.getType())
                    .categorie(q.getCategorie())
                    .options(q.getOptions())
                    .obligatoire(q.isObligatoire())
                    .build());
        }
        copie = enqueteRepository.save(copie);
        auditService.log(AuditAction.CREATE, "EnqueteSatisfaction", String.valueOf(copie.getId()),
                "Relance de l'enquête " + id);
        return toDto(copie, LocalDate.now(ZONE), null);
    }

    // ── enquêtes : côté usager ────────────────────────────────────────────────────────────────

    /** Les enquêtes ouvertes à mon profil, auxquelles je n'ai pas encore répondu. */
    @Transactional(readOnly = true)
    public List<FeedbackDtos.EnqueteDto> enquetesEnCours(AuthenticatedUser user) {
        ProfilFeedback profil = user != null ? ProfilFeedback.deRole(user.getRole()) : null;
        if (profil == null) {
            return List.of();
        }
        LocalDate aujourdhui = LocalDate.now(ZONE);
        return enqueteRepository.findByStatut(StatutEnquete.PUBLIEE).stream()
                .filter(e -> e.estOuverte(aujourdhui) && e.profils().contains(profil))
                .filter(e -> !reponseRepository.existsByEnqueteIdAndUtilisateurId(e.getId(), user.getUserId()))
                .map(e -> toDto(e, aujourdhui, false))
                .toList();
    }

    @Transactional
    public void repondre(Long enqueteId, FeedbackDtos.ReponseEnqueteRequest request, AuthenticatedUser user) {
        ProfilFeedback profil = profilObligatoire(user);
        EnqueteSatisfaction e = trouver(enqueteId);
        if (!e.estOuverte(LocalDate.now(ZONE))) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Cette enquête n'est pas ouverte aux réponses");
        }
        if (!e.profils().contains(profil)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Cette enquête ne s'adresse pas à votre profil");
        }
        if (reponseRepository.existsByEnqueteIdAndUtilisateurId(enqueteId, user.getUserId())) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Vous avez déjà répondu à cette enquête");
        }
        Map<Long, FeedbackDtos.ReponseQuestionRequest> parQuestion = request.getReponses().stream()
                .collect(Collectors.toMap(FeedbackDtos.ReponseQuestionRequest::getQuestionId, r -> r, (a, b) -> b));

        ReponseEnquete reponse = ReponseEnquete.builder()
                .enquete(e)
                .utilisateurId(user.getUserId())
                .profil(profil)
                .dateReponse(Instant.now())
                .build();
        for (QuestionEnquete q : e.getQuestions()) {
            FeedbackDtos.ReponseQuestionRequest r = parQuestion.remove(q.getId());
            ReponseQuestion rq = valider(q, r);
            if (rq != null) {
                rq.setReponseEnquete(reponse);
                reponse.getReponses().add(rq);
            }
        }
        if (!parQuestion.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Questions inconnues pour cette enquête : " + parQuestion.keySet());
        }
        reponseRepository.save(reponse);
    }

    // ── tableau de bord ───────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FeedbackDtos.TableauDeBord tableauDeBord(Instant from, Instant to) {
        Instant fin = to != null ? to : Instant.now();
        Instant debut = from != null ? from : fin.minus(365, ChronoUnit.DAYS);

        List<Note> notes = new ArrayList<>();
        List<FeedbackDtos.Commentaire> commentaires = new ArrayList<>();
        for (RetourUtilisateur r : retourRepository.findByDateCreationBetweenOrderByDateCreationDesc(debut, fin)) {
            notes.add(new Note(r.getProfil(), r.getCategorie(), r.getProcessus(), r.getNote(), r.getDateCreation()));
            if (r.getCommentaire() != null) {
                commentaires.add(FeedbackDtos.Commentaire.builder()
                        .profil(r.getProfil()).categorie(r.getCategorie()).note(r.getNote())
                        .texte(r.getCommentaire()).source("Avis spontané").date(r.getDateCreation()).build());
            }
        }

        List<FeedbackDtos.ResultatEnquete> enquetes = new ArrayList<>();
        for (EnqueteSatisfaction e : enqueteRepository.findAllByOrderByDateDebutDescIdDesc()) {
            if (e.getStatut() == StatutEnquete.BROUILLON) {
                continue;
            }
            List<ReponseEnquete> reponses = reponseRepository.findByEnqueteId(e.getId()).stream()
                    .filter(r -> !r.getDateReponse().isBefore(debut) && !r.getDateReponse().isAfter(fin))
                    .toList();
            if (reponses.isEmpty() && (e.getDateFin().atStartOfDay(ZONE).toInstant().isBefore(debut))) {
                continue;
            }
            enquetes.add(resultat(e, reponses));
            for (ReponseEnquete r : reponses) {
                for (ReponseQuestion rq : r.getReponses()) {
                    QuestionEnquete q = rq.getQuestion();
                    if (q.getType() == TypeQuestion.NOTE && rq.getNote() != null) {
                        notes.add(new Note(r.getProfil(),
                                q.getCategorie() != null ? q.getCategorie() : CategorieRetour.AUTRE,
                                null, rq.getNote(), r.getDateReponse()));
                    } else if (q.getType() == TypeQuestion.TEXTE && rq.getTexte() != null && !rq.getTexte().isBlank()) {
                        commentaires.add(FeedbackDtos.Commentaire.builder()
                                .profil(r.getProfil()).categorie(q.getCategorie()).texte(rq.getTexte())
                                .source(e.getTitre()).date(r.getDateReponse()).build());
                    }
                }
            }
        }

        List<FeedbackDtos.Agregat> parCategorie = agreger(notes, n -> n.categorie().name(),
                k -> LIBELLES_CATEGORIE.get(CategorieRetour.valueOf(k)), ordre(CategorieRetour.values()));
        commentaires.sort(Comparator.comparing(FeedbackDtos.Commentaire::getDate).reversed());

        return FeedbackDtos.TableauDeBord.builder()
                .from(debut)
                .to(fin)
                .nbAvis(notes.size())
                .noteMoyenne(moyenne(notes))
                .satisfaitsPct(satisfaits(notes))
                .parProfil(agreger(notes, n -> n.profil().name(),
                        k -> LIBELLES_PROFIL.get(ProfilFeedback.valueOf(k)), ordre(ProfilFeedback.values())))
                .parCategorie(parCategorie)
                .parProcessus(agreger(notes.stream().filter(n -> n.processus() != null).toList(),
                        Note::processus, k -> k, List.of()))
                .evolution(evolution(notes))
                .pointsForts(parCategorie.stream()
                        .filter(a -> a.getNoteMoyenne() != null && a.getNoteMoyenne().compareTo(SEUIL_POINT_FORT) >= 0)
                        .toList())
                .difficultes(parCategorie.stream()
                        .filter(a -> a.getNoteMoyenne() != null && a.getNoteMoyenne().compareTo(SEUIL_DIFFICULTE) <= 0)
                        .toList())
                .enquetes(enquetes)
                .commentaires(commentaires.size() > NB_COMMENTAIRES
                        ? commentaires.subList(0, NB_COMMENTAIRES) : commentaires)
                .build();
    }

    private FeedbackDtos.ResultatEnquete resultat(EnqueteSatisfaction e, List<ReponseEnquete> reponses) {
        List<Note> notes = new ArrayList<>();
        Map<Long, List<Integer>> parQuestion = new LinkedHashMap<>();
        e.getQuestions().stream().filter(q -> q.getType() == TypeQuestion.NOTE)
                .forEach(q -> parQuestion.put(q.getId(), new ArrayList<>()));
        for (ReponseEnquete r : reponses) {
            for (ReponseQuestion rq : r.getReponses()) {
                if (rq.getNote() != null && parQuestion.containsKey(rq.getQuestion().getId())) {
                    parQuestion.get(rq.getQuestion().getId()).add(rq.getNote());
                    notes.add(new Note(r.getProfil(), CategorieRetour.AUTRE, null, rq.getNote(), r.getDateReponse()));
                }
            }
        }
        List<FeedbackDtos.Agregat> questions = new ArrayList<>();
        for (QuestionEnquete q : e.getQuestions()) {
            if (q.getType() == TypeQuestion.NOTE) {
                List<Note> deLaQuestion = parQuestion.get(q.getId()).stream()
                        .map(n -> new Note(null, null, null, n, null)).toList();
                questions.add(agregat(String.valueOf(q.getId()), q.getLibelle(), deLaQuestion));
            }
        }
        long cibles = recipientResolver.activeUserIdsForRoles(roles(e.profils())).size();
        long nbReponses = reponses.size();
        return FeedbackDtos.ResultatEnquete.builder()
                .enqueteId(e.getId())
                .titre(e.getTitre())
                .dateDebut(e.getDateDebut())
                .dateFin(e.getDateFin())
                .statut(e.getStatut())
                .nbReponses(nbReponses)
                .nbCibles(cibles)
                .tauxReponsePct(cibles == 0 ? null : arrondi(100.0 * nbReponses / cibles))
                .noteMoyenne(moyenne(notes))
                .parQuestion(questions)
                .parProfil(agreger(notes, n -> n.profil().name(),
                        k -> LIBELLES_PROFIL.get(ProfilFeedback.valueOf(k)), List.of()))
                .build();
    }

    // ── utilitaires ───────────────────────────────────────────────────────────────────────────

    private void appliquer(EnqueteSatisfaction e, FeedbackDtos.EnqueteRequest request) {
        controlerDates(request.getDateDebut(), request.getDateFin());
        e.setTitre(request.getTitre().trim());
        e.setDescription(vide(request.getDescription()));
        e.setDateDebut(request.getDateDebut());
        e.setDateFin(request.getDateFin());
        e.definirProfils(request.getProfilsCibles());
        e.getQuestions().clear();
        int ordre = 1;
        for (FeedbackDtos.QuestionDto q : request.getQuestions()) {
            if (q.getType() == TypeQuestion.CHOIX && (q.getOptions() == null || q.getOptions().size() < 2)) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                        "La question à choix « " + q.getLibelle() + " » doit proposer au moins deux options");
            }
            e.getQuestions().add(QuestionEnquete.builder()
                    .enquete(e)
                    .ordre(ordre++)
                    .libelle(q.getLibelle().trim())
                    .type(q.getType())
                    .categorie(q.getCategorie())
                    .options(q.getOptions() != null ? String.join("\n", q.getOptions()) : null)
                    .obligatoire(q.getObligatoire() == null || q.getObligatoire())
                    .build());
        }
    }

    private static void controlerDates(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null || fin.isBefore(debut)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "La date de fin doit suivre la date de début");
        }
    }

    /** Une réponse conforme au type de la question, ou {@code null} si une question facultative est laissée vide. */
    private static ReponseQuestion valider(QuestionEnquete q, FeedbackDtos.ReponseQuestionRequest r) {
        boolean vide = r == null || (r.getNote() == null && (r.getTexte() == null || r.getTexte().isBlank()));
        if (vide) {
            if (q.isObligatoire()) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Réponse manquante : « " + q.getLibelle() + " »");
            }
            return null;
        }
        switch (q.getType()) {
            case NOTE -> {
                if (r.getNote() == null || r.getNote() < 1 || r.getNote() > 5) {
                    throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                            "La note de « " + q.getLibelle() + " » va de 1 à 5");
                }
                return ReponseQuestion.builder().question(q).note(r.getNote()).build();
            }
            case CHOIX -> {
                List<String> options = q.getOptions() != null ? Arrays.asList(q.getOptions().split("\n")) : List.of();
                if (!options.contains(r.getTexte())) {
                    throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                            "Option inconnue pour « " + q.getLibelle() + " »");
                }
                return ReponseQuestion.builder().question(q).texte(r.getTexte()).build();
            }
            default -> {
                String texte = r.getTexte() != null ? r.getTexte().trim() : "";
                if (texte.length() > 2000) {
                    throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Réponse limitée à 2000 caractères");
                }
                return ReponseQuestion.builder().question(q).texte(texte).build();
            }
        }
    }

    private void notifierCibles(EnqueteSatisfaction e) {
        List<Long> destinataires = recipientResolver.activeUserIdsForRoles(roles(e.profils()));
        if (destinataires.isEmpty()) {
            return;
        }
        notificationService.notifyUsers(destinataires, NotificationType.ENQUETE_SATISFACTION,
                "EnqueteSatisfaction", e.getId(), messageInvitation(e.getTitre(), e.getDateFin()),
                Map.of("enqueteId", e.getId()));
    }

    /** Texte de la notification d'une enquête ; la date de fin au format jj/mm/aaaa, lu par l'usager. */
    public static String messageInvitation(String titre, LocalDate dateFin) {
        String jusquau = dateFin != null ? ", ouverte jusqu'au " + dateFin.format(JJ_MM_AAAA) : "";
        return "Enquête de satisfaction : « " + titre + " »" + jusquau + ". Votre avis nous aide à améliorer la plateforme.";
    }

    private static final java.time.format.DateTimeFormatter JJ_MM_AAAA = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static Role[] roles(Set<ProfilFeedback> profils) {
        return profils.stream().flatMap(p -> p.roles().stream()).distinct().toArray(Role[]::new);
    }

    private static ProfilFeedback profilObligatoire(AuthenticatedUser user) {
        ProfilFeedback profil = user != null ? ProfilFeedback.deRole(user.getRole()) : null;
        if (profil == null) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Votre rôle ne participe pas aux enquêtes de satisfaction");
        }
        return profil;
    }

    private EnqueteSatisfaction trouver(Long id) {
        return enqueteRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Enquête non trouvée : " + id));
    }

    private FeedbackDtos.EnqueteDto toDto(EnqueteSatisfaction e, LocalDate aujourdhui, Boolean dejaRepondu) {
        return FeedbackDtos.EnqueteDto.builder()
                .id(e.getId())
                .titre(e.getTitre())
                .description(e.getDescription())
                .dateDebut(e.getDateDebut())
                .dateFin(e.getDateFin())
                .profilsCibles(e.profils())
                .statut(e.getStatut())
                .ouverte(e.estOuverte(aujourdhui))
                .datePublication(e.getDatePublication())
                .enqueteOrigineId(e.getEnqueteOrigineId())
                .questions(e.getQuestions().stream().map(q -> FeedbackDtos.QuestionDto.builder()
                        .id(q.getId())
                        .ordre(q.getOrdre())
                        .libelle(q.getLibelle())
                        .type(q.getType())
                        .categorie(q.getCategorie())
                        .options(q.getOptions() != null ? Arrays.asList(q.getOptions().split("\n")) : null)
                        .obligatoire(q.isObligatoire())
                        .build()).toList())
                .nbReponses(e.getId() != null ? reponseRepository.countByEnqueteId(e.getId()) : 0)
                .dejaRepondu(dejaRepondu)
                .build();
    }

    private static FeedbackDtos.RetourDto toDto(RetourUtilisateur r) {
        return FeedbackDtos.RetourDto.builder()
                .id(r.getId())
                .profil(r.getProfil())
                .note(r.getNote())
                .categorie(r.getCategorie())
                .processus(r.getProcessus())
                .page(r.getPage())
                .commentaire(r.getCommentaire())
                .dateCreation(r.getDateCreation())
                .build();
    }

    private static List<FeedbackDtos.Agregat> agreger(List<Note> notes, Function<Note, String> cle,
                                                     Function<String, String> libelle, List<String> ordre) {
        Map<String, List<Note>> groupes = new LinkedHashMap<>();
        ordre.forEach(k -> groupes.put(k, new ArrayList<>()));
        notes.forEach(n -> groupes.computeIfAbsent(cle.apply(n), k -> new ArrayList<>()).add(n));
        List<FeedbackDtos.Agregat> out = new ArrayList<>();
        groupes.forEach((k, liste) -> out.add(agregat(k, libelle.apply(k), liste)));
        return out;
    }

    private static FeedbackDtos.Agregat agregat(String cle, String libelle, List<Note> notes) {
        long[] distribution = new long[5];
        notes.forEach(n -> distribution[n.note() - 1]++);
        return FeedbackDtos.Agregat.builder()
                .cle(cle)
                .libelle(libelle)
                .nbNotes(notes.size())
                .noteMoyenne(moyenne(notes))
                .distribution(Arrays.stream(distribution).boxed().toList())
                .satisfaitsPct(satisfaits(notes))
                .build();
    }

    private static List<FeedbackDtos.Mois> evolution(List<Note> notes) {
        Map<YearMonth, List<Note>> parMois = new TreeMap<>();
        notes.forEach(n -> parMois.computeIfAbsent(YearMonth.from(n.date().atZone(ZONE)), k -> new ArrayList<>()).add(n));
        List<FeedbackDtos.Mois> out = new ArrayList<>();
        parMois.forEach((mois, liste) -> out.add(FeedbackDtos.Mois.builder()
                .periode(mois.toString())
                .nbNotes(liste.size())
                .noteMoyenne(moyenne(liste))
                .build()));
        return out;
    }

    private static List<String> ordre(Enum<?>[] valeurs) {
        return Arrays.stream(valeurs).map(Enum::name).toList();
    }

    private static BigDecimal moyenne(List<Note> notes) {
        if (notes.isEmpty()) {
            return null;
        }
        double somme = notes.stream().mapToInt(Note::note).sum();
        return BigDecimal.valueOf(somme / notes.size()).setScale(2, RoundingMode.HALF_UP);
    }

    private static Double satisfaits(List<Note> notes) {
        if (notes.isEmpty()) {
            return null;
        }
        return arrondi(100.0 * notes.stream().filter(n -> n.note() >= 4).count() / notes.size());
    }

    private static Double arrondi(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String vide(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
