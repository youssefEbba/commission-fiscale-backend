import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import {
  AutoriteContractanteDto,
  EntrepriseDto,
  DemandeCorrectionDto,
  CertificatCreditDto,
  UtilisationCreditDto,
  AuditLogDto
} from '../models/entities.models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly baseUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  health(): Observable<{ status: string; application: string }> {
    return this.http.get<{ status: string; application: string }>(`${this.baseUrl}/api/health`);
  }

  autorites(): Observable<AutoriteContractanteDto[]> {
    return this.http.get<AutoriteContractanteDto[]>(`${this.baseUrl}/api/autorites-contractantes`);
  }

  entreprises(): Observable<EntrepriseDto[]> {
    return this.http.get<EntrepriseDto[]>(`${this.baseUrl}/api/entreprises`);
  }

  demandes(): Observable<DemandeCorrectionDto[]> {
    return this.http.get<DemandeCorrectionDto[]>(`${this.baseUrl}/api/demandes-correction`);
  }

  certificats(): Observable<CertificatCreditDto[]> {
    return this.http.get<CertificatCreditDto[]>(`${this.baseUrl}/api/certificats-credit`);
  }

  utilisations(): Observable<UtilisationCreditDto[]> {
    return this.http.get<UtilisationCreditDto[]>(`${this.baseUrl}/api/utilisations-credit`);
  }

  auditLogs(): Observable<{ content: AuditLogDto[] }> {
    return this.http.get<{ content: AuditLogDto[] }>(`${this.baseUrl}/api/audit-logs`);
  }
}
