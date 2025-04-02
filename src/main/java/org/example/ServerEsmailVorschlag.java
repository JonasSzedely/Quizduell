package org.example;

import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.*;

public class ServerEsmailVorschlag {
    public static List<String[]> fragenListe = new ArrayList<>();
    private static List<ClientHandler> clients = new ArrayList<>();
    private static final Map <String, Integer> punkteMap = new ConcurrentHashMap<>();
    private static Set<String> beantwortet = ConcurrentHashMap.newKeySet();
    private static int port = 1404; // Iranische Kalender Jahr als Port-Schlüssel gemerkt. :-). ---> nicht vorreserviert in bekannte Netwerkdiensten.
    private static int poolsize = 2; // maximale Spieler die spielen dürfen, wird hier gesetzt!

    public static void main(String[] args) {
        String dateiPfad = "src/Ordner_Fragen/fragen.txt";
        ladeFragen(dateiPfad);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server läuft auf Port " + port);
            System.out.println("Warrten auf Spieler ... ");
            ExecutorService pool = Executors.newFixedThreadPool(poolsize); // Thread-Pool für mehrere Clients (asynchrone callable/Runable)

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientName = "Spieler " + (clients.size() + 1);
                    ClientHandler clientHandler = new ClientHandler(clientSocket,clientName);
                    clients.add(clientHandler);
                    pool.execute(clientHandler);
                    System.out.println("Der Erste Spieler ist: " + clients.get(0).getClientName());
                    System.out.println("Der Zweite Spieler ist: " + (clients.size() > 1 ? clients.get(1).getClientName() : "Noch kein zweiter Spieler"));
                    System.out.println("Der Zweite Spieler ist: " + (clients.size() > 2 ? clients.get(2).getClientName() : "Noch kein dritter Spieler "));
                } catch (IOException e) {
                    System.err.println("Fehler beim Akzeptieren einer Verbindung: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Starten des Servers: " + e.getMessage());
        }
    }

    private static void ladeFragen(String dateiPfad) {
        File file = new File(dateiPfad);
        System.out.println("Datei existiert? " + (file.exists()));

        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(dateiPfad))) {
            String zeile;
            String[] aktuelleFrage = new String[6]; // Es sind 6 Elemente pro Frage
            while ((zeile = bufferedReader.readLine()) != null) {
                zeile = zeile.trim();

                if (zeile.isEmpty()) {
                    continue; // Leere Zeilen überspringen
                }

                // Überprüfen, ob die Zeile eine Frage-Nummer ist
                if (zeile.startsWith("#")) {
                    aktuelleFrage[0] = zeile.substring(1).trim();
                    continue;
                }

                // Überprüfen, ob die Zeile mit * beginnt (Die Frage selbst)
                if (zeile.startsWith("*")) {
                    aktuelleFrage[1] = zeile.substring(1).trim(); // Frage speichern
                    continue; // Nächste Zeile lesen
                }

                // Antworten und richtige Antwort verarbeiten
                switch (zeile.charAt(0)) {
                    case 'A':
                        if (zeile.length() > 2) {
                            aktuelleFrage[2] = zeile.substring(2).trim();
                        }
                        break;
                    case 'B':
                        if (zeile.length() > 2) {
                            aktuelleFrage[3] = zeile.substring(2).trim();
                        }
                        break;
                    case 'C':
                        if (zeile.length() > 2) {
                            aktuelleFrage[4] = zeile.substring(2).trim();
                        }
                        break;
                    case 'r':
                        if (zeile.startsWith("richtig: ")) {
                            aktuelleFrage[5] = zeile.substring(9).trim();
                        }
                        break;
                    default:
                        // Sollte nicht passieren, da wir nur mit den oben genannten Fällen arbeiten
                        System.out.println("Etwas ist mit der Fragen-Struktur nicht in Ordnung. Bitte Fragen File anpassen!");
                        break;
                }

                // Überprüfen, ob die Frage vollständig ist
                if (aktuelleFrage[0] != null && aktuelleFrage[5] != null) {
                    fragenListe.add(aktuelleFrage);
                    aktuelleFrage = new String[6]; // Neue Frage initialisieren
                }
            }
            // Letzte Frage hinzufügen, wenn sie vollständig ist
            if (aktuelleFrage[0] != null && aktuelleFrage[5] != null) {
                fragenListe.add(aktuelleFrage);
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Laden der Fragen: " + e.getMessage());
        }
        System.out.println("\nAnzahl geladener Fragen: " + fragenListe.size());
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        final private String clientName;
        private PrintWriter out;
        private BufferedReader in;
        private int punkte; // Variable für Punkte des Clients

        public String getClientName() {
            return clientName;
        }

        public ClientHandler(Socket socket, String name) {
            this.socket = socket;
            this.clientName = name;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                System.err.println("Fehler beim Einrichten der Streams: " + e.getMessage());
                closeSocket();
            }
        }

        @Override
        public void run() {
            try {
                for (String[] frage : fragenListe) {
                    out.println(frage[0]); // Frage Nummer
                    out.println(frage[1]); // Die Frage
                    out.println("A: " + frage[2]);
                    out.println("B: " + frage[3]);
                    out.println("C: " + frage[4]);

                    // Debugging-Ausgabe
                    System.out.println("Frage gesendet: " + frage[0]);
                    System.out.println("Frage gesendet: " + frage[1]);
                    System.out.println("Antworten gesendet: A: " + frage[2] + ", B: " + frage[3] + ", C: " + frage[4]);

                    String antwort = in.readLine(); // Antwort vom Client lesen
                    System.out.println("Antwort ist gespeichert in der Variablen antwort und heißt: " + antwort);
                    if (antwort != null) {
                        // Validierung der Antwort
                        System.out.println("Antwort empfangen: " + antwort); // Debugging-Ausgabe
                        synchronized (punkteMap) {
                            if (antwort.equalsIgnoreCase(frage[4]) && !beantwortet.contains(frage[0]) && !beantwortet.contains(frage[1])) {
                                punkteMap.put(clientName, punkteMap.getOrDefault(clientName, 0) + 1); // Punkte erhöhen
                                out.println("Richtig! Aktuelle Punkte: " + punkteMap.get(clientName));
                                beantwortet.add(frage[0]); // Markiere die Frage als beantwortet
                                beantwortet.add(frage[1]); // die Frage ist bereit beantwortet
                            } else if (beantwortet.contains(frage[0]) && beantwortet.contains(frage[1])) {
                                out.println("Diese Frage wurde bereits beantwortet.");
                            } else {
                                out.println("Falsch! Die richtige Antwort ist: " + frage[4] + ". Aktuelle Punkte: " + punkteMap.getOrDefault(clientName, 0));
                            }
                        }
                    }
                }
                out.println("Das Spiel ist beendet! Ihre Gesamtpunkte: " + punkteMap.getOrDefault(clientName, 0)); // Ergebnisse am Ende anzeigen
            } catch (IOException e) {
                System.err.println("Fehler bei der Kommunikation mit dem Client: " + e.getMessage());
            } finally {
                closeSocket();
            }
        }
        private void closeSocket() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("Fehler beim Schließen des Sockets: " + e.getMessage());
            }
        }
    }
}
