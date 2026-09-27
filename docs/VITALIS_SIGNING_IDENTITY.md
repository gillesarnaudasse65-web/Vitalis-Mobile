# Vitalis production signing identity

Application ID: `com.vitalis.healthos`  
Signing lineage: **FIRST STABLE VITALIS PRODUCTION IDENTITY**  
Status: **PENDING SECURE INITIALIZATION**

Android Debug and earlier CI test certificates are not production identities. Every future production APK/AAB update for `com.vitalis.healthos` must use the permanent identity recorded here after secure initialization.

## Safe public identity metadata

| Field | Value |
|---|---|
| Alias | `vitalis-production` |
| Subject | `CN=Vitalis Health OS, OU=Mobile, O=Vitalis, L=Abidjan, ST=Abidjan, C=CI` |
| Algorithm | RSA 4096 |
| Creation date | PENDING |
| Validity | PENDING; requested validity is 10,000 days |
| Certificate SHA-256 | PENDING |
| Signing lineage status | PENDING SECURE INITIALIZATION |

Only public certificate metadata belongs in this document. Never add a keystore, password, Base64 keystore value, private key, privileged token, or vault recovery data.

## GitHub-only initialization design

The manual workflow `.github/workflows/initialize-production-signing.yml` uses the protected environment `production-signing-init` and requires:

- explicit `confirm_first_identity = true` confirmation;
- fixed alias `vitalis-production`;
- protected secrets `VITALIS_INIT_KEYSTORE_PASSWORD` and `VITALIS_INIT_KEY_PASSWORD`;
- a pending public identity marker, preventing accidental replacement after initialization.

Workflow inputs contain public certificate fields only. Passwords are never accepted as workflow inputs.

### Selected secure transfer mode: Mode C

No dedicated GitHub secret-management token or configured external vault integration is currently verified. The default workflow token cannot be treated as a generic secret-writing channel.

The initialization workflow therefore stops before key creation with:

`INITIALIZATION BLOCKED — SECURE SECRET TRANSFER CHANNEL REQUIRED`

It does not generate or upload a permanent keystore. This prevents the only recoverable copy from becoming an ordinary Actions artifact or disappearing with an ephemeral runner.

Mode A may be implemented later only if a deliberately authorized token can write environment secrets and its minimum permissions are reviewed. Mode B may be implemented only after an actual recoverable vault integration exists. Neither capability is currently claimed.

## Protected environments

From GitHub Web, create or review:

1. `production-signing-init`, used only by the one-time initialization preflight;
2. `production`, used by the existing production APK/AAB build.

Where the repository plan supports them, configure required reviewers and restrict deployment branches to `agent/vitalis-3.15-rc-validation` during RC validation. Protection is recommended but is **NOT VERIFIED** until reviewed in GitHub Settings.

Initialization environment secret names:

- `VITALIS_INIT_KEYSTORE_PASSWORD`
- `VITALIS_INIT_KEY_PASSWORD`

Production environment secret names remain:

- `VITALIS_KEYSTORE_BASE64`
- `VITALIS_KEYSTORE_PASSWORD`
- `VITALIS_KEY_ALIAS`
- `VITALIS_KEY_PASSWORD`

Never place their values in workflow inputs, logs, summaries, documentation, issues, PR comments, or commit history.

## Recovery gate

GitHub Secrets are not a user-downloadable backup vault. A production signing identity is not recoverable merely because its Base64 value exists as a GitHub secret.

Allowed status values:

- `BACKUP_CONFIRMED`: at least one independent, recoverable, access-controlled backup has been verified;
- `BACKUP_NOT_CONFIRMED`: secret storage may exist, but recovery has not been demonstrated;
- `BACKUP_BLOCKED`: no approved recovery mechanism exists.

Current backup status: **BACKUP_BLOCKED**.

The release blocker remains open until a recoverable backup exists independently of the ephemeral runner and GitHub secret value.

## Public metadata publication after secure initialization

Only after a secure Mode A or Mode B implementation succeeds:

1. replace the pending public values in this document with the alias, subject, SHA-256 fingerprint, algorithm, validity, initialization date, and lineage status;
2. publish only `Vitalis-production-signing-public-metadata`, containing `signing-public-metadata.txt` and the statement `PRIVATE KEY NOT INCLUDED`;
3. verify the production APK certificate matches this document;
4. add the permanent warning: **DO NOT REPLACE THIS CERTIFICATE FOR FUTURE UPDATES.**

No `.jks`, `.keystore`, Base64 keystore, password file, or private key may be uploaded as a normal artifact.

## Production build and upgrade continuity

After the identity, production secrets, and recovery gate are established, manually run `Vitalis Android quality gates` on `agent/vitalis-3.15-rc-validation` with `build_production = true`.

The production job must publish `Vitalis-3.15.0-rc1-production-signed`, and its certificate SHA-256 must match this document. It must not be `CN=Android Debug`.

Only then may a controlled versionCode 20 baseline be built from the verified pre-RC source with the same certificate. The phone upgrade must install versionCode 21 over versionCode 20 without uninstalling. The no-shell procedure is recorded in `VITALIS_PHYSICAL_ACCEPTANCE_CHECKLIST.md`.
