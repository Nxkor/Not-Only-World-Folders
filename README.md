# Not Only World Folders (NOWF)

Mod Fabric **client** qui organise les mondes solo, les serveurs multijoueur et les packs de ressources en dossiers, et partage mondes, packs, shaders et options entre les instances de l'ordinateur.

## Fonctionnalités

- **Dossiers et sous-dossiers** pour les mondes (écran Solo) et les serveurs (écran Multijoueur).
  - Clic sur un dossier : le déplie ou le replie dans la liste.
  - Double-clic : n'affiche que ce dossier. La ligne `..` permet de remonter.
- **Clic droit** sur un monde ou un serveur :
  - ouvrir le monde / rejoindre le serveur ;
  - renommer ;
  - ajouter une description ;
  - mettre en favori ou retirer des favoris ;
  - placer dans un nouveau dossier ;
  - déplacer vers… (arbre des dossiers) ;
  - copier / coller ;
  - placer en corbeille ;
  - pour les mondes seulement : ouvrir le dossier du monde et régler les règles du jeu.
- **Clic droit** sur un dossier : ouvrir, nouveau sous-dossier, coller ici, renommer, icône, description, supprimer (en gardant le contenu, ou avec son contenu, qui part à la corbeille).
- **Icône de dossier** : un id d'item, `minecraft:...` ou `mod:...` : n'importe quel item d'un mod chargé marche. La saisie suggère d'abord les espaces de noms (`minecraft:` puis les mods), puis les items de celui tapé ; Tab parcourt les suggestions (Maj + Tab en arrière, en restant appuyé il continue), un clic en prend une, la molette fait défiler la liste. L'icône est dessinée comme un item, donc les packs de ressources actifs la changent aussi. Un aperçu de l'item tapé (ou de la suggestion survolée) s'affiche à gauche de la saisie. Vide = icône par défaut, réglable dans les options (coffre de base).
- **Corbeille** : le bouton vanilla « Supprimer » y envoie le monde ou le serveur. La suppression définitive se fait depuis la vue Corbeille. C'est une corbeille propre au mod (une infobulle le rappelle) : rien n'est déplacé sur le disque. Un favori ne peut pas être mis à la corbeille. La vue Corbeille se ferme d'elle-même quand son dernier élément est restauré ou supprimé, et en revenant du menu principal.
- **Description** (mondes, serveurs, dossiers) : elle s'affiche au survol du monde ou serveur sélectionné, ou du dossier. Codes couleur `&a`, `&l`… et `\n` pour aller à la ligne.
- **Temps de jeu** en blanc, en dernière ligne de cette infobulle, ou à la place de `{time}` si la description le contient. Pour un monde, il vient de ses statistiques. Pour un serveur, le mod le compte lui-même (par adresse), à partir de la v2.
- **Couleurs dans les noms** de mondes, serveurs et dossiers : `&6Survie`. Pour les mondes et serveurs, le nom est enregistré avec les codes `§` de Minecraft, donc les couleurs restent même sans le mod.
- **Tri / filtre** (bouton ↑↓ à côté de « Nouveau dossier », menu déroulant) : par défaut (favoris en premier), favoris seulement, non favoris seulement, date la plus récente / ancienne, version la plus récente / ancienne. Les flèches passent en jaune quand un tri ou filtre est actif, et ne se dessinent plus par-dessus un menu déroulant ouvert.
- **Copier / coller** : le presse-papier garde le chemin complet du monde. On peut donc copier un monde depuis le dossier d'une autre instance, revenir au dossier par défaut, puis coller.
- **Glisser-déposer** d'un monde ou d'un serveur :
  - entre deux autres : la liste se réarrange en direct, et l'ordre est gardé (tri « Par défaut », favoris toujours en premier) ;
  - sur un dossier (ou `..`) : il y entre. Survolé 0,5 s, le dossier s'ouvre, pour placer le monde parmi son contenu ;
  - sur « Nouveau dossier » : demande le nom du dossier à créer, et l'y place. Le dossier est créé dans celui où se trouvait la ligne (un sous-dossier si elle venait d'un dossier) ;
  - sur la corbeille : le bouton devient rouge, et le monde part à la corbeille ;
  - clic droit pendant le glissement : annule, le monde revient à sa place.
