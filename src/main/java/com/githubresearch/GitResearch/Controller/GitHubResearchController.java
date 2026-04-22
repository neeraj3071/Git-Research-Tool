package com.githubresearch.GitResearch.Controller;
import java.util.List;

import com.githubresearch.GitResearch.DTO.GitHubSearchRequest;
import com.githubresearch.GitResearch.Service.GitHubResearchService;

public class GitHubResearchController {

    private GitHubResearchService gitHubResearchService;

    public GitHubResearchController(GitHubResearchService gitHubResearchService) {
        this.gitHubResearchService = gitHubResearchService;
    }

    public String searchRepositories(GitHubSearchRequest request) {
        List<String> domains = request.getDomains(); 
        List<String> keywords = request.getKeywords();
        String commitFilter = request.getCommitFilter(); 
        int commitThreshold = request.getCommitThreshold(); 
        int minStars = request.getMinStars(); 
        int maxModifiedFiles = request.getMaxModifiedFiles(); 

        String result = gitHubResearchService.processRepositories(domains, keywords, commitFilter, commitThreshold, minStars, maxModifiedFiles);
   
        return result;
    }
}

