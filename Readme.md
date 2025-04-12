# Git Research Tool

The Git Research Tool is a Java-based application designed to analyze GitHub repositories, extract commit data, and generate comprehensive reports. It facilitates research and analysis by automating the collection of commit information from specified repositories.

## Features

- **Repository Analysis:** Connects to GitHub repositories to extract commit histories.
- **Report Generation:** Compiles commit data into structured reports for further analysis.
- **User Authentication:** Utilizes GitHub tokens for authenticated access to repositories.

## Prerequisites

Before using the Git Research Tool, ensure you have the following:

- **Java Development Kit (JDK):** The application is developed in Java; ensure JDK is installed on your system.
- **GitHub Personal Access Token:** Necessary for authenticated access to GitHub repositories.

## Installation

### Clone the Repository:
```bash
git clone https://github.com/neeraj3071/Git-Research-Tool.git
```

### Navigate to the Project Directory:
```bash
cd Git-Research-Tool
```

### Build the Project:
Use Maven to build the project:
```bash
mvn clean install
```

## Configuration

### Repository URLs:
List the URLs of the GitHub repositories you wish to analyze in the `VR_Project_List.txt` file, each on a new line.

## Usage

### Run the Application:
Execute the compiled JAR file:
```bash
java -jar target/GitResearch.jar
```

### View Reports:
After execution, reports will be generated in the `reports` directory.

## Project Structure

- `.mvn/` - Maven wrapper files.
- `src/` - Source code of the application.
- `target/` - Compiled classes and packaged JAR files.
- `reports/` - Directory where generated reports are stored.
- `pom.xml` - Maven project configuration file.


