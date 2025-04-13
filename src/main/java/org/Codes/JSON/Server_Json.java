package org.example;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import com.google.gson.*;

public class Server_Json {
    public static List<Frage> fragenListe = new ArrayList<>();
    private static List<ClientHandler> clients = new ArrayList<>();
    private static final int PORT = 1404;
    private static final int POOL_SIZE = 10;
    private static final long COUNTDOWN_DELAY = 5000;
    private static final int WINNING_POINTS = 5;
    private static final int QUESTION_TIMEOUT = 30000;
    private static boolean GAME_IN_PROGRESS = false;
    private static boolean WAITING_FOR_START = true;

    public static void main(String[] args) {
        ladeFragen("src/Ordner_Fragen/fragen.json");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server läuft auf Port " + PORT);
            ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                pool.execute(clientHandler);
                System.out.println("Neuer Spieler verbunden: " + clientSocket.getRemoteSocketAddress());
                updateClientStatus();
            }
        } catch (IOException e) {
            System.err.println("Serverfehler: " + e.getMessage());
        }
    }

    private static void updateClientStatus() {
        String status;
        if (GAME_IN_PROGRESS) {
            status = "GAME_IN_PROGRESS";
        } else if (clients.size() < 2) {
            status = "WAITING_FOR_PLAYERS|Es werden mindestens 2 Spieler benötigt";
        } else if (clients.size() >= 10) {
            status = "MAX_PLAYERS_REACHED|Maximal 10 Spieler erreicht";
        } else {
            status = "READY_TO_START|Warte auf Start (" + clients.size() + "/10 Spieler)";
        }

        clients.forEach(c -> {
            c.sendMessage("STATUS|" + status);
            c.setCanStartGame(clients.size() >= 2 && clients.size() <= 10 && !GAME_IN_PROGRESS && WAITING_FOR_START);
        });
    }

    private static void ladeFragen(String dateiPfad) {
        try {
            String json = new String(Files.readAllBytes(Paths.get(dateiPfad)));
            Gson gson = new Gson();
            FragenContainer container = gson.fromJson(json, FragenContainer.class);
            fragenListe = container.fragen_json;
            System.out.println("Geladene Fragen: " + fragenListe.size());
        } catch (IOException e) {
            System.err.println("Fehler beim Laden der Fragen: " + e.getMessage());
        }
    }

    public static synchronized void startGame() {
        if (GAME_IN_PROGRESS || clients.size() < 2 || clients.size() > 10) {
            return;
        }

        clients.forEach(c -> c.setActivePlayer(true));
        WAITING_FOR_START = false;
        GAME_IN_PROGRESS = true;
        System.out.println("Spiel startet mit " + clients.size() + " Spielern");
        broadcastMessage("COUNTDOWN_START");

        new Thread(() -> {
            try {
                Thread.sleep(COUNTDOWN_DELAY);
                playGame();
            } catch (InterruptedException | IOException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private static void playGame() throws IOException {
        Collections.shuffle(fragenListe);
        boolean gameRunning = true;

        for (Frage frage : fragenListe) {
            if (!gameRunning) break;

            List<ClientHandler> activePlayers = getActivePlayers();
            if (activePlayers.isEmpty()) {
                broadcastMessage("GAME_OVER|Keine aktiven Spieler mehr");
                break;
            }

            System.out.println("Aktive Spieler: " + activePlayers.size());
            broadcastQuestion(frage);

            List<String> answers = new ArrayList<>();
            boolean correctAnswerGiven = false;
            long questionStartTime = System.currentTimeMillis();

            while (answers.size() < activePlayers.size() &&
                    System.currentTimeMillis() - questionStartTime < QUESTION_TIMEOUT) {

                for (ClientHandler client : activePlayers) {
                    if (!answers.contains(client.getCurrentAnswer())) {
                        if (client.hasAnswerAvailable()) {
                            String answer = client.getAnswer();
                            System.out.println("Antwort von " + client + ": " + answer);
                            answers.add(answer);

                            if (answer.equalsIgnoreCase(frage.richtig)) {
                                client.incrementPoints();
                                correctAnswerGiven = true;

                                if (client.getPoints() >= WINNING_POINTS) {
                                    announceWinner(client);
                                    gameRunning = false;
                                    break;
                                }
                            }
                        }
                    }
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (answers.size() < activePlayers.size()) {
                System.out.println("Timeout - Es fehlen " +
                        (activePlayers.size() - answers.size()) + " Antworten");

                activePlayers.stream()
                        .filter(c -> !answers.contains(c.getCurrentAnswer()))
                        .forEach(c -> {
                            c.setActivePlayer(false);
                            System.out.println("Deaktiviere Spieler: " + c);
                        });
            }

            if (gameRunning) {
                broadcastResults(answers, frage.richtig, correctAnswerGiven);
                broadcastMessage("NEXT_QUESTION_COUNTDOWN");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (gameRunning) {
            broadcastMessage("GAME_OVER|Kein Gewinner - alle Fragen beantwortet");
        }
        resetGame();
    }

    private static List<ClientHandler> getActivePlayers() {
        return clients.stream()
                .filter(ClientHandler::isActivePlayer)
                .collect(Collectors.toList());
    }

    private static void broadcastMessage(String message) {
        clients.forEach(c -> c.sendMessage(message));
    }

    private static void broadcastQuestion(Frage frage) {
        clients.forEach(c -> c.sendQuestion(frage));
    }

    private static void broadcastResults(List<String> answers, String correctAnswer, boolean correctAnswerGiven) {
        String message = "RESULTS|" + String.join(",", answers) + "|" +
                correctAnswer + "|" + correctAnswerGiven;
        broadcastMessage(message);
    }

    private static void announceWinner(ClientHandler winner) {
        clients.forEach(c -> {
            if (c == winner) {
                c.sendMessage("WINNER|Du hast gewonnen mit " + winner.getPoints() + " Punkten!");
            } else {
                c.sendMessage("LOST|Der Gewinner hat " + winner.getPoints() + " Punkte erreicht!");
            }
        });
    }

    private static void resetGame() {
        GAME_IN_PROGRESS = false;
        WAITING_FOR_START = true;
        clients.forEach(c -> {
            c.resetPoints();
            c.setActivePlayer(false);
        });
        updateClientStatus();
    }

    private static class FragenContainer {
        List<Frage> fragen_json;
    }

    public static class Frage {
        public int id;
        public String frage;
        public Antworten antworten;
        public String richtig;
    }

    public static class Antworten {
        public String A;
        public String B;
        public String C;
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private int points = 0;
        private String currentAnswer;
        private boolean canStartGame = false;
        private boolean isActivePlayer = false;
        private boolean answerAvailable = false;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        @Override
        public void run() {
            try {
                String input;
                while ((input = in.readLine()) != null) {
                    System.out.println("Empfangen von " + socket.getRemoteSocketAddress() + ": " + input);

                    if (input.equals("START") && canStartGame) {
                        startGame();
                    } else if (input.matches("[A-Ca-c]")) {
                        currentAnswer = input.toUpperCase();
                        answerAvailable = true;
                        System.out.println("Antwort gespeichert für " + this + ": " + currentAnswer);
                    }
                }
            } catch (IOException e) {
                System.err.println("Client-Verbindungsfehler: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Fehler beim Schließen: " + e.getMessage());
                }
                clients.remove(this);
                System.out.println("Client getrennt: " + socket.getRemoteSocketAddress());
                updateClientStatus();
            }
        }

        public void sendMessage(String message) {
            out.println(message);
            out.flush();
        }

        public void sendQuestion(Frage frage) {
            sendMessage("QUESTION|" + frage.frage + "|" +
                    frage.antworten.A + "|" +
                    frage.antworten.B + "|" +
                    frage.antworten.C);
        }

        public boolean hasAnswerAvailable() {
            return answerAvailable;
        }

        public String getAnswer() {
            answerAvailable = false;
            return currentAnswer;
        }

        public String getCurrentAnswer() {
            return currentAnswer;
        }

        public synchronized void incrementPoints() {
            points++;
            sendMessage("POINTS|" + points);
        }

        public synchronized int getPoints() {
            return points;
        }

        public synchronized void resetPoints() {
            points = 0;
            sendMessage("POINTS|0");
        }

        public void setCanStartGame(boolean canStart) {
            this.canStartGame = canStart;
            sendMessage("CAN_START|" + canStart);
        }

        public void setActivePlayer(boolean active) {
            this.isActivePlayer = active;
            if (!active) {
                sendMessage("INACTIVE|Du bist jetzt inaktiv");
            }
        }

        public boolean isActivePlayer() {
            return isActivePlayer;
        }

        @Override
        public String toString() {
            return "Client[" + socket.getRemoteSocketAddress() + "]";
        }
    }
}