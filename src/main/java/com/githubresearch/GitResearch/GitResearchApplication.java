package com.githubresearch.GitResearch;

import com.githubresearch.GitResearch.Service.GitHubResearchService;
import com.githubresearch.GitResearch.config.AppConfig;
import com.githubresearch.GitResearch.ui.MainFrame;

import javax.swing.*;

public class GitResearchApplication {
    public static void main(String[] args) {
        // Initialize services using the configuration
        GitHubResearchService researchService = new GitHubResearchService(
            AppConfig.getGitHubApiToken(),
            AppConfig.getGitHubApiUrl()
        );
        
        // Start Swing UI
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                MainFrame mainFrame = new MainFrame(researchService);
                mainFrame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, 
                    "Failed to start application: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}