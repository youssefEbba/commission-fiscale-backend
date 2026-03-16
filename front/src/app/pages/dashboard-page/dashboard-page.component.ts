import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import {
  AutoriteContractanteDto,
  EntrepriseDto,
  DemandeCorrectionDto,
  CertificatCreditDto,
  UtilisationCreditDto,
  AuditLogDto
} from '../../models/entities.models';

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard-page.component.html',
  styleUrl: './dashboard-page.component.scss'
})
export class DashboardPageComponent implements OnInit {
  healthStatus: string | null = null;
  autorites: AutoriteContractanteDto[] = [];
  entreprises: EntrepriseDto[] = [];
  demandes: DemandeCorrectionDto[] = [];
  certificats: CertificatCreditDto[] = [];
  utilisations: UtilisationCreditDto[] = [];
  auditLogs: AuditLogDto[] = [];

  constructor(
    private api: ApiService,
    private auth: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (typeof window !== 'undefined' && !this.auth.isAuthenticated()) {
      this.router.navigate(['/login']);
      return;
    }

    this.api.health().subscribe({
      next: (res) => (this.healthStatus = res.status),
      error: () => (this.healthStatus = 'OFFLINE')
    });

    this.api.autorites().subscribe((data) => (this.autorites = data));
    this.api.entreprises().subscribe((data) => (this.entreprises = data));
    this.api.demandes().subscribe((data) => (this.demandes = data));
    this.api.certificats().subscribe((data) => (this.certificats = data));
    this.api.utilisations().subscribe((data) => (this.utilisations = data));
    this.api.auditLogs().subscribe((data) => (this.auditLogs = data.content));
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
