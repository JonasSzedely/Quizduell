package org.Codes.Vorlagen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Einfacher Server für die Demonstration einer TCP/IP Kommunikation
 * mit einem Client.
 */
@SuppressWarnings("all")
public class Server extends Thread {

    /**
     * TCP/IP Kommunikationsport.
     */
    private static final int PORT = 8765;

    /**
     * Socket-Objekt für die TCP/IP Kommunikation.
     */
    private final Socket client;

    /**
     * Konstruktor für unseren Echo-Server.
     *
     * @param socket zu vernendendes Socket-Objekt für die TCP/IP Kommunikation.
     */
    public Server(final Socket socket) {
        this.client = socket;
    }

    /**
     * Startet einen Server auf dem konfigurierten Port (8765). In einer
     * ewigen Schleife wartet der Server auf eine Meldung des Client. Sobald
     * sich ein Client meldet wird in einem separaten Thread die Kommunikation
     * mit diesem Client aufgenommen.
     *
     * @param args wird aktuell nicht verwendet
     */
    public static void main(final String[] args) {
        int port = PORT;

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Server auf " + port + " gestartet ...");
            while (true) {
                Socket client = server.accept();
                new Server(client).start();
            }
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    /**
     * Eigentlicher Start des Threads. Diese Methode wird implizit aus der
     * main() aufgerufen.
     */
    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter client = new PrintWriter(this.client.getOutputStream(), true)) {

            client.println("Hallo, ich bin der Server...");

            String input;
            while ((input = in.readLine()) != null) {
                System.out.println("Meldung vom Client: " + input);
                client.println("[" + input + "]");
            }
        } catch (IOException e) {
            System.err.println(e);
        }
    }
}
