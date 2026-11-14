# Fiche Google Play : Mes Médocs (français)

Textes prêts à coller dans la Play Console. Les champs `<à compléter>` dépendent de toi.

## Textes du store

- **Nom de l'appli** (30 caractères max, 28 ici) : `Mes Médocs : rappel de prise`
- **Description courte** (80 max, 79 ici) : `Rappels de médicaments, stock et scan de boîte. Gratuit, sans pub, 100 % local.`
- **Description complète** (4000 max, 2203 ici) :

```
Mes Médocs est un aide-mémoire pour ne plus oublier tes médicaments : il te prévient à l'heure de chaque prise et suit ton stock. Gratuit, sans publicité, et 100 % privé.

RAPPELS FIABLES
• Une notification à l'heure de chaque prise, avec trois boutons : « J'ai pris », Reporter ou Sauter
• Les rappels restent actifs téléphone en veille, après un redémarrage ou un changement d'heure
• Plannings souples : tous les jours, certains jours de la semaine, tous les N jours, plusieurs heures et doses par jour (demi-comprimés compris)
• Les prises oubliées sont signalées

AJOUT RAPIDE
• Scanne le code-barres ou le petit carré (Datamatrix) de la boîte : le médicament et son conditionnement sont retrouvés automatiquement
• Ou cherche par nom, par molécule ou par numéro CIP dans la base publique française des médicaments
• Photo de la boîte pour reconnaître chaque médicament d'un coup d'œil

STOCK SOUS CONTRÔLE
• Chaque prise décompte ton stock
• Une alerte te prévient avant la rupture, en nombre de jours de traitement restants
• Prises hors planning (douleur, fièvre…), historique complet, archivage des traitements terminés

TES DONNÉES RESTENT CHEZ TOI
• Aucun compte, aucun cloud, aucune publicité, aucun accès à internet
• Sauvegarde et restauration par un fichier chiffré que tu gardes toi-même, pour changer de téléphone sans rien perdre

PENSÉ POUR TOUS
• Grandes polices, TalkBack, interface claire en français
• Un carrousel de prise en main t'aide à régler les notifications, les alarmes exactes et l'économie d'énergie

LIBRE
Logiciel libre sous licence GNU GPL v3 ou ultérieure. Données des médicaments : Base de données publique des médicaments (ANSM, HAS, Assurance Maladie), Licence Ouverte 2.0.

IMPORTANT
Mes Médocs est un simple aide-mémoire. Il ne donne aucun conseil médical, ne vérifie ni les doses, ni les interactions, ni les contre-indications, et ne remplace ni un médecin ni un pharmacien. La liste des médicaments peut être incomplète ou dépassée : vérifie toujours sur ta boîte et ton ordonnance. Un rappel peut échouer (téléphone éteint, économie d'énergie, notifications désactivées) : pour un traitement dont un oubli serait grave, ne te fie pas uniquement à cette application.
```

## Informations générales

| Champ | Valeur proposée |
|---|---|
| Identifiant du paquet | `net.grandou.medocs` (définitif après publication) |
| Catégorie | Santé et remise en forme (une appli de rappel n'est pas un outil médical clinique) |
| Gratuite / payante | Gratuite, sans achat intégré, sans publicité |
| Adresse e-mail de contact | `<à compléter>` (visible publiquement) |
| URL de la politique de confidentialité | `<à compléter>` : héberger `play/confidentialite-fr.md` sur une page publique (par exemple sur grandou.net) |
| Public cible | 18 ans et plus (évite le régime « applis pour enfants ») |
| Classification du contenu (questionnaire IARC) | Aucune violence, aucun contenu sexuel, aucun langage grossier, aucune drogue illustrée, aucun jeu d'argent, aucun partage d'achats : résultat attendu « tous publics » |

## Section « Sécurité des données » (Data safety)

Aucune donnée n'est collectée ni partagée : l'application n'a pas la permission `INTERNET`.

- Collecte de données : **Non**
- Partage de données : **Non**
- Chiffrement en transit : sans objet (aucune transmission)
- Demande de suppression : sans objet (les données ne quittent pas l'appareil ; désinstaller l'application les supprime)
- Précision utile : la photo de la boîte et les données de santé (médicaments, prises) sont **stockées uniquement sur l'appareil**.

## Déclarations d'autorisations et de politique

**Alarmes exactes (`SCHEDULE_EXACT_ALARM`)** : texte de justification :
> Mes Médocs est une application de rappel de prise de médicaments. L'utilisateur programme des heures de prise précises ; une notification retardée de plusieurs minutes (comportement des alarmes inexactes en veille profonde) peut avoir des conséquences sur un traitement. L'autorisation est demandée à l'utilisateur dans le carrousel de prise en main, avec une explication ; sans elle, l'application se rabat sur des alarmes inexactes et l'indique clairement.

**Caméra (`CAMERA`)** : lecture du code-barres ou du Datamatrix d'une boîte et prise de photo de la boîte, à la demande, après une explication. Rien n'est enregistré en dehors de la photo choisie, ni envoyé.

**Notifications (`POST_NOTIFICATIONS`)** : affichage des rappels de prise et des alertes de stock.

**Redémarrage (`RECEIVE_BOOT_COMPLETED`)** : réarmer les rappels après un redémarrage du téléphone.

**Applications de santé (déclaration obligatoire)** : l'application entre dans les « rappels de médicaments ou de traitement ». Elle ne diagnostique pas, ne donne aucun conseil médical, n'utilise pas Health Connect et ne partage aucune donnée. L'avertissement « simple aide-mémoire » est affiché au premier lancement (acceptation obligatoire) et dans « À propos ».

**Utilisation de l'optimisation de la batterie** : l'application n'utilise **pas** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (restreinte par le Play Store) : elle guide l'utilisateur vers les réglages du téléphone.

## Éléments graphiques à fournir

- Icône 512 x 512 px (PNG, 32 bits)
- Image de présentation 1024 x 500 px
- Captures d'écran de téléphone : au moins 2 (idéalement 4 à 8), 16:9 à 9:16, de 320 à 3840 px

## Notes de version de la 1re publication

```
Première version de Mes Médocs : rappels de prise à l'heure, plannings souples, stock avec alerte avant rupture, scan du code-barres de la boîte, photo, sauvegarde chiffrée, grandes polices et TalkBack.
```
