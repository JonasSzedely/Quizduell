package org.Codes.Vorlagen;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * Einfacher Client für die Demonstration einer TCP/IP Kommunikation
 * mit einem Server.
 */
public class Client {

    private static final int PORT = 8765;

    /**
     * Start des Echo Clients.
     *
     * @param args wird aktuell nicht verwendet
     */
    public static void main(String[] args) {

        String host = "127.0.0.1";
        PrintStream console = System.out;

        /*
         * In einem ersten Schritt werden die benötigten Kommunikationsobjekte
         * bereitgestellt. Im Wesentlichen schreiben/lesen wir String's, wie
         * wir dies von der Konsole gewohnt sind.
         */
        try (Socket socket = new Socket(host, PORT);
             BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true);
             Scanner keyboard = new Scanner(System.in)) {

            // Erste Meldung vom Server auf der Konsole ausgeben
            console.println(serverIn.readLine());

            /*
             * In dieser ewigen Schleife kann der Benutzer eine Eingabe auf
             * der Konsole machen, welche anschliessend zum Server weitergeleitet
             * und als Echo an dieser Stelle wieder gezeigt wird.
             */
            while (true) {
                console.print("> ");
                String line = keyboard.nextLine();
                if (line.length() == 0)
                    break;
                serverOut.println(line);
                console.println("Antwort vom Server:");
                console.println(serverIn.readLine());
            }
        } catch (Exception ignored) {
        }
    }
}
