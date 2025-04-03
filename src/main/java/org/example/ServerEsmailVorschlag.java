package org.example;

import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.*;

public class ServerEsmailVorschlag {
    private static final List<String[]> fragenListe = new ArrayList<>();
    private static final List<ClientHandler> clients = new ArrayList<>();
    private static final Map<String, Integer> punkteMap = new ConcurrentHashMap<>(); // spezielle HashMap für die Punkte zuweisung.
    private static final Set<String> beantwortet = ConcurrentHashMap.newKeySet();
    private static final int port = 1404; // Port für den Server
    private static final int poolSize = 2; // Maximale Anzahl an Spielern

    public static void main(String[] args) {
        String dateiPfad = "src/Ordner_Fragen/fragen.txt";
        ladeFragen(dateiPfad);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server läuft auf Port " + port);
            System.out.println("Warten auf Spieler ... ");
            ExecutorService pool = Executors.newFixedThreadPool(poolSize); // interface die Runnable asynchron ausführen kann.

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientName = "Spieler " + (clients.size() + 1);
                    ClientHandler clientHandler = new ClientHandler(clientSocket, clientName);
                    clients.add(clientHandler);
                    pool.execute(clientHandler);
                    informiereSpieler();
                } catch (IOException e) {
                    System.err.println("Fehler beim Akzeptieren einer Verbindung: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Starten des Servers: " + e.getMessage());
        }
    }

    private static void informiereSpieler() {
        System.out.println("Der Erste Spieler heisst: " + clients.get(0).getClientName());
        System.out.println("Der Zweite Spieler heisst: " + (clients.size() > 1 ? clients.get(1).getClientName() : "Noch kein zweiter Spieler"));
    }

    private static void ladeFragen(String dateiPfad) {
        File file = new File(dateiPfad);
        if (!file.exists()) {
            System.err.println("Die Fragen-Datei existiert nicht.");
            return;
        }

        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(dateiPfad))) {
            String zeile;
            String[] aktuelleFrage = new String[6];

            while ((zeile = bufferedReader.readLine()) != null) {
                zeile = zeile.trim();
                if (zeile.isEmpty()) continue;

                if (zeile.startsWith("#")) {
                    aktuelleFrage[0] = zeile.substring(1).trim();
                } else if (zeile.startsWith("*")) {
                    aktuelleFrage[1] = zeile.substring(1).trim();
                } else {
                    processAntwort(zeile, aktuelleFrage);
                }

                if (aktuelleFrage[0] != null && aktuelleFrage[5] != null) {
                    fragenListe.add(aktuelleFrage);
                    aktuelleFrage = new String[6]; // Neue Frage initialisieren
                }
            }

            if (aktuelleFrage[0] != null && aktuelleFrage[5] != null) {
                fragenListe.add(aktuelleFrage);
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Laden der Fragen: " + e.getMessage());
        }
        System.out.println("\nAnzahl geladener Fragen: " + fragenListe.size());
    }

    private static void processAntwort(String zeile, String[] aktuelleFrage) {
        switch (zeile.charAt(0)) {
            case 'A':
                if (zeile.length() > 2) aktuelleFrage[2] = zeile.substring(2).trim();
                break;
            case 'B':
                if (zeile.length() > 2) aktuelleFrage[3] = zeile.substring(2).trim();
                break;
            case 'C':
                if (zeile.length() > 2) aktuelleFrage[4] = zeile.substring(2).trim();
                break;
            case 'r':
                if (zeile.startsWith("richtig: ")) {
                    aktuelleFrage[5] = zeile.substring(9).trim();
                }
                break;
            default:
                System.out.println("Etwas ist mit der Fragen-Struktur nicht in Ordnung. Bitte Fragen-File anpassen!");
                break;
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final String clientName;
        private PrintWriter out;
        private BufferedReader in;

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
                    if (beantwortet.contains(frage[0])) continue;

                    sendFrageAnClient(frage);
                    String antwort = in.readLine();
                    processAntwortDesClients(antwort, frage);
                }

                // Am Ende des Spiels Punktzahlen ermitteln
                int eigenePunkte = punkteMap.getOrDefault(clientName, 0);
                out.println("Das Spiel ist beendet! Ihre Gesamtpunkte beträgt: " + eigenePunkte);

                // Vergleiche die Punktzahlen und bestimme den Sieger
                StringBuilder ergebnis = new StringBuilder();
                String sieger = null;
                int maxPunkte = -1;

                for (Map.Entry<String, Integer> entry : punkteMap.entrySet()) {
                    String spielerName = entry.getKey();
                    int punkte = entry.getValue();
                    ergebnis.append(spielerName).append(": ").append(punkte).append("\n");

                    if (punkte > maxPunkte) {
                        maxPunkte = punkte;
                        sieger = spielerName;
                    }
                }

                // Gebe die Ergebnisse aus
                for (Map.Entry<String, Integer> entry : punkteMap.entrySet()) {
                    String spielerName = entry.getKey();
                    if (spielerName.equals(sieger)) {
                        out.println("Sie haben gewonnen! Ihre Punktzahl: " + eigenePunkte);
                    } else {
                        out.println("Sie haben verloren: " + spielerName + " mit " + entry.getValue() + " Punkten.");
                    }
                }

                // Zeige die Gesamtpunkte aller Spieler
                out.println("Gesamtpunkte aller Spieler:\n" + ergebnis.toString());

            } catch (IOException e) {
                System.err.println("Fehler bei der Kommunikation mit dem Client: " + e.getMessage());
            } finally {
                closeSocket();
            }
        }

        private void sendFrageAnClient(String[] frage) {
            out.println(frage[0]); // Frage Nummer
            out.println(frage[1]); // Die Frage
            out.println("A: " + frage[2]);
            out.println("B: " + frage[3]);
            out.println("C: " + frage[4]);
            System.out.println("Frage gesendet: " + frage[0]);
        }

        private void processAntwortDesClients(String antwort, String[] frage) {
            if (antwort != null) {
                synchronized (punkteMap) {
                    if (antwort.equalsIgnoreCase(frage[5])) {
                        punkteMap.put(clientName, punkteMap.getOrDefault(clientName, 0) + 1);
                        out.println("Richtig! Aktuelle Punkte: " + punkteMap.get(clientName));
                        beantwortet.add(frage[0]);
                    } else {
                        out.println("Falsch! Die richtige Antwort ist: " + frage[5] + ". Aktuelle Punkte: " + punkteMap.getOrDefault(clientName, 0));
                    }

                    informiereTopClient();
                }
            }
        }

        private void informiereTopClient() {
            String topClient = null;
            int maxPunkte = -1;

            for (Map.Entry<String, Integer> entry : punkteMap.entrySet()) {
                if (entry.getValue() > maxPunkte) {
                    maxPunkte = entry.getValue();
                    topClient = entry.getKey();
                }
            }

            if (topClient != null) {
                out.println("Client mit der höchsten Punktzahl: " + topClient + " mit " + maxPunkte + " Punkten.");
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
