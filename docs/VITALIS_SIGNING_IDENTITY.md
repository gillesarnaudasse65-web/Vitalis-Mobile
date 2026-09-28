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

Only public certificate metadata belongs in this document. Never add a keystore, password, unencrypted Base64 keystore, private key, recovery passphrase, privileged token, or vault credential.

## Phone-only two-phase initialization

### Phase A — generate, encrypt, self-test and transfer

The manual workflow `.github/workflows/initialize-production-signing.yml`:

- is triggered only by `workflow_dispatch`;
- uses the protected `production-signing-init` environment;
- requires `confirm_first_identity = true`;
- accepts only safe public certificate fields as inputs;
- generates random keystore and key passwords inside the runner;
- creates the JKS only under `$RUNNER_TEMP`;
- encrypts the complete recovery package as a standard passphrase-protected age file;
- deletes the first plaintext package before decrypting into a new directory;
- proves that the restored keystore, alias, key password and certificate work;
- stores only the encrypted package and safe public metadata in the dedicated private vault repository;
- exposes only the encrypted package as a seven-day phone transfer artifact;
- removes all temporary signing material with `if: always()`.

Required environment secrets:

- `VITALIS_SIGNING_BACKUP_PASSPHRASE`
- `VITALIS_SIGNING_VAULT_TOKEN`

Passwords are never workflow inputs and are never printed.

### Phase B — independent backup confirmation

The workflow `.github/workflows/confirm-production-signing-backup.yml` generates no key. It requires four non-secret booleans and re-tests the durable encrypted vault copy. Production activation is allowed only after:

- Backup A exists in secure Android phone storage;
- Backup B exists in an independent user-controlled location outside GitHub;
- the recovery passphrase is stored in the phone password manager;
- the encrypted private-vault copy decrypts and signs successfully.

Current backup status: **BACKUP_BLOCKED — PHASE A NOT RUN**.

## Encryption and phone recovery

The recovery file is named `Vitalis-Production-Signing-Recovery.tar.gz.age`. It uses the age passphrase format with scrypt derivation and authenticated ChaCha20-Poly1305 encryption. An Android graphical age implementation such as AgePony can decrypt the standard file without a terminal.

The encrypted package contains:

- `vitalis-production.jks`;
- `signing-secrets.json` with the generated passwords and fixed alias;
- `certificate.txt` with safe public metadata;
- `README-RECOVERY.txt`.

The unencrypted directory exists only in runner temporary storage. It is never uploaded.

See `VITALIS_PHONE_SIGNING_RECOVERY_GUIDE.md` for the complete no-shell procedure.

## Dedicated private signing vault

Durable encrypted storage is the private repository:

`gillesarnaudasse65-web/Vitalis-Signing-Vault`

It may contain only:

- `Vitalis-Production-Signing-Recovery.tar.gz.age`;
- `certificate-public-metadata.txt`;
- public recovery instructions.

It must never contain a plaintext JKS, plaintext password, unencrypted Base64 keystore, private key, recovery passphrase, or privileged token. The current public repository must never store the encrypted signing package.

The private vault is one durable copy, but it does not replace Backup A or Backup B.

## Production activation marker

After Phase B succeeds and the public metadata is reviewed, this document must be updated to:

`Status: **ACTIVE — BACKUP_CONFIRMED**`

The pending fields must be replaced with the actual safe public certificate values. The permanent warning must remain:

**DO NOT REPLACE THIS CERTIFICATE FOR FUTURE APK/AAB UPDATES.**

The initialization workflow refuses replacement once the pending marker is gone and returns:

`PRODUCTION SIGNING ALREADY INITIALIZED`

## Production build and upgrade continuity

The protected `production` job restores the encrypted package directly from the private vault using:

- `VITALIS_SIGNING_BACKUP_PASSPHRASE`;
- `VITALIS_SIGNING_VAULT_TOKEN`.

It fails unless this document contains the active backup-confirmed marker and the restored certificate fingerprint matches this document. It has no Android Debug fallback.

The same manual build can produce:

- `Vitalis-3.15.0-rc1-production-signed` with the APK, AAB, R8 mapping, signature report and hashes;
- `Vitalis-upgrade-test-baseline-v20-production-signed`, built from verified source `188a31e2ff34ef102cdcfa861f8de69972bee88e` with the same certificate.

The phone upgrade must install versionCode 21 over versionCode 20 without uninstalling or clearing data.
