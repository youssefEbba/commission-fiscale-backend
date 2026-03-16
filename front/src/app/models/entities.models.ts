export interface AutoriteContractanteDto {
  id?: number;
  nom: string;
  code?: string;
  contact?: string;
}

export interface EntrepriseDto {
  id?: number;
  raisonSociale: string;
  nif?: string;
  adresse?: string;
  situationFiscale?: string;
}

export interface DemandeCorrectionDto {
  id: number;
  numero: string;
  dateDepot: string;
  statut: string;
  autoriteContractanteId?: number;
  autoriteContractanteNom?: string;
}

export interface CertificatCreditDto {
  id: number;
  numero: string;
  dateEmission: string;
  dateValidite: string;
  montantCordon: number;
  montantTVAInterieure: number;
  soldeCordon: number;
  soldeTVA: number;
  statut: string;
  entrepriseId?: number;
  entrepriseRaisonSociale?: string;
}

export interface UtilisationCreditDto {
  id: number;
  type: string;
  dateDemande: string;
  montant: number;
  statut: string;
  certificatCreditId?: number;
  entrepriseId?: number;
}

export interface AuditLogDto {
  id: number;
  timestamp: string;
  userId?: number;
  username: string;
  action: string;
  entityType: string;
  entityId?: string;
  objectSnapshot?: string;
}
