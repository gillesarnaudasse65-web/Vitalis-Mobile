# Vitalis Mobile Native — Android

Application Android native de Vitalis Health OS avec passerelle officielle Health Connect.

## Fonctionnement

- L’interface Vitalis existante est chargée depuis le domaine de production sécurisé.
- Seul le domaine officiel Vitalis reste dans l’application ; les liens externes s’ouvrent dans le navigateur.
- Le pont `VitalisAndroid` ouvre la fenêtre système Health Connect.
- L’utilisateur choisit les catégories qu’il souhaite partager.
- Samsung Health, Google Fit, Mibro, FlexMe et les autres connecteurs web ne sont ni remplacés ni modifiés.
- Aucune clé API n’est intégrée dans l’APK.

## Télécharger l’APK

1. Ouvrir l’onglet **Actions** du dépôt.
2. Sélectionner **Build Vitalis APK**.
3. Ouvrir la dernière exécution réussie.
4. Télécharger l’artefact **Vitalis-Mobile-debug-apk**.

Une compilation est lancée automatiquement à chaque mise à jour de `main`.

## Autoriser Health Connect

1. Installer l’APK sur Android 9 ou supérieur.
2. Ouvrir Vitalis et se connecter.
3. Aller dans **Sources et appareils > Health Connect**.
4. Appuyer sur **Autoriser dans Health Connect**.
5. Sélectionner les catégories à partager dans la fenêtre Android.