- **Maj + flèches** (serveurs, comme en vanilla) : déplace le serveur d'un cran, dans le même ordre que le glisser-déposer.
- **Instance** (menu déroulant ▼ à gauche de « Nouveau dossier » ; au-dessus, à côté du titre, quand la fenêtre est trop étroite) : liste les dossiers `saves` de cette instance, de celles trouvées sur l'ordinateur (Minecraft, CurseForge, Modrinth, Prism, MultiMC, ATLauncher, GDLauncher) et de ceux choisis à la main avec « Autre dossier… ». En choisir une affiche ses mondes sans quitter le jeu. Chaque dossier garde sa propre organisation.
- **Ouvrir le dossier des mondes** (bouton 📂 à gauche du menu des instances) : ouvre dans l'explorateur le dossier `saves` de l'instance affichée. Son chemin s'affiche au survol.
- **Menu principal**, de part et d'autre de « Solo » :
  - à gauche, le bouton 📷 ouvre le dossier des captures d'écran. Clic droit : choisir l'instance dont il ouvre les captures (son chemin s'affiche au survol) ;
  - à droite, le bouton « + » : créer un monde (dans le dossier `saves` de l'instance choisie sur l'écran Solo) ou charger un preset.
- **Sélection multiple** : Ctrl + clic ajoute ou retire une ligne, Maj + clic sélectionne une plage. Le clic droit propose alors favori, nouveau dossier, déplacer, corbeille (ou restaurer) pour toute la sélection, et un glisser-déposer déplace le groupe. Échap désélectionne.
- **Pendant un glisser-déposer**, la liste défile toute seule près de son bord haut ou bas.
- **Compteur des dossiers** : `(2 · 7)` = 2 éléments directement dans le dossier, 7 avec ses sous-dossiers.
- **Taille du monde** dans l'infobulle, ou à la place de `{size}` dans la description.
- **Actualisation automatique** : la liste des mondes se recharge quand un monde apparaît, disparaît ou est sauvegardé dans le dossier (autre launcher, copie dans l'explorateur…).
- **Vider la corbeille** : dans la vue Corbeille, « Tout vider » supprime définitivement tout son contenu, après confirmation.
- **Sauvegarder le monde** : bouton du menu pause, en solo, qui sauvegarde sans quitter le monde.
- **Glisser-déposer d'un dossier** : comme un monde, un dossier se place au-dessus ou au-dessous d'un monde ou d'un autre dossier (il y reste), sort de son dossier, ou entre dans un autre. Sur une ligne de dossier, le quart haut et le quart bas placent avant / après ; le milieu fait entrer dedans. Un dossier ne va jamais à la corbeille.
- **Presets de monde** : tout ce que règle l'écran de création (nom, seed, type de monde, mode de jeu, difficulté, commandes, structures, coffre bonus, règles du jeu, personnalisation du monde plat : couches, biome, préréglages « vide », « désert », « classique »…) s'enregistre sous le nom du monde.
  - écran Solo, à droite de « Créer un nouveau monde » : « P+ » crée un preset, « P▼ » en charge un ;
  - menu principal : le bouton « + » propose « Créer un monde » ou « Charger un preset » (sous-menu des presets, avec « + » pour en créer un) ;
  - créer ou modifier un preset ouvre l'écran de création de Minecraft, dont le bouton devient « Enregistrer le preset » : aucun monde n'est créé ;
  - options (Mod Menu) : « Presets de monde… » pour modifier ou supprimer un preset.
  - Les packs de données, les expériences et la personnalisation des autres types de monde (biome unique…) ne sont pas enregistrés.
- **Notifications** en haut à droite, comme un progrès : preset créé, modifié ou supprimé, dossier renommé ou dont l'icône ou la description change, monde ou serveur restauré ou supprimé définitivement. Dans les options : activées ou non, et leur durée, de 0,5 s à 10 s.
- **Instances sans monde** : elles n'apparaissent pas dans le menu des instances, et un dossier choisi à la main est refusé s'il ne contient aucun monde. Si le dernier monde de l'instance affichée est supprimé, le mod revient à l'instance de départ ; l'instance vidée reste proposée jusqu'au redémarrage du jeu.
- **Restauration** : un monde ou serveur restauré retrouve son dossier, recréé (avec ses parents) s'il avait été supprimé entre-temps.
- **Créer un monde dans un dossier** : le petit « + » à droite du nom d'un dossier (ou de la ligne `..`) ouvre la création de monde de Minecraft ; le monde créé est rangé dans ce dossier.
- **Packs de ressources de toutes les instances** : l'écran des packs de ressources liste aussi les packs des autres instances détectées (et des dossiers choisis à la main). Chacun indique son instance après sa description ; à nom de fichier égal, le pack de l'instance en cours l'emporte. L'avertissement « prévu pour une autre version » peut être sauté (option, activée par défaut).
- **Clic droit sur « Solo »** (menu principal) : ouvre le menu des instances. L'instance choisie est celle qu'un clic gauche affichera ; l'infobulle du bouton rappelle l'instance en cours.
- **FOV exact** : Ctrl + clic sur la barre de réglage du FOV demande le nombre à saisir (30 à 110).
- **Statistiques d'un monde** : clic droit sur un monde → « Statistiques » ouvre l'écran de statistiques de Minecraft, rempli depuis le dossier du monde (celles du joueur en cours s'il y a joué, sinon du joueur au plus gros fichier), sans charger le monde.
- **Interface au premier plan** : les boutons du mod sont dessinés par-dessus ceux des autres mods et reçoivent les clics en premier ; les injections du mod passent avant celles des autres (priorité 1500).
- **Organisation partagée entre les instances** (v2.5) : les dossiers, favoris, corbeille, descriptions et l'ordre des mondes d'un dossier `saves` sont les mêmes quelle que soit l'instance depuis laquelle le jeu est lancé ; pareil pour les packs de ressources. Au premier lancement de la v2.5, ce que chaque instance avait rangé de son côté est fusionné (copie de l'ancien fichier dans `config/worldfolders.json.pre-2.5`).
- **Packs de ressources rangés comme les mondes** (v2.5), dans la liste « Disponibles » :
  - clic droit sur un pack : activer, description, mettre en favori (les favoris restent épinglés en haut, avec une étoile), placer dans un nouveau dossier, déplacer vers…, ouvrir le dossier du pack, placer en corbeille ;
  - dossiers et sous-dossiers, avec le même clic droit que ceux des mondes (ouvrir, sous-dossier, renommer, icône, description, supprimer) ; clic droit dans le vide : le dossier affiché ;
  - boutons « Nouveau dossier » et « Corbeille (n) » en haut de l'écran ; depuis la corbeille, un pack se restaure ou se supprime définitivement (son fichier est alors effacé du disque, après confirmation) ;
  - glisser-déposer (sur un dossier, entre deux packs, sur « Nouveau dossier », sur la corbeille) et sélection multiple (Ctrl / Maj + clic), comme pour les mondes ;
  - la liste « Sélectionnés » et l'écran des packs de données ne changent pas.
- **Pack d'une autre instance qui se désactivait** (corrigé en v2.5) : un pack venant d'une autre instance reste dans « Sélectionnés » quand on rouvre l'écran des packs.
- **Options d'une autre instance** (v2.6, bouton ▼ en haut à gauche de l'écran Options) : comme pour les mondes, choisir une instance fait utiliser au jeu son `options.txt` (touches, graphismes, sons, langue, packs actifs…) : chaque instance sert de preset d'options. Le choix est gardé au prochain lancement ; « Cette instance » revient à ses propres options. Les réglages en cours sont enregistrés avant de changer. Le jeu réécrit ensuite le fichier de l'autre instance avec les options qu'il connaît : une copie de ce fichier tel qu'il était est gardée la première fois (`options.txt.nowf-backup`). Le chemin du fichier utilisé s'affiche au survol.
- **Shaders de toutes les instances** (v2.6, avec Iris) : l'écran des shaders liste aussi les shaders des autres instances, sous la forme « nom (instance) », et ils s'activent directement depuis leur dossier (rien n'est copié). Clic droit sur un shader : ouvrir son dossier, placer en corbeille. Bouton « Corbeille (n) » en haut à droite ; depuis la corbeille, clic droit : restaurer, ou supprimer définitivement (le shader et son fichier de réglages `.txt`, après confirmation). La corbeille est commune à toutes les instances. Sans Iris, rien de tout cela ne se charge.
- **Compatibilité** (v2.6) : vérifiée en jeu avec Iris, Sodium et Controlling (recherche dans les touches) chargés ensemble ; le nom affiché « Not Only World Folders (NOWF) » se trouve en cherchant « NOWF » dans la liste des mods.
- **Options (Mod Menu)** :
  - packs de ressources sans avertissement de version : activé ou non (activé par défaut) ;
  - glisser-déposer activé ou non (activé par défaut) ;
  - notifications : activées ou non, et leur durée ;
  - presets de monde : modifier, supprimer ;
  - dossier des mondes (le même réglage que le menu des instances) ;
  - icône par défaut des dossiers.

## Si on retire le mod

Toute l'organisation est stockée dans `config/worldfolders.json` (options, serveurs, presets) et, depuis la v2.5, dans `.notonlyworldfolders/shared.json` du dossier utilisateur (mondes et packs, communs à toutes les instances). Le dossier `saves/` ne contient toujours que des mondes vanilla, jamais de dossiers d'organisation. En retirant le mod, tous les mondes réapparaissent, y compris ceux qui étaient dans la corbeille.

Les serveurs reçoivent seulement une étiquette `worldfolders_id` dans `servers.dat`. Minecraft vanilla l'ignore.

## Versions

Un jar par version de Minecraft, dans `release/` (`not-only-world-folders-<version>+<minecraft>.jar` ; les anciens jars sont dans `release/v1/` et `release/v2/`). L'identifiant du mod reste `worldfolders`.

| Minecraft | Dossier source | Java | Test en jeu |
|---|---|---|---|
| 1.20 – 1.20.1 | `versions/1.20.1` | 17 | `gradlew runDevCheck` |
| 1.20.3 – 1.20.4 | `versions/1.20.4` | 17 | `gradlew runDevCheck` |
| 1.21 – 1.21.1 | `versions/1.21.1` | 21 | `gradlew runDevCheck` |
| 1.21.4 | `versions/1.21.4` | 21 | `gradlew runClientGameTest` |
| 1.21.6 – 1.21.8 | `versions/1.21.8` | 21 | `gradlew runClientGameTest` |
| 1.21.11 | `versions/1.21.11` | 21 | `gradlew runClientGameTest` |
| 26.1.2 | `versions/26.1.2` | 25 | `gradlew runClientGameTest` |
| 26.2 | `versions/26.2` | 25 | `gradlew runClientGameTest` |
| 26.3 | `versions/26.3` | 25 | `gradlew runClientGameTest` |

La racine du projet contient la même source que `versions/1.21.4` : c'est là que les nouveautés sont écrites, avant d'être portées dans les autres dossiers.

Dépendances : Fabric Loader uniquement. Mod Menu est optionnel (écran d'options), Iris aussi (shaders de toutes les instances). Fabric API n'est pas requise.

## Build

```
gradlew build            # jar dans build/libs, lance aussi le test de la logique (SpaceCheck)
gradlew runClientGameTest  # test de l'interface dans le vrai jeu, captures dans build/run/clientGameTest/screenshots
gradlew runClientGameTest -Pcompat  # le même test avec Iris, Sodium et Controlling chargés (1.21.4 et après ; avant, Iris ne démarre pas dans l'environnement de dev)
```

Les versions 26.x demandent un JDK 25 (`JAVA_HOME`). Les autres se compilent avec un JDK 21.

## Téléchargement et licence

Un jar par version de Minecraft, dans les [releases](../../releases).

Not Only World Folders est sous [licence MIT](LICENSE).
