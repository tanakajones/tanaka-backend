package com.urban.settlement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for executing Python scripts and capturing JSON output
 * Uses ProcessBuilder to run Python ML scripts from Spring Boot
 */
@Service
public class PythonExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(PythonExecutorService.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Value("${python.executable:python3}")
    private String pythonPath;

    /**
     * Execute Python script and return JSON output
     * 
     * @param scriptPath Absolute path to Python script
     * @param args       Command line arguments for the script
     * @return JSON output as JsonNode
     * @throws IOException if script execution fails
     */
    public JsonNode executePythonScript(String scriptPath, String... args) throws IOException {
        return executePythonScript(scriptPath, DEFAULT_TIMEOUT_SECONDS, args);
    }

    /**
     * Execute Python script with custom timeout
     * 
     * @param scriptPath     Absolute path to Python script
     * @param timeoutSeconds Timeout in seconds
     * @param args           Command line arguments
     * @return JSON output as JsonNode
     * @throws IOException if script execution fails
     */
    public JsonNode executePythonScript(String scriptPath, int timeoutSeconds, String... args)
            throws IOException {

        List<String> command = new ArrayList<>();
        command.add(pythonPath);
        command.add(scriptPath);
        command.addAll(Arrays.asList(args));

        logger.info("Executing Python script: {}", String.join(" ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = null;
        StringBuilder output = new StringBuilder();
        StringBuilder errors = new StringBuilder();

        try {
            process = processBuilder.start();

            // Read stdout
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("Python output: {}", line);
                }
            }

            // Wait for process to complete
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                throw new IOException("Python script execution timed out after " +
                        timeoutSeconds + " seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("Python script failed with exit code " + exitCode +
                        ". Output: " + output.toString());
            }

            // Parse JSON output
            String jsonOutput = output.toString().trim();
            if (jsonOutput.isEmpty()) {
                throw new IOException("Python script produced no output");
            }

            logger.info("Python script completed successfully");
            return objectMapper.readTree(jsonOutput);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Python script execution interrupted", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * Execute Python script with JSON input via stdin
     * 
     * @param scriptPath Path to Python script
     * @param inputJson  JSON input to pass to script
     * @param args       Additional command line arguments
     * @return JSON output
     * @throws IOException if execution fails
     */
    public JsonNode executePythonScriptWithInput(String scriptPath, String inputJson, String... args)
            throws IOException {

        List<String> command = new ArrayList<>();
        command.add(pythonPath);
        command.add(scriptPath);
        command.addAll(Arrays.asList(args));

        logger.info("Executing Python script with JSON input: {}", scriptPath);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = null;
        StringBuilder output = new StringBuilder();

        try {
            process = processBuilder.start();

            // Write JSON to stdin
            process.getOutputStream().write(inputJson.getBytes());
            process.getOutputStream().flush();
            process.getOutputStream().close();

            // Read output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean completed = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                throw new IOException("Python script timed out");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("Python script failed: " + output.toString());
            }

            return objectMapper.readTree(output.toString().trim());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Execution interrupted", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
