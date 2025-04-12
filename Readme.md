# 🔍 Git Research Tool

A robust Java-based research tool for analyzing GitHub repositories based on domain-specific keywords, commit-level filtering, and repository metadata. Designed for academic and research use, particularly in areas like Virtual Reality (VR), Augmented Reality (AR), Extended Reality (XR), and other emerging software fields.

---

## 📌 Features

- **Domain-Based Search:** Fetch repositories from GitHub by specifying domains like `VR`, `AR`, `XR`, or any custom keyword.
- **Commit-Level Filtering:** Analyze repositories based on:
  - Commit message keywords
  - Number of commits
  - File modifications
- **Preloaded Repository List Support:** Accepts a `.txt` file of GitHub repo links to skip the search phase.
- **Commit Metadata Extraction:** Extracts commit ID, message, modified file count, and timestamps.
- **Excel Report Generation:** Outputs detailed commit reports in `.xlsx` format with:
  - Repository links
  - Commit links (clickable)
  - Filtered data fields

---

## 📂 Project Structure

```
Git-Research-Tool/
├── src/
│   ├── main/
│   │   └── java/com/githubresearch/
│   │       ├── GitResearchToolApplication.java
│   │       ├── Controller/     # JavaFX Controllers (UI)
│   │       ├── Service/        # Core logic to fetch & process repos
│   │       └── Util/           # Excel export, helper utilities
├── input/
│   └── repositories.txt        # Optional input: list of GitHub repo links
├── output/
│   └── research_results.xlsx   # Final Excel report
├── logs/
│   └── debug.log               # Log outputs (optional)
├── README.md
└── pom.xml                     # Maven dependencies
```

---

## 🚀 Getting Started

### ✅ Prerequisites

- Java 17+
- Maven
- GitHub Personal Access Token (set as env variable `GITHUB_TOKEN`)

### 🔧 Setup & Run

1. **Clone the Repo:**
   ```bash
   git clone https://github.com/neeraj3071/Git-Research-Tool.git
   cd Git-Research-Tool
   ```

2. **Build the Project:**
   ```bash
   mvn clean install
   ```

3. **Run the App:**
   ```bash
   mvn exec:java -Dexec.mainClass="com.githubresearch.GitResearchToolApplication"
   ```

> Alternatively, run it from your IDE (IntelliJ / Eclipse) with proper configuration.

---

## 🧠 Use Cases

- Academic research involving commit analysis across domains like VR/AR.
- Repository health inspection by commit history patterns.
- Refactoring detection or commit frequency tracking.

---

## ✍️ Sample Output

The tool generates an Excel file with the following columns:

| Repository | Commit ID | Commit Message | Files Changed | Date | Commit URL |
|------------|-----------|----------------|----------------|------|------------|

---

## 🛠️ Technologies Used

- Java 17
- Maven
- JGit (Git integration)
- Apache POI (Excel generation)
- GitHub REST API
- JavaFX (UI under development)

---

## 🧪 Upcoming Features

- JavaFX desktop app with real-time logs and form input
- Search history persistence
- Refactoring pattern identification
- CSV export support
- GitHub rate-limit smart retry

---


## 👨‍💻 Contributors

- **Neeraj Saini** - [@neeraj3071](https://github.com/neeraj3071)
- Open to collaborations! Feel free to fork and contribute 🚀

---

## 🔗 Related Projects

If you like this, check out:
- [AI-Powered Test Case Generator](https://github.com/neeraj3071/AI-Powered-Test-Case-Generator)
