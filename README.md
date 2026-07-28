# Vitalis Mobile Native — Android

Application Android Vitalis Health OS avec interface classique, passerelle Health Connect, traçabilité des sources et continuité hors ligne.

## Vitalis 3.7 — connecteurs, sources et voix

- L’interface classique reste l’interface principale.
- Les sources Health Connect sont découvertes dynamiquement, sans liste ni nombre maximum codé dans Vitalis.
- Chaque donnée synchronisée indique le dernier connecteur, son package, l’heure de la dernière mesure et tous les contributeurs détectés.
- Un bouton **Actualiser** relance la lecture Health Connect et ouvre le rapport détaillé des données et sources.
- Le bouton **Voix** utilise le moteur vocal Android natif avec une vitesse et une tonalité stabilisées.
- Le bouton **Micro** active ou coupe explicitement le microphone. Le microphone est désactivé par défaut.
- La reconnaissance vocale transmet les résultats partiels et définitifs à l’interface et au coach.
- Le scanner de repas, l’activité, l’hydratation et les mesures manuelles restent opérationnels.
- Si le site ne répond pas, Vitalis bascule sur le tableau de bord local avec les mêmes commandes natives.

## Fonctionnement réel des connecteurs

Vitalis n’impose aucune limite au nombre de sources détectées. Toute application qui écrit des données autorisées dans Health Connect peut apparaître automatiquement.

Samsung Health, Google Fit, Mibro Fit, Fitbit et d’autres services peuvent fonctionner lorsqu’ils publient effectivement leurs données dans Health Connect. Un fournisseur qui ne prend pas en charge Health Connect nécessite obligatoirement son API officielle et son autorisation OAuth ; une application Android ne peut pas contourner cette restriction fournisseur.

## Confidentialité vocale

- L’autorisation microphone est demandée uniquement lors de la première activation.
- Le micro reste coupé au démarrage.
- Le bouton rouge **Couper micro** arrête immédiatement l’écoute.
- La voix de lecture peut être arrêtée avec le même bouton **Voix**.
- Aucune clé API n’est intégrée dans l’APK.

## Télécharger l’APK

1. Ouvrir l’onglet **Actions** du dépôt.
2. Sélectionner **Build Vitalis APK**.
3. Ouvrir la dernière exécution réussie.
4. Télécharger **Vitalis-Mobile-debug-apk**.
5. Décompresser le ZIP puis installer `app-debug.apk`.

Une compilation est lancée automatiquement à chaque mise à jour de `main`.
