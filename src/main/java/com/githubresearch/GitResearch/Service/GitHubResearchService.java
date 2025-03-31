//package com.githubresearch.GitResearch.Service;
//
//import com.githubresearch.GitResearch.Util.ExcelGenerator;
//import com.githubresearch.GitResearch.Util.ExcelGenerator.CommitData;
//import org.eclipse.jgit.api.Git;
//import org.eclipse.jgit.api.errors.GitAPIException;
//import org.eclipse.jgit.diff.DiffEntry;
//import org.eclipse.jgit.lib.ObjectId;
//import org.eclipse.jgit.lib.ObjectReader;
//import org.eclipse.jgit.lib.Repository;
//import org.eclipse.jgit.revwalk.RevCommit;
//import org.eclipse.jgit.transport.CredentialsProvider;
//import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
//import org.eclipse.jgit.treewalk.CanonicalTreeParser;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.*;
//import java.util.concurrent.*;
//import java.util.stream.Collectors;
//import java.util.stream.StreamSupport;
//
//public class GitHubResearchService {
//    private static final Logger logger = LogManager.getLogger(GitHubResearchService.class);
//
//    private final String githubApiToken;
//    private final GitHubApiService gitHubApiService;
//    private static final int THREAD_POOL_SIZE = 4;
//    private static final int MAX_RETRIES = 3;
//    private static final int RETRY_DELAY_MS = 2000;
//    private static final boolean USE_BARE_REPOS = true;
//
//    public GitHubResearchService(String githubApiToken, String githubApiUrl) {
//        if (githubApiToken == null || githubApiToken.isBlank()) {
//            throw new IllegalArgumentException("GitHub API token cannot be null or empty");
//        }
//        if (githubApiUrl == null || githubApiUrl.isBlank()) {
//            throw new IllegalArgumentException("GitHub API URL cannot be null or empty");
//        }
//        
//        this.githubApiToken = githubApiToken;
//        this.gitHubApiService = new GitHubApiService(githubApiUrl, githubApiToken);
//    }
//    
//    public String processRepositories(List<String> domains, List<String> keywords, String commitFilter, int commitThreshold, int minStars, int maxModifiedFiles) {
//        List<String> repoUrls = gitHubApiService.searchRepositories(domains, minStars, commitFilter, commitThreshold);
//        return processRepositoriesFromList(repoUrls, keywords, commitFilter, commitThreshold, maxModifiedFiles);
//    }
//
//    public String processRepositoriesFromList(
//        List<String> repoUrls, 
//        List<String> keywords, 
//        String commitFilter, 
//        int commitThreshold, 
//        int maxModifiedFiles
//    ) {
//        if (repoUrls == null || repoUrls.isEmpty()) {
//            logger.warn("No repositories provided in the list.");
//            return "No repositories found.";
//        }
//
//        logger.info("Processing {} repositories from list using multithreading...", repoUrls.size());
//
//        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
//        List<Future<List<CommitData>>> futures = new ArrayList<>();
//
//        for (String repoUrl : repoUrls) {
//            futures.add(executor.submit(() -> fetchCommitsFromRepo(repoUrl, keywords, maxModifiedFiles)));
//        }
//
//        executor.shutdown();
//
//        List<CommitData> commitDataList = new ArrayList<>();
//
//        for (Future<List<CommitData>> future : futures) {
//            try {
//                commitDataList.addAll(future.get());
//            } catch (InterruptedException | ExecutionException e) {
//                logger.error("Error fetching commits: {}", e.getMessage(), e);
//            }
//        }
//
//        if (commitDataList.isEmpty()) {
//            logger.warn("No relevant commits found in the repositories.");
//            return "No relevant commits found.";
//        }
//
//        try {
//            ExcelGenerator.generateExcel(commitDataList);
//            logger.info("Excel file generated successfully with {} commits from {} repositories.", commitDataList.size(), repoUrls.size());
//            return "Excel file generated successfully.";
//        } catch (IOException e) {
//            logger.error("Error generating Excel file: {}", e.getMessage(), e);
//            return "Error generating Excel file.";
//        }
//    }
//
//    // Rest of the existing methods remain the same as in your original implementation
//    private List<CommitData> fetchCommitsFromRepo(String repoUrl, List<String> keywords, int maxModifiedFiles) {
//        List<CommitData> commitDataList = new ArrayList<>();
//        int retryCount = 0;
//        
//        // Sanitize repo name for file path
//        String repoName = sanitizeFilePath(repoUrl.substring(repoUrl.lastIndexOf("/") + 1, repoUrl.lastIndexOf(".git")));
//        File localPath = new File("tempRepos/" + repoName);
//        
//        while (retryCount < MAX_RETRIES) {
//            Git git = null;
//            try {
//                // Check if repository already exists
//                if (isValidGitRepository(localPath)) {
//                    logger.info("Repository already exists locally: {}", localPath.getAbsolutePath());
//                    try {
//                        // Open existing repository
//                        git = Git.open(localPath);
//                        
//                        // Pull latest changes for non-bare repositories only
//                        if (!USE_BARE_REPOS) {
//                            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(githubApiToken, "");
//                            git.pull()
//                                .setCredentialsProvider(credentialsProvider)
//                                .setTimeout(120)
//                                .call();
//                            logger.info("Successfully pulled latest changes for repository: {}", repoUrl);
//                        } else {
//                            // For bare repositories, we can fetch instead of pull
//                            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(githubApiToken, "");
//                            git.fetch()
//                                .setCredentialsProvider(credentialsProvider)
//                                .setTimeout(120)
//                                .call();
//                            logger.info("Successfully fetched latest changes for bare repository: {}", repoUrl);
//                        }
//                    } catch (Exception e) {
//                        logger.warn("Error opening or updating existing repository: {}. Will attempt to re-clone.", e.getMessage());
//                        if (git != null) {
//                            git.close();
//                        }
//                        git = null;
//                        
//                        // Clean up corrupted repository
//                        deleteDirectory(localPath);
//                    }
//                }
//                
//                // If repository doesn't exist or couldn't be opened, clone it
//                if (git == null) {
//                    logger.info("Repository doesn't exist locally or could not be opened. Cloning: {}", repoUrl);
//                    
//                    // Ensure directory exists
//                    localPath.getParentFile().mkdirs();
//                    
//                    // Clean up any partial directory that might exist
//                    if (localPath.exists()) {
//                        deleteDirectory(localPath);
//                    }
//                    
//                    // Set credentials for authentication
//                    CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(githubApiToken, "");
//                    
//                    git = Git.cloneRepository()
//                            .setURI(repoUrl)
//                            .setDirectory(localPath)
//                            .setCredentialsProvider(credentialsProvider)
//                            .setBare(USE_BARE_REPOS)  // Use the constant to determine if we want bare repos
//                            .setTimeout(120) // Increase timeout to 2 minutes
//                            .call();
//                    
//                    logger.info("Cloned repository: {} into {} (Bare: {})", repoUrl, localPath.getAbsolutePath(), USE_BARE_REPOS);
//                }
//
//                // Process commits
//                Iterable<RevCommit> commits = git.log().call();
//
//                // Convert Iterable<RevCommit> to List<RevCommit>
//                List<RevCommit> commitList = StreamSupport.stream(commits.spliterator(), false)
//                        .collect(Collectors.toList());
//
//                // Create a final reference to git for use in the lambda
//                final Git finalGit = git;
//
//                // Filtering commits based on keywords and modified files condition
//                commitDataList = commitList.stream()
//                    .filter(commit -> {
//                        try {
//                            return containsKeyword(commit, keywords) && !hasExcessiveModifiedFiles(finalGit, commit, maxModifiedFiles);
//                        } catch (GitAPIException | IOException e) {
//                            logger.error("Error checking modified files for commit {}: {}", commit.getName(), e.getMessage());
//                            return false; // Skip this commit if there's an error
//                        }
//                    })
//                    .map(commit -> new CommitData(
//                            repoUrl.replace(".git", "") + "/commit/" + commit.getName(),
//                            commit.getName(),
//                            commit.getAuthorIdent().getName(),
//                            commit.getAuthorIdent().getWhen().toString(),
//                            commit.getFullMessage()))
//                    .collect(Collectors.toList());
//
//                logger.info("Repository: {} - Total commits scanned: {}, Matched commits: {}", 
//                        repoUrl, commitList.size(), commitDataList.size());
//                
//                // Operation successful
//                break;
//            } catch (Exception e) {
//                retryCount++;
//                logger.error("Error while processing repository: {}. Attempt {}/{}. Error: {}", 
//                        repoUrl, retryCount, MAX_RETRIES, e.getMessage());
//                
//                if (retryCount >= MAX_RETRIES) {
//                    logger.error("Failed to process repository after {} attempts: {}", MAX_RETRIES, repoUrl);
//                } else {
//                    // Wait before retrying
//                    try {
//                        Thread.sleep(RETRY_DELAY_MS * retryCount); // Increasing backoff
//                    } catch (InterruptedException ie) {
//                        Thread.currentThread().interrupt();
//                    }
//                }
//            } finally {
//                // Always close the Git repository to free resources
//                if (git != null) {
//                    git.close();
//                }
//            }
//        }
//
//        return commitDataList;
//    }
//    
//    /**
//     * Checks if the given directory is a valid Git repository (works for both bare and non-bare repos)
//     */
//    private boolean isValidGitRepository(File directory) {
//        if (!directory.exists() || !directory.isDirectory()) {
//            return false;
//        }
//        
//        // Determine if this might be a bare repo (has objects and refs directories)
//        boolean mightBeBare = new File(directory, "objects").exists() && 
//                              new File(directory, "refs").exists() &&
//                              new File(directory, "HEAD").exists();
//                              
//        // Or check if it's a non-bare repo (has .git directory)
//        boolean mightBeNonBare = new File(directory, ".git").isDirectory();
//        
//        if (!mightBeBare && !mightBeNonBare) {
//            return false;
//        }
//        
//        // Try to open the repository to validate it
//        try {
//            Repository repo = Git.open(directory).getRepository();
//            boolean isBare = repo.isBare();
//            
//            // If we're using bare repos but this is non-bare, or vice versa, we'll want to re-clone
//            if (USE_BARE_REPOS != isBare) {
//                logger.info("Repository format mismatch. Found: {}, Expected: {}. Will re-clone.",
//                            isBare ? "bare" : "non-bare", 
//                            USE_BARE_REPOS ? "bare" : "non-bare");
//                return false;
//            }
//            
//            return true;
//        } catch (Exception e) {
//            logger.warn("Directory exists but is not a valid Git repository: {}, Error: {}", 
//                    directory.getAbsolutePath(), e.getMessage());
//            return false;
//        }
//    }
//
//    private boolean containsKeyword(RevCommit commit, List<String> keywords) {
//        String message = commit.getFullMessage().toLowerCase();
//        return keywords.stream().anyMatch(keyword -> message.contains(keyword.toLowerCase()));
//    }
//
//    private boolean hasExcessiveModifiedFiles(Git git, RevCommit commit, int maxModifiedFiles) throws GitAPIException, IOException {
//        // Handle case where commit has no parents (initial commit)
//        if (commit.getParentCount() == 0) {
//            return false;
//        }
//        
//        // Get the previous commit (parent commit)
//        RevCommit parentCommit = commit.getParent(0);
//
//        try {
//            // Get the trees for both the current and previous commit
//            ObjectId oldTree = parentCommit.getTree().getId();
//            ObjectId newTree = commit.getTree().getId();
//
//            // Get the list of modified files between the two commits
//            List<DiffEntry> diffEntries = git.diff()
//                .setOldTree(getTreeIterator(git, oldTree))
//                .setNewTree(getTreeIterator(git, newTree))
//                .call();
//
//            // Return true if the number of modified files exceeds the max limit
//            return diffEntries.size() > maxModifiedFiles;
//        } catch (IOException e) {
//            logger.error("Error comparing trees for commit {}: {}", commit.getName(), e.getMessage());
//            throw e;
//        }
//    }
//
//    private CanonicalTreeParser getTreeIterator(Git git, ObjectId objectId) throws IOException {
//        try (ObjectReader reader = git.getRepository().newObjectReader()) {
//            CanonicalTreeParser treeParser = new CanonicalTreeParser();
//            treeParser.reset(reader, objectId);
//            return treeParser;
//        } catch (IOException e) {
//            logger.error("Error reading tree for commit {}", objectId.getName(), e);
//            throw new IOException("Error reading tree for commit", e);
//        }
//    }
//
//
//    private String sanitizeFilePath(String filePath) {
//        return filePath.replaceAll("[^a-zA-Z0-9._-]", "_");
//    }
//
//    private void deleteDirectory(File directory) {
//        if (directory.exists()) {
//            File[] files = directory.listFiles();
//            if (files != null) {
//                for (File file : files) {
//                    if (file.isDirectory()) {
//                        deleteDirectory(file);
//                    } else {
//                        file.delete();
//                    }
//                }
//            }
//            directory.delete();
//        }
//    }
//}


