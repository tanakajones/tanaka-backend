package com.urban.settlement.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * Service to automate the ML environment setup on Spring Boot startup.
 * Creates virtual environment, installs dependencies, and runs training if configured.
 */
@Service
public class MLInitializationService {

    private static final Logger logger = LoggerFactory.getLogger(MLInitializationService.class);

    @Value("${ml.setup.enabled:true}")
    private boolean setupEnabled;

    @Value("${ml.setup.scripts-dir:ml_scripts}")
    private String scriptsDirName;

    @Value("${python.executable:python}")
    private String pythonBaseCmd;

    @Value("${ml.train.on-startup:false}")
    private boolean trainOnStartup;

    @PostConstruct
    public void initialize() {
        if (!setupEnabled) {
            logger.info("ML Setup is disabled via configuration.");
            return;
        }

        // Run setup in a separate thread to avoid blocking startup
        Executors.newSingleThreadExecutor().execute(this::runSetup);
    }

    private void runSetup() {
        try {
            logger.info("Starting automated ML environment setup...");

            String workingDir = System.getProperty("user.dir");
            Path scriptsPath = Paths.get(workingDir, scriptsDirName);
            Path venvPath = scriptsPath.resolve("venv");
            
            // 1. Create venv if missing
            if (!Files.exists(venvPath)) {
                logger.info("Virtual environment not found. Creating venv in {}", venvPath);
                runProcess(scriptsPath.toFile(), pythonBaseCmd, "-m", "venv", "venv");
            } else {
                logger.info("Virtual environment already exists.");
            }

            // 2. Determine venv python path
            String os = System.getProperty("os.name").toLowerCase();
            String venvPython;
            if (os.contains("win")) {
                venvPython = venvPath.resolve("Scripts").resolve("python.exe").toString();
            } else {
                venvPython = venvPath.resolve("bin").resolve("python").toString();
            }

            // 3. Install dependencies
            File requirementsFile = scriptsPath.resolve("requirements.txt").toFile();
            if (requirementsFile.exists()) {
                logger.info("Installing/Updating dependencies from requirements.txt...");
                runProcess(scriptsPath.toFile(), venvPython, "-m", "pip", "install", "-r", "requirements.txt");
            }

            // 4. Run training if requested
            if (trainOnStartup) {
                logger.info("Running waste classification training script on startup...");
                runProcess(scriptsPath.toFile(), venvPython, "train_waste_yolo.py");
            }

            logger.info("ML Environment Setup completed successfully.");

        } catch (Exception e) {
            logger.error("Error during ML environment initialization: {}", e.getMessage(), e);
        }
    }

    private void runProcess(File workingDir, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        // Log output in real-time
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("ML Setup Output: {}", line);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            logger.warn("ML Setup process exited with non-zero code: {}", exitCode);
        }
    }
}
