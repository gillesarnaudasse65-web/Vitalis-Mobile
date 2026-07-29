# Vitalis Mobile Native — Android

Application Android Vitalis Health OS avec interface classique, passerelle Health Connect, traçabilité des sources et continuité hors ligne.

## Vitalis 3.9 — fonctions IA opérationnelles

- L’interface classique reste inchangée et demeure l’interface principale.
- La version 3.9.1 conserve strictement l’interface historique du site : les anciens boutons sont rendus fonctionnels sans ajouter de barre ou de boutons flottants.
- Le chargement de l’interface classique dispose de délais plus adaptés aux connexions mobiles et de deux nouvelles tentatives avant le mode hors ligne.
- La version 3.10 synchronise les données Health Connect sur la journée choisie dans le calendrier, puis actualise les cartes visibles et leurs sources.
- Les répétitions de coach sont réduites : le coach principal reste disponible, tandis qu’une carte dupliquée devient l’espace Nutrition du jour.
- L’espace Nutrition affiche repas, calories, glucides, protéines, lipides et fibres, avec accès au détail complet.
- Les rubriques existantes Activité, Sommeil, Signes vitaux, Nutrition, Bien-être mental, Dossier santé et Connexions ouvrent désormais leurs données ou analyses fonctionnelles.
- **Kofi, coach IA** utilise désormais l’API OpenAI Responses pour répondre aux questions et analyser les données Vitalis autorisées.
- Les analyses IA précédentes sont reliées au même moteur : bilan santé, score, nutrition, activité, sommeil, récupération, stress, recommandations et rapport personnalisé.
- Les boutons IA de l’interface classique sont reconnus par leur libellé et ouvrent automatiquement l’analyse correspondante.
- Le scanner de repas peut analyser la photo et estimer les aliments, calories et macronutriments avec un avertissement clair sur l’incertitude.
- Une analyse locale contextualisée reste disponible sans clé, sans Internet ou lorsque le service IA est indisponible : aucun bouton IA ne reste inactif.
- La dictée vocale alimente directement le coach ; le microphone et la lecture vocale peuvent être activés ou coupés séparément.
- La clé OpenAI n’est jamais inscrite dans l’APK ou dans GitHub : l’utilisateur la renseigne dans Vitalis et Android Keystore la chiffre sur le téléphone.
- Le consentement explicite est obligatoire avant toute transmission de données santé ou de photo à l’IA.

## Score explicable et détails santé

- L’interface classique reste l’interface principale, sans modification visuelle.
- **Comprendre mon score** ouvre désormais le calcul détaillé sur 100 points.
- Le score est décomposé en activité, sommeil, hydratation, nutrition et récupération, avec 20 points par catégorie.
- Chaque catégorie donne accès à ses enregistrements détaillés, à la source et à l’heure.
- La nutrition affiche calories, glucides, protéines, graisses, fibres, sucre et sodium, ainsi que le détail de chaque repas.
- L’activité affiche le type, la durée, les notes, l’heure et le connecteur de chaque séance.
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
- Aucune clé API n’est intégrée dans l’APK ou publiée dans GitHub.

## Première activation de Kofi IA

1. Ouvrir **Coach** ou **Ouvrir le coach** dans Vitalis.
2. Coller la clé OpenAI dédiée « Vitalis AI ».
3. Lire puis accepter le consentement de transmission des données nécessaires.
4. Appuyer sur **Activer l’IA**.

La clé reste chiffrée sur l’appareil. La supprimer depuis les réglages IA révoque la configuration locale ; la révocation définitive d’une clé s’effectue également dans OpenAI Platform.

## Télécharger l’APK

1. Ouvrir l’onglet **Actions** du dépôt.
2. Sélectionner **Build Vitalis APK**.
3. Ouvrir la dernière exécution réussie.
4. Télécharger **Vitalis-Mobile-debug-apk**.
5. Décompresser le ZIP puis installer `app-debug.apk`.

Une compilation est lancée automatiquement à chaque mise à jour de `main`.

## Vitalis 3.11 — coachs et actualisation

- Tous les coachs spécialisés sont de nouveau accessibles : santé globale, nutrition, activité, sommeil, récupération et bien-être mental.
- L’accueil conserve un seul coach principal afin d’éviter les doublons ; le catalogue complet reste accessible depuis l’espace Coach.
- Le bouton **Actualiser les données** relance la synchronisation pour la date sélectionnée et actualise cartes, nutrition, score et sources.
- L’état de synchronisation est visible : actualisation en cours, réussite horodatée ou possibilité de réessayer.
