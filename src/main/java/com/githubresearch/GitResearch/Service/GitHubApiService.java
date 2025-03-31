package com.githubresearch.GitResearch.Service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class GitHubApiService {
    private final String githubApiUrl;
    private final String githubApiBaseUrl;
    private final String githubApiToken;
    private static final Logger LOGGER = Logger.getLogger(GitHubApiService.class.getName());
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private final HttpClient httpClient;

    public GitHubApiService(String githubApiUrl, String githubApiToken) {
        this.githubApiUrl = githubApiUrl;
        // Extract the base URL for other API endpoints (without search/repositories?q=)
        this.githubApiBaseUrl = "https://api.github.com/";
        this.githubApiToken = githubApiToken;
        this.httpClient = HttpClient.newHttpClient();
    }

    public List<String> searchRepositories(List<String> domains, int minStars, String commitFilter, int commitThreshold) {
        List<String> repoUrls = new ArrayList<>();
        String domainQuery = String.join(" OR ", domains);

        // URL-encode the domainQuery to handle special characters
        String encodedDomainQuery = URLEncoder.encode(domainQuery, StandardCharsets.UTF_8);
        
        // Properly encode the stars filter
        String starsFilter = URLEncoder.encode("stars:>=" + minStars, StandardCharsets.UTF_8);

        int page = 1;
        boolean hasMorePages = true;

        while (hasMorePages) {
            // Since githubApiUrl already contains "search/repositories?q=", just append our parameters
            String url = githubApiUrl + encodedDomainQuery + "+" + starsFilter + "&page=" + page + "&per_page=100";

            // Log the constructed URL for debugging purposes
            LOGGER.info("Constructed URL: " + url);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + githubApiToken)
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET()
                    .build();

                boolean requestSuccess = false;
                int retryCount = 0;
                HttpResponse<String> response = null;

                // Retry logic in case of failure
                while (!requestSuccess && retryCount < MAX_RETRIES) {
                    try {
                        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body() != null) {
                            requestSuccess = true;
                        } else {
                            LOGGER.warning("Request failed with status: " + response.statusCode());
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

                // Parse the JSON response
                JSONObject jsonResponse = new JSONObject(response.body());
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

                    String linkHeader = response.headers().firstValue("Link").orElse(null);
                    if (linkHeader == null || !linkHeader.contains("rel=\"next\"")) {
                        hasMorePages = false;
                    } else {
                        page++;
                    }
                }
            } catch (Exception e) {
                LOGGER.severe("Error constructing or sending request: " + e.getMessage());
                e.printStackTrace();
                break;
            }
        }
        return repoUrls;
    }

    private int getRepositoryCommitCount(String repoFullName) {
        // Use the base URL for the commits endpoint, not the search URL
        String url = githubApiBaseUrl + "repos/" + repoFullName + "/commits?per_page=1";
        int retryCount = 0;

        while (retryCount < MAX_RETRIES) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + githubApiToken)
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    LOGGER.warning("Failed to get commit count for " + repoFullName + ": " + response.statusCode());
                    retryCount++;
                    Thread.sleep(RETRY_DELAY_MS * retryCount);
                    continue;
                }

                String linkHeader = response.headers().firstValue("Link").orElse(null);
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
                    JSONArray commits = new JSONArray(response.body());
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