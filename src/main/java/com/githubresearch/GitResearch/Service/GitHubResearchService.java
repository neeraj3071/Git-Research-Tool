package com.githubresearch.GitResearch.Service;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.githubresearch.GitResearch.Util.ExcelGenerator;
import com.githubresearch.GitResearch.Util.ExcelGenerator.CommitData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class GitHubResearchService {

    private static final Logger logger = LogManager.getLogger(GitHubResearchService.class);

    @Value("${github.api.token}")
    private String githubApiToken;

    @Autowired
    private GitHubApiService gitHubApiService;

    private static final int THREAD_POOL_SIZE = 4;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;
    private static final boolean USE_BARE_REPOS = true; // Set to true to use bare repositories

    public String processRepositories(List<String> domains, List<String> keywords, String commitFilter, int commitThreshold, int minStars, int maxModifiedFiles) {
        List<String> repoUrls = gitHubApiService.searchRepositories(domains, minStars, commitFilter, commitThreshold);

        if (repoUrls.isEmpty()) {
            logger.warn("No repositories found matching the criteria.");
            return "No repositories found.";
        }

        logger.info("Processing {} repositories using multithreading...", repoUrls.size());

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<List<CommitData>>> futures = new ArrayList<>();

        for (String repoUrl : repoUrls) {
            futures.add(executor.submit(() -> fetchCommitsFromRepo(repoUrl, keywords, maxModifiedFiles)));
        }

        executor.shutdown();

        List<CommitData> commitDataList = new ArrayList<>();

        for (Future<List<CommitData>> future : futures) {
            try {
                commitDataList.addAll(future.get());
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error fetching commits: {}", e.getMessage(), e);
            }
        }

        if (commitDataList.isEmpty()) {
            logger.warn("No relevant commits found in the repositories.");
            return "No relevant commits found.";
        }

        try {
            ExcelGenerator.generateExcel(commitDataList);
            logger.info("Excel file generated successfully with {} commits from {} repositories.", commitDataList.size(), repoUrls.size());
            return "Excel file generated successfully.";
        } catch (IOException e) {
            logger.error("Error generating Excel file: {}", e.getMessage(), e);
            return "Error generating Excel file.";
        }
    }

    private List<CommitData> fetchCommitsFromRepo(String repoUrl, List<String> keywords, int maxModifiedFiles) {
        List<CommitData> commitDataList = new ArrayList<>();
        int retryCount = 0;
        
        // Sanitize repo name for file path
        String repoName = sanitizeFilePath(repoUrl.substring(repoUrl.lastIndexOf("/") + 1, repoUrl.lastIndexOf(".git")));
        File localPath = new File("tempRepos/" + repoName);
        
        while (retryCount < MAX_RETRIES) {
            Git git = null;
            try {
                // Check if repository already exists
                if (isValidGitRepository(localPath)) {
                    logger.info("Repository already exists locally: {}", localPath.getAbsolutePath());
                    try {
                        // Open existing repository
                        git = Git.open(localPath);
                        
                        // Pull latest changes for non-bare repositories only
                        if (!USE_BARE_REPOS) {
                            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(githubApiToken, "");
                            git.pull()
                                .setCredentialsProvider(credentialsProvider)
                                .setTimeout(120)
                                .call();
                            logger.info("Successfully pulled latest changes for repository: {}", repoUrl);
                        } else {
                            // For bare repositories, we can fetch instead of pull
                            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(githubApiToken, "");
                            git.fetch()
                                .setCredentialsProvider(credentialsProvider)
                                .setTimeout(120)
                                .call();
                            logger.info("Successfully fetched latest changes for bare repository: {}", repoUrl);
                        }
                    } catch (Exception e) {
                        logger.warn("Error opening or updating existing repository: {}. Will attempt to re-clone.", e.getMessage());
                        if (git != null) {
                            git.close();
                        }
                        git = null;
                        
                        // Clean up corrupted repository
                        deleteDirectory(localPath);
                    }
                }
                
                // If repository doesn't exist or couldn't be opened, clone it
                if (git == null) {
                    logger.info("Repository doesn't exist locally or could not be opened. Cloning: {}", repoUrl);
                    
                    // Ensure directory exists
                    localPath.getParentFile().mkdirs();
                    
                    // Clean up any partial directory that might exist
                    if (localPath.exists()) {
                        deleteDirectory(localPath);
                    }
                    
                    // Set credentials for authentication
                    CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(githubApiToken, "");
                    
                    git = Git.cloneRepository()
                            .setURI(repoUrl)
                            .setDirectory(localPath)
                            .setCredentialsProvider(credentialsProvider)
                            .setBare(USE_BARE_REPOS)  // Use the constant to determine if we want bare repos
                            .setTimeout(120) // Increase timeout to 2 minutes
                            .call();
                    
                    logger.info("Cloned repository: {} into {} (Bare: {})", repoUrl, localPath.getAbsolutePath(), USE_BARE_REPOS);
                }

                // Process commits
                Iterable<RevCommit> commits = git.log().call();

                // Convert Iterable<RevCommit> to List<RevCommit>
                List<RevCommit> commitList = StreamSupport.stream(commits.spliterator(), false)
                        .collect(Collectors.toList());

                // Create a final reference to git for use in the lambda
                final Git finalGit = git;

                // Filtering commits based on keywords and modified files condition
                commitDataList = commitList.stream()
                    .filter(commit -> {
                        try {
                            return containsKeyword(commit, keywords) && !hasExcessiveModifiedFiles(finalGit, commit, maxModifiedFiles);
                        } catch (GitAPIException | IOException e) {
                            logger.error("Error checking modified files for commit {}: {}", commit.getName(), e.getMessage());
                            return false; // Skip this commit if there's an error
                        }
                    })
                    .map(commit -> new CommitData(
                            repoUrl.replace(".git", "") + "/commit/" + commit.getName(),
                            commit.getName(),
                            commit.getAuthorIdent().getName(),
                            commit.getAuthorIdent().getWhen().toString(),
                            commit.getFullMessage()))
                    .collect(Collectors.toList());

                logger.info("Repository: {} - Total commits scanned: {}, Matched commits: {}", 
                        repoUrl, commitList.size(), commitDataList.size());
                
                // Operation successful
                break;
            } catch (Exception e) {
                retryCount++;
                logger.error("Error while processing repository: {}. Attempt {}/{}. Error: {}", 
                        repoUrl, retryCount, MAX_RETRIES, e.getMessage());
                
                if (retryCount >= MAX_RETRIES) {
                    logger.error("Failed to process repository after {} attempts: {}", MAX_RETRIES, repoUrl);
                } else {
                    // Wait before retrying
                    try {
                        Thread.sleep(RETRY_DELAY_MS * retryCount); // Increasing backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                // Always close the Git repository to free resources
                if (git != null) {
                    git.close();
                }
            }
        }

        return commitDataList;
    }
    
    /**
     * Checks if the given directory is a valid Git repository (works for both bare and non-bare repos)
     */
    private boolean isValidGitRepository(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return false;
        }
        
        // Determine if this might be a bare repo (has objects and refs directories)
        boolean mightBeBare = new File(directory, "objects").exists() && 
                              new File(directory, "refs").exists() &&
                              new File(directory, "HEAD").exists();
                              
        // Or check if it's a non-bare repo (has .git directory)
        boolean mightBeNonBare = new File(directory, ".git").isDirectory();
        
        if (!mightBeBare && !mightBeNonBare) {
            return false;
        }
        
        // Try to open the repository to validate it
        try {
            Repository repo = Git.open(directory).getRepository();
            boolean isBare = repo.isBare();
            
            // If we're using bare repos but this is non-bare, or vice versa, we'll want to re-clone
            if (USE_BARE_REPOS != isBare) {
                logger.info("Repository format mismatch. Found: {}, Expected: {}. Will re-clone.",
                            isBare ? "bare" : "non-bare", 
                            USE_BARE_REPOS ? "bare" : "non-bare");
                return false;
            }
            
            return true;
        } catch (Exception e) {
            logger.warn("Directory exists but is not a valid Git repository: {}, Error: {}", 
                    directory.getAbsolutePath(), e.getMessage());
            return false;
        }
    }

    private boolean containsKeyword(RevCommit commit, List<String> keywords) {
        String message = commit.getFullMessage().toLowerCase();
        return keywords.stream().anyMatch(keyword -> message.contains(keyword.toLowerCase()));
    }

    private boolean hasExcessiveModifiedFiles(Git git, RevCommit commit, int maxModifiedFiles) throws GitAPIException, IOException {
        // Handle case where commit has no parents (initial commit)
        if (commit.getParentCount() == 0) {
            return false;
        }
        
        // Get the previous commit (parent commit)
        RevCommit parentCommit = commit.getParent(0);

        try {
            // Get the trees for both the current and previous commit
            ObjectId oldTree = parentCommit.getTree().getId();
            ObjectId newTree = commit.getTree().getId();

            // Get the list of modified files between the two commits
            List<DiffEntry> diffEntries = git.diff()
                .setOldTree(getTreeIterator(git, oldTree))
                .setNewTree(getTreeIterator(git, newTree))
                .call();

            // Return true if the number of modified files exceeds the max limit
            return diffEntries.size() > maxModifiedFiles;
        } catch (IOException e) {
            logger.error("Error comparing trees for commit {}: {}", commit.getName(), e.getMessage());
            throw e;
        }
    }

    private CanonicalTreeParser getTreeIterator(Git git, ObjectId objectId) throws IOException {
        try (ObjectReader reader = git.getRepository().newObjectReader()) {
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            treeParser.reset(reader, objectId);
            return treeParser;
        } catch (IOException e) {
            logger.error("Error reading tree for commit {}", objectId.getName(), e);
            throw new IOException("Error reading tree for commit", e);
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