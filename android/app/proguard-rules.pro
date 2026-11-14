# Règles R8 de Mes Médocs. Room, Compose, CameraX et ZXing fournissent leurs propres règles.

# Garder les numéros de ligne lisibles dans les rapports d'erreur (le fichier de correspondance mapping.txt les retrouve)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Guava n'est là que pour l'interface ListenableFuture de CameraX : ses annotations optionnelles sont absentes à l'exécution
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
