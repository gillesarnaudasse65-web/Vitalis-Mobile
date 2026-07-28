# Vitalis Mobile Native — Android

Application Android native Vitalis Health OS avec passerelle officielle Health Connect.

## Vitalis 3.5 — fonctionnement hors ligne

- L’interface Vitalis est désormais intégrée directement dans l’APK.
- L’application démarre et reste consultable même lorsque le site ou Internet est indisponible.
- Les saisies manuelles, le profil, les objectifs et le journal sont conservés localement sur le téléphone.
- Les actions rapides permettent de photographier un repas, enregistrer une activité, ajouter de l’eau et saisir une mesure.
- Kofi, le coach Vitalis, analyse localement l’activité, le sommeil, l’hydratation et la fréquence cardiaque disponibles.
- Internet reste utile pour les futurs services IA distants et les synchronisations nécessitant un fournisseur externe, mais n’est plus requis pour ouvrir l’application.

## Health Connect

- Le pont `VitalisAndroid` ouvre la fenêtre système Health Connect.
- L’utilisateur choisit les catégories qu’il souhaite partager.
- Vitalis lit les données autorisées et les injecte dans le tableau de bord local.
- Samsung Health, Google Fit, Mibro, FlexMe et les autres connecteurs ne sont ni supprimés ni déconnectés.
- Une application ne peut transmettre des données que si elle publie dans Health Connect ou autorise sa propre API.
- Aucune clé API n’est intégrée dans l’APK.

## Télécharger l’APK

1. Ouvrir l’onglet **Actions** du dépôt.
2. Sélectionner **Build Vitalis APK**.
3. Ouvrir la dernière exécution réussie.
4. Télécharger l’artefact **Vitalis-Mobile-debug-apk**.
5. Décompresser le ZIP puis installer `app-debug.apk`.

Une compilation est lancée automatiquement à chaque mise à jour de `main`.

## Autoriser Health Connect

1. Installer l’APK sur Android 9 ou supérieur.
2. Ouvrir Vitalis.
3. Aller dans **Sources > Autoriser dans Health Connect**.
4. Sélectionner les catégories à partager dans la fenêtre Android.
5. Revenir dans Vitalis puis appuyer sur **Actualiser les données**.
