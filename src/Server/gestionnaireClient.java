package Server;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;

public class gestionnaireClient implements Runnable {

    private final Socket client;

    public gestionnaireClient(Socket socket) {
        this.client = socket;
    }

    @Override
    public void run() {
        try {
            ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(client.getInputStream());

            SecretKey cleAESClient = generateKey(128);
            out.writeObject(cleAESClient.getEncoded());
            out.flush();

            boolean connexionFerme = false;

            while (!connexionFerme) {
                Object obj = in.readObject();
                if ((obj instanceof String)) {

                    String message = (String) obj;
                    System.out.println("[Client " + client.getInetAddress() + "] : " + message);

                    out.writeObject("Message reçu !");
                    out.flush();

                    connexionFerme = message.equalsIgnoreCase("bye");
                }
            }

            System.out.println("Client déconnecté : " + client.getInetAddress());
            client.close();

        } catch (Exception e) {
            System.out.println("Erreur avec client : " + e.getMessage());
        }
    }

    private static SecretKey generateKey(int bits) throws NoSuchAlgorithmException {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(bits);
        return kg.generateKey();
    }
}
