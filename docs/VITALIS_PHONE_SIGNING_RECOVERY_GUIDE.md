# Vitalis phone-only signing recovery guide

This guide uses an Android phone, GitHub Web, the phone password manager and phone-accessible storage. No PC, terminal, ADB, Android Studio, local `keytool`, or local Base64 conversion is required.

## The two things that must never be lost

1. `Vitalis-Production-Signing-Recovery.tar.gz.age` — the encrypted recovery file containing the permanent Vitalis keystore and its generated signing passwords.
2. `VITALIS_SIGNING_BACKUP_PASSPHRASE` — the recovery passphrase created by the user and stored only in the phone password manager and protected GitHub environments.

The encrypted file is unusable without the passphrase. The passphrase is unusable without the encrypted file. Never put both together in a message, document, screenshot, issue, PR, or public repository.

## Before running Phase A

From the Android phone:

1. In a trusted password manager, generate a unique recovery passphrase of at least 24 random characters or at least six random words.
2. Do not paste it into ChatGPT, an issue, a PR, workflow input, document, or message.
3. In GitHub Web, add it directly as the `VITALIS_SIGNING_BACKUP_PASSPHRASE` secret in the `production-signing-init` environment.
4. Create the private repository `gillesarnaudasse65-web/Vitalis-Signing-Vault` with a README and private visibility.
5. Create a fine-grained GitHub token restricted to that private repository with repository Contents read/write access. Add it directly as `VITALIS_SIGNING_VAULT_TOKEN` in the `production-signing-init` environment. Never send the token through chat.

## Download Backup A after Phase A succeeds

1. In GitHub Web, open **Actions** → **Initialize Vitalis Production Signing** → the successful run.
2. Under **Artifacts**, download `Vitalis-production-signing-encrypted-recovery`.
3. Extract the downloaded transfer ZIP with Android Files.
4. Locate `Vitalis-Production-Signing-Recovery.tar.gz.age`.
5. Save it in a protected folder on the phone. This is **Backup A**.

The transfer artifact expires after seven days. It is not the permanent backup.

## Create independent Backup B

Copy the same `.age` file to one independent user-controlled location, for example:

- Google Drive in the user's own account;
- encrypted cloud storage;
- a USB-C drive connected to the phone;
- another protected storage provider controlled by the user.

Do not rename the file in a way that hides its purpose. Do not delete Backup A after copying Backup B.

## Phone-compatible recovery check

The `.age` format uses authenticated age encryption with scrypt passphrase derivation and ChaCha20-Poly1305. It can be opened graphically on Android with an age-compatible application such as **AgePony**, available from Google Play or F-Droid.

For a future recovery:

1. Install an age-compatible Android application from its official store/source.
2. Open `Vitalis-Production-Signing-Recovery.tar.gz.age`.
3. Enter the passphrase from the password manager directly in that application.
4. Save the decrypted `.tar.gz` only in secure temporary phone storage.
5. Extract it with a compatible Android file manager.
6. Use the recovered material only inside a controlled signing-recovery process.
7. Delete plaintext extracted signing files from the phone immediately after recovery.

Routine recovery testing is performed automatically on GitHub Actions without exposing the passphrase or plaintext files. The user does not need to decrypt the real package on the phone during initialization.

## Confirm before activation

Production signing remains blocked until all three statements are true:

- `BACKUP A: CONFIRMED`
- `BACKUP B: CONFIRMED`
- `RECOVERY PASSPHRASE STORED: CONFIRMED`

Run **Confirm Vitalis Production Signing Backup** with all four boolean inputs enabled, or provide those three confirmations explicitly in chat. No secret value is entered in the confirmation.

## Why this identity is permanent

Android requires future APK updates for `com.vitalis.healthos` to use the same signing identity. Losing or replacing this identity can prevent seamless updates and may force users to uninstall the app, which can destroy local data.

**DO NOT REPLACE THIS CERTIFICATE FOR FUTURE APK/AAB UPDATES.**
