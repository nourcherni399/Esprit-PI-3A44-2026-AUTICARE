Connexion MySQL (base « pidb »)
================================

MyDatabase (org.example.utils) est un singleton vers :
  jdbc:mysql://localhost:3306/pidb
  utilisateur : root
  mot de passe : (vide)

Ajustez url / user / password dans MyDatabase.java selon votre installation.

Import du dump : créer la base pidb puis importer votre fichier .sql.

Les comptes sont dans la table « user » (rôles ROLE_* Symfony).

Note : produits, RDV, modules, etc. utilisent encore les noms de tables du vieux schéma SQLite (produits, rendez_vous…). Si ces tables n’existent pas dans MySQL sous ces noms, il faudra les créer ou adapter les services.
