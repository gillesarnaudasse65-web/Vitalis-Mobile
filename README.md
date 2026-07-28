# Vitalis Mobile Native — Android

Application Android native Vitalis Health OS avec interface classique, passerelle Health Connect et continuité hors ligne.

## Vitalis 3.6 — interface classique hybride

- L’ancienne interface Vitalis en ligne redevient l’interface principale.
- Les fonctions natives Android sont injectées derrière l’interface sans modifier son apparence.
- Le scanner de repas ouvre réellement la caméra ou la galerie.
- L’enregistrement d’activité, l’hydratation et les mesures disposent d’un formulaire fonctionnel.
- Le bouton Health Connect ouvre les autorisations système Android.
- Si le site ne répond pas, l’application bascule automatiquement sur le tableau de bord local.
- Depuis le mode local, un bouton permet de réessayer l’interface classique.
- Les données Health Connect et les saisies manuelles restent disponibles dans leurs contextes respectifs.
- Aucune clé API n’est intégrée dans l’APK.

## Sources santé

Vitalis peut recevoir les données autorisées de Samsung Health, Google Fit, Mibro Fit, Fitbit et d’autres applications lorsqu’elles publient dans Health Connect. Les fournisseurs qui n’utilisent pas Health Connect nécessitent toujours leur API officielle ou une autorisation OAuth.

## Télécharger l’APK

1. Ouvrir l’onglet **Actions** du dépôt.
2. Sélectionner **Build Vitalis APK**.
3. Ouvrir la dernière exécution réussie.
4. Télécharger **Vitalis-Mobile-debug-apk**.
5. Décompresser le ZIP puis installer `app-debug.apk`.

Une compilation est lancée automatiquement à chaque mise à jour de `main`.

## Utilisation

1. Ouvrir Vitalis : l’interface classique se charge en priorité.
2. Aller dans les sources santé et appuyer sur **Autoriser dans Health Connect**.
3. Autoriser les catégories souhaitées.
4. Utiliser les actions rapides depuis le tableau de bord.
5. En cas de panne réseau ou du site, utiliser le mode local puis **Réessayer l’interface classique**.
