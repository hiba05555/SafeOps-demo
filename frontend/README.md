# Tiko — frontend

Prototype web React/Vite en français pour les parcours client, établissement et fournisseur. Les données sont simulées dans le navigateur : aucun backend, compte réel ou stockage permanent n'est inclus.

## Lancer le projet

Prérequis : Node.js 20.19+ et npm.

```bash
npm install
npm run dev
```

Vite affiche l'adresse locale à ouvrir dans le navigateur. Les autres commandes disponibles :

```bash
npm run typecheck
npm run build
npm run preview
```

## Contenu

- `src/App.tsx` : écrans, routes, données de démonstration et interactions locales.
- `src/tiko.css` et `src/workspace.css` : styles du site, des espaces de gestion et de la console fournisseur.
- `src/index.css` : thème, polices et styles globaux.
- `src/main.tsx` : point d'entrée React.
- `public/favicon.svg` : icône du site.

Les chemins `/login`, `/home`, `/search`, `/tickets`, `/appointments`, `/profile`, `/tenant` et `/provider` sont des routes côté navigateur. En hébergement statique, configurez une redirection de ces routes vers `index.html` pour que le rechargement d'une page fonctionne.

## Préparer le branchement à votre backend

Le prototype ne fait pas d'appels réseau. Les listes et états d'exemple se trouvent dans `src/App.tsx`; les boutons modifient uniquement l'état React en mémoire. Pour le connecter à votre backend :

1. Définissez le contrat de vos API (authentification, établissements, files, tickets, rendez-vous, agents et organisations).
2. Ajoutez un module dédié aux requêtes, par exemple `src/lib/api.ts`, et configurez l'URL du serveur avec une variable publique telle que `VITE_API_BASE_URL`.
3. Remplacez progressivement les données initiales et gestionnaires locaux dans `src/App.tsx` par les appels à ces API, puis affichez les états de chargement et d'erreur renvoyés par le serveur.
4. Faites appliquer côté serveur l'authentification, les rôles et l'isolation par organisation. Le sélecteur de rôle et le changement d'établissement de cette démo ne sont pas des contrôles de sécurité.

L'export n'inclut ni secrets, ni configuration de backend, ni dépendance au monorepo Replit.
