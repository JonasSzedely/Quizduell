/*package org.example;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Server {
    public static List<String[]> fragenListe = new ArrayList<>();
    private static List<ClientHandler> clients = new ArrayList<>();
    private static int port = 1404; // nicht vorreserviert in bekannte Netwerkdiensten.
    private static int poolsize = 10; // maximale Spieler wird hier auf 10 gesetzt!

    public static void main(String[] args) {
        String dateiPfad = "src/Ordner_Fragen/fragen.txt";
        ladeFragen(dateiPfad);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server läuft auf Port " + port);
            System.out.println("Warten auf Spieler ... ");
            ExecutorService pool = Executors.newFixedThreadPool(poolsize); // Thread-Pool für mehrere Clients

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    clients.add(clientHandler);
                    pool.execute(clientHandler);
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
        System.out.println("Datei existiert? " + file.exists());

        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(dateiPfad))) {
            String zeile;
            String[] aktuelleFrage = new String[5]; // Immer Größe 5 verwenden

            while ((zeile = bufferedReader.readLine()) != null) {
                zeile = zeile.trim();

                if (zeile.isEmpty()) {
                    continue; // Leere Zeilen überspringen
                }

                if (zeile.startsWith("#")) {
                    // Neue Frage beginnt - vorherige speichern falls vorhanden
                    if (aktuelleFrage[0] != null && aktuelleFrage[4] != null) {
                        fragenListe.add(aktuelleFrage);
                    }
                    aktuelleFrage = new String[5]; // Neue Frage initialisieren
                } else {
                    // Hier wird jetzt sichergestellt, dass die Zeile nicht leer ist
                    switch (zeile.charAt(0)) {
                        case 'A':
                            if (zeile.length() > 2) {
                                aktuelleFrage[1] = zeile.substring(2).trim();
                            }
                            break;
                        case 'B':
                            if (zeile.length() > 2) {
                                aktuelleFrage[2] = zeile.substring(2).trim();
                            }
                            break;
                        case 'C':
                            if (zeile.length() > 2) {
                                aktuelleFrage[3] = zeile.substring(2).trim();
                            }
                            break;
                        case 'r':
                            if (zeile.startsWith("richtig: ")) {
                                aktuelleFrage[4] = zeile.substring(9).trim();
                            }
                            break;
                        default:
                            aktuelleFrage[0] = zeile; // Fragentext (nur wenn nicht leer)
                            break;
                    }
                }
            }
            // Letzte Frage hinzufügen
            if (aktuelleFrage[0] != null && aktuelleFrage[4] != null) {
                fragenListe.add(aktuelleFrage);
            }
        } catch (IOException e) {
            System.err.println("Fehler beim Laden der Fragen: " + e.getMessage());
        }

        System.out.println("\nAnzahl geladener Fragen: " + fragenListe.size());
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                socket.setSoTimeout(20000); // 20 Sekunden Timeout für Leseoperationen
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                System.err.println("Fehler beim Einrichten der Streams: " + e.getMessage());
                closeSocket();
            }
        }

        private int punkte = 0;

        @Override
        public void run() {
            try {
                boolean ersteFrage = true;
                for (String[] frage : fragenListe) {
                    out.println(frage[0]); // Frage
                    out.println("A: " + frage[1]);
                    out.println("B: " + frage[2]);
                    out.println("C: " + frage[3]);
                    if (ersteFrage) {
                        out.println("Bitte geben Sie Ihre Antwort (A, B, C) ein:");
                        ersteFrage = false;
                    }

                    // Debugging-Ausgabe
                    System.out.println("Frage gesendet: " + frage[0]);
                    System.out.println("Antworten gesendet: A: " + frage[1] + ", B: " + frage[2] + ", C: " + frage[3]);

                    String antwort = in.readLine(); // Antwort vom Client lesen
                    System.out.println("Antwort ist gespeichert in variabel antwort und heisst: " + antwort);
                    if (antwort != null) {
                        // Validierung der Antwort
                        System.out.println("Antwort empfangen: " + antwort); // Debugging-Ausgabe
                        if (antwort.equalsIgnoreCase(frage[4])) {
                            punkte++; // Punkte erhöhen
                            out.println("Richtig! Aktuelle Punkte: " + punkte);
                        } else if (antwort.equalsIgnoreCase("A") || antwort.equalsIgnoreCase("B") || antwort.equalsIgnoreCase("C")) {
                            out.println("Falsch! Die richtige Antwort ist: " + frage[4] + ". Aktuelle Punkte: " + punkte);
                        } else {
                            out.println("Ungültige Eingabe! Bitte geben Sie A, B oder C ein.");
                        }
                    }
                }

                out.println("Spiel beendet! Ihre Gesamtpunkte: " + punkte); // Ergebnisse am Ende anzeigen
            } catch (SocketTimeoutException e) {
                System.err.println("Timeout beim Warten auf eine Antwort vom Client: " + e.getMessage());
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
}*/