package com.githubresearch.GitResearch.Service;

import com.githubresearch.GitResearch.Util.ExcelGenerator;
import com.githubresearch.GitResearch.Util.ExcelGenerator.CommitData;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class GitHubResearchService {
    private static final java.util.logging.Logger LOGGER = 
        java.util.logging.Logger.getLogger(GitHubResearchService.class.getName());

    private final String githubApiToken;
    private final GitHubApiService gitHubApiService;
    private static final int THREAD_POOL_SIZE = 4;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;
    private static final boolean USE_BARE_REPOS = true;

    static {
        // Configure console logging
        System.setProperty("java.util.logging.SimpleFormatter.format", 
            "[%1$tF %1$tT] [%4$-7s] %5$s %n");
    }

    public GitHubResearchService(String githubApiToken, String githubApiUrl) {
        if (githubApiToken == null || githubApiToken.isBlank()) {
            LOGGER.severe("GitHub API token cannot be null or empty");
            throw new IllegalArgumentException("GitHub API token cannot be null or empty");
        }
        if (githubApiUrl == null || githubApiUrl.isBlank()) {
            LOGGER.severe("GitHub API URL cannot be null or empty");
            throw new IllegalArgumentException("GitHub API URL cannot be null or empty");
        }
        
        this.githubApiToken = githubApiToken;
        this.gitHubApiService = new GitHubApiService(githubApiUrl, githubApiToken);
        LOGGER.info("Initialized GitHubResearchService with API URL: " + githubApiUrl);
    }
    
    public String processRepositories(List<String> domains, List<String> keywords, String commitFilter, int commitThreshold, int minStars, int maxModifiedFiles) {
        LOGGER.info("Starting repository search with domains: " + domains + 
                   ", minStars: " + minStars + ", commitThreshold: " + commitThreshold);
        
        List<String> repoUrls = gitHubApiService.searchRepositories(domains, minStars, commitFilter, commitThreshold);
        return processRepositoriesFromList(repoUrls, keywords, commitFilter, commitThreshold, maxModifiedFiles);
    }

    public String processRepositoriesFromList(
        List<String> repoUrls, 
        List<String> keywords, 
        String commitFilter, 
        int commitThreshold, 
        int maxModifiedFiles
    ) {
        if (repoUrls == null || repoUrls.isEmpty()) {
            LOGGER.warning("No repositories provided in the list");
            return "No repositories found.";
        }

        LOGGER.info("Processing " + repoUrls.size() + " repositories using " + THREAD_POOL_SIZE + " threads");

        // Create temp directory if needed
        try {
            File tempDir = new File("tempRepos");
            if (!tempDir.exists()) {
                Files.createDirectories(tempDir.toPath());
                LOGGER.fine("Created temp directory: " + tempDir.getAbsolutePath());
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to create temp directory: " + e.getMessage());
            return "Failed to create temp directory";
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<List<CommitData>>> futures = new ArrayList<>();

        for (String repoUrl : repoUrls) {
            futures.add(executor.submit(() -> {
                LOGGER.fine("Processing repository: " + repoUrl);
                return fetchCommitsFromRepo(repoUrl, keywords, maxModifiedFiles);
            }));
        }

        executor.shutdown();

        List<CommitData> commitDataList = new ArrayList<>();
        int processedCount = 0;

        for (Future<List<CommitData>> future : futures) {
            try {
                List<CommitData> commits = future.get();
                commitDataList.addAll(commits);
                processedCount++;
                LOGGER.fine("Processed repository " + processedCount + "/" + repoUrls.size());
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.severe("Error fetching commits: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        if (commitDataList.isEmpty()) {
            LOGGER.warning("No relevant commits found in " + repoUrls.size() + " repositories");
            return "No relevant commits found.";
        }

        try {
            File excelFile = ExcelGenerator.generateExcel(commitDataList);
            String resultMessage = "Successfully generated Excel file: " + excelFile.getAbsolutePath() + 
                                 " with " + commitDataList.size() + " commits from " + 
                                 repoUrls.size() + " repositories";
            LOGGER.info(resultMessage);
            return resultMessage;
        } catch (IOException e) {
            LOGGER.severe("Error generating Excel: " + e.getMessage());
            return "Error generating Excel file: " + e.getMessage();
        }
    }

    private List<CommitData> fetchCommitsFromRepo(String repoUrl, List<String> keywords, int maxModifiedFiles) {
        LOGGER.fine("Fetching commits from repository: " + repoUrl);
        List<CommitData> commitDataList = new ArrayList<>();
        int retryCount = 0;
        
        String repoName = sanitizeFilePath(repoUrl.substring(repoUrl.lastIndexOf("/") + 1, repoUrl.lastIndexOf(".git")));
        File localPath = new File("tempRepos/" + repoName);
        LOGGER.fine("Local repo path: " + localPath.getAbsolutePath());
        
        while (retryCount < MAX_RETRIES) {
            Git git = null;
            try {
                if (isValidGitRepository(localPath)) {
                    LOGGER.fine("Found existing repository at: " + localPath);
                    git = Git.open(localPath);
                    
                    if (!USE_BARE_REPOS) {
                        LOGGER.fine("Pulling updates for: " + repoUrl);
                        CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(githubApiToken, "");
                        git.pull()
                           .setCredentialsProvider(credentialsProvider)
                           .setTimeout(120)
                           .call();
                        LOGGER.info("Updated repository: " + repoUrl);
                    } else {
                        LOGGER.fine("Fetching updates for bare repo: " + repoUrl);
                        CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(githubApiToken, "");
                        git.fetch()
                           .setCredentialsProvider(credentialsProvider)
                           .setTimeout(120)
                           .call();
                        LOGGER.info("Fetched updates for bare repo: " + repoUrl);
                    }
                } else {
                    LOGGER.fine("Cloning new repository: " + repoUrl);
                    if (localPath.exists()) {
                        LOGGER.fine("Cleaning directory: " + localPath);
                        deleteDirectory(localPath);
                    }
                    
                    localPath.getParentFile().mkdirs();
                    CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(githubApiToken, "");
                    
                    git = Git.cloneRepository()
                           .setURI(repoUrl)
                           .setDirectory(localPath)
                           .setCredentialsProvider(credentialsProvider)
                           .setBare(USE_BARE_REPOS)
                           .setTimeout(120)
                           .call();
                    LOGGER.info("Cloned repository: " + repoUrl + " to " + localPath);
                }

                // Process commits
                LOGGER.fine("Reading commit history for: " + repoUrl);
                Iterable<RevCommit> commits = git.log().call();
                List<RevCommit> commitList = StreamSupport.stream(commits.spliterator(), false)
                        .collect(Collectors.toList());
                LOGGER.fine("Found " + commitList.size() + " commits in " + repoUrl);

                final Git finalGit = git;

                commitDataList = commitList.stream()
                    .filter(commit -> {
                        try {
                            boolean hasKeywords = containsKeyword(commit, keywords);
                            boolean withinFileLimit = !hasExcessiveModifiedFiles(finalGit, commit, maxModifiedFiles);
                            
                            if (hasKeywords && !withinFileLimit) {
                                LOGGER.fine("Excluding commit " + commit.getName() + " - too many modified files");
                            }
                            
                            return hasKeywords && withinFileLimit;
                        } catch (GitAPIException | IOException e) {
                            LOGGER.warning("Error checking commit " + commit.getName() + ": " + e.getMessage());
                            return false;
                        }
                    })
                    .map(commit -> {
                        LOGGER.finer("Including commit: " + commit.getName() + " - " + commit.getShortMessage());
                        return new CommitData(
                            repoUrl.replace(".git", "") + "/commit/" + commit.getName(),
                            commit.getName(),
                            commit.getAuthorIdent().getName(),
                            commit.getAuthorIdent().getWhen().toString(),
                            commit.getFullMessage());
                    })
                    .collect(Collectors.toList());

                LOGGER.info(repoUrl + ": " + commitDataList.size() + " matching commits out of " + commitList.size());
                
                break;
            } catch (Exception e) {
                retryCount++;
                LOGGER.warning("Error processing " + repoUrl + " (attempt " + retryCount + "/" + MAX_RETRIES + "): " + e.getMessage());
                
                if (retryCount >= MAX_RETRIES) {
                    LOGGER.severe("Failed to process " + repoUrl + " after " + MAX_RETRIES + " attempts");
                } else {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                if (git != null) {
                    git.close();
                }
            }
        }

        return commitDataList;
    }
    
    private boolean isValidGitRepository(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return false;
        }
        
        boolean mightBeBare = new File(directory, "objects").exists() && 
                              new File(directory, "refs").exists() &&
                              new File(directory, "HEAD").exists();
                              
        boolean mightBeNonBare = new File(directory, ".git").isDirectory();
        
        if (!mightBeBare && !mightBeNonBare) {
            return false;
        }
        
        try {
            Repository repo = Git.open(directory).getRepository();
            boolean isBare = repo.isBare();
            
            if (USE_BARE_REPOS != isBare) {
                LOGGER.fine("Repository format mismatch. Found: " + (isBare ? "bare" : "non-bare") + 
                           ", Expected: " + (USE_BARE_REPOS ? "bare" : "non-bare"));
                return false;
            }
            
            return true;
        } catch (Exception e) {
            LOGGER.warning("Invalid Git repository at " + directory + ": " + e.getMessage());
            return false;
        }
    }

    private boolean containsKeyword(RevCommit commit, List<String> keywords) {
        String message = commit.getFullMessage().toLowerCase();
        return keywords.stream().anyMatch(keyword -> message.contains(keyword.toLowerCase()));
    }

    private boolean hasExcessiveModifiedFiles(Git git, RevCommit commit, int maxModifiedFiles) throws GitAPIException, IOException {
        if (commit.getParentCount() == 0) {
            return false;
        }
        
        RevCommit parentCommit = commit.getParent(0);
        ObjectId oldTree = parentCommit.getTree().getId();
        ObjectId newTree = commit.getTree().getId();

        List<DiffEntry> diffEntries = git.diff()
            .setOldTree(getTreeIterator(git, oldTree))
            .setNewTree(getTreeIterator(git, newTree))
            .call();

        return diffEntries.size() > maxModifiedFiles;
    }

    private CanonicalTreeParser getTreeIterator(Git git, ObjectId objectId) throws IOException {
        try (ObjectReader reader = git.getRepository().newObjectReader()) {
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            treeParser.reset(reader, objectId);
            return treeParser;
        }
    }

    private String sanitizeFilePath(String filePath) {
        return filePath.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}