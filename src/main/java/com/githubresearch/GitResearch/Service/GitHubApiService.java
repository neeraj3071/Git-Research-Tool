package com.githubresearch.GitResearch.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.logging.Logger;

@Service
public class GitHubApiService {

    @Value("${github.api.url}")
    private String githubApiUrl;

    @Value("${github.api.token}")
    private String githubApiToken;

    private static final Logger LOGGER = Logger.getLogger(GitHubApiService.class.getName());
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;

    public List<String> searchRepositories(List<String> domains, int minStars, String commitFilter, int commitThreshold) {
        RestTemplate restTemplate = new RestTemplate();
        List<String> repoUrls = new ArrayList<>();

        String domainQuery = String.join(" OR ", domains);
        int page = 1;
        boolean hasMorePages = true;

        while (hasMorePages) {
            String url = githubApiUrl + "search/repositories?q=" + domainQuery + "+stars:>=" + minStars + "&page=" + page + "&per_page=100";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + githubApiToken);
            headers.set("Accept", "application/vnd.github.v3+json");

            HttpEntity<String> entity = new HttpEntity<>(headers);
			ResponseEntity<String> response = null;
            boolean requestSuccess = false;
            int retryCount = 0;
            
            // Retry loop for API requests
            while (!requestSuccess && retryCount < MAX_RETRIES) {
                try {
                    response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        requestSuccess = true;
                    } else {
                        LOGGER.warning("Request failed with status: " + response.getStatusCode());
                        retryCount++;
                        Thread.sleep(RETRY_DELAY_MS * retryCount);
                    }
                } catch (Exception e) {
                    LOGGER.warning("Error making API request: " + e.getMessage());
                    retryCount++;
                    try {
                        Thread.sleep(RETRY_DELAY_MS * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            
            if (!requestSuccess) {
                LOGGER.severe("GitHub API Request Failed after " + MAX_RETRIES + " attempts");
                break;
            }

            JSONObject jsonResponse = new JSONObject(response.getBody());
            JSONArray items = jsonResponse.optJSONArray("items");
            if (items == null || items.isEmpty()) {
                hasMorePages = false;
            } else {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject repo = items.getJSONObject(i);
                    String repoUrl = repo.optString("clone_url", null);
                    String repoFullName = repo.optString("full_name", null);
                    int stars = repo.optInt("stargazers_count", 0);

                    if (repoUrl != null && repoFullName != null && stars >= minStars) {
                        int commitCount = getRepositoryCommitCount(repoFullName);
                        LOGGER.info("Repo: " + repoFullName + " | Stars: " + stars + " | Commits: " + commitCount);

                        if (commitFilter != null && commitThreshold > 0) {
                            if ("greater".equalsIgnoreCase(commitFilter) && commitCount <= commitThreshold) {
                                LOGGER.warning("Skipping repo " + repoFullName + " (Commits: " + commitCount + " ≤ Threshold: " + commitThreshold + ")");
                                continue;
                            } else if ("less".equalsIgnoreCase(commitFilter) && commitCount >= commitThreshold) {
                                LOGGER.warning("Skipping repo " + repoFullName + " (Commits: " + commitCount + " ≥ Threshold: " + commitThreshold + ")");
                                continue;
                            }
                        }

                        LOGGER.info("Adding repo: " + repoFullName + " (Commits: " + commitCount + ")");
                        repoUrls.add(repoUrl);
                    }
                }
                
                // Check if we've reached the last page
                String linkHeader = response.getHeaders().getFirst("Link");
                if (linkHeader == null || !linkHeader.contains("rel=\"next\"")) {
                    hasMorePages = false;
                } else {
                    page++;
                }
            }
        }
        return repoUrls;
    }

    private int getRepositoryCommitCount(String repoFullName) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://api.github.com/repos/" + repoFullName + "/commits?per_page=1";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubApiToken);
        headers.set("Accept", "application/vnd.github.v3+json");

        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        int retryCount = 0;
        while (retryCount < MAX_RETRIES) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                if (!response.getStatusCode().is2xxSuccessful()) {
                    LOGGER.warning("Failed to get commit count for " + repoFullName + ": " + response.getStatusCode());
                    retryCount++;
                    Thread.sleep(RETRY_DELAY_MS * retryCount);
                    continue;
                }

                String linkHeader = response.getHeaders().getFirst("Link");
                if (linkHeader != null && linkHeader.contains("rel=\"last\"")) {
                    int lastPageIndex = linkHeader.lastIndexOf("page=");
                    int endIndex = linkHeader.indexOf("&", lastPageIndex);
                    if (endIndex == -1) {
                        endIndex = linkHeader.indexOf(">", lastPageIndex);
                    }
                    if (lastPageIndex != -1 && endIndex != -1) {
                        String lastPageStr = linkHeader.substring(lastPageIndex + 5, endIndex);
                        try {
                            return Integer.parseInt(lastPageStr);
                        } catch (NumberFormatException e) {
                            LOGGER.warning("Failed to parse commit count from link header for " + repoFullName);
                        }
                    }
                } else {
                    JSONArray commits = new JSONArray(response.getBody());
                    return commits.length();
                }
                break;
            } catch (Exception e) {
                LOGGER.warning("Error getting commit count for " + repoFullName + ": " + e.getMessage());
                retryCount++;
                try {
                    Thread.sleep(RETRY_DELAY_MS * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return 0;
    }
}