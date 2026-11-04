## Playwright Profiles
Authenticated browser profiles for the front (http://localhost:5173) are available at `.playwright/profiles/`.
Available profiles:
- admin: Administrateur SI (ADMIN_SI)
- president: Président de la Commission fiscale (PRESIDENT)
- dgd: Douanes (DGD)
- dgi: Impôts (DGI)
- dgtcp: Trésor (DGTCP)
- dgb: Budget (DGB)
- autorite-contractante: Autorité contractante (AUTORITE_CONTRACTANTE, compte `ac`)
- entreprise: Entreprise (ENTREPRISE)
- commission-relais: Commission relais (COMMISSION_RELAIS)
Config: `.playwright/profiles.json`

The session lives in localStorage (`auth_token`, `auth_user`, `app.lang`); the JWT expires about 8 hours after capture.
To load a profile, use `browser_run_code_unsafe` and open a new context from the file (`require` is not available there):
`const ctx = await page.context().browser().newContext({ storageState: '<repo>/.playwright/profiles/<profil>.json' }); const p = await ctx.newPage();`
Check the role with `JSON.parse(localStorage.auth_user).role` before acting.
To switch accounts while capturing, clear localStorage instead of clicking « Déconnexion », which may revoke the saved token.
Run `/setup-profiles` to create new profiles or refresh expired sessions.
