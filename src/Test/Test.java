package Test;

import Cryptage.AES;
import javax.crypto.SecretKey;
import java.util.Base64;

/**
 * Tests pour le projet Chat.
 * Teste les fonctionnalites essentielles sans lancer l'interface graphique.
 *
 * @author Chris - Angel
 * @version 1.0
 */
public class Test {

    private static int testsReussis = 0;
    private static int testsTotal = 0;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║          SUITE DE TESTS - CHAT SECURISE AES-128                 ║");
        System.out.println("║              Chris KALOUCHE & Angel BESANCENEZ                   ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Tests AES
        testGenerationCle();
        testChiffrementDechiffrement();
        testChiffrementDifferentsMessages();
        testChiffrementCaracteresSpeciaux();
        testChiffrementMessagesLongs();
        testEncodageBase64();

        // Tests sécurité
        testClesDifferentes();
        testMauvaiseCle();
        testMessageVide();

        // Résultats
        afficherResultats();
    }

    /**
     * Test 1 : Generation de cle AES-128
     */
    private static void testGenerationCle() {
        testsTotal++;
        System.out.println("Test 1 : Génération clé AES-128");

        try {
            SecretKey cle = AES.genererCle(128);

            if (cle != null && cle.getEncoded().length == 16) {
                System.out.println("✓ Clé AES-128 générée (16 bytes)");
                testsReussis++;
            } else {
                System.out.println("✗ ÉCHEC : Taille clé incorrecte");
            }
        } catch (Exception e) {
            System.out.println("✗ ÉCHEC : " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * Test 2 : Chiffrement et dechiffrement basique
     */
    private static void testChiffrementDechiffrement() {
        testsTotal++;
        System.out.println("Test 2 : Chiffrement/Déchiffrement basique");

        try {
            String messageOriginal = "Hello World!";
            SecretKey cle = AES.genererCle(128);

            // Chiffrement
            String messageChiffre = AES.crypteAES(messageOriginal, cle);
            System.out.println("Message original : " + messageOriginal);
            System.out.println("Message chiffré  : " + messageChiffre);

            // Déchiffrement
            String messageDechiffre = AES.decrypteAES(messageChiffre, cle);
            System.out.println("Message déchiffré: " + messageDechiffre);

            if (messageOriginal.equals(messageDechiffre)) {
                System.out.println("✓ Chiffrement/Déchiffrement réussi");
                testsReussis++;
            } else {
                System.out.println("✗ ÉCHEC : Messages différents");
            }
        } catch (Exception e) {
            System.out.println("✗ ÉCHEC : " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * Test 3 : Chiffrement avec differents types de messages
     */
    private static void testChiffrementDifferentsMessages() {
        testsTotal++;
        System.out.println("Test 3 : Différents types de messages");

        String[] messages = {
                "A",
                "Test",
                "Message avec espaces",
                "123456789",
                "MixedCase123",
                "Caractères accentués : éèêàù",
                "Message\navec\nretours\nà\nla\nligne"
        };

        try {
            SecretKey cle = AES.genererCle(128);
            boolean tousReussis = true;

            for (String msg : messages) {
                String chiffre = AES.crypteAES(msg, cle);
                String dechiffre = AES.decrypteAES(chiffre, cle);

                if (!msg.equals(dechiffre)) {
                    System.out.println("✗ ÉCHEC pour : " + msg);
                    tousReussis = false;
                }
            }

            if (tousReussis) {
                System.out.println("✓ " + messages.length + " types de messages testés avec succès");
                testsReussis++;
            }
        } catch (Exception e) {
            System.out.println("✗ ÉCHEC : " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * Test 4 : Chiffrement avec caracteres speciaux
     */
    private static void testChiffrementCaracteresSpeciaux() {
        testsTotal++;
        System.out.println("Test 4 : Caractères spéciaux");

        String message = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

        try {
            SecretKey cle = AES.genererCle(128);
            String chiffre = AES.crypteAES(message, cle);
            String dechiffre = AES.decrypteAES(chiffre, cle);

            if (message.equals(dechiffre)) {
                System.out.println("✓ Caractères spéciaux gérés correctement");
                testsReussis++;
            } else {
                System.out.println("✗ ÉCHEC : Caractères corrompus");
            }
        } catch (Exception e) {
            System.out.println("✗ ÉCHEC : " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * Test 5 : Chiffrement messages longs
     */
    private static void testChiffrementMessagesLongs() {
        testsTotal++;
        System.out.println("Test 5 : Messages longs (500+ caractères)");

        // Générer un message de 500 caractères
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("Message long test ");
        }
        String messageLong = sb.toString();

        try {
            SecretKey cle = AES.genererCle(128);
            String chiffre = AES.crypteAES(messageLong, cle);
            String dechiffre = AES.decrypteAES(chiffre, cle);

            if (messageLong.equals(dechiffre)) {
                System.out.println("✓ Message de " + messageLong.length() + " caractères OK");
                testsReussis++;
            } else {
                System.out.println("✗ ÉCHEC : Message long corrompu");
            }
        } catch (Exception e) {
            System.out.println("✗ ÉCHEC : " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * Test 6 : Encodage Base64
     */
    private static void testEncodageBase64() {
        testsTotal++;
        System.out.println("Test 6 : Encodage Base64");

        try {
            String message = "Test Base64";
            SecretKey cle = AES.genererCle(128);
            String chiffre = AES.crypteAES(message, cle);

            // Vérifier que c'est bien du Base64 valide
            byte[] decoded = Base64.getDecoder().decode(chiffre);

            if (decoded != null && decoded.length > 0) {
                System.out.println("✓ Base64 valide : " + chiffre.substring(0, Math.min(40, chiffre.length())) + "...");
                testsReussis++;
            } else {
                System.out.println("✗ ÉCHEC : Base64 invalide");
            }
        } catch (Exception e) {
            System.out.println("✗ ÉCHEC : " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * Test 7 : Cles differentes donnent chiffres differents
     */
    private static void testClesDifferentes() {
        testsTotal++;
        System.out.println("Test 7 : Clés différentes");

        try {
            String message = "Test sécurité";
            SecretKey cle1 = AES.genererCle(128);
            SecretKey cle2 = AES.genererCle(128);

            String chiffre1 = AES.crypteAES(message, cle1);
            String chiffre2 = AES.crypteAES(message, cle2);

            if (!chiffre1.equals(chiffre2)) {
                System.out.println("✓ Clés différentes → chiffrés différents");
                int len1 = Math.min(30, chiffre1.length());
                int len2 = Math.min(30, chiffre2.length());
                System.out.println("  Chiffré 1 : " + chiffre1.substring(0, len1) + "...");
                System.out.println("  Chiffré 2 : " + chiffre2.substring(0, len2) + "...");
                testsReussis++;
            } else {
                System.out.println("✗ ÉCHEC : Même chiffré avec clés différentes !");
            }
        } catch (Exception e) {
            System.out.println("✗ ÉCHEC : " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * Test 8 : Dechiffrement avec mauvaise cle echoue
     */
    private static void testMauvaiseCle() {
        testsTotal++;
        System.out.println("Test 8 : Déchiffrement avec mauvaise clé");

        try {
            String message = "Message secret";
            SecretKey cle1 = AES.genererCle(128);
            SecretKey cle2 = AES.genererCle(128);

            String chiffre = AES.crypteAES(message, cle1);

            try {
                String dechiffre = AES.decrypteAES(chiffre, cle2);
                System.out.println("✗ ÉCHEC : Déchiffrement avec mauvaise clé devrait échouer");
            } catch (Exception e) {
                System.out.println("✓ Déchiffrement avec mauvaise clé échoue correctement");
                testsReussis++;
            }
        } catch (Exception e) {
            System.out.println("✗ ÉCHEC : " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * Test 9 : Gestion message vide
     */
    private static void testMessageVide() {
        testsTotal++;
        System.out.println("Test 9 : Message vide");

        try {
            String message = "";
            SecretKey cle = AES.genererCle(128);

            String chiffre = AES.crypteAES(message, cle);
            String dechiffre = AES.decrypteAES(chiffre, cle);

            if (message.equals(dechiffre)) {
                System.out.println("✓ Message vide géré correctement");
                testsReussis++;
            } else {
                System.out.println("✗ ÉCHEC : Message vide corrompu");
            }
        } catch (Exception e) {
            System.out.println("✗ ÉCHEC : " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * Affiche les resultats finaux
     */
    private static void afficherResultats() {
        System.out.println("════════════════════════════════════════════════════════════════════");
        System.out.println("                        RÉSULTATS FINAUX");
        System.out.println("════════════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("Tests réussis : " + testsReussis + "/" + testsTotal);

        double pourcentage = (testsReussis * 100.0) / testsTotal;
        System.out.printf("Taux de réussite : %.1f%%\n", pourcentage);
        System.out.println();

        if (testsReussis == testsTotal) {
            System.out.println("🎉 PARFAIT ! Tous les tests sont passés !");
            System.out.println("Le système de chiffrement AES-128 est 100% fonctionnel.");
        } else {
            System.out.println("⚠️  " + (testsTotal - testsReussis) + " test(s) ont échoué.");
            System.out.println("Vérifiez l'implémentation AES.");
        }
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════════");
    }
}