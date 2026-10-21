# Atelier TV

Un accueil Android TV qui laisse la place aux œuvres.

Deux thèmes, **Atelier** et **CleanFrame**, 100 tableaux, une veille artistique, des citations, la météo et un mode musique Spotify. Interface en français, pensée pour la télécommande.

### Atelier

![Accueil Atelier](docs/screenshots/atelier.jpg)

### CleanFrame

![Accueil CleanFrame](docs/screenshots/cleanframe.jpg)

<details>
<summary>Voir les autres écrans</summary>

### Liste CleanFrame

![Liste CleanFrame](docs/screenshots/liste.jpg)

### Cartel de l’œuvre

![Cartel intégré](docs/screenshots/cartel.jpg)

### Galerie

![Galerie de tableaux](docs/screenshots/galerie.jpg)

### Applications

![Applications](docs/screenshots/applications.jpg)

### Centre de contrôle

![Centre de contrôle](docs/screenshots/controle.jpg)

### Focus musique

![Focus musique Spotify](docs/screenshots/musique.jpg)

### Veille

![Veille artistique](docs/screenshots/veille.jpg)

</details>

### Compiler

Version **0.17.3** · Java natif, sans Gradle. Préparation prévue pour macOS Apple Silicon :

```sh
python3 scripts/setup_toolchain.py
./build.sh
```

APK généré dans `dist/`. Testé sur Philips 65PUS8007/12, Android TV 14. Spotify nécessite l’accès aux notifications ; AirPlay dépend d’un récepteur installé sur la TV.

Inspiré de [CleanFrame](https://github.com/nthnclrk/CleanFrame). [Crédits et licences](THIRD_PARTY_NOTICES.md) · sources des œuvres dans [le catalogue](app/src/main/assets/art/catalog.json).
