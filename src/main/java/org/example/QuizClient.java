package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class QuizClient {
    private Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
    private JLabel frage;
    private JButton[] ant = new JButton[3]; // A, B, C
    private JButton startButton; // Start-Button
    private JFrame w1;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private int port = 1404; // Port initialisieren
    private boolean quizStarted = false;

    public static void main(String[] args) {
        new QuizClient();
    }

    public QuizClient() {
        w1 = new JFrame("QuizDuell, Wer ist der Beste!!!");
        w1.setSize(800, 600); // Größe des Fensters erhöhen
        w1.setLocation((int) (dim.getWidth() / 2 - 400), (int) (dim.getHeight() / 2 - 300));
        w1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        w1.setLayout(new BorderLayout()); // Verwende BorderLayout

        // Frage-Label initialisieren
        frage = new JLabel("Hier steht die Frage", SwingConstants.CENTER);
        frage.setFont(new Font("Arial", Font.BOLD, 24)); // Schriftgröße erhöhen
        w1.add(frage, BorderLayout.NORTH); // Frage oben platzieren

        // Panel für die Antwort-Buttons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(4, 1)); // 4 Zeilen, 1 Spalte

        // Buttons initialisieren und zum Panel hinzufügen
        for (int i = 0; i < 3; i++) { // Nur 3 Antworten (A, B, C)
            ant[i] = new JButton("Antwort: " + (char) ('A' + i)); // Setze Button-Text auf "Antwort: A", "Antwort: B", "Antwort: C"
            final int index = i; // für die ActionListener
            ant[i].addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String answer = String.valueOf((char) ('A' + index)); // Die gewählte Antwort
                    sendAnswer(answer); // Sende die gewählte Antwort an den Server
                    // Deaktiviere die Buttons, während auf die Antwort gewartet wird
                    for (JButton button : ant) {
                        button.setEnabled(false);
                    }
                }
            });
            ant[i].setEnabled(false); // Zuerst deaktivieren
            buttonPanel.add(ant[i]); // Füge Button zum Panel hinzu
        }

        // Start-Button initialisieren
        startButton = new JButton("Starten");
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startQuiz();
            }
        });
        buttonPanel.add(startButton); // Füge Start-Button zum Panel hinzu

        w1.add(buttonPanel, BorderLayout.CENTER); // Füge das Button-Panel zum JFrame hinzu

        w1.setVisible(true);
        connectToServer();
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", port); // Server-Adresse und Port
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(w1, "Verbindung zum Server konnte nicht hergestellt werden. Bitte sicherstellen, dass der Server läuft und der Port korrekt ist.");
        }
    }

    private void startQuiz() {
        quizStarted = true;
        startButton.setEnabled(false); // Deaktiviere den Start-Button
        for (JButton button : ant) {
            button.setEnabled(true); // Aktiviere die Antwort-Buttons
        }
        displayQuestions(); // Beginne die Anzeige der Fragen
    }

    private void displayQuestions() {
        new Thread(() -> { // Erstelle einen neuen Thread für die Fragenanzeige
            try {
                String line;
                while (quizStarted && (line = in.readLine()) != null) {
                    // Setze die Frage
                    frage.setText(line); // Setze die Frage
                    System.out.println("Frage empfangen: " + line); // Debugging-Ausgabe

                    String[] antworten = new String[3]; // Array für die Antworten

                    for (int i = 0; i < 3; i++) { // Lese die Antworten
                        line = in.readLine(); // Lese die Antworten
                        if (line == null) {
                            JOptionPane.showMessageDialog(w1, "Fehler beim Empfangen der Antworten. Bitte überprüfen Sie den Server.");
                            return;
                        }
                        antworten[i] = line; // Speichere die Antwort in der Variablen
                        System.out.println("Antwort empfangen: " + antworten[i]); // Debugging-Ausgabe
                        ant[i].setText("Antwort: " + antworten[i]);
                    }

                    // Lese die richtige Antwort (vierte Zeile)
                    String richtigeAntwort = in.readLine(); // Lese die richtige Antwort
                    System.out.println("Richtige Antwort empfangen: " + richtigeAntwort); // Debugging-Ausgabe

                    // Warte auf die Rückmeldung des Servers (Richtig/Falsch)
                    String responseMessage = in.readLine(); // Lese die Antwort vom Server (Richtig/Falsch)

                    // Zeige die Rückmeldung an und warte auf die Benutzeraktion
                    JOptionPane.showMessageDialog(w1, responseMessage); // Zeige die Rückmeldung an

                    // Aktiviere die Buttons nach der Rückmeldung
                    for (JButton button : ant) {
                        button.setEnabled(true);
                    }
                }

                // Wenn keine Fragen mehr vorhanden sind
                JOptionPane.showMessageDialog(w1, "Bitte warten, Ihre Ergebnisse werden ausgewertet..."); // Nachricht anzeigen
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void processResponse(String responseMessage) {
        // Zeige die Rückmeldung an
        JOptionPane.showMessageDialog(w1, responseMessage); // Zeige die Rückmeldung an

        // Aktiviere die Buttons nach der Rückmeldung
        for (JButton button : ant) {
            button.setEnabled(true);
        }
    }

    private void sendAnswer(String answer) {
        out.println(answer); // Sende die Antwort an den Server
        // Warte auf die Bestätigung des Servers, dass die Antwort empfangen wurde
        new Thread(() -> {
            try {
                String responseMessage = in.readLine(); // Lese die Rückmeldung vom Server
                JOptionPane.showMessageDialog(w1, responseMessage); // Zeige die Rückmeldung an
                // Aktiviere die Buttons nach Erhalt der Rückmeldung
                for (JButton button : ant) {
                    button.setEnabled(true);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
