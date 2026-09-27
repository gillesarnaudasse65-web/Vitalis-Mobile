# Vitalis production signing identity

Application ID: `com.vitalis.healthos`  
Signing lineage: **FIRST STABLE VITALIS PRODUCTION IDENTITY**  
Status: **PENDING USER-SIDE SECURE CREATION**

Android Debug and earlier CI test certificates are not production identities. Every future production APK/AAB update for `com.vitalis.healthos` must use the permanent identity recorded here after creation.

## Safe public identity metadata

| Field | Value |
|---|---|
| Alias | `vitalis-production` |
| Subject | `CN=Vitalis Health OS, OU=Mobile, O=Vitalis, L=Abidjan, ST=Abidjan, C=CI` |
| Algorithm | RSA 4096 |
| Creation date | PENDING |
| Validity | PENDING; requested validity is 10,000 days |
| Certificate SHA-256 | PENDING |

Only the public fingerprint and certificate metadata belong in this document. Never add the keystore, passwords, Base64 keystore value, private-key output, or secret-manager recovery data.

## Windows creation procedure

Use PowerShell on the trusted Windows release workstation. Confirm `keytool` is from a trusted JDK 17 installation:

```powershell
keytool -version
New-Item -ItemType Directory -Force -Path "C:\Vitalis-Signing" | Out-Null
keytool -genkeypair `
  -v `
  -keystore "C:\Vitalis-Signing\vitalis-production.jks" `
  -alias "vitalis-production" `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000 `
  -dname "CN=Vitalis Health OS, OU=Mobile, O=Vitalis, L=Abidjan, ST=Abidjan, C=CI"
```

Choose strong passwords interactively. Do not type them into ChatGPT, source files, shell history, PR comments, documentation, or ordinary text files.

Verify the entry and read only its safe public metadata:

```powershell
keytool -list -v `
  -keystore "C:\Vitalis-Signing\vitalis-production.jks" `
  -alias "vitalis-production"
```

Required observations:

- entry type is `PrivateKeyEntry`;
- alias is `vitalis-production`;
- subject matches the intended Vitalis identity;
- SHA-256 fingerprint and validity dates are present.

Record the SHA-256 fingerprint and dates in this document only after independently confirming them.

## Backup and recovery gate

Production signing must not be enabled until all three controls are confirmed:

- **Backup A:** primary protected keystore at `C:\Vitalis-Signing\vitalis-production.jks` or an equivalently protected local location;
- **Backup B:** independently stored encrypted copy outside the release workstation;
- **Password vault:** keystore and key passwords saved in a secure password manager or equivalent protected organizational vault.

Current backup status: **NOT CONFIRMED**.

Losing the keystore or passwords can permanently prevent seamless Android updates. A copy inside the repository, Downloads, an ordinary shared folder, or an unprotected cloud-synced Desktop does not satisfy this policy.

## GitHub production environment

After the backup gate is confirmed, open:

`Vitalis-Mobile → Settings → Environments → production`

Create the environment if absent and add exactly these environment secrets:

- `VITALIS_KEYSTORE_BASE64`
- `VITALIS_KEYSTORE_PASSWORD`
- `VITALIS_KEY_ALIAS`
- `VITALIS_KEY_PASSWORD`

Create the Base64 value locally and copy it directly to the GitHub secret form:

```powershell
$bytes = [System.IO.File]::ReadAllBytes(
  "C:\Vitalis-Signing\vitalis-production.jks"
)
$base64 = [System.Convert]::ToBase64String($bytes)
$base64 | Set-Clipboard
Remove-Variable base64, bytes
```

`VITALIS_KEYSTORE_BASE64` can reconstruct the encrypted keystore and must be treated as sensitive. Never paste it into ChatGPT, documentation, issues, logs, or commits.

Set `VITALIS_KEY_ALIAS` to `vitalis-production`. Enter both passwords directly from the secure vault. Where available, enable required reviewers and restrict deployment branches for the `production` environment.

## Production build and verification

Run `Vitalis Android quality gates` manually on `agent/vitalis-3.15-rc-validation` with `build_production = true`.

The job must fail if a secret is absent. A passing job must publish `Vitalis-3.15.0-rc1-production-signed` and record the APK/AAB signatures and SHA-256 checksums. The APK certificate fingerprint must match the fingerprint recorded above and must not be `CN=Android Debug`.

## Upgrade continuity rule

Because this is the first stable production lineage, create the controlled versionCode 20 upgrade baseline only after the identity is established. Sign both baseline 20 and RC 21 with this exact same identity, install RC with `adb install -r`, and never uninstall to conceal a signer mismatch.

