package com.example.mcp.tool.development;

import com.example.mcp.exception.ToolExecutionException;
import com.example.mcp.model.McpModels;
import com.example.mcp.resource.ResourceLimiter;
import com.example.mcp.security.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProjectAnalysisToolTest {

    @Mock
    private SecurityContext mockSecurityContext;
    @Mock
    private ResourceLimiter mockResourceLimiter;
    @Mock
    private ResourceLimiter.ResourcePermit mockResourcePermit;

    @Spy
    private ProjectAnalysisTool toolSpy;

    @Captor
    private ArgumentCaptor<FileVisitor<Path>> fileVisitorCaptor;
    @Captor
    private ArgumentCaptor<Integer> maxDepthCaptor;
    @Captor
    private ArgumentCaptor<Set<FileVisitOption>> fileVisitOptionsCaptor;

    @TempDir
    Path tempDir;
    Path mockProjectPath;

    @BeforeEach
    void setUp() throws IOException, ToolExecutionException {
        mockProjectPath = tempDir.resolve("sampleproject");
        Files.createDirectories(mockProjectPath);

        when(mockSecurityContext.validatePath(anyString())).thenAnswer(invocation -> {
            String pathStr = invocation.getArgument(0);
            if (".".equals(pathStr) || mockProjectPath.toString().equals(pathStr)) return mockProjectPath;
            return tempDir.resolve(pathStr);
        });
        when(mockSecurityContext.isFileAllowed(any(Path.class))).thenReturn(true);
        doNothing().when(mockSecurityContext).validateFileSize(any(Path.class));
        when(mockResourceLimiter.acquireFileOperation("project_analysis")).thenReturn(mockResourcePermit);

        ProjectAnalysisTool realTool = new ProjectAnalysisTool(mockSecurityContext, mockResourceLimiter);
        toolSpy = spy(realTool);
    }

    private Map<String, Object> createArgs(String path) {
        return Map.of("path", path);
    }

    private Map<String, Object> createArgs(String path, Map<String, Object> additionalParams) {
        Map<String, Object> args = new java.util.HashMap<>(additionalParams);
        args.put("path", path);
        return args;
    }

    private void setupMockFilesWalk(MockedStatic<Files> mockedFiles, Path root, Map<String, String> fileContents, List<Path> dirsToCreate) throws IOException {
        // Default behavior for Files methods
        mockedFiles.when(() -> Files.exists(any(Path.class))).thenAnswer(inv -> java.nio.file.Files.exists(inv.getArgument(0)));
        mockedFiles.when(() -> Files.isDirectory(any(Path.class))).thenAnswer(inv -> java.nio.file.Files.isDirectory(inv.getArgument(0)));
        mockedFiles.when(() -> Files.isRegularFile(any(Path.class))).thenAnswer(inv -> java.nio.file.Files.isRegularFile(inv.getArgument(0)));
        mockedFiles.when(() -> Files.readString(any(Path.class))).thenReturn(""); // Default empty content
        mockedFiles.when(() -> Files.lines(any(Path.class))).thenReturn(Stream.empty()); // Default empty stream for lines

        // Create specified directories and files with content
        for (Path dirToCreate : dirsToCreate) {
            java.nio.file.Files.createDirectories(dirToCreate); // Use real Files to create temp structure
        }
        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            Path filePath = root.resolve(entry.getKey());
            java.nio.file.Files.createDirectories(filePath.getParent()); // Ensure parent dirs exist
            java.nio.file.Files.writeString(filePath, entry.getValue());
            mockedFiles.when(() -> Files.readString(eq(filePath))).thenReturn(entry.getValue());
            mockedFiles.when(() -> Files.lines(eq(filePath))).thenReturn(entry.getValue().lines());
        }
        
        // Mock walkFileTree to simulate visiting these paths
        mockedFiles.when(() -> Files.walkFileTree(eq(root), anySet(), anyInt(), any(FileVisitor.class)))
            .thenAnswer(invocation -> {
                FileVisitor<Path> visitor = invocation.getArgument(3);
                // Simulate traversal
                visitor.preVisitDirectory(root, mock(BasicFileAttributes.class));
                for (Path dir : dirsToCreate) {
                    if (dir.startsWith(root)) { // Only visit dirs under the current root
                         visitor.preVisitDirectory(dir, mock(BasicFileAttributes.class));
                         visitor.postVisitDirectory(dir, null);
                    }
                }
                for (String filePathStr : fileContents.keySet()) {
                    Path filePath = root.resolve(filePathStr);
                     if (filePath.startsWith(root)) { // Only visit files under the current root
                        visitor.visitFile(filePath, mock(BasicFileAttributes.class));
                    }
                }
                visitor.postVisitDirectory(root, null);
                return root;
            });
    }


    @Test
    void testToolInstantiationAndBasicInfo() {
        assertNotNull(toolSpy);
        assertEquals("analyze_project", toolSpy.getName());
        assertNotNull(toolSpy.getDescription());
        assertTrue(toolSpy.getInputSchema().containsKey("type"));
        verify(mockResourcePermit, never()).close(); // Ensure permit not closed prematurely
    }

    @Test
    void testValidateInputs_validPath() {
        Map<String, Object> args = createArgs(mockProjectPath.toString());
        assertDoesNotThrow(() -> toolSpy.validateInputs(args));
        verify(mockSecurityContext).validatePath(mockProjectPath.toString());
    }

    @Test
    void testValidateInputs_pathNotDirectory() throws IOException {
        Path filePath = Files.createFile(tempDir.resolve("not_a_dir.txt"));
        when(mockSecurityContext.validatePath(filePath.toString())).thenReturn(filePath);
        Map<String, Object> args = createArgs(filePath.toString());
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Project path is not a directory:"));
    }
    
    @Test
    void testValidateInputs_pathDoesNotExist() {
        Path nonExistentPath = tempDir.resolve("nonexistentdir");
        when(mockSecurityContext.validatePath(nonExistentPath.toString())).thenReturn(nonExistentPath);
        Map<String, Object> args = createArgs(nonExistentPath.toString());
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Project path is not a directory:"));
    }

    @Test
    void testValidateInputs_maxDepth_tooLow() {
        Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("max_depth", 0));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("max_depth must be between 1 and 10.", ex.getMessage());
    }

    @Test
    void testValidateInputs_maxDepth_tooHigh() {
        Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("max_depth", 11));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertEquals("max_depth must be between 1 and 10.", ex.getMessage());
    }

    @Test
    void testValidateInputs_invalidAnalysisType() {
        Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("analysis_type", "invalid_type"));
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.validateInputs(args));
        assertTrue(ex.getMessage().contains("Invalid analysis_type: invalid_type"));
    }

    @Test
    void testAcquireResourcesAndPermitClosureOnSuccess() throws ToolExecutionException {
        // Test that permit is acquired and closed on a successful run
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            setupMockFilesWalk(mockedFiles, mockProjectPath, Collections.emptyMap(), Collections.emptyList());
            Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("analysis_type", "structure"));
            toolSpy.execute(args); // This will call acquireResources internally via BaseMcpTool
            verify(mockResourceLimiter).acquireFileOperation("project_analysis");
            verify(mockResourcePermit).close();
        }
    }
    
    @Test
    void testSecurity_RootPathNotAllowed() {
        when(mockSecurityContext.isFileAllowed(mockProjectPath)).thenReturn(false);
        Map<String, Object> args = createArgs(mockProjectPath.toString());
        ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.execute(args));
        assertTrue(ex.getMessage().contains("Access to project path is denied by security policy: " + mockProjectPath));
    }
    
    @Test
    void testSecurity_ProjectFileNotAllowedForTypeDetection() throws Exception {
        Path pomXmlPath = mockProjectPath.resolve("pom.xml");
        Files.createFile(pomXmlPath); // Create the file in tempDir
        when(mockSecurityContext.isFileAllowed(pomXmlPath)).thenReturn(false); // Explicitly deny this specific file

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            setupMockFilesWalk(mockedFiles, mockProjectPath, Map.of("pom.xml", ""), Collections.emptyList());
            Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("analysis_type", "structure"));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
            
            assertFalse(result.isError);
            assertFalse(result.content.get(0).text.contains("Project Types: maven"));
            verify(mockSecurityContext, atLeastOnce()).isFileAllowed(pomXmlPath);
        }
    }

    @Test
    void testSecurity_DependencyFile_ValidateFileSizeThrows() throws Exception {
        Path pomXmlPath = mockProjectPath.resolve("pom.xml");
        Files.writeString(pomXmlPath, "<project></project>"); // Maven project
        
        // isFileAllowed is true by default, but validateFileSize will throw
        doThrow(new ToolExecutionException("pom.xml is too large!"))
            .when(mockSecurityContext).validateFileSize(pomXmlPath);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            setupMockFilesWalk(mockedFiles, mockProjectPath, Map.of("pom.xml", "<project></project>"), Collections.emptyList());
            
            Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("analysis_type", "dependencies"));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
            
            assertFalse(result.isError);
            String textOutput = result.content.get(0).text;
            // Expect "maven" type to be detected, but no dependencies listed due to read failure.
            // The current ProjectAnalysisTool logs a warning and continues if read fails.
            // The output should reflect that no dependencies were parsed for maven.
            assertTrue(textOutput.contains("maven (0):")); // or similar, indicating no deps found
            verify(mockSecurityContext).validateFileSize(pomXmlPath);
        }
    }

    @Test
    void testSecurity_VisitorSkipsDisallowedFile() throws Exception {
        Path allowedFile = mockProjectPath.resolve("allowed.txt");
        Path disallowedFile = mockProjectPath.resolve("disallowed.txt");
        Files.createFile(allowedFile);
        Files.createFile(disallowedFile);

        when(mockSecurityContext.isFileAllowed(disallowedFile)).thenReturn(false);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            // Mock Files.walkFileTree to simulate visiting these files
            mockedFiles.when(() -> Files.walkFileTree(eq(mockProjectPath), anySet(), anyInt(), fileVisitorCaptor.capture()))
                .thenAnswer(invocation -> {
                    FileVisitor<Path> visitor = fileVisitorCaptor.getValue();
                    visitor.preVisitDirectory(mockProjectPath, mock(BasicFileAttributes.class));
                    visitor.visitFile(allowedFile, mock(BasicFileAttributes.class));
                    visitor.visitFile(disallowedFile, mock(BasicFileAttributes.class)); // This should be skipped by visitor logic
                    visitor.postVisitDirectory(mockProjectPath, null);
                    return mockProjectPath;
                });
            setupMockFilesWalk(mockedFiles, mockProjectPath, Collections.emptyMap(), Collections.emptyList()); // Base setup

            Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("analysis_type", "metrics"));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);

            assertFalse(result.isError);
            // Only allowedFile should be counted
            assertTrue(result.content.get(0).text.contains("Total Files: 1"));
            verify(mockSecurityContext, times(1)).isFileAllowed(allowedFile);
            verify(mockSecurityContext, times(1)).isFileAllowed(disallowedFile);
        }
    }
    
    @Test
    void testMaxDepthIsPassedToWalkFileTree() throws Exception {
        int expectedMaxDepth = 3;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            setupMockFilesWalk(mockedFiles, mockProjectPath, Collections.emptyMap(), Collections.emptyList());
            
            Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("max_depth", expectedMaxDepth));
            toolSpy.execute(args);

            mockedFiles.verify(() -> Files.walkFileTree(eq(mockProjectPath), anySet(), maxDepthCaptor.capture(), any(FileVisitor.class)));
            assertEquals(expectedMaxDepth, maxDepthCaptor.getValue());
        }
    }
    
    // --- Project Type Detection Tests ---
    @Test
    void testProjectTypeDetection_Gradle() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            setupMockFilesWalk(mockedFiles, mockProjectPath, Map.of("build.gradle", ""), Collections.emptyList());
            Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("analysis_type", "structure"));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
            assertTrue(result.content.get(0).text.contains("Project Types: gradle"));
        }
    }

    @Test
    void testProjectTypeDetection_Npm() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            setupMockFilesWalk(mockedFiles, mockProjectPath, Map.of("package.json", ""), Collections.emptyList());
            Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("analysis_type", "structure"));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
            assertTrue(result.content.get(0).text.contains("Project Types: npm"));
        }
    }

    @Test
    void testProjectTypeDetection_Python() throws Exception {
         try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            setupMockFilesWalk(mockedFiles, mockProjectPath, Map.of("requirements.txt", ""), Collections.emptyList());
            Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("analysis_type", "structure"));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
            assertTrue(result.content.get(0).text.contains("Project Types: python"));
        }
    }

    // --- Dependency Extraction Tests ---
    @Test
    void testDependencyExtraction_Gradle_Simple() throws Exception {
        String gradleContent = "dependencies { implementation 'com.google.guava:guava:30.0-jre' }";
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            setupMockFilesWalk(mockedFiles, mockProjectPath, Map.of("build.gradle", gradleContent), Collections.emptyList());
            Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("analysis_type", "dependencies"));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
            String textOutput = result.content.get(0).text;
            assertTrue(textOutput.contains("gradle (1)"));
            assertTrue(textOutput.contains("com.google.guava:guava:30.0-jre")); // Version not separated for gradle in current impl
        }
    }

    @Test
    void testDependencyExtraction_Npm_Simple() throws Exception {
        String packageJsonContent = "{ \"name\": \"test-project\", \"dependencies\": { \"lodash\": \"^4.17.21\" } }";
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            setupMockFilesWalk(mockedFiles, mockProjectPath, Map.of("package.json", packageJsonContent), Collections.emptyList());
            Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("analysis_type", "dependencies"));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
            String textOutput = result.content.get(0).text;
            assertTrue(textOutput.contains("npm (1)"));
            assertTrue(textOutput.contains("lodash : ^4.17.21"));
        }
    }

    @Test
    void testDependencyExtraction_Python_Simple() throws Exception {
        String requirementsContent = "requests>=2.25.1\n# A comment\npandas==1.1.5";
         try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            setupMockFilesWalk(mockedFiles, mockProjectPath, Map.of("requirements.txt", requirementsContent), Collections.emptyList());
            Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("analysis_type", "dependencies"));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
            String textOutput = result.content.get(0).text;
            assertTrue(textOutput.contains("python (2)"));
            assertTrue(textOutput.contains("requests : =2.25.1")); // Note: current regex captures the comparator
            assertTrue(textOutput.contains("pandas : =1.1.5"));
        }
    }
    
    // --- Test includeTestFiles flag ---
    @Test
    void testIncludeTestFiles_False() throws Exception {
        Path testDir = mockProjectPath.resolve("test");
        Path testFile = testDir.resolve("MyTest.java");
        Path mainFile = mockProjectPath.resolve("Main.java");

        Map<String, String> fileContents = Map.of(
            "test/MyTest.java", "public class MyTest {}",
            "Main.java", "public class Main {}"
        );
        List<Path> dirsToCreate = List.of(testDir);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            setupMockFilesWalk(mockedFiles, mockProjectPath, fileContents, dirsToCreate);
            // Files.lines will be called for Main.java but not MyTest.java
            mockedFiles.when(() -> Files.lines(mainFile)).thenReturn(Stream.of("line1"));
            // MyTest.java should not have Files.lines called on it.

            Map<String, Object> args = createArgs(mockProjectPath.toString(),
                Map.of("analysis_type", "metrics", "include_test_files", false));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);

            String textOutput = result.content.get(0).text;
            assertTrue(textOutput.contains("Total Files: 1")); // Only Main.java
            assertTrue(textOutput.contains("Code Files: 1"));
            verify(mockedFiles, times(1)).lines(mainFile);
            verify(mockedFiles, never()).lines(testFile); // Ensure test file lines are not counted
        }
    }

    // --- Output Formatting for other analysis_type ---
    @Test
    void testOutputFormatting_Full() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            setupMockFilesWalk(mockedFiles, mockProjectPath, Map.of("pom.xml", "<project><artifactId>Test</artifactId></project>"), Collections.emptyList());
            Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("analysis_type", "full"));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
            String textOutput = result.content.get(0).text;
            assertTrue(textOutput.contains("Project Analysis Report"));
            assertTrue(textOutput.contains("📁 Project Structure"));
            assertTrue(textOutput.contains("📦 Dependencies"));
            assertTrue(textOutput.contains("📊 Code Metrics"));
        }
    }
    
    @Test
    void testOutputFormatting_Summary_NonMarkdown() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            setupMockFilesWalk(mockedFiles, mockProjectPath, Map.of("pom.xml", "<project><artifactId>Test</artifactId></project>"), Collections.emptyList());
            Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("analysis_type", "summary", "generate_summary", false));
            McpModels.CallToolResponse.CallToolResult result = toolSpy.execute(args);
            String textOutput = result.content.get(0).text;
            assertTrue(textOutput.contains("📋 Project Summary"));
            assertFalse(textOutput.startsWith("# PROJECT_SUMMARY.md"));
        }
    }


    // --- Error Handling ---
    @Test
    void testErrorHandling_IOExceptionDuringAnalysis_ReadString() throws Exception {
        Path pomXmlPath = mockProjectPath.resolve("pom.xml");
        // File exists for type detection, but reading it for dependencies fails
        Files.createFile(pomXmlPath); 

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            setupMockFilesWalk(mockedFiles, mockProjectPath, Collections.emptyMap(), Collections.emptyList()); // Basic walk
            // Specific mock for pom.xml for type detection (empty content is fine)
            mockedFiles.when(() -> Files.readString(eq(pomXmlPath))).thenThrow(new IOException("Failed to read pom.xml for deps"));

            Map<String, Object> args = createArgs(mockProjectPath.toString(), Map.of("analysis_type", "dependencies"));
            ToolExecutionException ex = assertThrows(ToolExecutionException.class, () -> toolSpy.execute(args));
            assertTrue(ex.getMessage().contains("Project analysis IO failed: Failed to read pom.xml for deps"));
        }
    }
}
