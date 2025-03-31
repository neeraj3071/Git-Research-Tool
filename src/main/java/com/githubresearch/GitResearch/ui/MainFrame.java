package com.githubresearch.GitResearch.ui;

import com.githubresearch.GitResearch.DTO.GitHubSearchRequest;
import com.githubresearch.GitResearch.Service.GitHubResearchService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MainFrame extends JFrame {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final GitHubResearchService researchService;
    
    // UI Components
    private JTextField domainsField;
    private JTextField keywordsField;
    private JComboBox<String> commitFilterCombo;
    private JSpinner commitThresholdSpinner;
    private JSpinner minStarsSpinner;
    private JSpinner maxModifiedFilesSpinner;
    private JTextArea resultArea;
    
    public MainFrame(GitHubResearchService researchService) {
        this.researchService = researchService;
        initializeUI();
    }
    
    private void initializeUI() {
        setTitle("GitHub Research Tool");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // Create main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create form panel
        JPanel formPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        
        // Add form components
        formPanel.add(new JLabel("Domains (comma separated):"));
        domainsField = new JTextField();
        formPanel.add(domainsField);
        
        formPanel.add(new JLabel("Keywords (comma separated):"));
        keywordsField = new JTextField();
        formPanel.add(keywordsField);
        
        formPanel.add(new JLabel("Commit Filter:"));
        commitFilterCombo = new JComboBox<>(new String[]{"", "greater", "less"});
        formPanel.add(commitFilterCombo);
        
        formPanel.add(new JLabel("Commit Threshold:"));
        commitThresholdSpinner = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
        formPanel.add(commitThresholdSpinner);
        
        formPanel.add(new JLabel("Minimum Stars:"));
        minStarsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
        formPanel.add(minStarsSpinner);
        
        formPanel.add(new JLabel("Max Modified Files:"));
        maxModifiedFilesSpinner = new JSpinner(new SpinnerNumberModel(10, 1, Integer.MAX_VALUE, 1));
        formPanel.add(maxModifiedFilesSpinner);
        
        // Add buttons
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(this::handleSearch);
        
        JButton fileSearchButton = new JButton("Search From File");
        fileSearchButton.addActionListener(this::handleFileSearch);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.add(searchButton);
        buttonPanel.add(fileSearchButton);
        
        // Result area
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(resultArea);
        
        // Add components to main panel
        mainPanel.add(formPanel, BorderLayout.NORTH);
        mainPanel.add(buttonPanel, BorderLayout.CENTER);
        mainPanel.add(scrollPane, BorderLayout.SOUTH);
        
        add(mainPanel);
    }
    
    private void handleSearch(ActionEvent e) {
        try {
            GitHubSearchRequest request = new GitHubSearchRequest();
            request.setDomains(List.of(domainsField.getText().split(",")));
            request.setKeywords(List.of(keywordsField.getText().split(",")));
            request.setCommitFilter((String) commitFilterCombo.getSelectedItem());
            request.setCommitThreshold((Integer) commitThresholdSpinner.getValue());
            request.setMinStars((Integer) minStarsSpinner.getValue());
            request.setMaxModifiedFiles((Integer) maxModifiedFilesSpinner.getValue());
            
            String result = researchService.processRepositories(
                request.getDomains(),
                request.getKeywords(),
                request.getCommitFilter(),
                request.getCommitThreshold(),
                request.getMinStars(),
                request.getMaxModifiedFiles()
            );
            
            resultArea.setText(result);
        } catch (Exception ex) {
            resultArea.setText("Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    
    private void handleFileSearch(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fileChooser.getSelectedFile();
                List<String> repoUrls = readRepositoriesFromFile(file);
                
                if (repoUrls.isEmpty()) {
                    resultArea.setText("No valid repository URLs found in the file.");
                    return;
                }
                
                String result = researchService.processRepositoriesFromList(
                    repoUrls,
                    List.of(keywordsField.getText().split(",")),
                    (String) commitFilterCombo.getSelectedItem(),
                    (Integer) commitThresholdSpinner.getValue(),
                    (Integer) maxModifiedFilesSpinner.getValue()
                );
                
                resultArea.setText(result);
            } catch (IOException ex) {
                resultArea.setText("Error reading file: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }
    
    private List<String> readRepositoriesFromFile(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
            return lines.stream()
                .filter(l -> l.matches("^(https?://)?github\\.com/.+/.+(\\.git)?$"))
                .map(l -> {
                    if (!l.startsWith("http")) {
                        l = "https://github.com/" + l;
                    }
                    return l.endsWith(".git") ? l : l + ".git";
                })
                .collect(Collectors.toList());
        }
    }
}