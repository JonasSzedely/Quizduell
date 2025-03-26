package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class Quiz_Fenster {
    private Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
    private JLabel frage;
    private JButton[] ant = new JButton[4];
    private JFrame w1;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private List<QuizQuestion> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;

    public static void main(String[] args) {
        new Quiz_Fenster();
    }

    public Quiz_Fenster() {
        w1 = new JFrame("Quiz");
        w1.setSize(400, 400);
        w1.setLocation((int) (dim.getWidth() / 2 - 200), (int) (dim.getHeight() / 2 - 200));
        w1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        w1.setLayout(null);

        // Frage-Label initialisieren
        frage = new JLabel("Hier steht die Frage");
        frage.setBounds(52, 30, 300, 40);
        w1.add(frage);


        // Buttons initialisieren und zum JFrame hinzufügen
        for (int i = 0; i < 4; i++) {
            ant[i] = new JButton("Antwort " + (char) ('A' + i));
            ant[i].setBounds(52 + (i % 2) * 168, 90 + (i / 2) * 70, 120, 40);
            final int index = i; // für die ActionListener
            ant[i].addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    sendAnswer(String.valueOf((char) ('A' + index)));
                }
            });
            w1.add(ant[i]);
        }

        w1.setVisible(true);
        connectToServer();
        loadQuestions();
        displayQuestion();
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 12345); // Server-Adresse und Port
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(w1, "Could not connect to server.");
            System.exit(1);
        }
    }

    private void loadQuestions() {
        try {
            // Fragen vom Server laden
            out.println("LOAD_QUESTIONS");
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals("END")) break; // Ende der Fragen
                String questionText = line;
                String[] options = new String[4];
                for (int i = 0; i < 4; i++) {
                    line = in.readLine();
                    options[i] = line;
                }
                questions.add(new QuizQuestion(questionText, options));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void displayQuestion() {
        if (currentQuestionIndex < questions.size()) {
            QuizQuestion question = questions.get(currentQuestionIndex);
            frage.setText(question.getQuestion());
            String[] options = question.getOptions();
            for (int i = 0; i < ant.length; i++) {
                ant[i].setText(options[i]);
            }
        } else {
            JOptionPane.showMessageDialog(w1, "Das Quiz ist beendet!");
            System.exit(0);
        }
    }

    private void sendAnswer(String answer) {
        out.println(answer);
        currentQuestionIndex++;
        displayQuestion();
    }
}

class QuizQuestion {
    private String question;
    private String[] options;

    public QuizQuestion(String question, String[] options) {
        this.question = question;
        this.options = options;
    }

    public String getQuestion() {
        return question;
    }

    public String[] getOptions() {
        return options;
    }
}

